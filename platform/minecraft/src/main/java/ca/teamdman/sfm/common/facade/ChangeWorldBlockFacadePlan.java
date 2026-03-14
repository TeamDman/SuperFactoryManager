package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.IFacadableBlock;
import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.ConfirmationParams;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static ca.teamdman.sfm.common.facade.FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY;

public record ChangeWorldBlockFacadePlan(
        IFacadableBlock worldBlock,
        Set<BlockPos> positions
) implements IFacadePlan {
    @Override
    public void apply(Level level) {
        this.positions().forEach(pos -> {
            if (level.getBlockEntity(pos) instanceof IFacadeBlockEntity oldFacadeBlockEntity) {
                // this position already has a facade

                // get the old state
                BlockState oldState = level.getBlockState(pos);
                FacadeData oldFacadeData = oldFacadeBlockEntity.getFacadeData();

                // if the old state is valid, we can set the new world block and restore the facade
                if (oldFacadeData != null && oldState.hasProperty(FACADE_TRANSPARENCY_PROPERTY)) {
                    level.setBlock(
                            pos,
                            this
                                    .worldBlock()
                                    .getFacadeBlock()
                                    .getStateForPlacementByFacadePlan(level, pos)
                                    .setValue(
                                            FACADE_TRANSPARENCY_PROPERTY,
                                            oldState.getValue(FACADE_TRANSPARENCY_PROPERTY)
                                    )
                                    .setValue(
                                            LightBlock.LEVEL,
                                            oldState.getValue(LightBlock.LEVEL)
                                    ),
                            Block.UPDATE_IMMEDIATE | Block.UPDATE_CLIENTS
                    );
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity instanceof IFacadeBlockEntity facadeBlockEntity) {
                        facadeBlockEntity.updateFacadeData(oldFacadeData);
                    } else {
                        SFM.LOGGER.warn("Block entity {} at {} is not a facade block entity", pos, blockEntity);
                    }
                }
            } else {
                // there was no old facade, just set the new world block
                level.setBlock(
                        pos,
                        this.worldBlock()
                                .getNonFacadeBlock()
                                .getStateForPlacementByFacadePlan(level, pos),
                        Block.UPDATE_IMMEDIATE | Block.UPDATE_CLIENTS
                );
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
                    LocalizationKeys.FACADE_CONFIRM_CHANGE_WORLD_BLOCK_SCREEN_TITLE.getComponent(),
                    LocalizationKeys.FACADE_CONFIRM_CHANGE_WORLD_BLOCK_SCREEN_MESSAGE.getComponent(
                            analysisResult.countAffected()
                    )
            );
        }
        return null;
    }
}
