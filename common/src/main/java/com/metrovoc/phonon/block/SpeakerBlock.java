package com.metrovoc.phonon.block;

import com.metrovoc.phonon.audio.Receiver;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public class SpeakerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty PLAYING = BooleanProperty.create("playing");

    public SpeakerBlock() {
        super(Properties.of()
            .strength(4.0f, 6.0f)
            .sound(SoundType.LANTERN)
            .requiresCorrectToolForDrops()
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
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
            speaker.setOwner(placer.getUUID(), placer.getName().getString());
        }
    }

    @Override
    protected void neighborChanged(
        BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston
    ) {
        if (level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
            speaker.onNeighborSignals();
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker ? speaker.comparatorSignal() : 0;
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
            openGui(new Receiver.Block(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Open speaker GUI (client-side).
     * Platform-specific implementation via static method.
     */
    private static java.util.function.Consumer<Receiver> guiOpener;

    public static void setGuiOpener(java.util.function.Consumer<Receiver> opener) {
        guiOpener = opener;
    }

    public static void openGui(Receiver receiver) {
        if (guiOpener != null) {
            guiOpener.accept(receiver);
        }
    }
}
