package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerScreen;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSource;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;

public final class OpenFileExplorerPuppetAction implements SFMPuppetAction {
    private final SFMFileExplorerSource source;
    private boolean requested;
    private int ticks;

    public OpenFileExplorerPuppetAction(SFMFileExplorerSource source) {
        this.source = source;
    }

    @Override
    public String description() {
        return "open file explorer for " + source.displayName();
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!requested) {
            requested = true;
            runtime.openFileExplorer(source);
        }
        if (runtime.isScreen(SFMFileExplorerScreen.class)) return true;
        if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for file explorer");
        }
        return false;
    }
}
