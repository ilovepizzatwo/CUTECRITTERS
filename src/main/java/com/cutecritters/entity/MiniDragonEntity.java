package com.cutecritters.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Little dragon. Keeps its owner warm (thaws powder snow freezing) and singes hostile mobs that get too close to the owner. */
public class MiniDragonEntity extends CritterEntity {
    public MiniDragonEntity(EntityType<? extends CritterEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void ability(ServerPlayer owner, int bond) {
        if (this.distanceToSqr(owner) < 100.0D && owner.getTicksFrozen() > 0) {
            owner.setTicksFrozen(0);
        }
        if (this.isOrderedToSit() || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        AABB area = owner.getBoundingBox().inflate(5.0D);
        for (Monster monster : serverLevel.getEntitiesOfClass(Monster.class, area)) {
            if (!monster.fireImmune() && !monster.isOnFire()) {
                monster.setSecondsOnFire(4);
                serverLevel.sendParticles(ParticleTypes.FLAME, this.getX(), this.getEyeY(), this.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.02D);
                break;
            }
        }
    }

    @Override
    protected ItemStack makeGift(ServerPlayer owner, int bond) {
        if (this.random.nextInt(6) == 0) {
            return new ItemStack(Items.BLAZE_POWDER);
        }
        return new ItemStack(this.random.nextBoolean() ? Items.COAL : Items.CHARCOAL, 1 + this.random.nextInt(2));
    }
}
