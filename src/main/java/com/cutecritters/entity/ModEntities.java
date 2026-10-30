package com.cutecritters.entity;

import com.cutecritters.CuteCritters;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CuteCritters.MODID);

    private static String id(String name) {
        return new ResourceLocation(CuteCritters.MODID, name).toString();
    }

    public static final RegistryObject<EntityType<WatcherEntity>> WATCHER = ENTITIES.register("watcher",
            () -> EntityType.Builder.of(WatcherEntity::new, MobCategory.MISC)
                    .sized(0.6F, 2.9F)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .noSave()
                    .build(id("watcher")));

    public static final RegistryObject<EntityType<MimicEntity>> MIMIC = ENTITIES.register("mimic",
            () -> EntityType.Builder.of(MimicEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .noSave()
                    .build(id("mimic")));

    public static final RegistryObject<EntityType<FoxCubEntity>> FOX_CUB = ENTITIES.register("fox_cub",
            () -> EntityType.Builder.of(FoxCubEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.7F).clientTrackingRange(8).build(id("fox_cub")));

    public static final RegistryObject<EntityType<CloudSheepEntity>> CLOUD_SHEEP = ENTITIES.register("cloud_sheep",
            () -> EntityType.Builder.of(CloudSheepEntity::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.2F).clientTrackingRange(8).build(id("cloud_sheep")));

    public static final RegistryObject<EntityType<MiniDragonEntity>> MINI_DRAGON = ENTITIES.register("mini_dragon",
            () -> EntityType.Builder.of(MiniDragonEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.7F).fireImmune().clientTrackingRange(8).build(id("mini_dragon")));

    public static final RegistryObject<EntityType<FireflyEntity>> FIREFLY = ENTITIES.register("firefly",
            () -> EntityType.Builder.of(FireflyEntity::new, MobCategory.CREATURE)
                    .sized(0.3F, 0.3F).clientTrackingRange(8).build(id("firefly")));

    public static final RegistryObject<EntityType<WeaselEntity>> WEASEL = ENTITIES.register("weasel",
            () -> EntityType.Builder.of(WeaselEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.45F).clientTrackingRange(8).build(id("weasel")));

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(WATCHER.get(), WatcherEntity.createAttributes().build());
        event.put(MIMIC.get(), MimicEntity.createAttributes().build());
        event.put(FOX_CUB.get(), CritterEntity.createAttributes().build());
        event.put(CLOUD_SHEEP.get(), CritterEntity.createAttributes().build());
        event.put(MINI_DRAGON.get(), CritterEntity.createAttributes().build());
        event.put(FIREFLY.get(), CritterEntity.createAttributes().add(Attributes.FLYING_SPEED, 0.6D).build());
        event.put(WEASEL.get(), CritterEntity.createAttributes().build());
    }

    public static void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
        SpawnPlacementRegisterEvent.Operation op = SpawnPlacementRegisterEvent.Operation.OR;
        Heightmap.Types map = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        event.register(FOX_CUB.get(), SpawnPlacements.Type.ON_GROUND, map, Animal::checkAnimalSpawnRules, op);
        event.register(CLOUD_SHEEP.get(), SpawnPlacements.Type.ON_GROUND, map, Animal::checkAnimalSpawnRules, op);
        event.register(MINI_DRAGON.get(), SpawnPlacements.Type.ON_GROUND, map, Mob::checkMobSpawnRules, op);
        event.register(FIREFLY.get(), SpawnPlacements.Type.NO_RESTRICTIONS, map, Mob::checkMobSpawnRules, op);
        event.register(WEASEL.get(), SpawnPlacements.Type.ON_GROUND, map, Animal::checkAnimalSpawnRules, op);
    }
}
