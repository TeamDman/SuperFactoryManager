package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.IFacadableBlock;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.ConfirmationParams;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public record ClearFacadesFacadePlan(
        BlockPosSet positions
) implements IFacadePlan {
    @SFMLocalizationDatagen
    public static final LocalizationEntry FACADE_CONFIRM_CLEAR_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.facade_confirm_clear.title",
            "Are you sure you want to clear these facades?"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry FACADE_CONFIRM_CLEAR_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.facade_confirm_clear.message",
            "%d different facade states across %d blocks will be wiped from the world."
    );

    @Override
    public void apply(Level level) {

        this.positions().blockPosIterator().forEach(pos -> {
            Block existingBlock = level.getBlockState(pos).getBlock();
            if (existingBlock instanceof IFacadableBlock facadableBlock) {
                BlockState nextBlockState = facadableBlock
                        .getNonFacadeBlock()
                        .getStateForPlacementByFacadePlan(
                                level,
                                pos
                        );
                level.setBlock(pos, nextBlockState, Block.UPDATE_IMMEDIATE | Block.UPDATE_CLIENTS);
            } else {
                SFM.LOGGER.warn("Block {} at {} is not a facadable block", existingBlock, pos);
            }
        });
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public @Nullable ConfirmationParams computeWarning(
            Level level
    ) {

        FacadePlanAnalysisResult analysisResult = FacadePlanAnalysisResult.analyze(level, positions);
        if (analysisResult.shouldWarn()) {
            return ConfirmationParams.of(
                    FACADE_CONFIRM_CLEAR_SCREEN_TITLE.getComponent(),
                    FACADE_CONFIRM_CLEAR_SCREEN_MESSAGE.getComponent(
                            analysisResult.facadeDataToCount().size(),
                            analysisResult.countAffected()
                    )
            );
        }
        return null;
    }

}
