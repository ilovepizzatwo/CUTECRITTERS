package com.cutecritters.entity;

import com.cutecritters.FearUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A harmless black silhouette that haunts the ruined village. It wanders slowly and watches you.
 * If you stare at it, come close or hit it, it dissolves and re-forms somewhere else nearby a minute later.
 */
public class ShadowEntity extends PathfinderMob {
    private int stare;
    private int hidden;
    private BlockPos home;

    public ShadowEntity(EntityType<? extends ShadowEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.5D, 0.003F));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 18.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.home != null) {
            tag.putInt("HomeX", this.home.getX());
            tag.putInt("HomeY", this.home.getY());
            tag.putInt("HomeZ", this.home.getZ());
        }
        tag.putInt("Hidden", this.hidden);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) {
            this.home = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
        }
        this.hidden = tag.getInt("Hidden");
        if (this.hidden > 0) {
            this.setInvisible(true);
            this.setNoAi(true);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.home == null) {
            this.home = this.blockPosition();
        }
        if (this.hidden > 0) {
            if (--this.hidden == 0) {
                this.reappear();
            }
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player nearest = serverLevel.getNearestPlayer(this, 24.0D);
        if (!(nearest instanceof ServerPlayer player) || player.isSpectator()) {
            this.stare = 0;
            return;
        }
        boolean seen = FearUtil.isLookingAt(player, this, 0.93D);
        if (seen) {
            this.stare++;
        } else if (this.stare > 0 && this.tickCount % 4 == 0) {
            this.stare--;
        }
        if (this.stare > 35 || this.distanceTo(player) < 3.5F) {
            this.fade();
        }
    }

    private void fade() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 1.0D, this.getZ(), 14, 0.25D, 0.7D, 0.25D, 0.01D);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.HOSTILE, 0.35F, 0.3F);
        }
        this.getNavigation().stop();
        this.stare = 0;
        this.hidden = 20 * (40 + this.random.nextInt(50));
        this.setInvisible(true);
        this.setNoAi(true);
    }

    private void reappear() {
        if (this.level() instanceof ServerLevel serverLevel && this.home != null) {
            for (int attempt = 0; attempt < 10; attempt++) {
                BlockPos candidate = this.home.offset(this.random.nextInt(25) - 12, 0, this.random.nextInt(25) - 12);
                if (!serverLevel.hasChunkAt(candidate)) {
                    continue;
                }
                BlockPos top = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate);
                BlockState ground = serverLevel.getBlockState(top.below());
                if (ground.isSolid() && serverLevel.getFluidState(top).isEmpty()) {
                    this.moveTo(top.getX() + 0.5D, top.getY(), top.getZ() + 0.5D, this.random.nextFloat() * 360.0F, 0.0F);
                    break;
                }
            }
        }
        this.setInvisible(false);
        this.setNoAi(false);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && this.hidden == 0 && source.getEntity() instanceof Player) {
            this.fade();
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
