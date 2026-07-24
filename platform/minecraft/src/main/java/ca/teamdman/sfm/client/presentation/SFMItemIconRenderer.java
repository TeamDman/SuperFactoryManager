package ca.teamdman.sfm.client.presentation;

import net.minecraft.client.Minecraft;

/** Shared fixed-size renderer for file and action icons. */
public final class SFMItemIconRenderer {
    public static final int SIZE = 16;

    private SFMItemIconRenderer() {
    }

    public static SFMResolvedItemIcon render(Minecraft minecraft, SFMItemIcon icon, int x, int y) {
        SFMResolvedItemIcon resolved = SFMItemIconResolver.resolve(icon);
        minecraft.getItemRenderer().renderAndDecorateItem(resolved.stack(), x, y);
        return resolved;
    }
}
