package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Asserts whether the explorer or its owned preview slot has workspace focus. */
public record AssertFileExplorerPreviewFocusPuppetAction(boolean previewFocused) implements SFMPuppetAction {
    @Override
    public String description() {
        return "assert file explorer preview focus=" + previewFocused;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.assertFileExplorerPreviewFocus(previewFocused);
        return true;
    }
}
