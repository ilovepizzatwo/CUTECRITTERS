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
 * Screen effects for the cookie "high" and for the "truth" flash.
 * Uses the post-processing shaders that already ship with Minecraft.
 * Soft mode (client config) turns the shaders off completely.
 */
@Mod.EventBusSubscriber(modid = CuteCritters.MODID, value = Dist.CLIENT)
public final class ClientEffects {
    private static final ResourceLocation HIGH_SHADER = new ResourceLocation("shaders/post/deconverge.json");
    private static final ResourceLocation TRUTH_SHADER = new ResourceLocation("shaders/post/wobble.json");

    private static int remaining;
    private static boolean active;

    private ClientEffects() {
    }

    public static void apply(int mode, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mode == EffectPacket.OFF) {
            stop(mc);
            return;
        }
        if (Config.SOFT_MODE.get()) {
            return;
        }
        remaining = ticks;
        active = true;
        mc.gameRenderer.loadEffect(mode == EffectPacket.TRUTH ? TRUTH_SHADER : HIGH_SHADER);
    }

    private static void stop(Minecraft mc) {
        if (active) {
            mc.gameRenderer.shutdownEffect();
        }
        active = false;
        remaining = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || --remaining <= 0) {
            stop(mc);
        }
    }
}
