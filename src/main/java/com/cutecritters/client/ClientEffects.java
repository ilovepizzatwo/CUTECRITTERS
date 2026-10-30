package com.cutecritters.client;

import com.cutecritters.Config;
import com.cutecritters.CuteCritters;
import com.cutecritters.net.EffectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Screen effects for the cookie "high" and the "truth" flash.
 * Uses the post-processing shaders that already ship with Minecraft.
 * The stronger the high (the more cookies), the stranger the picture.
 * Soft mode (client config) turns the shaders off completely.
 */
@Mod.EventBusSubscriber(modid = CuteCritters.MODID, value = Dist.CLIENT)
public final class ClientEffects {
    private static final ResourceLocation LEVEL_LOW = new ResourceLocation("shaders/post/deconverge.json");
    private static final ResourceLocation LEVEL_MID = new ResourceLocation("shaders/post/phosphor.json");
    private static final ResourceLocation LEVEL_HIGH = new ResourceLocation("shaders/post/wobble.json");
    private static final ResourceLocation FLASH = new ResourceLocation("shaders/post/invert.json");

    private static int remaining;
    private static int pendingTicks;
    private static int pendingLevel;
    private static boolean active;

    private ClientEffects() {
    }

    public static void apply(int mode, int ticks, int level, int afterTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mode == EffectPacket.OFF) {
            stop(mc);
            return;
        }
        if (Config.SOFT_MODE.get()) {
            return;
        }
        if (mode == EffectPacket.TRUTH) {
            pendingTicks = afterTicks;
            pendingLevel = level;
            start(mc, FLASH, ticks);
        } else {
            pendingTicks = 0;
            start(mc, shaderFor(level), ticks);
        }
    }

    private static ResourceLocation shaderFor(int level) {
        if (level >= 5) {
            return LEVEL_HIGH;
        }
        return level >= 3 ? LEVEL_MID : LEVEL_LOW;
    }

    private static void start(Minecraft mc, ResourceLocation shader, int ticks) {
        remaining = ticks;
        active = true;
        mc.gameRenderer.loadEffect(shader);
    }

    private static void stop(Minecraft mc) {
        if (active) {
            mc.gameRenderer.shutdownEffect();
        }
        active = false;
        remaining = 0;
        pendingTicks = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            stop(mc);
            return;
        }
        if (--remaining > 0) {
            return;
        }
        if (pendingTicks > 0) {
            int ticks = pendingTicks;
            pendingTicks = 0;
            start(mc, shaderFor(pendingLevel), ticks);
        } else {
            stop(mc);
        }
    }
}
