package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.block.TouchDisplaySurface;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/** Draws server-selected static or bounded snapshot imagery on the outward face. */
public class TouchDisplayBlockEntityRenderer implements BlockEntityRenderer<TouchDisplayBlockEntity> {
    private static final float SURFACE_OFFSET = 1F / 1024F;

    public TouchDisplayBlockEntityRenderer(BlockEntityRendererProvider.Context ignoredContext) {
    }

    @Override
    public void render(
            TouchDisplayBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        // Keep the image reference, interaction state, and revision from one atomic snapshot.
        // In particular, a content commit must not change the selected texture mid-render.
        TouchDisplayBlockEntity.DisplayContent content = blockEntity.content();
        Direction face = blockEntity.getBlockState().getValue(TouchDisplayBlock.FACING);
        TouchDisplaySurface.Basis basis = TouchDisplaySurface.basis(face);
        // The cache uploads each digest at most once while resident. If its
        // bounded admission is full or decoding fails, render the bundled
        // placeholder instead of binding an unregistered synthetic image ID.
        var imageLocation = ClientManagerFrameRuntime.textureFor(blockEntity).orElseGet(() ->
                content.imageSnapshot() == null
                        ? content.imageRef()
                        : TouchDisplayTextureRuntime.textureFor(content.imageSnapshot(), blockEntity.getLevel())
                                .orElse(TouchDisplayBlockEntity.DEFAULT_IMAGE)
        );
        VertexConsumer vertices = bufferSource.getBuffer(RenderType.entityCutoutNoCull(imageLocation));
        PoseStack.Pose pose = poseStack.last();

        float centerX = 0.5F + face.getStepX() * (0.5F + SURFACE_OFFSET);
        float centerY = 0.5F + face.getStepY() * (0.5F + SURFACE_OFFSET);
        float centerZ = 0.5F + face.getStepZ() * (0.5F + SURFACE_OFFSET);

        // The basis always satisfies right x up = outward normal. This fixes
        // top/left orientation for every face and keeps future click UVs stable.
        // A display emits its image rather than reflecting ambient block light.
        // The block bezel remains world-lit; only the raster surface is full-bright.
        int surfaceLight = LightTexture.FULL_BRIGHT;
        vertex(vertices, pose, centerX, centerY, centerZ, basis, -1, 1, 0, 0, face, surfaceLight, packedOverlay);
        vertex(vertices, pose, centerX, centerY, centerZ, basis, -1, -1, 0, 1, face, surfaceLight, packedOverlay);
        vertex(vertices, pose, centerX, centerY, centerZ, basis, 1, -1, 1, 1, face, surfaceLight, packedOverlay);
        vertex(vertices, pose, centerX, centerY, centerZ, basis, 1, 1, 1, 0, face, surfaceLight, packedOverlay);
    }

    private static void vertex(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            float centerX,
            float centerY,
            float centerZ,
            TouchDisplaySurface.Basis basis,
            int horizontal,
            int vertical,
            float u,
            float v,
            Direction face,
            int packedLight,
            int packedOverlay
    ) {
        float x = centerX + TouchDisplaySurface.HALF_IMAGE_SIZE * (horizontal * basis.rightX() + vertical * basis.upX());
        float y = centerY + TouchDisplaySurface.HALF_IMAGE_SIZE * (horizontal * basis.rightY() + vertical * basis.upY());
        float z = centerZ + TouchDisplaySurface.HALF_IMAGE_SIZE * (horizontal * basis.rightZ() + vertical * basis.upZ());
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        vertices.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(normal, face.getStepX(), face.getStepY(), face.getStepZ())
                .endVertex();
    }

}
