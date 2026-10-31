package com.cutecritters.entity;

import com.cutecritters.FearUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.UUID;

/**
 * The Mimic: wears the skin of a player and repeats everything that player does,
 * a few seconds late and some distance away. It disappears if you stare at it
 * for too long, walk up to it, or hit it. Never saved to disk.
 */
public class MimicEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** How many ticks late the mimic repeats the player. */
    private static final int DELAY = 70;

    private record Step(double dx, double dz, float yaw, float pitch, boolean jump, boolean sneak) {
    }

    private final ArrayDeque<Step> trail = new ArrayDeque<>();
    private double lastX;
    private double lastZ;
    private boolean tracking;
    private boolean ownerWasOnGround = true;
    private int lookTicks;
    private int maxLife = 20 * 45;

    public MimicEntity(EntityType<? extends MimicEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_OWNER, Optional.empty());
    }

    public UUID getOwnerId() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public void setup(ServerPlayer owner, int lifeTicks) {
        this.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
        this.maxLife = lifeTicks;
    }

    @Override
    public void tick() {
        if (this.level().isClientSide) {
            super.tick();
            return;
        }

        ServerPlayer owner = this.findOwner();
        if (owner == null || this.tickCount > this.maxLife) {
            this.vanish(false);
            return;
        }

        if (!this.tracking) {
            this.lastX = owner.getX();
            this.lastZ = owner.getZ();
            this.tracking = true;
        }
        boolean jump = this.ownerWasOnGround && !owner.onGround() && owner.getDeltaMovement().y > 0.1D;
        this.ownerWasOnGround = owner.onGround();
        this.trail.addLast(new Step(owner.getX() - this.lastX, owner.getZ() - this.lastZ,
                owner.getYRot(), owner.getXRot(), jump, owner.isShiftKeyDown()));
        this.lastX = owner.getX();
        this.lastZ = owner.getZ();

        if (this.trail.size() > DELAY) {
            Step step = this.trail.pollFirst();
            if (step != null) {
                this.setDeltaMovement(step.dx(), this.getDeltaMovement().y, step.dz());
                this.setYRot(step.yaw());
                this.yRotO = step.yaw();
                this.setYHeadRot(step.yaw());
                this.setYBodyRot(step.yaw());
                this.setXRot(step.pitch());
                this.setShiftKeyDown(step.sneak());
                if (step.jump() && this.onGround()) {
                    this.jumpFromGround();
                }
            }
        }

        super.tick();

        boolean seen = FearUtil.isLookingAt(owner, this, 0.92D);
        if (seen) {
            this.lookTicks++;
        } else if (this.lookTicks > 0 && this.tickCount % 4 == 0) {
            this.lookTicks--;
        }
        if (this.lookTicks > 45 || this.distanceTo(owner) < 4.5D) {
            this.vanish(true);
        }
    }

    private ServerPlayer findOwner() {
        UUID id = this.getOwnerId();
        if (id == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(id);
        if (player == null || player.level() != serverLevel || !player.isAlive() || player.isSpectator()) {
            return null;
        }
        return player;
    }

    public void vanish(boolean withSound) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 1.0D, this.getZ(), 16, 0.3D, 0.8D, 0.3D, 0.01D);
            if (withSound) {
                serverLevel.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.HOSTILE, 0.4F, 0.3F);
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
    protected void registerGoals() {
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
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
