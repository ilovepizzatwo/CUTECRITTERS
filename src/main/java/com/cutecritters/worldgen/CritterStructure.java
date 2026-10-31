package com.cutecritters.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A normal jigsaw structure that first looks at the ground it is about to stand on.
 * It gives up (nothing is built there) when the footprint has water in it or when the
 * ground differs by more than {@code maxDiff} blocks, so buildings no longer end up half inside
 * a mountain, in a pond or hanging over a canyon.
 *
 * It reads exactly the same JSON as {@code minecraft:jigsaw}; only the "type" differs.
 */
public class CritterStructure extends Structure {
    private static final int STEP = 3;

    private final JigsawStructure inner;
    private final int sizeX;
    private final int sizeZ;
    private final int maxDiff;
    private final AtomicReference<StructureType<CritterStructure>> kind;

    private CritterStructure(JigsawStructure inner, int sizeX, int sizeZ, int maxDiff,
                             AtomicReference<StructureType<CritterStructure>> kind) {
        super(new Structure.StructureSettings(inner.biomes(), inner.spawnOverrides(), inner.step(),
                inner.terrainAdaptation()));
        this.inner = inner;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.maxDiff = maxDiff;
        this.kind = kind;
    }

    /** Creates a structure type for a template of the given footprint. */
    public static StructureType<CritterStructure> make(int sizeX, int sizeZ, int maxDiff) {
        AtomicReference<StructureType<CritterStructure>> ref = new AtomicReference<>();
        // The structure codec dispatches on a MapCodec; a plain xmap'ed Codec would be read as a
        // wrapped value ("Not a JSON object: null"), so the map codec is unwrapped first.
        MapCodec<JigsawStructure> jigsawMap =
                ((MapCodec.MapCodecCodec<JigsawStructure>) JigsawStructure.CODEC).codec();
        Codec<CritterStructure> codec = jigsawMap.xmap(
                jigsaw -> new CritterStructure(jigsaw, sizeX, sizeZ, maxDiff, ref),
                structure -> structure.inner).codec();
        StructureType<CritterStructure> type = () -> codec;
        ref.set(type);
        return type;
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        // The start chunk itself is often a slope or a pond. Instead of giving up at once, look for
        // a flat dry spot among the nearby chunks (closest first) and build there.
        for (int[] off : OFFSETS) {
            Structure.GenerationContext ctx = off[0] == 0 && off[1] == 0 ? context
                    : new Structure.GenerationContext(context.registryAccess(), context.chunkGenerator(),
                    context.biomeSource(), context.randomState(), context.structureTemplateManager(),
                    context.seed(), new ChunkPos(context.chunkPos().x + off[0], context.chunkPos().z + off[1]),
                    context.heightAccessor(), context.validBiome());
            if (!groundIsSuitable(ctx)) {
                continue;
            }
            Optional<Structure.GenerationStub> stub = this.inner.findGenerationPoint(ctx);
            if (stub.isPresent()) {
                return stub;
            }
        }
        return Optional.empty();
    }

    private static final java.util.List<int[]> OFFSETS = buildOffsets();

    private static java.util.List<int[]> buildOffsets() {
        java.util.List<int[]> list = new java.util.ArrayList<>();
        for (int dx = -6; dx <= 6; dx += 2) {
            for (int dz = -6; dz <= 6; dz += 2) {
                list.add(new int[]{dx, dz});
            }
        }
        list.sort(java.util.Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return list;
    }

    @Override
    public StructureType<?> type() {
        return this.kind.get();
    }

    private boolean groundIsSuitable(Structure.GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x0 = chunk.getMinBlockX();
        int z0 = chunk.getMinBlockZ();

        // The jigsaw code turns the template by a random rotation taken from the very same random source.
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(context.seed(), chunk.x, chunk.z);
        Rotation rotation = Rotation.getRandom(random);

        int minX;
        int maxX;
        int minZ;
        int maxZ;
        switch (rotation) {
            case CLOCKWISE_90 -> {
                minX = x0 - (this.sizeZ - 1);
                maxX = x0;
                minZ = z0;
                maxZ = z0 + this.sizeX - 1;
            }
            case CLOCKWISE_180 -> {
                minX = x0 - (this.sizeX - 1);
                maxX = x0;
                minZ = z0 - (this.sizeZ - 1);
                maxZ = z0;
            }
            case COUNTERCLOCKWISE_90 -> {
                minX = x0;
                maxX = x0 + this.sizeZ - 1;
                minZ = z0 - (this.sizeX - 1);
                maxZ = z0;
            }
            default -> {
                minX = x0;
                maxX = x0 + this.sizeX - 1;
                minZ = z0;
                maxZ = z0 + this.sizeZ - 1;
            }
        }

        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int x = minX; x <= maxX + STEP; x += STEP) {
            for (int z = minZ; z <= maxZ + STEP; z += STEP) {
                int px = Math.min(x, maxX);
                int pz = Math.min(z, maxZ);
                int floor = context.chunkGenerator().getBaseHeight(px, pz, Heightmap.Types.OCEAN_FLOOR_WG,
                        context.heightAccessor(), context.randomState());
                int surface = context.chunkGenerator().getBaseHeight(px, pz, Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState());
                if (surface > floor) {
                    return false; // there is water (or lava) on top of the ground here
                }
                low = Math.min(low, floor);
                high = Math.max(high, floor);
                if (high - low > this.maxDiff) {
                    return false;
                }
            }
        }
        return true;
    }
}
