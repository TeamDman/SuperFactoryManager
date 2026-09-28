package ca.teamdman.sfm.client.presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared fixed-size renderer for file and action icons. */
public final class SFMItemIconRenderer {
    public static final int SIZE = 16;

    private SFMItemIconRenderer() {
    }

    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public static SFMResolvedItemIcon render(GuiGraphicsExtractor graphics, Minecraft minecraft, SFMItemIcon icon, int x, int y) {
        SFMResolvedItemIcon resolved = SFMItemIconResolver.resolve(icon);
        graphics.item(resolved.stack(), x, y);
        return resolved;
    }
}
