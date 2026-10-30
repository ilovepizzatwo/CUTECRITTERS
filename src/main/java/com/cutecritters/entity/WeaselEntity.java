package com.cutecritters.entity;

import com.cutecritters.item.LoreNoteItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Weasel messenger. Brings the notes of the valley. The higher the owner's bond,
 * the deeper (and more unsettling) the notes it can bring.
 */
public class WeaselEntity extends CritterEntity {
    public WeaselEntity(EntityType<? extends CritterEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected ItemStack makeGift(ServerPlayer owner, int bond) {
        int unlocked = Math.min(LoreNoteItem.NOTE_COUNT, 1 + bond / 2);
        return LoreNoteItem.create(this.random.nextInt(unlocked));
    }
}
