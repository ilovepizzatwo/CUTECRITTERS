package com.cutecritters.block;

import com.cutecritters.Endings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The heart of the altar. Right-click it with the right things to end the story; break it to break the ritual.
 */
public class AltarCoreBlock extends Block {
    public AltarCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (Endings.isSpecialCookie(held)) {
            return InteractionResult.PASS; // the cookie handles itself ("are you sure?")
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
            Endings.useAltar(serverPlayer, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            Endings.altarBroken(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
