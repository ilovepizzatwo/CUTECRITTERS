package com.cutecritters.client;

import com.cutecritters.entity.MimicEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Draws the mimic with the skin of the player it copies. */
public class MimicRenderer extends HumanoidMobRenderer<MimicEntity, PlayerModel<MimicEntity>> {
    public MimicRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(MimicEntity entity) {
        UUID id = entity.getOwnerId();
        if (id == null) {
            return DefaultPlayerSkin.getDefaultSkin();
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(id);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(id);
    }
}
