package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import ca.teamdman.sfm.common.facade.FacadeTransparency;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
{% when '26.1.2' %}
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
{% endcase %}
import net.minecraft.util.RandomSource;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '26.1.2' %}
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.world.level.BlockAndTintGetter;
{% endcase %}
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
{% when '26.1.2' %}
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.model.data.ModelData;
{% endcase %}
import java.util.List;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
public class CableFacadeBlockModelWrapper extends BakedModelWrapper<BakedModel> {
{% when '26.1.2' %}
public class CableFacadeBlockModelWrapper extends DelegateBlockStateModel {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());
    private static final ChunkRenderTypeSet ALL = ChunkRenderTypeSet.all();

    public CableFacadeBlockModelWrapper(BakedModel originalModel) {
        super(originalModel);
    }
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());
    private static final ChunkRenderTypeSet ALL = ChunkRenderTypeSet.all();

    public CableFacadeBlockModelWrapper(BakedModel originalCableModel) {
        super(originalCableModel);
    }

    @Override
    public ModelData getModelData(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ModelData modelData
    ) {
        return this.originalModel.getModelData(level, pos, state, modelData);
    }
{% when '26.1.2' %}
    public CableFacadeBlockModelWrapper(BlockStateModel originalCableModel) {
        super(originalCableModel);
    }

    @Override
    public void collectParts(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            RandomSource random,
            List<BlockStateModelPart> parts
    ) {
        ModelData modelData = level.getModelData(pos);
        BlockState mimicState = modelData.get(IFacadeBlockEntity.FACADE_BLOCK_STATE_MODEL_PROPERTY);
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource rand,
            ModelData extraData,
            @Nullable RenderType renderType
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockState mimicState = extraData.get(IFacadeBlockEntity.FACADE_BLOCK_STATE_MODEL_PROPERTY);

{% when '26.1.2' %}
{% endcase %}
        if (mimicState != null) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
            BakedModel mimicModel = blockRenderer.getBlockModel(mimicState);
            ChunkRenderTypeSet renderTypes = mimicModel.getRenderTypes(mimicState, rand, extraData);
            if (renderType == null || renderTypes.contains(renderType)) {
                return mimicModel.getQuads(mimicState, side, rand, ModelData.EMPTY, renderType);
{% when '26.1.2' %}
            BlockStateModel mimicModel = Minecraft.getInstance()
                    .getModelManager()
                    .getBlockStateModelSet()
                    .get(mimicState);
            if (mimicModel != null) {
                mimicModel.collectParts(level, pos, mimicState, random, parts);
                return;
{% endcase %}
            }
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return minecraft
                .getModelManager()
                .getMissingModel()
                .getQuads(state, side, rand, ModelData.EMPTY, renderType);
    }
{% when '26.1.2' %}
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @SuppressWarnings("DuplicatedCode")
    @Override
    public ChunkRenderTypeSet getRenderTypes(
            BlockState cableBlockState,
            RandomSource rand,
            ModelData data
    ) {
        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        BlockState paintBlockState = data.get(IFacadeBlockEntity.FACADE_BLOCK_STATE_MODEL_PROPERTY);
        if (paintBlockState == null) {
            return cableBlockState.getValue(FacadeTransparency.FACADE_TRANSPARENCY_PROPERTY) == FacadeTransparency.TRANSLUCENT ? ALL : SOLID;
        }
        BakedModel bakedModel = blockRenderer.getBlockModel(paintBlockState);
        return bakedModel.getRenderTypes(paintBlockState, rand, ModelData.EMPTY);
{% when '26.1.2' %}
        this.delegate.collectParts(level, pos, state, random, parts);
{% endcase %}
    }
}
