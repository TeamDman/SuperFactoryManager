package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.block.LibraryBlock;
import ca.teamdman.sfm.common.blockentity.LibraryBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * Renders disk slot indicators on the front face of the library block.
 * Shows amber lights for occupied slots, matching their physical positions.
 */
public class LibraryBlockEntityRenderer implements BlockEntityRenderer<LibraryBlockEntity> {

    // Amber phosphor color (matching the UI palette)
    private static final float AMBER_R = 204f / 255f;
    private static final float AMBER_G = 150f / 255f;
    private static final float AMBER_B = 64f / 255f;

    // Slot layout constants (in block units, 0-1 range)
    private static final float SLOT_SIZE = 1.0f / 16.0f;  // 1 pixel
    private static final float SLOT_SPACING = 2.0f / 16.0f;  // 2 pixels between slots

    // Starting position for the slot grid
    private static final float GRID_START_X = 3.0f / 16.0f;
    private static final float ROW1_Y = 9.0f / 16.0f;
    private static final float ROW2_Y = 12.0f / 16.0f;

    // Z offset to prevent z-fighting
    private static final float Z_OFFSET = -0.001f;

    public LibraryBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            LibraryBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        int diskMask = blockEntity.getDiskSlotMask();
        if (diskMask == 0) return;

        Direction facing = blockEntity.getBlockState().getValue(LibraryBlock.FACING);

        poseStack.pushPose();

        // Move to center of block
        poseStack.translate(0.5, 0.5, 0.5);

        // Rotate based on facing direction
        // Note: blockstate uses clockwise rotation, but renderer uses counter-clockwise,
        // so EAST/WEST values are inverted (90 <-> 270)
        float rotation = switch (facing) {
            case NORTH -> 0;
            case SOUTH -> 180;
            case EAST -> 270;
            case WEST -> 90;
            default -> 0;
        };
        poseStack.mulPose(Vector3f.YP.rotationDegrees(rotation));

        // Move back from center
        poseStack.translate(-0.5, -0.5, -0.5);

        // Set up rendering for colored quads
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        Matrix4f matrix = poseStack.last().pose();

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // Render indicators for each occupied slot
        for (int slot = 0; slot < LibraryBlockEntity.DISK_SLOT_COUNT; slot++) {
            if ((diskMask & (1 << slot)) != 0) {
                renderSlotIndicator(buffer, matrix, slot);
            }
        }

        tesselator.end();

        poseStack.popPose();
    }

    private void renderSlotIndicator(BufferBuilder buffer, Matrix4f matrix, int slot) {
        int row = slot / 5;
        int col = 4 - (slot % 5);  // Mirror column to match GUI layout

        float x = GRID_START_X + col * SLOT_SPACING;
        float y = (row == 0) ? ROW1_Y : ROW2_Y;

        float x1 = x;
        float x2 = x + SLOT_SIZE;
        float y1 = 1.0f - y - SLOT_SIZE;
        float y2 = 1.0f - y;
        float z = Z_OFFSET;

        // Quad on the north face (z=0)
        buffer.vertex(matrix, x1, y1, z).color(AMBER_R, AMBER_G, AMBER_B, 1.0f).endVertex();
        buffer.vertex(matrix, x1, y2, z).color(AMBER_R, AMBER_G, AMBER_B, 1.0f).endVertex();
        buffer.vertex(matrix, x2, y2, z).color(AMBER_R, AMBER_G, AMBER_B, 1.0f).endVertex();
        buffer.vertex(matrix, x2, y1, z).color(AMBER_R, AMBER_G, AMBER_B, 1.0f).endVertex();
    }
}
