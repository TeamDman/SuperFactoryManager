package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import net.minecraft.client.Minecraft;

/** Exact host/panel ownership for a callback that outlives the transient command palette. */
public record SFMClientActionContinuation(Object host, SFMWorkspacePanelId panelId, SFMScreenPanel panel) {
    public static SFMClientActionContinuation capture(SFMClientActionContext context) {
        var id = context.originatingPanelId();
        var panel = context.originatingHost() instanceof SFMScreenMultiplexer workspace && id != null
                ? workspace.panelInstance(id) : null;
        return new SFMClientActionContinuation(context.originatingHost(), id, panel);
    }

    public boolean isCurrent() {
        Object active = Minecraft.getInstance().screen;
        for (int depth = 0; depth < 16 && active instanceof SFMCommandPaletteScreen palette; depth++) {
            Object parent = palette.originatingActionContext().originatingHost();
            if (parent == active) return false;
            active = parent;
        }
        return matches(active);
    }

    /** Pure identity check; moving focus does not revoke a still-live captured panel. */
    public boolean matches(Object activeHost) {
        return activeHost == host && !(host instanceof SFMScreenMultiplexer closing && closing.isClosing()) && (panelId == null
                || host instanceof SFMScreenMultiplexer workspace
                && panel != null && workspace.panelInstance(panelId) == panel);
    }

    public SFMClientActionContext context() {
        return new SFMClientActionContext(host, this::isCurrent, panelId);
    }
}
