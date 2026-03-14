package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.IFacadableBlock;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.ConfirmationParams;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public record ClearFacadesFacadePlan(
        Set<BlockPos> positions
) implements IFacadePlan {
    @Override
    public void apply(Level level) {
        this.positions().forEach(pos -> {
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
                    LocalizationKeys.FACADE_CONFIRM_CLEAR_SCREEN_TITLE.getComponent(),
                    LocalizationKeys.FACADE_CONFIRM_CLEAR_SCREEN_MESSAGE.getComponent(
                            analysisResult.facadeDataToCount().size(),
                            analysisResult.countAffected()
                    )
            );
        }
        return null;
    }
}
