package com.metrovoc.phonon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

public class SpeakerBlock extends Block implements EntityBlock {

    public SpeakerBlock() {
        super(Properties.of()
            .strength(1.5f)
            .noOcclusion()
        );
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult
    ) {
        if (level.isClientSide) {
            // Open GUI on client
            openGui(pos);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Open speaker GUI (client-side).
     * Platform-specific implementation via static method.
     */
    private static java.util.function.Consumer<BlockPos> guiOpener;

    public static void setGuiOpener(java.util.function.Consumer<BlockPos> opener) {
        guiOpener = opener;
    }

    private void openGui(BlockPos pos) {
        if (guiOpener != null) {
            guiOpener.accept(pos);
        }
    }
}
