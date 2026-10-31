package com.cutecritters.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/** Firefly. Flies next to its owner and gives night vision in the dark. */
public class FireflyEntity extends CritterEntity {
    public FireflyEntity(EntityType<? extends CritterEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, true);
    }

    @Override
    protected boolean flies() {
        return true;
    }

    @Override
    protected Goal createWanderGoal() {
        return new WaterAvoidingRandomFlyingGoal(this, 1.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide && !this.isCreepy() && this.random.nextInt(10) == 0) {
            this.level().addParticle(ParticleTypes.GLOW,
                    this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void ability(ServerPlayer owner, int bond) {
        if (this.distanceToSqr(owner) < 100.0D
                && owner.level().getBrightness(LightLayer.BLOCK, owner.blockPosition()) < 5
                && owner.level().getBrightness(LightLayer.SKY, owner.blockPosition()) < 5) {
            owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
        }
    }

    @Override
    protected ItemStack makeGift(ServerPlayer owner, int bond) {
        if (this.random.nextInt(3) == 0) {
            return new ItemStack(Items.GLOW_BERRIES, 1 + this.random.nextInt(3));
        }
        return new ItemStack(Items.GLOWSTONE_DUST, 1 + this.random.nextInt(2));
    }
}
