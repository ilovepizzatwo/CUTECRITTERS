package com.cutecritters;

import com.cutecritters.entity.CritterEntity;
import com.cutecritters.entity.WatcherEntity;
import com.cutecritters.item.LoreNoteItem;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Decides what happens to each player and when.
 *
 * Stage 0 (first minutes):  only cute hearts above animals.
 * Stage 1: strange sounds (steps behind you, breathing, a door far away).
 * Stage 2: a tall figure stands far away and watches you.
 * Stage 3: the figure appears close behind you, animals freeze and stare, darkness and heartbeat.
 * Stage 4: the figure starts hunting you. It only moves when you are not looking at it.
 */
@Mod.EventBusSubscriber(modid = CuteCritters.MODID)
public class FearDirector {
    /** Play time in minutes (before timeScale) at which stages 1..4 begin. */
    private static final double[] STAGE_MINUTES = {8.0D, 16.0D, 28.0D, 40.0D};
    private static final String KEY_SECONDS = "cutecrittersSeconds";

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Frozen> FROZEN = new ArrayList<>();

    /** Emergency switch, changed with /critters on|off. */
    private static boolean paused = false;

    private static class State {
        int ticks;
        int countdown = -1;
        UUID watcher;
    }

    private record Task(int runAt, Runnable action) {
    }

    private record Frozen(Animal animal, ServerPlayer player, long until) {
    }

    // ------------------------------------------------------------------ stages

    public static int stageFor(int seconds) {
        double minutes = seconds / 60.0D * Config.TIME_SCALE.get();
        int stage = 0;
        for (int i = 0; i < STAGE_MINUTES.length; i++) {
            if (minutes >= STAGE_MINUTES[i]) {
                stage = i + 1;
            }
        }
        return stage;
    }

    private static int secondsForStage(int stage) {
        if (stage <= 0) {
            return 0;
        }
        return (int) Math.ceil(STAGE_MINUTES[stage - 1] * 60.0D / Config.TIME_SCALE.get());
    }

    private static int nextDelay(int stage, RandomSource random) {
        return switch (stage) {
            case 1 -> 60 + random.nextInt(80);
            case 2 -> 80 + random.nextInt(90);
            case 3 -> 55 + random.nextInt(65);
            default -> 70 + random.nextInt(80);
        };
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        if (++state.ticks < 20) {
            return;
        }
        state.ticks = 0;
        secondTick(player, state);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        runTasks(server.getTickCount());
        updateFrozen();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.displayClientMessage(
                    Component.translatable("message.cutecritters.welcome").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        STATES.clear();
        TASKS.clear();
        FROZEN.clear();
    }

    // ------------------------------------------------------------------ main loop

    private static void secondTick(ServerPlayer player, State state) {
        CompoundTag root = player.getPersistentData();
        CompoundTag tag = root.getCompound(Player.PERSISTED_NBT_TAG);
        int seconds = tag.getInt(KEY_SECONDS) + 1;
        tag.putInt(KEY_SECONDS, seconds);
        root.put(Player.PERSISTED_NBT_TAG, tag);

        Bond.tick(player);

        if (paused || !Config.ENABLED.get() || player.isSpectator()) {
            return;
        }
        if (player.level().dimension() != Level.OVERWORLD) {
            return;
        }

        RandomSource random = player.getRandom();
        if (state.countdown < 0) {
            state.countdown = 40 + random.nextInt(30);
        }

        // The cute part: hearts above animals nearby.
        if (seconds % 12 == 0) {
            heartsNearby(player, random);
        }

        int stage = stageFor(seconds + Bond.get(player) * Bond.SECONDS_PER_BOND);
        if (stage == 0) {
            return;
        }
        if (state.countdown > 0) {
            state.countdown--;
            return;
        }
        runEvent(player, state, stage, random);
        state.countdown = nextDelay(stage, random);
    }

    private static void runEvent(ServerPlayer player, State state, int stage, RandomSource random) {
        if (stage == 1) {
            ambient(player, random);
            return;
        }
        if (stage == 2) {
            if (random.nextInt(100) < 55) {
                spawnStare(player, state, 26.0D, 38.0D, 0.0D, 60.0D);
            } else {
                ambient(player, random);
            }
            return;
        }
        if (stage >= 4 && random.nextInt(100) < 30) {
            stalker(player, state);
            return;
        }
        int roll = random.nextInt(100);
        if (roll < 25) {
            spawnStare(player, state, 10.0D, 16.0D, 180.0D, 35.0D);
        } else if (roll < 45) {
            animalStare(player, random);
        } else if (roll < 65) {
            darkPulse(player);
        } else if (roll < 85) {
            whisper(player, random);
        } else {
            ambient(player, random);
        }
    }

    // ------------------------------------------------------------------ individual scares

    private static void heartsNearby(ServerPlayer player, RandomSource random) {
        ServerLevel level = player.serverLevel();
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(16.0D));
        if (animals.isEmpty()) {
            return;
        }
        Animal animal = animals.get(random.nextInt(animals.size()));
        level.sendParticles(ParticleTypes.HEART, animal.getX(), animal.getEyeY() + 0.5D, animal.getZ(),
                3, 0.3D, 0.3D, 0.3D, 0.0D);
    }

    private static void ambient(ServerPlayer player, RandomSource random) {
        switch (random.nextInt(5)) {
            case 0 -> FearUtil.playToSelf(player, SoundEvents.AMBIENT_CAVE.value(), 0.9F, 0.8F + random.nextFloat() * 0.4F);
            case 1 -> footsteps(player);
            case 2 -> FearUtil.playAt(player, SoundEvents.WOODEN_DOOR_OPEN,
                    FearUtil.behind(player, 8.0D + random.nextInt(5)), 0.9F, 0.7F);
            case 3 -> FearUtil.playAt(player, SoundEvents.ENDERMAN_STARE,
                    FearUtil.behind(player, 12.0D), 0.7F, 0.5F);
            default -> breathing(player);
        }
    }

    private static void footsteps(ServerPlayer player) {
        Vec3 look = FearUtil.horizontalLook(player);
        SoundEvent step = player.level().getBlockState(player.blockPosition().below()).getSoundType().getStepSound();
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            final double distance = 6.0D - i * 0.8D;
            schedule(server, i * 9, () -> {
                if (!player.isRemoved()) {
                    FearUtil.playAt(player, step, player.position().subtract(look.scale(distance)), 0.7F, 0.85F);
                }
            });
        }
    }

    private static void breathing(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            schedule(server, i * 32, () -> {
                if (!player.isRemoved()) {
                    FearUtil.playAt(player, SoundEvents.PLAYER_BREATH, FearUtil.behind(player, 1.5D), 1.0F, 0.6F);
                }
            });
        }
    }

    private static void heartbeats(ServerPlayer player, int count, int gap) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            final float pitch = 0.8F + i * 0.07F;
            schedule(server, i * gap, () -> {
                if (!player.isRemoved()) {
                    FearUtil.playToSelf(player, SoundEvents.WARDEN_HEARTBEAT, 1.0F, pitch);
                }
            });
        }
    }

    private static void darkPulse(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0, false, false, false));
        heartbeats(player, 7, 18);
    }

    private static void whisper(ServerPlayer player, RandomSource random) {
        Component message = Component.translatable("message.cutecritters.whisper." + random.nextInt(9))
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
        player.displayClientMessage(message, true);
        breathing(player);
    }

    private static void animalStare(ServerPlayer player, RandomSource random) {
        ServerLevel level = player.serverLevel();
        List<Animal> animals = new ArrayList<>(level.getEntitiesOfClass(Animal.class,
                player.getBoundingBox().inflate(28.0D), Animal::isAlive));
        if (animals.isEmpty()) {
            ambient(player, random);
            return;
        }
        Collections.shuffle(animals);
        long until = level.getGameTime() + 220L;
        int count = Math.min(8, animals.size());
        for (int i = 0; i < count; i++) {
            Animal animal = animals.get(i);
            animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 240, 255, false, false, false));
            FROZEN.add(new Frozen(animal, player, until));
        }
        FearUtil.playToSelf(player, SoundEvents.WARDEN_NEARBY_CLOSE, 0.8F, 0.7F);
    }

    private static void spawnStare(ServerPlayer player, State state, double min, double max,
                                   double angle, double cone) {
        if (watcherAlive(player, state)) {
            return;
        }
        BlockPos pos = FearUtil.findSpot(player, min, max, angle, cone);
        if (pos == null) {
            ambient(player, player.getRandom());
            return;
        }
        WatcherEntity watcher = FearUtil.spawnWatcher(player, pos, false, 20 * 45);
        if (watcher != null) {
            state.watcher = watcher.getUUID();
            if (angle > 90.0D) {
                heartbeats(player, 2, 14);
            }
        }
    }

    private static void stalker(ServerPlayer player, State state) {
        if (watcherAlive(player, state)) {
            return;
        }
        BlockPos pos = FearUtil.findSpot(player, 20.0D, 28.0D, 0.0D, 180.0D);
        if (pos == null) {
            return;
        }
        WatcherEntity watcher = FearUtil.spawnWatcher(player, pos, true, 20 * 100);
        if (watcher == null) {
            return;
        }
        state.watcher = watcher.getUUID();
        player.displayClientMessage(Component.translatable("message.cutecritters.stalker")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
        heartbeats(player, 3, 25);
    }

    /** The final scare: the figure appears right in front of the player's face. */
    public static void jumpscare(ServerPlayer player, WatcherEntity watcher) {
        if (!Config.LOUD_SURPRISE.get()) {
            watcher.vanish(true);
            return;
        }
        Vec3 look = FearUtil.horizontalLook(player);
        watcher.scare(player.getX() + look.x * 1.5D, player.getY(), player.getZ() + look.z * 1.5D);

        FearUtil.playToSelf(player, SoundEvents.ENDERMAN_SCREAM, 1.0F, 0.5F);
        FearUtil.playToSelf(player, SoundEvents.WARDEN_ROAR, 0.7F, 1.0F);
        FearUtil.playToSelf(player, SoundEvents.ENDERMAN_SCREAM, 0.8F, 0.8F);

        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false, false));

        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 15));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("message.cutecritters.scare")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));

        MinecraftServer server = player.getServer();
        if (server != null) {
            schedule(server, 28, () -> {
                if (!watcher.isRemoved()) {
                    watcher.vanish(true);
                }
            });
        }
    }

    private static boolean watcherAlive(ServerPlayer player, State state) {
        if (state.watcher == null) {
            return false;
        }
        Entity entity = player.serverLevel().getEntity(state.watcher);
        if (entity != null && entity.isAlive()) {
            return true;
        }
        state.watcher = null;
        return false;
    }

    // ------------------------------------------------------------------ scheduler and frozen animals

    private static void schedule(MinecraftServer server, int delayTicks, Runnable action) {
        TASKS.add(new Task(server.getTickCount() + delayTicks, action));
    }

    private static void runTasks(int now) {
        if (TASKS.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<>();
        Iterator<Task> iterator = TASKS.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (task.runAt() <= now) {
                due.add(task);
                iterator.remove();
            }
        }
        for (Task task : due) {
            try {
                task.action().run();
            } catch (Exception ignored) {
                // a scare that fails should never crash the server
            }
        }
    }

    private static void updateFrozen() {
        Iterator<Frozen> iterator = FROZEN.iterator();
        while (iterator.hasNext()) {
            Frozen frozen = iterator.next();
            Animal animal = frozen.animal();
            if (animal.isRemoved() || !animal.isAlive() || frozen.player().isRemoved()
                    || animal.level().getGameTime() > frozen.until()) {
                iterator.remove();
                continue;
            }
            animal.getNavigation().stop();
            animal.lookAt(EntityAnchorArgument.Anchor.EYES, frozen.player().getEyePosition());
        }
    }

    // ------------------------------------------------------------------ commands (for testing, ops only)

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("critters")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("off").executes(ctx -> {
                    paused = true;
                    return reply(ctx, "Cute Critters+: special surprises are OFF");
                }))
                .then(Commands.literal("on").executes(ctx -> {
                    paused = false;
                    return reply(ctx, "Cute Critters+: special surprises are ON");
                }))
                .then(Commands.literal("watcher").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    spawnStare(player, stateOf(player), 12.0D, 18.0D, 0.0D, 30.0D);
                    return 1;
                }))
                .then(Commands.literal("stalker").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    stalker(player, stateOf(player));
                    return 1;
                }))
                .then(Commands.literal("scare").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    BlockPos pos = FearUtil.findSpot(player, 3.0D, 4.0D, 0.0D, 20.0D);
                    if (pos != null) {
                        WatcherEntity watcher = FearUtil.spawnWatcher(player, pos, true, 20 * 10);
                        if (watcher != null) {
                            jumpscare(player, watcher);
                        }
                    }
                    return 1;
                }))
                .then(Commands.literal("bond")
                        .then(Commands.literal("get").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return reply(ctx, "Bond: " + Bond.get(player) + ", notes read: " + Bond.readCount(player));
                        }))
                        .then(Commands.literal("set")
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, 1000)).executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    Bond.set(player, IntegerArgumentType.getInteger(ctx, "n"));
                                    return reply(ctx, "Bond set to " + Bond.get(player));
                                }))))
                .then(Commands.literal("gift").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    int count = 0;
                    for (CritterEntity critter : player.serverLevel().getEntitiesOfClass(CritterEntity.class,
                            player.getBoundingBox().inflate(32.0D))) {
                        if (critter.isOwnedBy(player)) {
                            critter.forceGift();
                            count++;
                        }
                    }
                    return reply(ctx, "Gifts coming from " + count + " critter(s) within a second");
                }))
                .then(Commands.literal("note")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, LoreNoteItem.NOTE_COUNT)).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            player.getInventory().add(LoreNoteItem.create(IntegerArgumentType.getInteger(ctx, "n") - 1));
                            return 1;
                        })))
                .then(Commands.literal("stage")
                        .then(Commands.argument("n", IntegerArgumentType.integer(0, 4)).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int stage = IntegerArgumentType.getInteger(ctx, "n");
                            CompoundTag root = player.getPersistentData();
                            CompoundTag tag = root.getCompound(Player.PERSISTED_NBT_TAG);
                            tag.putInt(KEY_SECONDS, secondsForStage(stage));
                            root.put(Player.PERSISTED_NBT_TAG, tag);
                            stateOf(player).countdown = 5;
                            return reply(ctx, "Cute Critters+: stage set to " + stage);
                        }))));
    }

    private static State stateOf(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
