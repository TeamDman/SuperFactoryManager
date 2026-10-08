package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.21', '1.21.1', '26.1.2' %}
{% when '1.20.3', '1.20.4' %}
import com.mojang.serialization.MapCodec;
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import com.mojang.serialization.MapCodec;
{% endcase %}
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
{% endcase %}
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '26.1.2' %}
import net.minecraft.world.InteractionResult;
{% when '1.21', '1.21.1' %}
import net.minecraft.world.ItemInteractionResult;
{% endcase %}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.world.level.block.*;
{% when '26.1.2' %}
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
{% endcase %}
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
import net.minecraft.world.level.material.Material;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import net.minecraft.world.phys.BlockHitResult;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import org.apache.commons.lang3.NotImplementedException;
{% endcase %}
import org.jetbrains.annotations.Nullable;

public class PrintingPressBlock extends BaseEntityBlock implements EntityBlock {

    @SFMLocalizationDatagen
    public static final LocalizationEntry PRINTING_PRESS_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.PRINTING_PRESS.get().getDescriptionId(),
            () -> "Printing Press"
    );

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public PrintingPressBlock() {
{% when '26.1.2' %}
    public PrintingPressBlock(BlockBehaviour.Properties properties) {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        super(BlockBehaviour.Properties.of(Material.METAL).strength(5.0F, 6.0F).noOcclusion());
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        super(BlockBehaviour.Properties.of().strength(5.0F, 6.0F).noOcclusion());
{% when '26.1.2' %}
        super(properties.strength(5.0F, 6.0F).noOcclusion());
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @SuppressWarnings("deprecation")
{% when '26.1.2' %}
    protected BlockState updateShape(
            BlockState state,
            LevelReader pLevel,
            ScheduledTickAccess ticks,
            BlockPos pPos,
            Direction directionToNeighbour,
            BlockPos neighbourPos,
            BlockState neighbourState,
            RandomSource random
    ) {
        if (!pLevel.isClientSide()
            && neighbourPos.getY() == pPos.getY() + 1
            && pLevel.getBlockState(neighbourPos).getBlock() == Blocks.PISTON_HEAD
            && pLevel.getBlockEntity(pPos) instanceof PrintingPressBlockEntity blockEntity) {
            blockEntity.performPrint();
        }
        return super.updateShape(state, pLevel, ticks, pPos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

/*    @Override
{% endcase %}
    public void neighborChanged(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Block pBlock,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            BlockPos pFromPos,
{% when '26.1.2' %}
            @Nullable Orientation orientation,
{% endcase %}
            boolean pIsMoving
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}

        super.neighborChanged(pState, pLevel, pPos, pBlock, pFromPos, pIsMoving);
        if (!pLevel.isClientSide
{% when '26.1.2' %}
        super.neighborChanged(pState, pLevel, pPos, pBlock, orientation, pIsMoving);
        if (!pLevel.isClientSide()
{% endcase %}
            && pFromPos.getY() == pPos.getY() + 1
            && pLevel.getBlockState(pFromPos).getBlock() == Blocks.PISTON_HEAD
            && pLevel.getBlockEntity(pPos) instanceof PrintingPressBlockEntity blockEntity) {
            blockEntity.performPrint();
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
    }*/

    @Override
    protected MapCodec<WaterTankBlock> codec() {
        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.21', '1.21.1', '26.1.2' %}
{% when '1.20.3', '1.20.4' %}
    @Override
    protected MapCodec<WaterTankBlock> codec() {
        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
    }

{% endcase %}
    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    @SuppressWarnings("deprecation")
    public InteractionResult use(
{% when '1.21', '1.21.1' %}
    protected MapCodec<WaterTankBlock> codec() {
        throw new NotImplementedException("This isn't used until 1.20.5 apparently");
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
{% when '26.1.2' %}
    protected InteractionResult useItemOn(
            ItemStack stack,
{% endcase %}
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
            BlockHitResult hit
{% when '1.21', '1.21.1', '26.1.2' %}
            BlockHitResult hitResult
{% endcase %}
    ) {

        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PrintingPressBlockEntity blockEntity) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
            var stack = player.getItemInHand(hand);
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
            player.setItemInHand(hand, blockEntity.acceptStack(stack));
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '26.1.2' %}
        return InteractionResult.SUCCESS;
{% when '1.21', '1.21.1' %}
        return ItemInteractionResult.SUCCESS;
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @SuppressWarnings("deprecation")
    public void onRemove(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            BlockState pNewState,
            boolean pIsMoving
    ) {

        if (!pState.is(pNewState.getBlock())) {
            BlockEntity blockentity = pLevel.getBlockEntity(pPos);
            if (blockentity instanceof PrintingPressBlockEntity blockEntity) {
                for (ItemStack itemStack : blockEntity.getStacksToDrop()) {
                    Containers.dropItemStack(pLevel, pPos.getX(), pPos.getY(), pPos.getZ(), itemStack);
                }
                pLevel.updateNeighbourForOutputSignal(pPos, this);
            }

            super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
        }
{% when '26.1.2' %}
    protected void affectNeighborsAfterRemoval(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            boolean movedByPiston
    ) {

        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Containers.updateNeighboursAfterDestroy(state, level, pos);
{% endcase %}
    }


}
