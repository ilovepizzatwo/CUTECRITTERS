package com.cutecritters.entity;

import com.cutecritters.FearDirector;
import com.cutecritters.FearUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * A tall silent figure. It is spawned and controlled by the FearDirector,
 * only exists for one player at a time and is never saved to disk.
 *
 * Passive mode: stands still and stares. Vanishes if you look at it for a while,
 * walk up to it, or hit it.
 * Stalker mode: only moves while you are NOT looking at it.
 */
public class WatcherEntity extends PathfinderMob {
    private UUID targetId;
    private boolean stalker;
    private boolean scaring;
    private int lookTicks;
    private int maxLife = 20 * 45;
    private int heartbeatTimer;

    public WatcherEntity(EntityType<? extends WatcherEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    public void setup(ServerPlayer target, boolean stalker, int lifeTicks) {
        this.targetId = target.getUUID();
        this.stalker = stalker;
        this.maxLife = lifeTicks;
    }

    public void scare(double x, double y, double z) {
        this.scaring = true;
        this.getNavigation().stop();
        this.setNoAi(true);
        this.setNoGravity(true);
        this.moveTo(x, y, z, this.getYRot(), 0.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new OpenDoorGoal(this, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        ServerPlayer target = this.findTarget();
        if (target == null || this.tickCount > this.maxLife) {
            this.vanish(false);
            return;
        }

        this.faceTarget(target);
        if (this.scaring) {
            return;
        }

        double dist = this.distanceTo(target);
        boolean seen = FearUtil.isLookingAt(target, this, this.stalker ? 0.72D : 0.90D);

        if (this.stalker) {
            this.tickStalker(target, dist, seen);
        } else {
            this.tickStare(dist, seen);
        }
    }

    private void tickStare(double dist, boolean seen) {
        this.getNavigation().stop();
        if (seen) {
            this.lookTicks++;
        } else if (this.lookTicks > 0 && this.tickCount % 4 == 0) {
            this.lookTicks--;
        }
        if (this.lookTicks > 28 || dist < 8.0D) {
            this.vanish(true);
        }
    }

    private void tickStalker(ServerPlayer target, double dist, boolean seen) {
        this.heartbeat(target, dist);

        if (dist <= 2.8D) {
            FearDirector.jumpscare(target, this);
            return;
        }

        if (seen) {
            this.getNavigation().stop();
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        } else if (this.tickCount % 10 == 0 || this.getNavigation().isDone()) {
            this.getNavigation().moveTo(target, 1.0D);
        }
    }

    private void heartbeat(ServerPlayer target, double dist) {
        if (--this.heartbeatTimer > 0) {
            return;
        }
        if (dist > 36.0D) {
            this.heartbeatTimer = 40;
            return;
        }
        float volume = dist < 12.0D ? 1.0F : 0.7F;
        float pitch = 0.9F + (float) Math.max(0.0D, 24.0D - dist) * 0.015F;
        this.heartbeatTimer = dist < 8.0D ? 10 : dist < 14.0D ? 16 : dist < 22.0D ? 24 : 34;
        FearUtil.playToSelf(target, SoundEvents.WARDEN_HEARTBEAT, volume, pitch);
    }

    private ServerPlayer findTarget() {
        if (this.targetId == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(this.targetId);
        if (player == null || player.level() != serverLevel || !player.isAlive() || player.isSpectator()) {
            return null;
        }
        return player;
    }

    private void faceTarget(ServerPlayer target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double dy = target.getEyeY() - this.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, horizontal) * (180.0D / Math.PI)));

        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
        this.setXRot(pitch);
    }

    public void vanish(boolean withSound) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 1.5D, this.getZ(), 20, 0.3D, 1.0D, 0.3D, 0.01D);
            if (withSound) {
                serverLevel.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.HOSTILE, 0.5F, 0.4F);
            }
        }
        this.discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && !this.isRemoved() && source.getEntity() instanceof Player) {
            this.vanish(true);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }
}
