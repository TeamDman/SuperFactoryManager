package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.blockentity.FancyCableFacadeBlockEntity;
import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import ca.teamdman.sfm.common.facade.FacadeTransparency;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FancyCableFacadeBlockModelWrapper extends BakedModelWrapper<BakedModel> {

    private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());
    private static final ChunkRenderTypeSet ALL = ChunkRenderTypeSet.all();

    public FancyCableFacadeBlockModelWrapper(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            @NotNull RandomSource rand,
            @NotNull ModelData extraData,
            @Nullable RenderType renderType
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockState mimicState = extraData.get(IFacadeBlockEntity.FACADE_BLOCK_STATE_MODEL_PROPERTY);
        Direction mimicDirection = extraData.get(FancyCableFacadeBlockEntity.FACADE_DIRECTION);

        // get all quads for the original model on the null-direction pass
        if (mimicState != null && side == null && mimicDirection != null) {
            /// the original model only uses un-culled faces so we force null side
            /// [net.minecraft.client.resources.model.SimpleBakedModel#getQuads(BlockState, Direction, RandomSource)]
            List<BakedQuad> originalQuads = originalModel.getQuads(state, null, rand, ModelData.EMPTY, null);

            BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
            BakedModel mimicModel = blockRenderer.getBlockModel(mimicState);
            ChunkRenderTypeSet renderTypes = mimicModel.getRenderTypes(mimicState, rand, extraData);

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
            }
        }
        return List.of();
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(
            @NotNull BlockState cableBlockState,
            @NotNull RandomSource rand,
            @NotNull ModelData data
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
    }
}
