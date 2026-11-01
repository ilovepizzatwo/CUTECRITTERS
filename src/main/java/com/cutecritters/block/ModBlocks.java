package com.cutecritters.block;

import com.cutecritters.CuteCritters;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CuteCritters.MODID);

    private static BlockBehaviour.Properties burnt() {
        return BlockBehaviour.Properties.copy(Blocks.TORCH).lightLevel(state -> 0);
    }

    public static final RegistryObject<Block> BURNT_TORCH = BLOCKS.register("burnt_torch",
            () -> new BurntTorchBlock(burnt()));

    public static final RegistryObject<Block> BURNT_WALL_TORCH = BLOCKS.register("burnt_wall_torch",
            () -> new BurntWallTorchBlock(burnt()));

    /** The core of the altar: right-click to end the story, break it to break the ritual. */
    public static final RegistryObject<Block> ALTAR_CORE = BLOCKS.register("altar_core",
            () -> new AltarCoreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(8.0F, 3600000.0F).lightLevel(state -> 8)));
}
