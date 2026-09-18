package ca.teamdman.sfm.client.presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Shared fixed-size renderer for file and action icons. */
public final class SFMItemIconRenderer {
    public static final int SIZE = 16;

    private SFMItemIconRenderer() {
    }

    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public static SFMResolvedItemIcon render(GuiGraphics graphics, Minecraft minecraft, SFMItemIcon icon, int x, int y) {
        SFMResolvedItemIcon resolved = SFMItemIconResolver.resolve(icon);
        graphics.renderItem(resolved.stack(), x, y);
        return resolved;
    }
}
