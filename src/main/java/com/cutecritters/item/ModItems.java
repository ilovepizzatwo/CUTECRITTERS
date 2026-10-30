package com.cutecritters.item;

import com.cutecritters.CuteCritters;
import net.minecraft.world.food.FoodProperties;
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

    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(CRITTER_COOKIE);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            for (int i = 0; i < LoreNoteItem.NOTE_COUNT; i++) {
                event.accept(LoreNoteItem.create(i));
            }
        }
    }
}
