package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record SetFileExplorerSnapshotPuppetAction(
        SFMFileExplorerSnapshot snapshot
) implements SFMPuppetAction {
    @Override
    public String description() {
        return "set file explorer snapshot to " + snapshot.state();
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.setFileExplorerSnapshot(snapshot);
        return true;
    }
}
