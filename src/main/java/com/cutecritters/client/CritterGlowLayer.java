package com.cutecritters.client;

import com.cutecritters.CuteCritters;
import com.cutecritters.entity.CritterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws the whole model again at full brightness, so it glows in the dark. Off when the critter is "wrong". */
public class CritterGlowLayer<T extends CritterEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final RenderType glow;

    public CritterGlowLayer(RenderLayerParent<T, M> parent, String textureName) {
        super(parent);
        this.glow = RenderType.eyes(new ResourceLocation(CuteCritters.MODID, "textures/entity/" + textureName + ".png"));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isCreepy() || entity.isInvisible()) {
            return;
        }
        VertexConsumer consumer = buffer.getBuffer(this.glow);
        this.getParentModel().renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
