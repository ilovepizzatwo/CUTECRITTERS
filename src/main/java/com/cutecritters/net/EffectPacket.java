package com.cutecritters.net;

import com.cutecritters.client.ClientEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: start a screen effect for a number of ticks. */
public class EffectPacket {
    public static final int OFF = 0;
    public static final int HIGH = 1;
    public static final int TRUTH = 2;

    private final int mode;
    private final int ticks;

    public EffectPacket(int mode, int ticks) {
        this.mode = mode;
        this.ticks = ticks;
    }

    public static void encode(EffectPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.mode);
        buf.writeVarInt(packet.ticks);
    }

    public static EffectPacket decode(FriendlyByteBuf buf) {
        return new EffectPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(EffectPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientEffects.apply(packet.mode, packet.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
