package com.cutecritters.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Little dragon. Keeps its owner warm: thaws the freezing effect from powder snow. */
public class MiniDragonEntity extends CritterEntity {
    public MiniDragonEntity(EntityType<? extends CritterEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void ability(ServerPlayer owner, int bond) {
        if (this.distanceToSqr(owner) < 100.0D && owner.getTicksFrozen() > 0) {
            owner.setTicksFrozen(0);
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
