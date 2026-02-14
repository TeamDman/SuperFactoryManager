package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.IFacadableBlock;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.ConfirmationParams;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@Desugar
public record ClearFacadesFacadePlan(
        BlockPosSet positions
) implements IFacadePlan {
    @Override
    public void apply(World level) {
        this.positions().blockPosIterator().forEach(pos -> {
            Block existingBlock = level.getBlockState(pos).getBlock();
            if (existingBlock instanceof IFacadableBlock facadableBlock) {
                IBlockState nextBlockState = facadableBlock
                        .getNonFacadeBlock()
                        .getStateForPlacementByFacadePlan(
                                level,
                                pos
                        );
                level.setBlockState(pos, nextBlockState, Constants.BlockFlags.SEND_TO_CLIENTS);
            } else {
                SFM.LOGGER.warn("Block {} at {} is not a facadable block", existingBlock, pos);
            }
        });
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public @Nullable ConfirmationParams computeWarning(
            World level
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
