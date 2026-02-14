package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.IFacadableBlock;
import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.ConfirmationParams;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static ca.teamdman.sfm.common.facade.FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY;

@Desugar
public record ChangeWorldBlockFacadePlan(
        IFacadableBlock worldBlock,
        BlockPosSet positions
) implements IFacadePlan
{
    @Override
    public void apply(World level) {
        this.positions().blockPosIterator().forEach(pos -> {
            if (level.getTileEntity(pos) instanceof IFacadeBlockEntity oldFacadeBlockEntity) {
                // this position already has a facade

                // get the old state
                IBlockState oldState = level.getBlockState(pos);
                FacadeData oldFacadeData = oldFacadeBlockEntity.getFacadeData();

                // if the old state is valid, we can set the new world block and restore the facade
                if (oldFacadeData != null && oldState.getPropertyKeys().contains(FACADE_TRANSPARENCY_PROPERTY)) {
                    level.setBlockState(
                            pos,
                            this
                                    .worldBlock()
                                    .getFacadeBlock()
                                    .getStateForPlacementByFacadePlan(level, pos),
//                                    .withProperty(
//                                            FACADE_TRANSPARENCY_PROPERTY,
//                                            oldState.getValue(FACADE_TRANSPARENCY_PROPERTY)
//                                    ).withProperty(
//                                            LIGHT_LEVEL,
//                                            oldState.getValue(LIGHT_LEVEL)
//                                    ),
                            Constants.BlockFlags.SEND_TO_CLIENTS

                    );
                    TileEntity blockEntity = level.getTileEntity(pos);
                    if (blockEntity instanceof IFacadeBlockEntity facadeBlockEntity) {
                        facadeBlockEntity.updateFacadeData(oldFacadeData);
                    } else {
                        SFM.LOGGER.warn("Block entity {} at {} is not a facade block entity", pos, blockEntity);
                    }
                }
            } else {
                // there was no old facade, just set the new world block
                level.setBlockState(
                        pos,
                        this.worldBlock()
                                .getNonFacadeBlock()
                                .getStateForPlacementByFacadePlan(level, pos),
                        Constants.BlockFlags.SEND_TO_CLIENTS
                );
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
                    LocalizationKeys.FACADE_CONFIRM_CHANGE_WORLD_BLOCK_SCREEN_TITLE.getComponent(),
                    LocalizationKeys.FACADE_CONFIRM_CHANGE_WORLD_BLOCK_SCREEN_MESSAGE.getComponent(
                            analysisResult.countAffected()
                    )
            );
        }
        return null;
    }
}
