package com.cutecritters.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Cloud sheep. Calms its owner nearby (slow regeneration) and gives soft wool. */
public class CloudSheepEntity extends CritterEntity {
    public CloudSheepEntity(EntityType<? extends CritterEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void ability(ServerPlayer owner, int bond) {
        if (this.distanceToSqr(owner) < 64.0D && owner.getHealth() < owner.getMaxHealth()
                && this.tickCount % 100 == 0) {
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, true, false, true));
        }
    }

    @Override
    protected ItemStack makeGift(ServerPlayer owner, int bond) {
        return new ItemStack(Items.WHITE_WOOL, 1 + this.random.nextInt(2));
    }
}
