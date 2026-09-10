package ca.teamdman.sfm.client.presentation;

import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Shared fixed-size renderer for file and action icons. */
public final class SFMItemIconRenderer {
    public static final int SIZE = 16;
    public record Inspection(SFMResolvedItemIcon resolved, boolean levelAvailable, String reason) {}

    /** Same decision used by rendering and contextual evidence; does not draw or acquire source data. */
    public static Inspection inspect(Minecraft minecraft,SFMItemIcon icon) {
        SFMResolvedItemIcon resolved=SFMItemIconResolver.resolve(icon);
        String reason=resolved.usedFallback() ? "requested-item-unavailable" : "requested-item-available";
        if (requiresTitleScreenFallback(minecraft,resolved)) {
            reason="title-screen-custom-renderer-not-proven-level-independent";
            resolved=SFMItemIconResolver.resolveFallback(icon);
            if (requiresTitleScreenFallback(minecraft,resolved)) {
                reason="title-screen-declared-fallback-also-unavailable";
                resolved=SFMItemIconResolver.resolvePaper(icon);
            }
        }
        return new Inspection(resolved,minecraft.level!=null,reason);
    }

    private SFMItemIconRenderer() {
    }

    public static SFMResolvedItemIcon render(Minecraft minecraft, SFMItemIcon icon, int x, int y) {
        return render(null, minecraft, icon, x, y);
    }

    /**
     * Renders a GUI item in the caller's local coordinate space.
     *
     * <p>Vanilla's 1.19.2 GUI item renderer writes through the global
     * model-view stack and otherwise ignores the {@link PoseStack} supplied to
     * a panel. Composing the caller's current pose here keeps translated and
     * independently scaled workspace panels aligned with their physical
     * scissors.</p>
     */
    public static SFMResolvedItemIcon render(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMItemIcon icon,
            int x,
            int y
    ) {
        SFMResolvedItemIcon resolved = inspect(minecraft,icon).resolved();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            if (poseStack != null) {
                modelView.mulPoseMatrix(poseStack.last().pose());
                RenderSystem.applyModelViewMatrix();
            }
            minecraft.getItemRenderer().renderAndDecorateItem(resolved.stack(), x, y);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
        if (resolved.usedFallback() && poseStack!=null)
            ca.teamdman.sfm.client.screen.SFMFontUtils.draw(poseStack,minecraft.font,"!",x+11,y,0xFFFFFF55,true);
        return resolved;
    }

    /**
     * A mod-supplied custom item renderer may require a loaded level or player.
     * Title-screen rendering therefore inspects custom models before entering
     * them, while preserving Minecraft's explicitly level-independent default
     * renderer.
     */
    static boolean shouldInspectCustomRenderer(boolean levelAvailable) {
        return !levelAvailable;
    }

    static boolean shouldUseTitleScreenFallback(
            boolean levelAvailable,
            boolean customModel,
            boolean knownLevelIndependentRenderer
    ) {
        return !levelAvailable && customModel && !knownLevelIndependentRenderer;
    }

    private static boolean requiresTitleScreenFallback(
            Minecraft minecraft,
            SFMResolvedItemIcon icon
    ) {
        boolean levelAvailable = minecraft.level != null;
        if (!shouldInspectCustomRenderer(levelAvailable)) return false;
        boolean customModel;
        try {
            customModel = minecraft.getItemRenderer()
                    .getModel(icon.stack(), minecraft.level, minecraft.player, 0).isCustomRenderer();
        } catch (RuntimeException unavailable) {
            // Failed model lookup is not evidence of a safe vanilla custom renderer.
            return true;
        }
        return shouldUseTitleScreenFallback(
                levelAvailable,
                customModel,
                customModel && usesMinecraftLevelIndependentRenderer(minecraft, icon)
        );
    }

    /**
     * Vanilla's default custom item path is explicitly implemented by
     * {@code BlockEntityWithoutLevelRenderer}; for example, ChestRenderer has
     * a deliberate {@code level == null} branch. A mod-supplied renderer is not
     * assumed to share that contract until a future capability contribution
     * says so.
     */
    private static boolean usesMinecraftLevelIndependentRenderer(
            Minecraft minecraft,
            SFMResolvedItemIcon icon
    ) {
        try {
            ResourceLocation itemId = SFMWellKnownRegistries.ITEMS.getId(icon.stack().getItem());
            return itemId != null
                    && itemId.getNamespace().equals("minecraft")
                    && IClientItemExtensions.of(icon.stack()).getCustomRenderer()
                    == minecraft.getItemRenderer().getBlockEntityRenderer();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

}
