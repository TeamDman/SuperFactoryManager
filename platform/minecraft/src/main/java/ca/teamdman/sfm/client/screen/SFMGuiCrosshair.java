package ca.teamdman.sfm.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared pixel-aligned crosshair used by Draw cursors and read-only visualizations. */
public final class SFMGuiCrosshair {
    private SFMGuiCrosshair() {
    }

    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int radius, int color) {
        graphics.fill(x - radius, y, x - 2, y + 1, color);
        graphics.fill(x + 3, y, x + radius + 1, y + 1, color);
        graphics.fill(x, y - radius, x + 1, y - 2, color);
        graphics.fill(x, y + 3, x + 1, y + radius + 1, color);
        graphics.fill(x, y, x + 1, y + 1, color);
    }
}
