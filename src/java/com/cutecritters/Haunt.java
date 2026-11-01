package com.cutecritters;

import com.cutecritters.block.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Small "something is wrong" events: the world changes behind your back,
 * a friend seems to leave the game, your own words come back to you,
 * a storm rolls in. Everything stays inside the game and only the target player sees it.
 */
public final class Haunt {
    public enum Habit {
        NONE, HOME, DARK, OUT
    }

    private static final String KEY_HOME = "cutecrittersHabitHome";
    private static final String KEY_DARK = "cutecrittersHabitDark";
    private static final String KEY_OUT = "cutecrittersHabitOut";
    private static final int FAKE_CHAT_LINES = 6;

    private static final Map<UUID, String> LAST_CHAT = new HashMap<>();

    private Haunt() {
    }

    // ------------------------------------------------------------------ chat memory

    public static void rememberChat(UUID id, String message) {
        if (message != null && !message.isBlank()) {
            LAST_CHAT.put(id, message);
        }
    }

    public static void forget(UUID id) {
        LAST_CHAT.remove(id);
        PENDING.remove(id);
    }

    public static void clear() {
        LAST_CHAT.clear();
        PENDING.clear();
    }

    // ------------------------------------------------------------------ habits (the fear adapts to how you play)

    private static CompoundTag tag(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, 10)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Called once per second: remembers whether you are at home, in the dark, or out in the open. */
    public static void trackHabits(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        BlockPos home = player.getRespawnPosition();
        boolean atHome = home != null && player.getRespawnDimension() == player.level().dimension()
                && home.distSqr(pos) < 24.0D * 24.0D;
        boolean dark = player.level().getBrightness(LightLayer.SKY, pos) < 4;
        String key = atHome ? KEY_HOME : dark ? KEY_DARK : KEY_OUT;
        CompoundTag tag = tag(player);
        tag.putInt(key, tag.getInt(key) + 1);
    }

    public static Habit habit(ServerPlayer player) {
        CompoundTag tag = tag(player);
        int home = tag.getInt(KEY_HOME);
        int dark = tag.getInt(KEY_DARK);
        int out = tag.getInt(KEY_OUT);
        int total = home + dark + out;
        if (total < 300) {
            return Habit.NONE;
        }
        int max = Math.max(home, Math.max(dark, out));
        if (max * 100 < total * 40) {
            return Habit.NONE;
        }
        return max == home ? Habit.HOME : max == dark ? Habit.DARK : Habit.OUT;
    }

    // ------------------------------------------------------------------ the world changes behind your back

    private static boolean inView(ServerPlayer player, BlockPos pos) {
        Vec3 to = Vec3.atCenterOf(pos).subtract(player.getEyePosition());
        double length = to.length();
        if (length < 0.001D) {
            return true;
        }
        return player.getViewVector(1.0F).normalize().dot(to.scale(1.0D / length)) > 0.55D;
    }

    public static boolean worldChange(ServerPlayer player) {
        if (!Config.WORLD_CHANGES.get()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        BlockPos center = player.blockPosition();

        List<BlockPos> torches = new ArrayList<>();
        List<BlockPos> doors = new ArrayList<>();
        for (BlockPos scan : BlockPos.betweenClosed(center.offset(-12, -4, -12), center.offset(12, 5, 12))) {
            BlockState state = level.getBlockState(scan);
            if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
                if (!inView(player, scan)) {
                    torches.add(scan.immutable());
                }
            } else if (state.is(BlockTags.WOODEN_DOORS)
                    && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    && !state.getValue(DoorBlock.OPEN)) {
                if (!inView(player, scan)) {
                    doors.add(scan.immutable());
                }
            }
        }

        boolean useDoors = !doors.isEmpty() && (torches.isEmpty() || random.nextBoolean());
        if (useDoors) {
            BlockPos pos = doors.get(random.nextInt(doors.size()));
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock door) {
                door.setOpen(null, level, state, pos, true);
                return true;
            }
            return false;
        }
        if (torches.isEmpty()) {
            return false;
        }
        Collections.shuffle(torches);
        int count = Math.min(torches.size(), 1 + random.nextInt(3));
        for (int i = 0; i < count; i++) {
            BlockPos pos = torches.get(i);
            BlockState old = level.getBlockState(pos);
            BlockState burnt = old.is(Blocks.WALL_TORCH)
                    ? ModBlocks.BURNT_WALL_TORCH.get().defaultBlockState().setValue(WallTorchBlock.FACING, old.getValue(WallTorchBlock.FACING))
                    : ModBlocks.BURNT_TORCH.get().defaultBlockState();
            level.setBlock(pos, burnt, 3);
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D, 6, 0.1D, 0.15D, 0.1D, 0.01D);
            FearUtil.playAt(player, SoundEvents.FIRE_EXTINGUISH, Vec3.atCenterOf(pos), 0.8F, 0.7F);
        }
        return true;
    }

    // ------------------------------------------------------------------ in-game "friends" tricks

    private static ServerPlayer otherPlayer(ServerPlayer player, MinecraftServer server) {
        List<ServerPlayer> others = new ArrayList<>();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other != player) {
                others.add(other);
            }
        }
        return others.isEmpty() ? null : others.get(player.getRandom().nextInt(others.size()));
    }

    /** True if other players are online but none of them is close to this one. */
    public static boolean isIsolated(ServerPlayer player, MinecraftServer server) {
        boolean anyOther = false;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player) {
                continue;
            }
            anyOther = true;
            if (other.level() == player.level() && other.distanceToSqr(player) < 48.0D * 48.0D) {
                return false;
            }
        }
        return anyOther;
    }

    /** A friend seems to leave the game, then to come back a little later. */
    public static boolean fakeLeave(ServerPlayer player, MinecraftServer server) {
        if (!Config.FAKE_LEAVE.get()) {
            return false;
        }
        ServerPlayer other = otherPlayer(player, server);
        if (other == null) {
            return false;
        }
        Component name = other.getDisplayName();
        player.sendSystemMessage(Component.translatable("multiplayer.player.left", name).withStyle(ChatFormatting.YELLOW));
        FearDirector.schedule(server, 120 + player.getRandom().nextInt(180), () -> {
            if (!player.isRemoved()) {
                player.sendSystemMessage(Component.translatable("multiplayer.player.joined", name)
                        .withStyle(ChatFormatting.YELLOW));
            }
        });
        return true;
    }

    /** A friend's name says one strange line (off by default, see the config). */
    public static boolean fakeChat(ServerPlayer player, MinecraftServer server) {
        if (!Config.FAKE_CHAT.get()) {
            return false;
        }
        ServerPlayer other = otherPlayer(player, server);
        if (other == null) {
            return false;
        }
        int line = player.getRandom().nextInt(FAKE_CHAT_LINES);
        player.sendSystemMessage(Component.translatable("chat.type.text", other.getDisplayName(),
                Component.translatable("message.cutecritters.fakechat." + line)));
        return true;
    }

    /** Your own last chat message comes back to you, as if somebody else said it. */
    public static boolean echoChat(ServerPlayer player, MinecraftServer server) {
        if (!Config.ECHO_CHAT.get()) {
            return false;
        }
        String message = LAST_CHAT.get(player.getUUID());
        if (message == null) {
            return false;
        }
        FearDirector.schedule(server, 40 + player.getRandom().nextInt(60), () -> {
            if (!player.isRemoved()) {
                FearUtil.playAt(player, SoundEvents.PLAYER_BREATH, FearUtil.behind(player, 2.0D), 0.8F, 0.6F);
                player.sendSystemMessage(Component.translatable("chat.type.text", player.getDisplayName(),
                        Component.literal(message)));
            }
        });
        LAST_CHAT.remove(player.getUUID());
        return true;
    }

    // ------------------------------------------------------------------ weather

    public static boolean storm(ServerPlayer player) {
        if (!Config.WEATHER.get()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (level.isThundering()) {
            return false;
        }
        level.setWeatherParameters(0, 20 * 150, true, true);
        return true;
    }

    // ------------------------------------------------------------------ habits report

    public static String habitReport(ServerPlayer player) {
        CompoundTag tag = tag(player);
        return "Habit: " + habit(player) + " (at home " + tag.getInt(KEY_HOME) + " s, in the dark "
                + tag.getInt(KEY_DARK) + " s, outside " + tag.getInt(KEY_OUT) + " s)";
    }

    // ------------------------------------------------------------------ questions from the dark (answer in chat)

    private record Pending(int question, long deadline) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final int QUESTIONS = 6;
    private static final int ANSWER_SECONDS = 40;

    private static final Pattern NO = Pattern.compile("(^|[^\\p{L}])(нет|не|no|nope|nah)([^\\p{L}]|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern YES = Pattern.compile("(^|[^\\p{L}])(да|ага|угу|yes|yeah|yep|один|одна|alone|останусь)([^\\p{L}]|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static Component voice(Component text) {
        return Component.translatable("chat.type.text", Component.literal("???"), text)
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
    }

    public static boolean hasPending(UUID id) {
        return PENDING.containsKey(id);
    }

    /** Something asks a question and waits for the player to answer in chat. */
    public static boolean ask(ServerPlayer player, MinecraftServer server) {
        if (!Config.ASK_CHAT.get() || PENDING.containsKey(player.getUUID())) {
            return false;
        }
        int question = player.getRandom().nextInt(QUESTIONS);
        PENDING.put(player.getUUID(), new Pending(question, player.level().getGameTime() + 20L * ANSWER_SECONDS));
        player.sendSystemMessage(voice(Component.translatable("message.cutecritters.ask." + question)));
        player.displayClientMessage(Component.translatable("message.cutecritters.ask.hint")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        FearUtil.playAt(player, SoundEvents.PLAYER_BREATH, FearUtil.behind(player, 1.2D), 1.0F, 0.5F);
        return true;
    }

    /** Called every second: reminds the player, and punishes silence. */
    public static void checkPending(ServerPlayer player) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        long left = (pending.deadline() - player.level().getGameTime()) / 20L;
        if (left <= 0L) {
            PENDING.remove(player.getUUID());
            player.sendSystemMessage(voice(Component.translatable("message.cutecritters.silence")));
            FearDirector.silenceHaunt(player);
        } else if (left == 25L || left == 10L) {
            player.displayClientMessage(Component.translatable("message.cutecritters.ask.hint")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
            FearUtil.playToSelf(player, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.9F);
        }
    }

    /** Called for every chat message. If the player was asked something, this is the answer. */
    public static void onAnswer(ServerPlayer player, String text, MinecraftServer server) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null || text == null) {
            return;
        }
        boolean no = NO.matcher(text).find();
        boolean yes = !no && YES.matcher(text).find();
        String shown = text.length() > 24 ? text.substring(0, 24) : text;
        int question = pending.question();

        Component reply;
        Runnable consequence;
        switch (question) {
            case 0 -> {
                reply = Component.translatable(yes ? "message.cutecritters.reply.0.yes" : "message.cutecritters.reply.0.no");
                consequence = yes ? () -> FearDirector.stalkerNow(player) : () -> FearDirector.ambientNow(player);
            }
            case 1 -> {
                reply = Component.translatable("message.cutecritters.reply.1", Component.literal(shown));
                consequence = () -> FearDirector.ambientNow(player);
            }
            case 2 -> {
                reply = Component.translatable("message.cutecritters.reply.2");
                consequence = () -> FearDirector.ambientNow(player);
            }
            case 3 -> {
                reply = Component.translatable("message.cutecritters.reply.3");
                consequence = () -> FearDirector.darkNow(player);
            }
            case 4 -> {
                reply = Component.translatable(yes ? "message.cutecritters.reply.4.yes" : "message.cutecritters.reply.4.no");
                consequence = yes ? () -> Bond.add(player, 2) : () -> FearDirector.mimicNow(player);
            }
            default -> {
                reply = Component.translatable("message.cutecritters.reply.5");
                consequence = () -> {
                    if (!fakeLeave(player, server)) {
                        echoChat(player, server);
                    }
                };
            }
        }

        FearDirector.schedule(server, 40 + player.getRandom().nextInt(40), () -> {
            if (!player.isRemoved()) {
                player.sendSystemMessage(voice(reply));
                consequence.run();
            }
        });
    }
}
