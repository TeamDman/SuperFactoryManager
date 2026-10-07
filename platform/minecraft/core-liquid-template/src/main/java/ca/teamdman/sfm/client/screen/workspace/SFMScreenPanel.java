package ca.teamdman.sfm.client.screen.workspace;

{% if features.workspace_keyboard_context %}
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% else %}
import net.minecraft.client.gui.GuiGraphics;
{% endcase %}
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
{% if features.workspace_keyboard_context %}
import net.minecraft.resources.ResourceLocation;
{% endif %}
{% if features.workspace_widget_hosts or features.workspace_panel_tooltips %}

import java.util.Optional;
{% endif %}


/**
 * Content hosted by {@link SFMScreenMultiplexer}.
 *
 * <p>This is intentionally narrower than Minecraft's {@code Screen}. A
 * vanilla screen owns a complete viewport and can replace the global current
 * screen from its close path, so it cannot be embedded safely without an
 * explicit adapter.</p>
 */
public interface SFMScreenPanel {
    Component title();

{% if features.workspace_keyboard_context %}
    /** Deepest contextual keybinding situation when no child widget owns focus. */
    default ResourceLocation keyboardUsageSituationId() {
        return SFMKeyboardUsageSituations.DEFAULT;
    }

{% endif %}
    default Component narration() {
        return title();
    }

{% if features.workspace_lifecycle %}
    /** Current data-loss posture used by pane-level close preflight. */
    default SFMPanelCloseState closeState() {
        return SFMPanelCloseState.cleanEditable();
    }

{% endif %}
{% if features.workspace_widget_hosts %}
    /**
     * Optional Minecraft-like child surface hosted by this panel.
     *
     * <p>The workspace remains the only real {@code Screen}; this host gives
     * embedded controls one ordered focus, rendering, narration, and event
     * path without pretending that each panel owns a full screen.</p>
     */
    default Optional<SFMPanelWidgetHost> widgetHost() {
        return Optional.empty();
    }

    /**
     * True when every panel-level input path has been represented as a child.
     * This prevents an unhandled child event from being retried against legacy
     * callbacks and delivered twice.
     */
    default boolean widgetHostOwnsInput() {
        return false;
    }

{% endif %}
    default void opened(
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            SFMWorkspacePanelContext context
    ) {
    }

    default void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
    }

    default void closed() {
    }

    default void tick() {
    }

{% if features.workspace_panel_tooltips %}
    /**
     * Describes a tooltip for the current pointer without painting it.
     * The workspace renders the returned tooltip after all panel scissors and
     * sibling panels have finished.
     */
    default Optional<SFMPanelTooltip> tooltipAt(double mouseX, double mouseY) {
        return Optional.empty();
    }

{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    void render(
            PoseStack poseStack,
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    void render(
            GuiGraphicsExtractor graphics,
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    void render(
            GuiGraphics graphics,
{% endcase %}
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            float partialTick,
            boolean focused
    );

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean charTyped(char character, int modifiers) {
        return false;
    }

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default void mouseMoved(double mouseX, double mouseY) {
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

}
