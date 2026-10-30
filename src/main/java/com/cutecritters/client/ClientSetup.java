package com.cutecritters.client;

import com.cutecritters.CuteCritters;
import com.cutecritters.entity.CloudSheepEntity;
import com.cutecritters.entity.FireflyEntity;
import com.cutecritters.entity.FoxCubEntity;
import com.cutecritters.entity.MiniDragonEntity;
import com.cutecritters.entity.ModEntities;
import com.cutecritters.entity.WeaselEntity;
import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.model.OcelotModel;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CuteCritters.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WATCHER.get(), WatcherRenderer::new);

        event.registerEntityRenderer(ModEntities.FOX_CUB.get(), ctx ->
                new CritterRenderer<FoxCubEntity, OcelotModel<FoxCubEntity>>(ctx,
                        new OcelotModel<>(ctx.bakeLayer(ModelLayers.OCELOT)), 0.3F, 0.85F, "fox_cub"));

        event.registerEntityRenderer(ModEntities.CLOUD_SHEEP.get(), ctx ->
                new CritterRenderer<CloudSheepEntity, PigModel<CloudSheepEntity>>(ctx,
                        new PigModel<>(ctx.bakeLayer(ModelLayers.PIG)), 0.5F, 1.0F, "cloud_sheep"));

        event.registerEntityRenderer(ModEntities.MINI_DRAGON.get(), ctx ->
                new CritterRenderer<MiniDragonEntity, ChickenModel<MiniDragonEntity>>(ctx,
                        new ChickenModel<>(ctx.bakeLayer(ModelLayers.CHICKEN)), 0.25F, 1.1F, "mini_dragon"));

        event.registerEntityRenderer(ModEntities.FIREFLY.get(), ctx -> {
            CritterRenderer<FireflyEntity, ChickenModel<FireflyEntity>> renderer =
                    new CritterRenderer<FireflyEntity, ChickenModel<FireflyEntity>>(ctx,
                            new ChickenModel<>(ctx.bakeLayer(ModelLayers.CHICKEN)), 0.1F, 0.45F, "firefly");
            renderer.addLayer(new CritterGlowLayer<FireflyEntity, ChickenModel<FireflyEntity>>(renderer, "firefly"));
            return renderer;
        });

        event.registerEntityRenderer(ModEntities.WEASEL.get(), ctx ->
                new CritterRenderer<WeaselEntity, OcelotModel<WeaselEntity>>(ctx,
                        new OcelotModel<>(ctx.bakeLayer(ModelLayers.OCELOT)), 0.2F, 0.6F, "weasel"));
    }
}
