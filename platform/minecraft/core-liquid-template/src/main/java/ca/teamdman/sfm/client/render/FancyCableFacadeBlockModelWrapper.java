package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.FancyCableFacadeBlockEntity;
import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import ca.teamdman.sfm.common.facade.FacadeTransparency;
{% when '26.1.2' %}
{% endcase %}
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
{% when '26.1.2' %}
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
{% endcase %}
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.world.item.ItemStack;
{% when '26.1.2' %}
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
import java.util.ArrayList;
import java.util.List;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
public class FancyCableFacadeBlockModelWrapper extends BakedModelWrapper<BakedModel> {
{% when '26.1.2' %}
public class FancyCableFacadeBlockModelWrapper extends DelegateBlockStateModel {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());
    private static final ChunkRenderTypeSet ALL = ChunkRenderTypeSet.all();

    public FancyCableFacadeBlockModelWrapper(BakedModel originalModel) {
{% when '26.1.2' %}
    public FancyCableFacadeBlockModelWrapper(BlockStateModel originalModel) {
{% endcase %}
        super(originalModel);
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource rand,
            ModelData extraData,
            @Nullable RenderType renderType
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockState mimicState = extraData.get(IFacadeBlockEntity.FACADE_BLOCK_STATE_MODEL_PROPERTY);
        Direction mimicDirection = extraData.get(FancyCableFacadeBlockEntity.FACADE_DIRECTION);
{% when '26.1.2' %}
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        ModelData modelData = level.getModelData(pos);
        BlockState mimicState = modelData.get(IFacadeBlockEntity.FACADE_BLOCK_STATE_MODEL_PROPERTY);
        Direction mimicDirection = modelData.get(FancyCableFacadeBlockEntity.FACADE_DIRECTION);
{% endcase %}

        if (SFMEnvironmentUtils.isInIDE()) {
            if (mimicDirection == null) {
                SFM.LOGGER.warn("Facade direction is null for block state {} mimicking {}", state, mimicState);
            }
        }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
        if (mimicState == null || mimicDirection == null) {
            return;
        }

{% endcase %}
        // get all quads for the original model on the null-direction pass
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if (mimicState != null && side == null && mimicDirection != null) {
            /// the original model only uses un-culled faces so we force null side
            /// [net.minecraft.client.resources.model.SimpleBakedModel#getQuads(BlockState, Direction, RandomSource)]
            List<BakedQuad> originalQuads = originalModel.getQuads(state, null, rand, ModelData.EMPTY, null);
{% when '26.1.2' %}
        /// the original model only uses un-culled faces so we force null side
        /// [net.minecraft.client.resources.model.SimpleBakedModel#getQuads(BlockState, Direction, RandomSource)]
        List<BlockStateModelPart> originalParts = new ArrayList<>();
        this.delegate.collectParts(level, pos, state, random, originalParts);
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
            BakedModel mimicModel = blockRenderer.getBlockModel(mimicState);
            ChunkRenderTypeSet renderTypes = mimicModel.getRenderTypes(mimicState, rand, extraData);
{% when '26.1.2' %}
        BlockStateModel mimicModel = Minecraft.getInstance()
                .getModelManager()
                .getBlockStateModelSet()
                .get(mimicState);
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            if (renderType == null || renderTypes.contains(renderType)) {
                // Find the sprite for the mimic model
                TextureAtlasSprite sprite = null;
                List<BakedQuad> mimicQuads = mimicModel.getQuads(
                        mimicState,
                        mimicDirection,
                        rand,
                        ModelData.EMPTY,
                        renderType
                );
                if (!mimicQuads.isEmpty()) {
                    sprite = mimicQuads.get(0).getSprite();
                }
                if (sprite != null) {
                    // we want to return the original quads with the other texture
                    List<BakedQuad> resultQuads = new ArrayList<>(originalQuads.size());
                    for (BakedQuad originalQuad : originalQuads) {
                        resultQuads.add(new RetexturedBakedQuad(
                                originalQuad,
                                sprite
                        ));
                    }
                    return resultQuads;
                }
{% when '26.1.2' %}
        if (mimicModel == null) {
            return;
        }

        Material.Baked material = particleMaterial(level, pos, mimicState);

        for (BlockStateModelPart originalPart : originalParts) {
            parts.add(new RetexturedBlockStateModelPart(originalPart, material));
        }
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        ModelData modelData = level.getModelData(pos);
        BlockState mimicState = modelData.get(IFacadeBlockEntity.FACADE_BLOCK_STATE_MODEL_PROPERTY);
        if (mimicState != null) {
            BlockStateModel mimicModel = Minecraft.getInstance()
                    .getModelManager()
                    .getBlockStateModelSet()
                    .get(mimicState);
            if (mimicModel != null) {
                return mimicModel.particleMaterial(level, pos, mimicState);
{% endcase %}
            }
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return List.of();
    }

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
    }

    @Override
    public List<RenderType> getRenderTypes(
            ItemStack itemStack,
            boolean fabulous
    ) {
        return super.getRenderTypes(itemStack, fabulous);
{% when '26.1.2' %}
        return super.particleMaterial(level, pos, state);
{% endcase %}
    }
}
