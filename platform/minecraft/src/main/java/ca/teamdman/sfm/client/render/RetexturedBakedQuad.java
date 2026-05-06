package ca.teamdman.sfm.client.render;


import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;

import java.util.Arrays;
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
public class RetexturedBakedQuad implements BlockStateModel {

    private final BlockStateModel wrapped;

    public RetexturedBakedQuad(BlockStateModel wrapped) {
        this.wrapped = wrapped;
    }

    private void remapQuad() {

        for (int i = 0; i < 4; ++i) {
            int integerSize = DefaultVertexFormat.BLOCK.getVertexSize() / 4;
            int j = integerSize * i;
            int uvIndex = 4;
            this.vertices[j + uvIndex] = Float.floatToRawIntBits(this.texture.getU(getUnInterpolatedU(this.sprite, Float.intBitsToFloat(this.vertices[j + uvIndex]))));
            this.vertices[j + uvIndex + 1] = Float.floatToRawIntBits(this.texture.getV(getUnInterpolatedV(this.sprite, Float.intBitsToFloat(this.vertices[j + uvIndex + 1]))));
        }
    }

    @Override
    public TextureAtlasSprite getSprite() {

        return texture;
    }

    @MCVersionDependentBehaviour
    private static float getUnInterpolatedU(TextureAtlasSprite sprite, float u) {

        float f = sprite.getU1() - sprite.getU0();
        return (u - sprite.getU0()) / f;// * 16.0F; // don't multiple for 1.20.2 and above
    }

    @MCVersionDependentBehaviour
    private static float getUnInterpolatedV(TextureAtlasSprite sprite, float v) {

        float f = sprite.getV1() - sprite.getV0();
        return (v - sprite.getV0()) / f;// * 16.0F; // don't multiple for 1.20.2 and above
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> output) {

    }

    @Override
    public Material.Baked particleMaterial() {
        return null;
    }

    @Override
    public @BakedQuad.MaterialFlags int materialFlags() {
        return 0;
    }
}
