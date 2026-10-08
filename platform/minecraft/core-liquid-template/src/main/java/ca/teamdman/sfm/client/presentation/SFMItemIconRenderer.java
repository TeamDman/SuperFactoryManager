package ca.teamdman.sfm.client.presentation;

{% if features.item_icon_context_safety %}
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
{% endif %}
{% case minecraft_version %}
{% when "1.19.4" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% when "1.19.2" %}
import com.mojang.blaze3d.systems.RenderSystem;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% endcase %}
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.item_icon_context_safety %}
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
{% endif %}
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% else %}
import net.minecraft.client.gui.GuiGraphics;
{% endcase %}

/** Shared fixed-size renderer for file and action icons. */
public final class SFMItemIconRenderer {
    public static final int SIZE = 16;
{% if features.item_icon_context_safety %}
    public record Inspection(SFMResolvedItemIcon resolved, boolean levelAvailable, String reason) {}

    /** Same decision used by rendering and contextual evidence; does not draw or acquire source data. */
    public static Inspection inspect(
            Minecraft minecraft,
            SFMItemIcon icon
    ) {
        SFMResolvedItemIcon resolved = SFMItemIconResolver.resolve(icon);
        String reason = resolved.usedFallback() ? "requested-item-unavailable" : "requested-item-available";
        if (requiresTitleScreenFallback(minecraft, resolved)) {
            reason = "title-screen-custom-renderer-not-proven-level-independent";
            resolved = SFMItemIconResolver.resolveFallback(icon);
            if (requiresTitleScreenFallback(minecraft, resolved)) {
                reason = "title-screen-declared-fallback-also-unavailable";
                resolved = SFMItemIconResolver.resolvePaper(icon);
            }
        }
        return new Inspection(resolved, minecraft.level != null, reason);
    }
{% endif %}

    private SFMItemIconRenderer() {
    }

{% case minecraft_version %}
{% when "1.19.2" %}
    public static SFMResolvedItemIcon render(
            Minecraft minecraft,
            SFMItemIcon icon,
            int x,
            int y
    ) {
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
{% if features.item_icon_context_safety %}
        SFMResolvedItemIcon resolved = inspect(minecraft, icon).resolved();
{% else %}
        SFMResolvedItemIcon resolved = SFMItemIconResolver.resolve(icon);
{% endif %}
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
{% if features.item_icon_fallback_indicator %}
        if (resolved.usedFallback() && poseStack!=null)
            ca.teamdman.sfm.client.screen.SFMFontUtils.draw(poseStack,minecraft.font,"!",x+11,y,0xFFFFFF55,true);
{% endif %}
        return resolved;
    }
{% when "1.19.4" %}
    @MCVersionDependentBehaviour
    public static SFMResolvedItemIcon render(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMItemIcon icon,
            int x,
            int y
    ) {
{% if features.item_icon_context_safety %}
        SFMResolvedItemIcon resolved = inspect(minecraft, icon).resolved();
{% else %}
        SFMResolvedItemIcon resolved = SFMItemIconResolver.resolve(icon);
{% endif %}
        minecraft.getItemRenderer().renderAndDecorateItem(poseStack, resolved.stack(), x, y);
{% if features.item_icon_fallback_indicator %}
        if (resolved.usedFallback()) {
            ca.teamdman.sfm.client.screen.SFMFontUtils.draw(
                    poseStack,
                    minecraft.font,
                    "!",
                    x + 11,
                    y,
                    0xFFFFFF55,
                    true
            );
        }
{% endif %}
        return resolved;
    }
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    public static SFMResolvedItemIcon render(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            SFMItemIcon icon,
            int x,
            int y
    ) {
{% else %}
    public static SFMResolvedItemIcon render(
            GuiGraphics graphics,
            Minecraft minecraft,
            SFMItemIcon icon,
            int x,
            int y
    ) {
{% endcase %}
        SFMResolvedItemIcon resolved = SFMItemIconResolver.resolve(icon);
{% case minecraft_version %}
{% when "26.1.2" %}
        graphics.item(resolved.stack(), x, y);
{% else %}
        graphics.renderItem(resolved.stack(), x, y);
{% endcase %}
        return resolved;
    }
{% endcase %}
{% if features.item_icon_context_safety %}

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

{% endif %}
}
