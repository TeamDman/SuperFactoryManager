package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

public class PrintingPressBlock extends BaseEntityBlock implements EntityBlock {

    @SFMLocalizationDatagen
    public static final LocalizationEntry PRINTING_PRESS_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.PRINTING_PRESS.get().getDescriptionId(),
            () -> "Printing Press"
    );

    public PrintingPressBlock() {

        super(BlockBehaviour.Properties.of().strength(5.0F, 6.0F).noOcclusion());
        this.registerDefaultState(this.defaultBlockState());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {

        return SFMBlockEntities.PRINTING_PRESS
                .get()
                .create(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {

        return RenderShape.MODEL;
    }

    @Override
    protected void neighborChanged(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Block pBlock,
            @Nullable Orientation orientation,
            boolean pIsMoving
    ) {
        super.neighborChanged(pState, pLevel, pPos, pBlock, orientation, pIsMoving);
        if (!pLevel.isClientSide()
            && pFromPos.getY() == pPos.getY() + 1
            && pLevel.getBlockState(pFromPos).getBlock() == Blocks.PISTON_HEAD
            && pLevel.getBlockEntity(pPos) instanceof PrintingPressBlockEntity blockEntity) {
            blockEntity.performPrint();
        }
    }

    @Override
    protected MapCodec<WaterTankBlock> codec() {
        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {

        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PrintingPressBlockEntity blockEntity) {
            player.setItemInHand(hand, blockEntity.acceptStack(stack));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }
}
