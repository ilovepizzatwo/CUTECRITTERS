package com.cutecritters.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Fluffy fox cub. Digs up small gifts for its owner. */
public class FoxCubEntity extends CritterEntity {
    public FoxCubEntity(EntityType<? extends CritterEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected ItemStack makeGift(ServerPlayer owner, int bond) {
        int roll = this.random.nextInt(10);
        if (roll < 5) {
            return new ItemStack(Items.SWEET_BERRIES, 2 + this.random.nextInt(3));
        }
        if (roll < 8) {
            return new ItemStack(Items.STRING, 1 + this.random.nextInt(2));
        }
        if (roll < 9) {
            return new ItemStack(Items.BONE);
        }
        return new ItemStack(Items.RABBIT_FOOT);
    }
}
