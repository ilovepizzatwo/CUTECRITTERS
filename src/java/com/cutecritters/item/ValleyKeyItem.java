package com.cutecritters.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** One of the three keys of the valley. The kind (0 hearth, 1 hush, 2 depth) is stored in the "Key" tag. */
public class ValleyKeyItem extends Item {
    public static final int KINDS = 3;

    public ValleyKeyItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(int kind) {
        ItemStack stack = new ItemStack(ModItems.VALLEY_KEY.get());
        stack.getOrCreateTag().putInt("Key", Math.floorMod(kind, KINDS));
        return stack;
    }

    /** The kind of key, or -1 if the stack is not a key. */
    public static int kindOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ValleyKeyItem)) {
            return -1;
        }
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.floorMod(tag.getInt("Key"), KINDS);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return "item.cutecritters.valley_key." + Math.max(0, kindOf(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cutecritters.valley_key.tip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
