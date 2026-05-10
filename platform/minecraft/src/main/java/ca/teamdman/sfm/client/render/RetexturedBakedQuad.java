package ca.teamdman.sfm.client.render;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Revived from 1.14
 *
 * @author Mojang
 * Thanks tterrag!
 *
 */
// The original file can be found at:
// https://github.com/CoFH/CoFHCore/blob/dcd7bd6703418ee2e8eb2185957de83925fa89fe/src/main/java/cofh/lib/client/renderer/block/model/RetexturedBakedQuad.java
// The license can be found at:
// https://github.com/CoFH/CoFHCore/blob/dcd7bd6703418ee2e8eb2185957de83925fa89fe/README.md
// Their don't-be-a-jerk license is compatible as far as I can tell, thanks CoFH <3
public class RetexturedBakedQuad extends DelegateBlockStateModel {

    private final Material.Baked texture;

    public RetexturedBakedQuad(BlockStateModel wrapped, Material.Baked texture) {
        super(wrapped);
        this.texture = texture;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        List<BlockStateModelPart> originalParts = new ArrayList<>();
        this.delegate.collectParts(level, pos, state, random, originalParts);
        for (BlockStateModelPart originalPart : originalParts) {
            parts.add(new RetexturedBlockStateModelPart(originalPart, texture));
        }
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return texture;
    }

    /**
     * Utility method to retexture a single BakedQuad.
     */
    public static BakedQuad retexture(BakedQuad quad, Material.Baked material) {
        MutableQuad mutable = new MutableQuad();
        mutable.setFrom(quad);
        mutable.setSpriteAndMoveUv(material);
        return mutable.toBakedQuad();
    }

    public record RetexturedBlockStateModelPart(
            BlockStateModelPart delegate,
            Material.Baked material
    ) implements BlockStateModelPart {

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            List<BakedQuad> original = this.delegate.getQuads(direction);
            List<BakedQuad> result = new ArrayList<>(original.size());
            for (BakedQuad quad : original) {
                result.add(retexture(quad, material));
            }
            return result;
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
