package ca.teamdman.sfm.client.render;


import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RetexturedBakedQuad implements BlockStateModel {

    private final BlockStateModel wrapped;
    private final Material.Baked   texture;

    public RetexturedBakedQuad(BlockStateModel wrapped, Material.Baked texture) {
        this.wrapped = wrapped;
        this.texture = texture;
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
        // Deprecated but still in interface
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> output) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        wrapped.collectParts(level, pos, state, random, parts);
        for (BlockStateModelPart part : parts) {
            output.add(new RetexturedBlockStateModelPart(part, texture));
        }
    }

    @Override
    public Material.Baked particleMaterial() {
        return texture;
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return texture;
    }

    @Override
    public int materialFlags() {
        return wrapped.materialFlags();
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return wrapped.materialFlags(level, pos, state);
    }

    private record RetexturedBlockStateModelPart(
            BlockStateModelPart delegate,
            Material.Baked material
    ) implements BlockStateModelPart {

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            List<BakedQuad> original = this.delegate.getQuads(direction);
            List<BakedQuad> result   = new ArrayList<>(original.size());
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
