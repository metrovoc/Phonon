package com.metrovoc.phonon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public class SpeakerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty PLAYING = BooleanProperty.create("playing");

    public SpeakerBlock() {
        super(Properties.of()
            .strength(1.5f)
            .noOcclusion()
            .lightLevel(state -> state.getValue(PLAYING) ? 7 : 0)
        );
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(PLAYING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PLAYING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PLAYING)) {
            // Note particle above the speaker, like a jukebox
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 1.2;
            double z = pos.getZ() + 0.5;
            // Color cycles through 0-24 range
            level.addParticle(ParticleTypes.NOTE, x, y, z, random.nextInt(25) / 24.0, 0, 0);
        }
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
