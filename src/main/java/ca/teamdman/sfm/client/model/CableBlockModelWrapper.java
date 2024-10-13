package ca.teamdman.sfm.client.model;

import ca.teamdman.sfm.common.block.CableBlock;
import ca.teamdman.sfm.common.util.FacadeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CableBlockModelWrapper extends BakedModelWrapper<BakedModel> {

    public CableBlockModelWrapper(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(BlockState state, Direction side, @NotNull RandomSource rand, @NotNull ModelData extraData, RenderType renderType) {
        BlockState mimicState = extraData.get(CableBlock.FACADE_BLOCK_STATE);
        if (mimicState == null || state.getValue(CableBlock.FACADE_TYPE_PROP) == FacadeType.NONE)
            return originalModel.getQuads(state, side, rand, ModelData.EMPTY, renderType);

        BakedModel mimicModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(mimicState);
        ChunkRenderTypeSet renderTypes = mimicModel.getRenderTypes(mimicState, rand, extraData);

        if (renderType == null || renderTypes.contains(renderType)) {
            return mimicModel.getQuads(mimicState, side, rand, ModelData.EMPTY, renderType);
        }

        return List.of();
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return state.getValue(CableBlock.FACADE_TYPE_PROP) == FacadeType.TRANSLUCENT_FACADE ?
                ChunkRenderTypeSet.all() :
                ChunkRenderTypeSet.of(RenderType.solid());
    }
}
