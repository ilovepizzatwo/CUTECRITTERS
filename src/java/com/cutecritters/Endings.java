package com.cutecritters;

import com.cutecritters.block.AltarCoreBlock;
import com.cutecritters.block.ModBlocks;
import com.cutecritters.entity.MimicEntity;
import com.cutecritters.entity.ShadowEntity;
import com.cutecritters.entity.WatcherEntity;
import com.cutecritters.item.ModItems;
import com.cutecritters.item.ValleyKeyItem;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The five endings of the valley.
 * <ol>
 *   <li>ESCAPE - three keys at the altar, and the player leaves the valley.</li>
 *   <li>BREAK - the altar core is destroyed: the ritual is over for everyone.</li>
 *   <li>STAY - the special cookie, eaten at the altar after an "are you sure?".</li>
 *   <li>REPLACE - the special cookie is fed to a friend, who becomes the Keeper.</li>
 *   <li>SECRET - the recipe book and a cookie are given to the Keeper, after reading every note.</li>
 * </ol>
 * Everything happens inside the game. /critters ending reset undoes it all for testing.
 */
@Mod.EventBusSubscriber(modid = CuteCritters.MODID)
public final class Endings {
    public static final int NONE = 0;
    public static final int ESCAPE = 1;
    public static final int BREAK = 2;
    public static final int STAY = 3;
    public static final int REPLACED = 4; // the friend who became the Keeper
    public static final int FED = 5;      // the one who fed the friend to the altar
    public static final int SECRET = 6;

    /** Bond needed before the special cookie appears in the pocket. */
    public static final int STAY_BOND = 6;
    private static final int CONFIRM_TICKS = 20 * 30;
    private static final int LEASH_RADIUS = 36;
    private static final int LEASH_SECONDS = 60 * 10;

    private static final String KEY_ENDING = "cutecrittersEnding";
    private static final String KEY_COOKIE_GIVEN = "cutecrittersSpecialGiven";
    private static final String KEY_CONFIRM = "cutecrittersConfirm";
    private static final String KEY_LEASH = "cutecrittersLeash";

    private Endings() {
    }

    // ------------------------------------------------------------------ saved world state

    public static class ValleyData extends SavedData {
        boolean calm;
        UUID keeper;

        static ValleyData load(CompoundTag tag) {
            ValleyData data = new ValleyData();
            data.calm = tag.getBoolean("calm");
            if (tag.hasUUID("keeper")) {
                data.keeper = tag.getUUID("keeper");
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("calm", this.calm);
            if (this.keeper != null) {
                tag.putUUID("keeper", this.keeper);
            }
            return tag;
        }

        static ValleyData get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(ValleyData::load, ValleyData::new, "cutecritters_valley");
        }
    }

    private static CompoundTag tag(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, 10)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static int endingOf(Player player) {
        return tag(player).getInt(KEY_ENDING);
    }

    private static void setEnding(Player player, int ending) {
        tag(player).putInt(KEY_ENDING, ending);
    }

    /** True when the valley should leave this player alone: they have an ending, or the valley is calm. */
    public static boolean isFree(ServerPlayer player) {
        if (endingOf(player) != NONE) {
            return true;
        }
        MinecraftServer server = player.getServer();
        return server != null && ValleyData.get(server).calm;
    }

    // ------------------------------------------------------------------ helpers

    public static boolean isSpecialCookie(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.SPECIAL_COOKIE.get());
    }

    public static boolean isRecipeBook(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.WRITTEN_BOOK) || stack.getTag() == null) {
            return false;
        }
        return "Рецепт".equals(stack.getTag().getString("title"))
                && "Хранительница".equals(stack.getTag().getString("author"));
    }

    private static int countKinds(Player player) {
        boolean[] have = new boolean[ValleyKeyItem.KINDS];
        List<ItemStack> all = new ArrayList<>(player.getInventory().items);
        all.addAll(player.getInventory().offhand);
        for (ItemStack stack : all) {
            int kind = ValleyKeyItem.kindOf(stack);
            if (kind >= 0) {
                have[kind] = true;
            }
        }
        int count = 0;
        for (boolean b : have) {
            if (b) {
                count++;
            }
        }
        return count;
    }

    private static void takeKeys(Player player) {
        boolean[] taken = new boolean[ValleyKeyItem.KINDS];
        for (List<ItemStack> list : List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (ItemStack stack : list) {
                int kind = ValleyKeyItem.kindOf(stack);
                if (kind >= 0 && !taken[kind]) {
                    taken[kind] = true;
                    stack.shrink(1);
                }
            }
        }
    }

    public static BlockPos findAltar(Level level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -4, -radius), center.offset(radius, 4, radius))) {
            if (level.getBlockState(pos).getBlock() instanceof AltarCoreBlock) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static void say(ServerPlayer player, String key, ChatFormatting... styles) {
        player.sendSystemMessage(Component.translatable(key).withStyle(styles));
    }

    private static void title(ServerPlayer player, String key, int in, int stay, int out) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable("ending.cutecritters." + key + ".sub").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("ending.cutecritters." + key + ".title").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
    }

    private static void award(ServerPlayer player, String name) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Advancement advancement = server.getAdvancements().getAdvancement(new ResourceLocation(CuteCritters.MODID, name));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "done");
        }
    }

    private static void banish(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> list = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof WatcherEntity || entity instanceof MimicEntity || entity instanceof ShadowEntity) {
                    list.add(entity);
                }
            }
            for (Entity entity : list) {
                level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 1.0D, entity.getZ(), 12, 0.3D, 0.5D, 0.3D, 0.02D);
                entity.discard();
            }
        }
    }

    private static void dawn(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        overworld.setWeatherParameters(24000, 0, false, false);
        overworld.setDayTime(overworld.getDayTime() - overworld.getDayTime() % 24000L + 1000L);
    }

    private static void calm(MinecraftServer server) {
        ValleyData data = ValleyData.get(server);
        data.calm = true;
        data.setDirty();
    }

    // ------------------------------------------------------------------ the altar

    /** Right-click on the altar core with something that is not the special cookie. */
    public static void useAltar(ServerPlayer player, BlockPos pos) {
        ItemStack held = player.getMainHandItem();
        int keyKind = ValleyKeyItem.kindOf(held);

        if (isRecipeBook(held)) {
            secret(player, pos);
            return;
        }
        if (keyKind >= 0) {
            int have = countKinds(player);
            if (have >= ValleyKeyItem.KINDS) {
                escape(player);
            } else {
                player.sendSystemMessage(Component.translatable("altar.cutecritters.keys.partial", have, ValleyKeyItem.KINDS)
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                FearUtil.playToSelf(player, SoundEvents.IRON_DOOR_CLOSE, 0.7F, 0.6F);
            }
            return;
        }
        // Empty hand or anything else: the altar tells what it is waiting for.
        player.sendSystemMessage(Component.translatable("altar.cutecritters.status.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("altar.cutecritters.status.keys", countKinds(player), ValleyKeyItem.KINDS)
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("altar.cutecritters.status.notes", Bond.readCount(player), LoreNoteCount.TOTAL)
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("altar.cutecritters.status.bond", Bond.get(player), STAY_BOND)
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("altar.cutecritters.status.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        FearUtil.playToSelf(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.5F);
    }

    /** Tiny holder so the number of notes lives in one place. */
    private static final class LoreNoteCount {
        static final int TOTAL = com.cutecritters.item.LoreNoteItem.NOTE_COUNT;
    }

    // ------------------------------------------------------------------ 1. escape

    private static void escape(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        takeKeys(player);
        setEnding(player, ESCAPE);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 90, 0, false, false, false));
        FearUtil.playToSelf(player, SoundEvents.PORTAL_TRAVEL, 0.6F, 1.2F);
        title(player, "escape", 20, 80, 30);
        FearDirector.schedule(server, 60, () -> {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            if (player.getRespawnPosition() != null && player.getRespawnDimension() == Level.OVERWORLD) {
                spawn = player.getRespawnPosition();
            }
            int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
            player.teleportTo(overworld, spawn.getX() + 0.5D, y, spawn.getZ() + 0.5D, player.getYRot(), 0.0F);
            player.sendSystemMessage(Component.translatable("ending.cutecritters.escape.text")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            award(player, "end_escape");
        });
        broadcast(server, player, "ending.cutecritters.escape.broadcast");
    }

    // ------------------------------------------------------------------ 2. break the ritual

    /** The core block disappeared. A player nearby is counted as the one who did it. */
    public static void altarBroken(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        ServerPlayer breaker = null;
        double best = 24.0D * 24.0D;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            double d = p.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            if (d < best) {
                best = d;
                breaker = p;
            }
        }
        level.playSound(null, pos, SoundEvents.ENDERMAN_SCREAM, SoundSource.AMBIENT, 2.0F, 0.5F);
        level.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 3, 0.5D, 0.5D, 0.5D, 0.0D);
        if (ValleyData.get(server).calm) {
            return; // the ritual was over already
        }
        calm(server);
        banish(server);
        dawn(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (endingOf(p) == NONE) {
                setEnding(p, BREAK);
            }
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));
            title(p, "break", 10, 80, 30);
            say(p, "ending.cutecritters.break.text", ChatFormatting.GRAY, ChatFormatting.ITALIC);
            award(p, "end_break");
        }
    }

    // ------------------------------------------------------------------ 3. stay

    /** Called when the special cookie is right-clicked. Returns true when eating may begin. */
    public static boolean confirmStay(ServerPlayer player) {
        BlockPos altar = findAltar(player.level(), player.blockPosition(), 6);
        if (altar == null) {
            say(player, "cookie.cutecritters.notHere", ChatFormatting.GRAY, ChatFormatting.ITALIC);
            return false;
        }
        if (Bond.get(player) < STAY_BOND) {
            say(player, "cookie.cutecritters.stranger", ChatFormatting.GRAY, ChatFormatting.ITALIC);
            return false;
        }
        CompoundTag tag = tag(player);
        long now = player.level().getGameTime();
        long asked = tag.getLong(KEY_CONFIRM);
        if (asked > 0L && now - asked <= CONFIRM_TICKS) {
            tag.putLong(KEY_CONFIRM, 0L);
            return true;
        }
        tag.putLong(KEY_CONFIRM, now);
        player.sendSystemMessage(Component.translatable("cookie.cutecritters.sure.0").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("cookie.cutecritters.sure.1").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("cookie.cutecritters.sure.2").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("cookie.cutecritters.sure.3").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        FearUtil.playToSelf(player, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.8F);
        return false;
    }

    public static void stay(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        setEnding(player, STAY);
        applyStay(player);
        title(player, "stay", 30, 100, 40);
        say(player, "ending.cutecritters.stay.text", ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
        FearUtil.playToSelf(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.6F);
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.5D, player.getZ(), 20, 0.6D, 0.6D, 0.6D, 0.02D);
        award(player, "end_stay");
        if (server != null) {
            broadcast(server, player, "ending.cutecritters.stay.broadcast");
        }
    }

    private static void applyStay(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 0, false, false, false));
    }

    // ------------------------------------------------------------------ 4. replacement

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer feeder) || !(event.getTarget() instanceof ServerPlayer friend)) {
            return;
        }
        ItemStack held = feeder.getMainHandItem();
        if (!isSpecialCookie(held) || friend == feeder) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        BlockPos altar = findAltar(feeder.level(), feeder.blockPosition(), 8);
        if (altar == null) {
            say(feeder, "cookie.cutecritters.notHere", ChatFormatting.GRAY, ChatFormatting.ITALIC);
            return;
        }
        if (Bond.get(feeder) < STAY_BOND) {
            say(feeder, "cookie.cutecritters.stranger", ChatFormatting.GRAY, ChatFormatting.ITALIC);
            return;
        }
        CompoundTag tag = tag(feeder);
        long now = feeder.level().getGameTime();
        long asked = tag.getLong(KEY_CONFIRM + "Feed");
        if (asked <= 0L || now - asked > CONFIRM_TICKS) {
            tag.putLong(KEY_CONFIRM + "Feed", now);
            feeder.sendSystemMessage(Component.translatable("cookie.cutecritters.feed.0", friend.getDisplayName())
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            feeder.sendSystemMessage(Component.translatable("cookie.cutecritters.feed.1").withStyle(ChatFormatting.GRAY));
            feeder.sendSystemMessage(Component.translatable("cookie.cutecritters.feed.2").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            FearUtil.playToSelf(feeder, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.8F);
            return;
        }
        tag.putLong(KEY_CONFIRM + "Feed", 0L);
        held.shrink(1);
        replace(feeder, friend, altar);
    }

    private static void replace(ServerPlayer feeder, ServerPlayer friend, BlockPos altar) {
        MinecraftServer server = feeder.getServer();
        if (server == null) {
            return;
        }
        ValleyData data = ValleyData.get(server);
        data.keeper = friend.getUUID();
        data.setDirty();
        calm(server);
        banish(server);
        dawn(server);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p != friend && endingOf(p) == NONE) {
                setEnding(p, p == feeder ? FED : BREAK);
            }
        }
        setEnding(friend, REPLACED);
        tag(friend).putInt(KEY_LEASH, LEASH_SECONDS);
        tag(friend).putInt("cutecrittersLeashX", altar.getX());
        tag(friend).putInt("cutecrittersLeashY", altar.getY());
        tag(friend).putInt("cutecrittersLeashZ", altar.getZ());
        friend.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false, false));
        friend.teleportTo((ServerLevel) friend.level(), altar.getX() + 0.5D, altar.getY() + 2.0D, altar.getZ() + 0.5D,
                friend.getYRot(), 0.0F);
        title(friend, "keeper", 20, 120, 40);
        say(friend, "ending.cutecritters.keeper.text", ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
        FearUtil.playToSelf(friend, SoundEvents.WARDEN_AMBIENT, 1.0F, 0.5F);
        award(friend, "end_keeper");

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p != friend) {
                title(p, "fed", 20, 100, 40);
                say(p, "ending.cutecritters.fed.text", ChatFormatting.GRAY, ChatFormatting.ITALIC);
                award(p, "end_fed");
            }
        }
    }

    // ------------------------------------------------------------------ 5. the secret ending

    private static void secret(ServerPlayer player, BlockPos altar) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        int read = Bond.readCount(player);
        if (read < LoreNoteCount.TOTAL) {
            player.sendSystemMessage(Component.translatable("altar.cutecritters.secret.notEnough", read, LoreNoteCount.TOTAL)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            FearUtil.playToSelf(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.4F);
            return;
        }
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.is(ModItems.CRITTER_COOKIE.get())) {
            player.sendSystemMessage(Component.translatable("altar.cutecritters.secret.noCookie")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        offhand.shrink(1);
        calm(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (endingOf(p) == NONE || endingOf(p) == BREAK) {
                setEnding(p, SECRET);
            }
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 14, 0, false, false, false));
        }
        // She remembers, one line at a time.
        for (int i = 0; i < 6; i++) {
            final int line = i;
            FearDirector.schedule(server, 40 + i * 70, () -> {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.sendSystemMessage(Component.translatable("ending.cutecritters.secret.line." + line)
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
                    FearUtil.playToSelf(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 0.6F + 0.12F * line);
                }
            });
        }
        FearDirector.schedule(server, 40 + 6 * 70, () -> {
            banish(server);
            dawn(server);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.removeEffect(MobEffects.DARKNESS);
                title(p, "secret", 20, 140, 60);
                ItemStack gift = new ItemStack(ModItems.CRITTER_COOKIE.get(), 8);
                if (!p.getInventory().add(gift)) {
                    p.drop(gift, false);
                }
                ((ServerLevel) p.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0D, p.getZ(), 40, 1.0D, 1.0D, 1.0D, 0.0D);
                award(p, "end_secret");
            }
        });
    }

    // ------------------------------------------------------------------ upkeep

    /** The special cookie appears in the pocket of someone who has grown close enough. */
    public static void checkGift(ServerPlayer player) {
        CompoundTag tag = tag(player);
        if (tag.getBoolean(KEY_COOKIE_GIVEN) || Bond.get(player) < STAY_BOND) {
            return;
        }
        tag.putBoolean(KEY_COOKIE_GIVEN, true);
        giveSpecialCookie(player);
        player.sendSystemMessage(Component.translatable("cookie.cutecritters.found").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        FearUtil.playToSelf(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.5F);
    }

    public static void giveSpecialCookie(ServerPlayer player) {
        ItemStack cookie = new ItemStack(ModItems.SPECIAL_COOKIE.get());
        if (!player.getInventory().add(cookie)) {
            player.drop(cookie, false);
        }
    }

    /** Called once per second for each player. */
    public static void secondTick(ServerPlayer player) {
        int ending = endingOf(player);
        if (ending == STAY) {
            applyStay(player);
        } else if (ending == REPLACED) {
            CompoundTag tag = tag(player);
            int left = tag.getInt(KEY_LEASH);
            if (left > 0) {
                tag.putInt(KEY_LEASH, left - 1);
                BlockPos home = new BlockPos(tag.getInt("cutecrittersLeashX"), tag.getInt("cutecrittersLeashY"),
                        tag.getInt("cutecrittersLeashZ"));
                boolean far = player.level().dimension() != Level.OVERWORLD
                        || player.blockPosition().distSqr(home) > (double) LEASH_RADIUS * LEASH_RADIUS;
                if (far && player.level().getServer() != null) {
                    ServerLevel overworld = player.level().getServer().overworld();
                    player.teleportTo(overworld, home.getX() + 0.5D, home.getY() + 2.0D, home.getZ() + 0.5D, player.getYRot(), 0.0F);
                    player.displayClientMessage(Component.translatable("ending.cutecritters.keeper.leash")
                            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
                }
                if (left == 1) {
                    player.sendSystemMessage(Component.translatable("ending.cutecritters.keeper.free")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            }
        }
    }

    private static void broadcast(MinecraftServer server, ServerPlayer who, String key) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p != who) {
                p.sendSystemMessage(Component.translatable(key, who.getDisplayName())
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            }
        }
    }

    // ------------------------------------------------------------------ commands (ops only)

    /** Forces an ending for the player (testing). 1..5 as in the design, 0 = reset everything. */
    public static void force(ServerPlayer player, int n) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        BlockPos spot = player.blockPosition();
        switch (n) {
            case 0 -> reset(player);
            case 1 -> escape(player);
            case 2 -> altarBroken(player.serverLevel(), spot);
            case 3 -> stay(player);
            case 4 -> {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p != player) {
                        replace(player, p, spot);
                        return;
                    }
                }
                player.sendSystemMessage(Component.literal("Needs another player online."));
            }
            default -> {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    for (int i = 0; i < com.cutecritters.item.LoreNoteItem.NOTE_COUNT; i++) {
                        Bond.markRead(p, i);
                    }
                }
                player.getInventory().add(new ItemStack(ModItems.CRITTER_COOKIE.get()));
                player.sendSystemMessage(Component.literal(
                        "All notes marked as read. Now hold the Recipe book in the main hand and a cookie in the off hand and right-click the altar core."));
            }
        }
    }

    public static void reset(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            ValleyData data = ValleyData.get(server);
            data.calm = false;
            data.keeper = null;
            data.setDirty();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                setEnding(p, NONE);
                tag(p).putInt(KEY_LEASH, 0);
                p.removeEffect(MobEffects.NIGHT_VISION);
            }
        }
        player.sendSystemMessage(Component.literal("Cute Critters+: the endings are reset for everyone online."));
    }

    public static boolean hasAltarBlock() {
        return ModBlocks.ALTAR_CORE.isPresent();
    }
}
