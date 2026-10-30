package com.cutecritters;

import com.cutecritters.entity.ModEntities;
import com.cutecritters.entity.WatcherEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class FearUtil {
    private FearUtil() {
    }

    /** True if the entity is inside the player's view cone and not behind a wall. */
    public static boolean isLookingAt(ServerPlayer player, Entity entity, double threshold) {
        Vec3 eye = player.getEyePosition();
        Vec3 to = new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() * 0.85D, entity.getZ()).subtract(eye);
        double dist = to.length();
        if (dist < 0.001D) {
            return true;
        }
        double dot = player.getViewVector(1.0F).normalize().dot(to.scale(1.0D / dist));
        if (dot < threshold) {
            return false;
        }
        return player.hasLineOfSight(entity);
    }

    public static Vec3 horizontalLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return flat.normalize();
    }

    public static Vec3 behind(ServerPlayer player, double distance) {
        return player.position().subtract(horizontalLook(player).scale(distance));
    }

    /** Plays a sound only for this one player, at a position in the world. */
    public static void playAt(ServerPlayer player, SoundEvent sound, Vec3 pos, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.AMBIENT,
                pos.x, pos.y, pos.z, volume, pitch, player.level().getRandom().nextLong()));
    }

    public static void playToSelf(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        playAt(player, sound, player.position(), volume, pitch);
    }

    public static WatcherEntity spawnWatcher(ServerPlayer player, BlockPos pos, boolean stalker, int lifeTicks) {
        ServerLevel level = player.serverLevel();
        WatcherEntity watcher = ModEntities.WATCHER.get().create(level);
        if (watcher == null) {
            return null;
        }
        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        float yaw = (float) (Mth.atan2(player.getZ() - z, player.getX() - x) * (180.0D / Math.PI)) - 90.0F;
        watcher.moveTo(x, pos.getY(), z, yaw, 0.0F);
        watcher.setup(player, stalker, lifeTicks);
        level.addFreshEntity(watcher);
        return watcher;
    }

    /**
     * Finds a free spot to stand on, at a distance from the player.
     * angleOffsetDeg: 0 = in front of the player, 180 = behind.
     * halfConeDeg: how far to either side of that direction the spot may be.
     */
    public static BlockPos findSpot(ServerPlayer player, double min, double max,
                                    double angleOffsetDeg, double halfConeDeg) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        boolean openSky = level.canSeeSky(player.blockPosition());
        int baseY = (int) Math.floor(player.getY());

        for (int i = 0; i < 40; i++) {
            double angle = Math.toRadians(player.getYRot() + angleOffsetDeg
                    + (random.nextDouble() * 2.0D - 1.0D) * halfConeDeg);
            double dist = min + random.nextDouble() * (max - min);
            int x = Mth.floor(player.getX() - Math.sin(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.cos(angle) * dist);

            if (!level.hasChunkAt(new BlockPos(x, baseY, z))) {
                continue;
            }

            if (openSky) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                if (Math.abs(y - baseY) < 14 && isStandable(level, pos)) {
                    return pos;
                }
            }

            for (int dy = 6; dy >= -8; dy--) {
                BlockPos pos = new BlockPos(x, baseY + dy, z);
                if (isStandable(level, pos)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            BlockPos q = pos.above(i);
            BlockState state = level.getBlockState(q);
            if (!state.getCollisionShape(level, q).isEmpty()) {
                return false;
            }
            if (!level.getFluidState(q).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
