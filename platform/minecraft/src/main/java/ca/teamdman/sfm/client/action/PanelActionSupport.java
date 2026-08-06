package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class PanelActionSupport {
    private PanelActionSupport() {
    }

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

    static int closePaletteAfter(int result) {
        Minecraft minecraft = Minecraft.getInstance();
        if (result > 0 && minecraft != null
                && minecraft.screen instanceof SFMCommandPaletteScreen palette) {
            palette.onClose();
        }
        return result;
    }

    record CapturedPanel(SFMScreenMultiplexer workspace, SFMWorkspacePanelId panelId) {
    }
}
