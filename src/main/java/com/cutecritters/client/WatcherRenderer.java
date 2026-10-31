package com.cutecritters.client;

import com.cutecritters.CuteCritters;
import com.cutecritters.entity.WatcherEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Keeper, consumed: stretched too tall and too thin, leaning to one side with a broken neck,
 * jaw hanging open. Everything is done to the vanilla model, no custom geometry.
 */
public class WatcherRenderer extends MobRenderer<WatcherEntity, EndermanModel<WatcherEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(CuteCritters.MODID, "textures/entity/watcher.png");

    public WatcherRenderer(EntityRendererProvider.Context context) {
        super(context, new EndermanModel<>(context.bakeLayer(ModelLayers.ENDERMAN)), 0.0F);
        this.addLayer(new WatcherEyesLayer(this));
    }

    @Override
    public void render(WatcherEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        this.model.creepy = true;
        this.model.carrying = false;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected void scale(WatcherEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.78F, 1.25F, 0.78F);
    }

    @Override
    protected void setupRotations(WatcherEntity entity, PoseStack poseStack, float bob, float yBodyRot,
                                  float partialTick) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick);
        // a slow sway and a permanent lean, like a body hanging from one side
        poseStack.mulPose(Axis.ZP.rotationDegrees(8.0F + 3.5F * Mth.sin(bob * 0.045F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(4.0F + 2.0F * Mth.sin(bob * 0.031F + 1.3F)));
    }

    @Override
    public ResourceLocation getTextureLocation(WatcherEntity entity) {
        return TEXTURE;
    }
}
