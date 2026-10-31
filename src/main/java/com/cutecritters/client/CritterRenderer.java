package com.cutecritters.client;

import com.cutecritters.CuteCritters;
import com.cutecritters.entity.CritterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** One renderer for every critter: a vanilla model, our texture, and a darker texture when it turns "wrong". */
public class CritterRenderer<T extends CritterEntity, M extends EntityModel<T>> extends MobRenderer<T, M> {
    private final ResourceLocation normal;
    private final ResourceLocation creepy;
    private final float size;

    public CritterRenderer(EntityRendererProvider.Context context, M model, float shadow, float size, String name) {
        super(context, model, shadow);
        this.size = size;
        this.normal = new ResourceLocation(CuteCritters.MODID, "textures/entity/" + name + ".png");
        this.creepy = new ResourceLocation(CuteCritters.MODID, "textures/entity/" + name + "_creepy.png");
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        // A sitting critter is squashed down, so it looks like it is sitting.
        float height = entity.isInSittingPose() ? this.size * 0.78F : this.size;
        poseStack.scale(this.size, height, this.size);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return entity.isCreepy() ? this.creepy : this.normal;
    }
}
