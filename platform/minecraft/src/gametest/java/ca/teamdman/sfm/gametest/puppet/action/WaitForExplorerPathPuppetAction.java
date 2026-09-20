package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.client.Minecraft;

import java.util.Objects;

/** Waits for one lazy explorer row and optionally makes it the keyboard selection. */
public final class WaitForExplorerPathPuppetAction implements SFMPuppetAction {
    private final SFMPath path;
    private final boolean select;
    private int ticks;

    public WaitForExplorerPathPuppetAction(SFMPath path, boolean select) {
        this.path = Objects.requireNonNull(path, "path");
        this.select = select;
    }

    @Override
    public String description() {
        return "wait for explorer path " + path.canonical() + (select ? " and select it" : "");
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace) {
            for (SFMWorkspacePanelId panelId : workspace.panelIds()) {
                if (!(workspace.panelInstance(panelId) instanceof SFMExplorerPanel explorer)) continue;
                SFMScreenPanelBounds bounds = workspace.panelContentBounds(panelId);
                if (bounds == null) continue;
                boolean visible = explorer.model().state(bounds).projection().rows().stream()
                        .anyMatch(row -> row.path().equals(path));
                if (!visible) continue;
                if (select) {
                    explorer.model().select(path, bounds);
                    workspace.focusPanel(panelId);
                }
                return true;
            }
        }
        if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for explorer path " + path.canonical());
        }
        return false;
    }
}
