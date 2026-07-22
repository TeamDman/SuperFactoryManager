package ca.teamdman.sfm.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;

/** Shared pixel-aligned crosshair used by Draw cursors and read-only visualizations. */
public final class SFMGuiCrosshair {
    private SFMGuiCrosshair() {
    }

    public static void draw(PoseStack poseStack, int x, int y, int radius, int color) {
        GuiComponent.fill(poseStack, x - radius, y, x - 2, y + 1, color);
        GuiComponent.fill(poseStack, x + 3, y, x + radius + 1, y + 1, color);
        GuiComponent.fill(poseStack, x, y - radius, x + 1, y - 2, color);
        GuiComponent.fill(poseStack, x, y + 3, x + 1, y + radius + 1, color);
        GuiComponent.fill(poseStack, x, y, x + 1, y + 1, color);
    }
}
