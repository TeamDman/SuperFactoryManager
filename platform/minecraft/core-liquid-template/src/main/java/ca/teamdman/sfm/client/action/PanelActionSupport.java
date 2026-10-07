package ca.teamdman.sfm.client.action;

{% if features.command_palette %}
{% if features.document_history %}
import ca.teamdman.sfm.client.history.SFMDocumentHistoryHost;
{% endif %}
{% endif %}
{% if features.command_palette %}
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
{% endif %}
{% if features.workspace_panels %}
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
{% endif %}
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class PanelActionSupport {
    private PanelActionSupport() {
    }

{% if features.workspace_panels %}
    static SFMClientActionAvailability<SFMScreenMultiplexer> resolve(SFMClientActionContext context) {
        if (!context.originatingHostIsCurrent().getAsBoolean()) {
            return SFMClientActionAvailability.unavailable(SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
        }
        if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)) {
            return SFMClientActionAvailability.unavailable(
                    Component.literal("Panel actions require an SFM panel workspace"));
        }
        if (context.originatingPanelId() != null
                && !workspace.containsPanel(context.originatingPanelId())) {
            return SFMClientActionAvailability.unavailable(
                    Component.literal("The originating SFM panel is no longer available"));
        }
        return SFMClientActionAvailability.available(workspace);
    }

{% endif %}
{% if features.workspace_panel_actions %}
    static java.util.Optional<SFMScreenPanel> capturedPanel(
            SFMScreenMultiplexer workspace,
            SFMClientActionContext context
    ) {
        if (context.originatingPanelId() != null) {
            return workspace.panel(context.originatingPanelId());
        }
        return java.util.Optional.ofNullable(workspace.focusedPanelInstance());
    }

    static SFMClientActionAvailability<CapturedPanel> resolveCapturedPanel(
            SFMClientActionContext context
    ) {
        SFMClientActionAvailability<SFMScreenMultiplexer> workspaceAvailability = resolve(context);
        if (!workspaceAvailability.isAvailable()) {
            return SFMClientActionAvailability.unavailable(workspaceAvailability.unavailableReason());
        }
        SFMScreenMultiplexer workspace = workspaceAvailability.target();
        SFMWorkspacePanelId panelId = context.originatingPanelId() == null
                ? workspace.focusedPanelId()
                : context.originatingPanelId();
        if (!workspace.containsPanel(panelId)) {
            return SFMClientActionAvailability.unavailable(
                    Component.literal("The originating SFM panel is no longer available"));
        }
        return SFMClientActionAvailability.available(new CapturedPanel(workspace, panelId));
    }

{% endif %}
    static int closePaletteAfter(int result) {
        Minecraft minecraft = Minecraft.getInstance();
{% if features.command_palette %}
        if (result > 0 && minecraft != null
                && minecraft.screen instanceof SFMCommandPaletteScreen palette) {
            palette.onClose();
        }
{% endif %}
        return result;
    }

    /**
     * Closes a constrained confirmation and any palette that presented it,
     * stopping as soon as the action's owning host is visible again.
     *
     * <p>A confirmed operation is terminal for that choice journey. Returning
     * to the now-stale parent choice surface would strand the user above the
     * workspace whose pane membership just changed.</p>
     */
    static int closePaletteChainAfter(int result, Object owningHost) {
        Minecraft minecraft = Minecraft.getInstance();
        if (result <= 0 || minecraft == null) return result;
{% if features.command_palette %}
        for (int depth = 0; depth < 16
                && minecraft.screen instanceof SFMCommandPaletteScreen palette;
                depth++) {
            Object before = minecraft.screen;
            palette.onClose();
            if (minecraft.screen == owningHost || minecraft.screen == before) break;
        }
{% endif %}
        return result;
    }

    /**
     * Keep an active palette open when that exact palette is the mutated
     * document. A constrained child palette still closes back to its parent,
     * and palette actions targeting a workspace document retain the ordinary
     * close-after-execution behavior.
     */
    static int closePaletteAfterUnlessTarget(int result, Object target, String targetSessionId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (result > 0 && minecraft != null
                && shouldKeepActivePaletteOpen(minecraft.screen, target, targetSessionId)) return result;
        return closePaletteAfter(result);
    }

    static boolean shouldKeepActivePaletteOpen(
            Object activeScreen,
            Object target,
            String targetSessionId
    ) {
{% if features.command_palette %}
{% if features.document_history %}
        java.util.Optional<String> activePaletteSession = activeScreen instanceof SFMCommandPaletteScreen palette
                && palette.documentHistoryAvailable()
                ? java.util.Optional.of(palette.documentHistorySessionId())
                : java.util.Optional.empty();
{% else %}
        java.util.Optional<String> activePaletteSession = java.util.Optional.empty();
{% endif %}
{% else %}
        java.util.Optional<String> activePaletteSession = java.util.Optional.empty();
{% endif %}
        return shouldKeepPaletteOpen(activeScreen == target, activePaletteSession, targetSessionId);
    }

    static boolean shouldKeepPaletteOpen(
            boolean activePaletteIsDirectTarget,
            java.util.Optional<String> activePaletteSessionId,
            String targetSessionId
    ) {
        return activePaletteIsDirectTarget
               || activePaletteSessionId.filter(targetSessionId::equals).isPresent();
    }

{% if features.workspace_panel_actions %}
    record CapturedPanel(SFMScreenMultiplexer workspace, SFMWorkspacePanelId panelId) {
    }
{% endif %}
}
