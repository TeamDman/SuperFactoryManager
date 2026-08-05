package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record AssertTerminalPropertiesEvidencePuppetAction(
        String artifactName,
        String expectedRendererId,
        String expectedTransportId,
        String expectedSurfaceMode,
        String expectedFontMode,
        String expectedCellsMode,
        Integer expectedConfiguredGuiScale,
        Integer expectedPanelGuiScaleOverride,
        String expectedRejectionCode,
        boolean retainedFrameExpected
) implements SFMPuppetAction {
    @Override
    public String description() {
        return "assert terminal properties evidence " + artifactName;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        return runtime.assertTerminalPropertiesEvidence(
                artifactName,
                expectedRendererId,
                expectedTransportId,
                expectedSurfaceMode,
                expectedFontMode,
                expectedCellsMode,
                expectedConfiguredGuiScale,
                expectedPanelGuiScaleOverride,
                expectedRejectionCode,
                retainedFrameExpected);
    }
}
