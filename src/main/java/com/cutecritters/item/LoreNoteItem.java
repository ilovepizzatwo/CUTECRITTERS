package com.cutecritters.item;

import com.cutecritters.Bond;
import com.cutecritters.FearDirector;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A note from the valley's past. The note number is stored in the item's "Note" tag. */
public class LoreNoteItem extends Item {
    /** Notes #1-#9 are numbered and can be found or brought by a weasel. */
    public static final int NORMAL_COUNT = 9;
    /** Index 9 is the secret, unnumbered note: it is only found in one hidden place. */
    public static final int SECRET = 9;
    public static final int NOTE_COUNT = 10;

    public LoreNoteItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(int note) {
        ItemStack stack = new ItemStack(ModItems.LORE_NOTE.get());
        stack.getOrCreateTag().putInt("Note", Math.floorMod(note, NOTE_COUNT));
        return stack;
    }

    public static int noteOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.floorMod(tag.getInt("Note"), NOTE_COUNT);
    }

    @Override
    public Component getName(ItemStack stack) {
        int note = noteOf(stack);
        if (note == SECRET) {
            return Component.translatable("item.cutecritters.lore_note.secret");
        }
        return Component.translatable("item.cutecritters.lore_note")
                .append(Component.literal(" #" + (note + 1)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            int note = noteOf(stack);
            player.sendSystemMessage(Component.translatable("note.cutecritters." + note + ".title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            player.sendSystemMessage(Component.translatable("note.cutecritters." + note + ".text")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            Bond.markRead(player, note);
            if (note == SECRET && player instanceof ServerPlayer serverPlayer) {
                FearDirector.secretNoteRead(serverPlayer);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
