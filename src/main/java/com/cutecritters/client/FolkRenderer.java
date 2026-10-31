package com.cutecritters.client;

import com.cutecritters.CuteCritters;
import com.cutecritters.entity.FolkEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Residents use the villager model with their own flat-colour textures. The child is smaller. */
public class FolkRenderer extends MobRenderer<FolkEntity, VillagerModel<FolkEntity>> {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[FolkEntity.KINDS];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = new ResourceLocation(CuteCritters.MODID, "textures/entity/folk_" + i + ".png");
        }
    }

    public FolkRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(FolkEntity entity) {
        return TEXTURES[entity.getVariant()];
    }

    @Override
    protected void scale(FolkEntity entity, PoseStack poseStack, float partialTick) {
        float size = entity.getVariant() == 2 ? 0.65F : 1.0F;
        poseStack.scale(size, size, size);
    }
}
