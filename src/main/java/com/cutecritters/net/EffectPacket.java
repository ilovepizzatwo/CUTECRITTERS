package com.cutecritters.net;

import com.cutecritters.client.ClientEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: start a screen effect.
 * mode: OFF, HIGH (bright shader for `ticks`) or TRUTH (short flash, then the HIGH shader for `afterTicks`).
 * level: how strong the high is (1 and up), it picks the shader.
 */
public class EffectPacket {
    public static final int OFF = 0;
    public static final int HIGH = 1;
    public static final int TRUTH = 2;

    private final int mode;
    private final int ticks;
    private final int level;
    private final int afterTicks;

    public EffectPacket(int mode, int ticks, int level, int afterTicks) {
        this.mode = mode;
        this.ticks = ticks;
        this.level = level;
        this.afterTicks = afterTicks;
    }

    public static void encode(EffectPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.mode);
        buf.writeVarInt(packet.ticks);
        buf.writeVarInt(packet.level);
        buf.writeVarInt(packet.afterTicks);
    }

    public static EffectPacket decode(FriendlyByteBuf buf) {
        return new EffectPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(EffectPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientEffects.apply(packet.mode, packet.ticks, packet.level, packet.afterTicks)));
        ctx.get().setPacketHandled(true);
    }
}
