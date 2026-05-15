package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.model.data.ModelData;

import java.util.List;

public class CableFacadeBlockModelWrapper extends DelegateBlockStateModel {

    public CableFacadeBlockModelWrapper(BlockStateModel originalCableModel) {
        super(originalCableModel);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        ModelData modelData = level.getModelData(pos);
        BlockState mimicState = modelData.get(IFacadeBlockEntity.FACADE_BLOCK_STATE_MODEL_PROPERTY);

        if (mimicState != null) {
            BlockStateModel mimicModel = Minecraft.getInstance()
                    .getModelManager()
                    .getBlockStateModelSet()
                    .get(mimicState);
            if (mimicModel != null) {
                mimicModel.collectParts(level, pos, mimicState, random, parts);
                return;
            }
        }

        this.delegate.collectParts(level, pos, state, random, parts);
    }
}
