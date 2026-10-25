package ca.teamdman.sfm.client.inspection;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import net.minecraft.client.Minecraft;

/** One client-local prepared diff preset and its last visible Explorer selection. */
public final class SFMInspectionPresetReopen {
    private record Last(SFMInspectionRuntime.PreparedWorkspace prepared,
                        SFMExplorerSession.Snapshot view, SFMExplorerId activeExplorer) {}
    private static Last last;

    private SFMInspectionPresetReopen() {}

    public static boolean canReopen() {
        return last != null && last.activeExplorer() == null;
    }

    /** Record only an opened preset; temporary ad hoc comparisons remain independent. */
    public static void remember(SFMInspectionRuntime.PreparedWorkspace prepared) {
        SFMExplorerPanel panel = focusedPanel();
        last = new Last(prepared, null, panel.explorerId());
    }

    /** Capture before the panel closes its selection ledger and resolver lease. */
    public static void panelClosing(SFMExplorerPanel panel) {
        Last previous = last;
        if (previous != null && panel.explorerId().equals(previous.activeExplorer()))
            last = new Last(previous.prepared(), panel.sessionSnapshot(), null);
    }

    public static void reopen(SFMClientActionContext context) {
        Last previous = last;
        if (previous == null || previous.activeExplorer() != null)
            throw new IllegalStateException("Close the current comparison before reopening it");
        SFMInspectionRuntime.open(previous.prepared(), context, false);
        SFMExplorerPanel panel = focusedPanel();
        if (previous.view() != null) panel.restoreInspectionView(previous.view());
        last = new Last(previous.prepared(), null, panel.explorerId());
    }

    private static SFMExplorerPanel focusedPanel() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)
                || workspace.focusedPanelId() == null
                || !(workspace.panelInstance(workspace.focusedPanelId()) instanceof SFMExplorerPanel panel))
            throw new IllegalStateException("The comparison Explorer is not the focused workspace panel");
        return panel;
    }
}
