package com.cutecritters;

import com.cutecritters.net.EffectPacket;
import com.cutecritters.net.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * The "bond" of a player with the valley. It grows when the player eats cookies,
 * and it makes the scary stages come sooner. Stored in the player's persistent data,
 * so it survives relogging and dying.
 */
public final class Bond {
    private static final String KEY_BOND = "cutecrittersBond";
    private static final String KEY_DOSES = "cutecrittersDoses";
    private static final String KEY_LAST = "cutecrittersLastCookie";
    private static final String KEY_COMEDOWN = "cutecrittersComedown";
    private static final String KEY_NOTES = "cutecrittersNotes";

    /** One cookie is worth this many seconds of "play time" for the stage calculation. */
    public static final int SECONDS_PER_BOND = 90;

    private static final int HIGH_TICKS = 20 * 50;
    private static final int OVERDOSE_TICKS = 20 * 12;
    private static final int DOSES_FOR_OVERDOSE = 5;
    private static final int DOSE_WINDOW_TICKS = 20 * 180;

    private Bond() {
    }

    private static CompoundTag tag(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, 10)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static int get(Player player) {
        return tag(player).getInt(KEY_BOND);
    }

    public static void add(Player player, int amount) {
        CompoundTag tag = tag(player);
        tag.putInt(KEY_BOND, Math.max(0, tag.getInt(KEY_BOND) + amount));
    }

    public static void set(Player player, int value) {
        tag(player).putInt(KEY_BOND, Math.max(0, value));
    }

    // ------------------------------------------------------------------ notes

    public static boolean hasRead(Player player, int note) {
        return (tag(player).getInt(KEY_NOTES) & (1 << note)) != 0;
    }

    public static void markRead(Player player, int note) {
        CompoundTag tag = tag(player);
        tag.putInt(KEY_NOTES, tag.getInt(KEY_NOTES) | (1 << note));
    }

    public static int readCount(Player player) {
        return Integer.bitCount(tag(player).getInt(KEY_NOTES));
    }

    // ------------------------------------------------------------------ cookies

    public static void onCookie(ServerPlayer player) {
        CompoundTag tag = tag(player);
        long now = player.level().getGameTime();

        add(player, 1);

        int doses = tag.getInt(KEY_DOSES);
        if (now - tag.getLong(KEY_LAST) > DOSE_WINDOW_TICKS) {
            doses = 0;
        }
        doses++;
        tag.putInt(KEY_DOSES, doses);
        tag.putLong(KEY_LAST, now);

        if (doses >= DOSES_FOR_OVERDOSE) {
            overdose(player, tag, now);
        } else {
            high(player, tag, now, doses);
        }
    }

    private static void high(ServerPlayer player, CompoundTag tag, long now, int doses) {
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, HIGH_TICKS, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, HIGH_TICKS, 0, false, false, false));
        ModNetwork.sendTo(player, new EffectPacket(EffectPacket.HIGH, HIGH_TICKS));
        tag.putLong(KEY_COMEDOWN, now + HIGH_TICKS);

        String key = doses >= 3 ? "message.cutecritters.high.2" : doses == 2 ? "message.cutecritters.high.1" : "message.cutecritters.high.0";
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
    }

    private static void overdose(ServerPlayer player, CompoundTag tag, long now) {
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, OVERDOSE_TICKS * 2, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));
        ModNetwork.sendTo(player, new EffectPacket(EffectPacket.TRUTH, OVERDOSE_TICKS));
        tag.putInt(KEY_DOSES, 0);
        tag.putLong(KEY_COMEDOWN, now + OVERDOSE_TICKS);
        player.displayClientMessage(Component.translatable("message.cutecritters.truth")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
    }

    /** Called once per second for each player. */
    public static void tick(ServerPlayer player) {
        CompoundTag tag = tag(player);
        long comedown = tag.getLong(KEY_COMEDOWN);
        if (comedown <= 0L || player.level().getGameTime() < comedown) {
            return;
        }
        tag.putLong(KEY_COMEDOWN, 0L);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 0, false, false, false));
        player.displayClientMessage(Component.translatable("message.cutecritters.comedown")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }
}
