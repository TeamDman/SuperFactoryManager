package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.IFacadableBlock;
import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.ConfirmationParams;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static ca.teamdman.sfm.common.facade.FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY;

@Desugar
public record ApplyFacadesFacadePlan(
        FacadeData facadeData,
        FacadeTransparency facadeTransparency,
        BlockPosSet positions
) implements IFacadePlan {
    @Override
    public void apply(World level) {
        this.positions().blockPosIterator().forEach(pos -> {
            IBlockState blockState = level.getBlockState(pos);
            Block block = blockState.getBlock();
            if (block instanceof IFacadableBlock facadableBlock) {
                IBlockState nextBlockState = facadableBlock.getFacadeBlock()
                        .getStateForPlacementByFacadePlan(level, pos);
//                        .withProperty(FACADE_TRANSPARENCY_PROPERTY, this.facadeTransparency())
//                        .withProperty(
//                                LIGHT_LEVEL,
//                                facadeData.facadeBlockState().getLightValue(level, pos)
//                        );
                level.setBlockState(pos, nextBlockState, Constants.BlockFlags.SEND_TO_CLIENTS);
                TileEntity blockEntity = level.getTileEntity(pos);
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
            World level
    ) {
        FacadePlanAnalysisResult analysisResult = FacadePlanAnalysisResult.analyze(level, positions);
        if (analysisResult.shouldWarn()) {
            return ConfirmationParams.of(
                    LocalizationKeys.FACADE_CONFIRM_APPLY_SCREEN_TITLE.getComponent(),
                    LocalizationKeys.FACADE_CONFIRM_APPLY_SCREEN_MESSAGE.getComponent(
                            analysisResult.facadeDataToCount().size(),
                            analysisResult.countAffected()
                    )
            );
        }
        return null;
    }
}
