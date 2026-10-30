package com.cutecritters.client;

import com.cutecritters.CuteCritters;
import com.cutecritters.entity.WatcherEntity;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

public class WatcherEyesLayer extends EyesLayer<WatcherEntity, EndermanModel<WatcherEntity>> {
    private static final RenderType EYES =
            RenderType.eyes(new ResourceLocation(CuteCritters.MODID, "textures/entity/watcher_eyes.png"));

    public WatcherEyesLayer(RenderLayerParent<WatcherEntity, EndermanModel<WatcherEntity>> parent) {
        super(parent);
    }

    @Override
    public RenderType renderType() {
        return EYES;
    }
}
