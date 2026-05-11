package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.FancyCableFacadeBlockEntity;
import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

public class FancyCableFacadeBlockModelWrapper extends DelegateBlockStateModel {

    public FancyCableFacadeBlockModelWrapper(BlockStateModel originalModel) {
        super(originalModel);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        ModelData modelData = level.getModelData(pos);
        BlockState mimicState = modelData.get(IFacadeBlockEntity.FACADE_BLOCK_STATE_MODEL_PROPERTY);
        Direction mimicDirection = modelData.get(FancyCableFacadeBlockEntity.FACADE_DIRECTION);

        if (SFMEnvironmentUtils.isInIDE()) {
            if (mimicDirection == null) {
                SFM.LOGGER.warn("Facade direction is null for block state {} mimicking {}", state, mimicState);
            }
        }

        if (mimicState == null || mimicDirection == null) {
            return;
        }

        // get all quads for the original model on the null-direction pass
        /// the original model only uses un-culled faces so we force null side
        /// [net.minecraft.client.resources.model.SimpleBakedModel#getQuads(BlockState, Direction, RandomSource)]
        List<BlockStateModelPart> originalParts = new ArrayList<>();
        this.delegate.collectParts(level, pos, state, random, originalParts);

        BlockStateModel mimicModel = Minecraft.getInstance()
                .getModelManager()
                .getBlockStateModelSet()
                .get(mimicState);

        if (mimicModel == null) {
            return;
        }

        List<BlockStateModelPart> mimicParts = new ArrayList<>();
        mimicModel.collectParts(level, pos, mimicState, random, mimicParts);

        Material.Baked material = particleMaterial(level, pos, mimicState);

        for (BlockStateModelPart originalPart : originalParts) {
            parts.add(new RetexturedBlockStateModelPart(originalPart, material));
        }
    }

    private record RetexturedBlockStateModelPart(
            BlockStateModelPart delegate,
            Material.Baked material
    ) implements BlockStateModelPart {

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            List<BakedQuad> original = this.delegate.getQuads(direction);
            List<BakedQuad> result = new ArrayList<>(original.size());
            for (BakedQuad quad : original) {
                MutableQuad mutable = new MutableQuad();
                mutable.setFrom(quad);
                mutable.setSpriteAndMoveUv(material);
                result.add(mutable.toBakedQuad());
            }
            return result;
        }

        @Override
        public TriState ambientOcclusion() {
            return this.delegate.ambientOcclusion();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return this.delegate.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return material;
        }

        @Override
        public int materialFlags() {
            return this.delegate.materialFlags();
        }
    }
}
