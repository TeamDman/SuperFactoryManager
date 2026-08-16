package ca.teamdman.sfm.client.presentation;

import net.minecraft.client.Minecraft;

/** Shared fixed-size renderer for file and action icons. */
public final class SFMItemIconRenderer {
    public static final int SIZE = 16;

    private SFMItemIconRenderer() {
    }

    public static SFMResolvedItemIcon render(Minecraft minecraft, SFMItemIcon icon, int x, int y) {
        SFMResolvedItemIcon resolved = SFMItemIconResolver.resolve(icon);
        if (requiresContextFallback(minecraft.level != null, usesCustomRenderer(minecraft, resolved))) {
            resolved = SFMItemIconResolver.resolveFallback(icon);
            if (usesCustomRenderer(minecraft, resolved)) {
                resolved = SFMItemIconResolver.resolvePaper(icon);
            }
        }
        minecraft.getItemRenderer().renderAndDecorateItem(resolved.stack(), x, y);
        return resolved;
    }

    /**
     * Custom item renderers supplied by mods commonly require a loaded level
     * or player. Invoking one on a title-screen workspace can crash before the
     * vanilla renderer restores its model-view stack, so choose the icon's
     * declared fallback before entering that renderer.
     */
    static boolean requiresContextFallback(boolean levelAvailable, boolean customRenderer) {
        return !levelAvailable && customRenderer;
    }

    private static boolean usesCustomRenderer(Minecraft minecraft, SFMResolvedItemIcon icon) {
        try {
            return minecraft.getItemRenderer()
                    .getModel(icon.stack(), minecraft.level, minecraft.player, 0)
                    .isCustomRenderer();
        } catch (RuntimeException unavailable) {
            // Model lookup itself is still outside the stateful GUI render path.
            // Treat an incompatible lookup exactly like an unsafe custom model.
            return true;
        }
    }
}
