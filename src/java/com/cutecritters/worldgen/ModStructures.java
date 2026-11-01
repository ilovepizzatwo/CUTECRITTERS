package com.cutecritters.worldgen;

import com.cutecritters.CuteCritters;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * One structure type per footprint (blocks the template covers) and the largest height difference allowed.
 * The structure JSON files refer to them as "cutecritters:flat_village" and so on.
 */
public class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, CuteCritters.MODID);

    public static final RegistryObject<StructureType<CritterStructure>> VILLAGE =
            TYPES.register("flat_village", () -> CritterStructure.make(25, 25, 5, 6));
    public static final RegistryObject<StructureType<CritterStructure>> ALTAR =
            TYPES.register("flat_altar", () -> CritterStructure.make(21, 21, 4, 6));
    public static final RegistryObject<StructureType<CritterStructure>> TOWN =
            TYPES.register("flat_town", () -> CritterStructure.make(41, 31, 6, 6));
    public static final RegistryObject<StructureType<CritterStructure>> GRAVEYARD =
            TYPES.register("flat_graveyard", () -> CritterStructure.make(27, 25, 5, 6));
    public static final RegistryObject<StructureType<CritterStructure>> TREEHOUSE =
            TYPES.register("flat_treehouse", () -> CritterStructure.make(31, 31, 7, 6));
    public static final RegistryObject<StructureType<CritterStructure>> LIBRARY =
            TYPES.register("flat_library", () -> CritterStructure.make(24, 26, 6, -1));
}
