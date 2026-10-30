package com.cutecritters.client;

import com.cutecritters.CuteCritters;
import com.cutecritters.entity.WatcherEntity;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class WatcherRenderer extends MobRenderer<WatcherEntity, EndermanModel<WatcherEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(CuteCritters.MODID, "textures/entity/watcher.png");

    public WatcherRenderer(EntityRendererProvider.Context context) {
        super(context, new EndermanModel<>(context.bakeLayer(ModelLayers.ENDERMAN)), 0.0F);
        this.addLayer(new WatcherEyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(WatcherEntity entity) {
        return TEXTURE;
    }
}
