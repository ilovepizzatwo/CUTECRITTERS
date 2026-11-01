package com.cutecritters.block;

import com.cutecritters.CuteCritters;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CuteCritters.MODID);

    private static BlockBehaviour.Properties burnt() {
        return BlockBehaviour.Properties.copy(Blocks.TORCH).lightLevel(state -> 0);
    }

    public static final RegistryObject<Block> BURNT_TORCH = BLOCKS.register("burnt_torch",
            () -> new BurntTorchBlock(burnt()));

    public static final RegistryObject<Block> BURNT_WALL_TORCH = BLOCKS.register("burnt_wall_torch",
            () -> new BurntWallTorchBlock(burnt()));
}
