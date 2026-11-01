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
    private static final String KEY_HIGH_END = "cutecrittersHighEnd";
    private static final String KEY_PEAK = "cutecrittersPeak";
    private static final String KEY_LAST_GAIN = "cutecrittersLastGain";
    private static final String KEY_NOTES = "cutecrittersNotes";

    /** One cookie is worth this many seconds of "play time" for the stage calculation. */
    public static final int SECONDS_PER_BOND = 90;

    private static final int BASE_TICKS = 20 * 50;
    private static final int EXTEND_TICKS = 20 * 30;
    private static final int MAX_TICKS = 20 * 240;
    private static final int FLASH_TICKS = 70;
    private static final int FLASH_EVERY = 5;
    private static final int DOSE_WINDOW_TICKS = 20 * 180;
    /** The bond grows by 1 at most once per this many ticks, however many cookies are eaten. */
    private static final int BOND_COOLDOWN_TICKS = 20 * 90;

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

        if (now - tag.getLong(KEY_LAST_GAIN) >= BOND_COOLDOWN_TICKS || tag.getLong(KEY_LAST_GAIN) == 0L) {
            add(player, 1);
            tag.putLong(KEY_LAST_GAIN, now);
            Endings.checkGift(player);
        }

        int doses = tag.getInt(KEY_DOSES);
        if (now - tag.getLong(KEY_LAST) > DOSE_WINDOW_TICKS) {
            doses = 0;
            tag.putInt(KEY_PEAK, 0);
        }
        doses++;
        tag.putInt(KEY_DOSES, doses);
        tag.putLong(KEY_LAST, now);
        tag.putInt(KEY_PEAK, Math.max(tag.getInt(KEY_PEAK), doses));

        // Every cookie stretches the high out, up to a cap.
        long previousEnd = tag.getLong(KEY_HIGH_END);
        long end = previousEnd > now ? previousEnd + EXTEND_TICKS : now + BASE_TICKS;
        end = Math.min(end, now + MAX_TICKS);
        tag.putLong(KEY_HIGH_END, end);
        int remaining = (int) (end - now);
        int level = Math.min(doses, 8);

        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, remaining, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, remaining, level >= 3 ? 1 : 0, false, false, false));
        if (level >= 5) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, false, false));
        }

        if (doses % FLASH_EVERY == 0) {
            // A short flash of "the truth", then the high carries on, stronger.
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, false));
            ModNetwork.sendTo(player, new EffectPacket(EffectPacket.TRUTH, FLASH_TICKS, level, remaining));
            player.displayClientMessage(Component.translatable("message.cutecritters.truth")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        } else {
            ModNetwork.sendTo(player, new EffectPacket(EffectPacket.HIGH, remaining, level, 0));
            String key = doses >= 3 ? "message.cutecritters.high.2"
                    : doses == 2 ? "message.cutecritters.high.1" : "message.cutecritters.high.0";
            player.displayClientMessage(Component.translatable(key)
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
        }
    }

    /** Called once per second for each player. */
    public static void tick(ServerPlayer player) {
        CompoundTag tag = tag(player);
        long end = tag.getLong(KEY_HIGH_END);
        if (end <= 0L || player.level().getGameTime() < end) {
            return;
        }
        int peak = tag.getInt(KEY_PEAK);
        tag.putLong(KEY_HIGH_END, 0L);
        tag.putInt(KEY_DOSES, 0);
        tag.putInt(KEY_PEAK, 0);

        // The harder the high, the colder the world afterwards.
        int duration = Math.min(100 + peak * 50, 700);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, duration, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, peak >= 5 ? 1 : 0, false, false, false));
        if (peak >= 5) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, false, false, false));
        }
        String key = peak >= 4 ? "message.cutecritters.comedown.hard" : "message.cutecritters.comedown";
        player.displayClientMessage(Component.translatable(key)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }
}
