package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record AssertFileExplorerWorkspacePuppetAction(
        int panelCount,
        String expectedRootName,
        String expectedViewerPath,
        String expectedViewerText,
        boolean rememberOrRequireViewerIdentity
) implements SFMPuppetAction {
    @Override public String description() { return "assert integrated file explorer workspace state"; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.assertFileExplorerWorkspace(panelCount, expectedRootName, expectedViewerPath,
                expectedViewerText, rememberOrRequireViewerIdentity);
        return true;
    }
}
