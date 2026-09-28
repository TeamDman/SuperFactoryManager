package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.*;
import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.ConfirmationParams;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;


public record ApplyFacadesFacadePlan(
        FacadeData facadeData,
        BlockPosSet positions
) implements IFacadePlan {
    @SFMLocalizationDatagen
    public static final LocalizationEntry FACADE_CONFIRM_APPLY_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.facade_confirm_apply.title",
            "Are you sure you want to update the facade appearance?"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry FACADE_CONFIRM_APPLY_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.facade_confirm_apply.message",
            "%d different facade states across %d blocks that will be overwritten."
    );

    @Override
    public void apply(Level level) {

        this.positions().blockPosIterator().forEach(pos -> {
            BlockState blockState = level.getBlockState(pos);
            Block block = blockState.getBlock();
            if (block instanceof IFacadableBlock facadableBlock) {
                BlockState facadeBlockState = facadeData.facadeBlockState();
                BlockState nextBlockState = facadableBlock
                        .getFacadeBlock()
                        .getStateForPlacementByFacadePlan(level, pos)
                        .setValue(LightBlock.LEVEL, facadeBlockState.getLightEmission(level, pos));

                if (
                        facadableBlock instanceof CableBlock && !(facadableBlock instanceof FancyCableBlock)
                ) {
                    nextBlockState = nextBlockState.setValue(FacadeProperties.SOLID, facadeBlockState.isSolidRender());
                }

                level.setBlock(pos, nextBlockState, Block.UPDATE_IMMEDIATE | Block.UPDATE_CLIENTS);

                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof IFacadeBlockEntity facadeBlockEntity) {
                    facadeBlockEntity.updateFacadeData(this.facadeData());
                } else {
                    SFM.LOGGER.warn("Block entity {} at {} is not a facade block entity", pos, blockEntity);
                }
            } else {
                SFM.LOGGER.warn("Block {} at {} is not a facadable block", block, pos);
            }
        });
    }

    @Override
    public @Nullable ConfirmationParams computeWarning(
            Level level
    ) {

        FacadePlanAnalysisResult analysisResult = FacadePlanAnalysisResult.analyze(level, positions);
        if (analysisResult.shouldWarn()) {
            return ConfirmationParams.of(
                    FACADE_CONFIRM_APPLY_SCREEN_TITLE.getComponent(),
                    FACADE_CONFIRM_APPLY_SCREEN_MESSAGE.getComponent(
                            analysisResult.facadeDataToCount().size(),
                            analysisResult.countAffected()
                    )
            );
        }
        return null;
    }

}
