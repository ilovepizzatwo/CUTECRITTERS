package com.cutecritters.item;

import com.cutecritters.CuteCritters;
import com.cutecritters.entity.ModEntities;
import net.minecraft.world.food.FoodProperties;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CuteCritters.MODID);

    public static final RegistryObject<Item> CRITTER_COOKIE = ITEMS.register("critter_cookie",
            () -> new CritterCookieItem(new Item.Properties().food(
                    new FoodProperties.Builder().nutrition(2).saturationMod(0.4F).alwaysEat().fast().build())));

    public static final RegistryObject<Item> LORE_NOTE = ITEMS.register("lore_note",
            () -> new LoreNoteItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> FOX_CUB_EGG = ITEMS.register("fox_cub_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.FOX_CUB, 0xE8873C, 0xFFF1D6, new Item.Properties()));
    public static final RegistryObject<Item> CLOUD_SHEEP_EGG = ITEMS.register("cloud_sheep_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.CLOUD_SHEEP, 0xF2F6FF, 0xA8D4FF, new Item.Properties()));
    public static final RegistryObject<Item> MINI_DRAGON_EGG = ITEMS.register("mini_dragon_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.MINI_DRAGON, 0xA046C8, 0xFFB23C, new Item.Properties()));
    public static final RegistryObject<Item> FIREFLY_EGG = ITEMS.register("firefly_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.FIREFLY, 0xFFF078, 0x3C3C28, new Item.Properties()));
    public static final RegistryObject<Item> WEASEL_EGG = ITEMS.register("weasel_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.WEASEL, 0x96704F, 0xF0E0C8, new Item.Properties()));

    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(FOX_CUB_EGG);
            event.accept(CLOUD_SHEEP_EGG);
            event.accept(MINI_DRAGON_EGG);
            event.accept(FIREFLY_EGG);
            event.accept(WEASEL_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(CRITTER_COOKIE);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            for (int i = 0; i < LoreNoteItem.NORMAL_COUNT; i++) {
                event.accept(LoreNoteItem.create(i));
            }
        }
    }
}
