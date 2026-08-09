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
import net.minecraft.world.level.Level;

/**
 * Renders disk slot indicators on the front face of the library block.
 * Shows colored lights for occupied slots: green (normal), yellow (warnings), red (errors).
 * Indicators pulse with speed/intensity based on status.
 */
public class LibraryBlockEntityRenderer implements BlockEntityRenderer<LibraryBlockEntity> {

    // Indicator light colors
    // Normal state: Green
    private static final float NORMAL_R = 64f / 255f;
    private static final float NORMAL_G = 204f / 255f;
    private static final float NORMAL_B = 64f / 255f;

    // Error state: Red
    private static final float ERROR_R = 204f / 255f;
    private static final float ERROR_G = 64f / 255f;
    private static final float ERROR_B = 64f / 255f;

    // Warning state: Yellow
    private static final float WARNING_R = 204f / 255f;
    private static final float WARNING_G = 204f / 255f;
    private static final float WARNING_B = 64f / 255f;

    // Disk edge color (matching disk texture - dark red/burgundy)
    private static final float DISK_R = 139f / 255f;
    private static final float DISK_G = 35f / 255f;
    private static final float DISK_B = 35f / 255f;

    // Pulse parameters for each status
    private static final float NORMAL_PULSE_SPEED = 0.1f;
    private static final float NORMAL_PULSE_AMPLITUDE = 0.15f;
    private static final float WARNING_PULSE_SPEED = 0.2f;
    private static final float WARNING_PULSE_AMPLITUDE = 0.25f;
    private static final float ERROR_PULSE_SPEED = 0.4f;
    private static final float ERROR_PULSE_AMPLITUDE = 0.35f;

    // Base brightness for indicators
    private static final float BASE_BRIGHTNESS = 0.7f;

    // Layout constants based on 128x128 texture
    // Texture generator uses: GRID_START_X=4, ROW1_Y=48, ROW2_Y=72, COL_SPACING=25
    // Slot layout: [LED 4px][divider 1px][disk area 15px] = 20px total
    private static final float TEX = 128.0f;

    // Disk slot dimensions
    private static final float LIGHT_SIZE_PX = 4.0f;
    private static final float DISK_WIDTH_PX = 15.0f;
    private static final float SLOT_WIDTH = (LIGHT_SIZE_PX + 1 + DISK_WIDTH_PX) / TEX;  // 20px total
    private static final float SLOT_HEIGHT = 6.0f / TEX;
    private static final float COL_SPACING = 25.0f / TEX;
    private static final float GRID_START_X = 4.0f / TEX;
    private static final float ROW1_Y = 48.0f / TEX;
    private static final float ROW2_Y = 72.0f / TEX;

    // Indicator light position (left side of slot, integrated)
    private static final float LIGHT_SIZE = 4.0f / TEX;
    private static final float LIGHT_OFFSET_Y = (SLOT_HEIGHT - LIGHT_SIZE) / 2.0f;

    // Disk area position (right side of slot, after LED + divider)
    private static final float DISK_AREA_OFFSET_X = (LIGHT_SIZE_PX + 1) / TEX;
    private static final float DISK_WIDTH = DISK_WIDTH_PX / TEX;

    // Disk line dimensions (thin red line inside disk area)
    private static final float DISK_LINE_INSET = 1.0f / TEX;
    private static final float DISK_LINE_HEIGHT = 2.0f / TEX;

    // Z offset to prevent z-fighting
    private static final float Z_OFFSET = -0.001f;
    private static final float Z_GLOW_OFFSET = -0.002f;

    // Glow effect
    private static final float GLOW_SIZE_MULTIPLIER = 1.5f;
    private static final float GLOW_ALPHA = 0.5f;

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

        int errorMask = blockEntity.getErrorSlotMask();
        int warningMask = blockEntity.getWarningSlotMask();

        Direction facing = blockEntity.getBlockState().getValue(LibraryBlock.FACING);

        // Calculate time for pulsing animation
        Level level = blockEntity.getLevel();
        float gameTime = level != null ? level.getGameTime() + partialTick : 0;

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

        Matrix4f matrix = poseStack.last().pose();

        // Set up rendering for colored quads with blending for glow
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        // First pass: render disk lines (thin red line in each occupied slot)
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int slot = 0; slot < LibraryBlockEntity.DISK_SLOT_COUNT; slot++) {
            if ((diskMask & (1 << slot)) != 0) {
                renderDiskLine(buffer, matrix, slot);
            }
        }

        tesselator.end();

        // Second pass: render glow effects (larger, semi-transparent)
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int slot = 0; slot < LibraryBlockEntity.DISK_SLOT_COUNT; slot++) {
            if ((diskMask & (1 << slot)) != 0) {
                int slotBit = 1 << slot;
                float r, g, b, pulseSpeed, pulseAmplitude;

                // Priority: error (red) > warning (yellow) > normal (green)
                if ((errorMask & slotBit) != 0) {
                    r = ERROR_R;
                    g = ERROR_G;
                    b = ERROR_B;
                    pulseSpeed = ERROR_PULSE_SPEED;
                    pulseAmplitude = ERROR_PULSE_AMPLITUDE;
                } else if ((warningMask & slotBit) != 0) {
                    r = WARNING_R;
                    g = WARNING_G;
                    b = WARNING_B;
                    pulseSpeed = WARNING_PULSE_SPEED;
                    pulseAmplitude = WARNING_PULSE_AMPLITUDE;
                } else {
                    r = NORMAL_R;
                    g = NORMAL_G;
                    b = NORMAL_B;
                    pulseSpeed = NORMAL_PULSE_SPEED;
                    pulseAmplitude = NORMAL_PULSE_AMPLITUDE;
                }

                // Calculate pulse with slot-based phase offset for visual variety
                float phaseOffset = slot * 0.5f;
                float pulse = (float) (Math.sin((gameTime + phaseOffset) * pulseSpeed) * 0.5 + 0.5);
                float intensity = BASE_BRIGHTNESS + pulse * pulseAmplitude;

                // Render glow (larger, semi-transparent)
                renderGlow(buffer, matrix, slot, r * intensity, g * intensity, b * intensity);
            }
        }

        tesselator.end();

        // Third pass: render main indicator lights
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int slot = 0; slot < LibraryBlockEntity.DISK_SLOT_COUNT; slot++) {
            if ((diskMask & (1 << slot)) != 0) {
                int slotBit = 1 << slot;
                float r, g, b, pulseSpeed, pulseAmplitude;

                if ((errorMask & slotBit) != 0) {
                    r = ERROR_R;
                    g = ERROR_G;
                    b = ERROR_B;
                    pulseSpeed = ERROR_PULSE_SPEED;
                    pulseAmplitude = ERROR_PULSE_AMPLITUDE;
                } else if ((warningMask & slotBit) != 0) {
                    r = WARNING_R;
                    g = WARNING_G;
                    b = WARNING_B;
                    pulseSpeed = WARNING_PULSE_SPEED;
                    pulseAmplitude = WARNING_PULSE_AMPLITUDE;
                } else {
                    r = NORMAL_R;
                    g = NORMAL_G;
                    b = NORMAL_B;
                    pulseSpeed = NORMAL_PULSE_SPEED;
                    pulseAmplitude = NORMAL_PULSE_AMPLITUDE;
                }

                float phaseOffset = slot * 0.5f;
                float pulse = (float) (Math.sin((gameTime + phaseOffset) * pulseSpeed) * 0.5 + 0.5);
                float intensity = BASE_BRIGHTNESS + pulse * pulseAmplitude;

                renderSlotIndicator(buffer, matrix, slot, r * intensity, g * intensity, b * intensity);
            }
        }

        tesselator.end();

        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private void renderDiskLine(BufferBuilder buffer, Matrix4f matrix, int slot) {
        int row = slot / 5;
        int col = slot % 5;

        float slotX = GRID_START_X + col * COL_SPACING;
        float slotY = (row == 0) ? ROW1_Y : ROW2_Y;

        // Disk area is on the right side of slot (after LED + divider)
        float diskAreaX = slotX + DISK_AREA_OFFSET_X;

        // Thin red line inside the disk area (representing disk edge)
        // Mirror X coordinate (1.0f - x) to match texture orientation on north face
        float x1 = 1.0f - diskAreaX - DISK_WIDTH + DISK_LINE_INSET;
        float x2 = 1.0f - diskAreaX - DISK_LINE_INSET;
        float y1 = 1.0f - slotY - SLOT_HEIGHT / 2.0f - DISK_LINE_HEIGHT / 2.0f;
        float y2 = 1.0f - slotY - SLOT_HEIGHT / 2.0f + DISK_LINE_HEIGHT / 2.0f;
        float z = Z_OFFSET;

        buffer.vertex(matrix, x1, y1, z).color(DISK_R, DISK_G, DISK_B, 1.0f).endVertex();
        buffer.vertex(matrix, x1, y2, z).color(DISK_R, DISK_G, DISK_B, 1.0f).endVertex();
        buffer.vertex(matrix, x2, y2, z).color(DISK_R, DISK_G, DISK_B, 1.0f).endVertex();
        buffer.vertex(matrix, x2, y1, z).color(DISK_R, DISK_G, DISK_B, 1.0f).endVertex();
    }

    private void renderSlotIndicator(BufferBuilder buffer, Matrix4f matrix, int slot, float r, float g, float b) {
        int row = slot / 5;
        int col = slot % 5;

        float slotX = GRID_START_X + col * COL_SPACING;
        float slotY = (row == 0) ? ROW1_Y : ROW2_Y;

        // LED is on the left side of slot (integrated)
        // Mirror X coordinate (1.0f - x) to match texture orientation on north face
        float x1 = 1.0f - slotX - LIGHT_SIZE;
        float x2 = 1.0f - slotX;
        float y1 = 1.0f - slotY - LIGHT_OFFSET_Y - LIGHT_SIZE;
        float y2 = 1.0f - slotY - LIGHT_OFFSET_Y;
        float z = Z_OFFSET;

        // Quad on the north face (z=0)
        buffer.vertex(matrix, x1, y1, z).color(r, g, b, 1.0f).endVertex();
        buffer.vertex(matrix, x1, y2, z).color(r, g, b, 1.0f).endVertex();
        buffer.vertex(matrix, x2, y2, z).color(r, g, b, 1.0f).endVertex();
        buffer.vertex(matrix, x2, y1, z).color(r, g, b, 1.0f).endVertex();
    }

    private void renderGlow(BufferBuilder buffer, Matrix4f matrix, int slot, float r, float g, float b) {
        int row = slot / 5;
        int col = slot % 5;

        float slotX = GRID_START_X + col * COL_SPACING;
        float slotY = (row == 0) ? ROW1_Y : ROW2_Y;

        // Glow centered on the LED (left side of slot)
        // Mirror X coordinate (1.0f - x) to match texture orientation on north face
        float lightCenterX = 1.0f - slotX - LIGHT_SIZE / 2.0f;
        float lightCenterY = slotY + LIGHT_OFFSET_Y + LIGHT_SIZE / 2.0f;

        float glowSize = LIGHT_SIZE * GLOW_SIZE_MULTIPLIER;
        float halfGlow = glowSize / 2.0f;

        float x1 = lightCenterX - halfGlow;
        float x2 = lightCenterX + halfGlow;
        float y1 = 1.0f - lightCenterY - halfGlow;
        float y2 = 1.0f - lightCenterY + halfGlow;
        float z = Z_GLOW_OFFSET;

        // Semi-transparent glow quad
        buffer.vertex(matrix, x1, y1, z).color(r, g, b, GLOW_ALPHA).endVertex();
        buffer.vertex(matrix, x1, y2, z).color(r, g, b, GLOW_ALPHA).endVertex();
        buffer.vertex(matrix, x2, y2, z).color(r, g, b, GLOW_ALPHA).endVertex();
        buffer.vertex(matrix, x2, y1, z).color(r, g, b, GLOW_ALPHA).endVertex();
    }
}
