package com.cutecritters.client;

import com.cutecritters.CuteCritters;
import com.cutecritters.entity.ShadowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** A flat black humanoid with two white eyes that glow in the dark. */
public class ShadowRenderer extends HumanoidMobRenderer<ShadowEntity, HumanoidModel<ShadowEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(CuteCritters.MODID, "textures/entity/shadow.png");

    public ShadowRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.0F);
        this.addLayer(new Eyes(this));
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowEntity entity) {
        return TEXTURE;
    }

    private static class Eyes extends EyesLayer<ShadowEntity, HumanoidModel<ShadowEntity>> {
        private static final RenderType EYES =
                RenderType.eyes(new ResourceLocation(CuteCritters.MODID, "textures/entity/shadow_eyes.png"));

        Eyes(RenderLayerParent<ShadowEntity, HumanoidModel<ShadowEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, ShadowEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            // eye layers ignore invisibility, so a dissolved shadow must switch them off by hand
            if (!entity.isInvisible()) {
                super.render(poseStack, buffer, packedLight, entity, limbSwing, limbSwingAmount, partialTick,
                        ageInTicks, netHeadYaw, headPitch);
            }
        }

        @Override
        public RenderType renderType() {
            return EYES;
        }
    }
}
