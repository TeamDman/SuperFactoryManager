package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.terminal.SFMTerminalInteractionPuppetProbe;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Copies through the real panel callback and proves exact clipboard/clear behavior without raster work. */
public final class CopyTerminalSelectionPuppetAction implements SFMPuppetAction {
    private final String artifactName;
    private final String rendererId;
    private final String transportId;
    private final String expectedText;
    private final boolean rightClick;
    private SFMTerminalInteractionPuppetProbe.Observation before;

    public CopyTerminalSelectionPuppetAction(
            String artifactName,
            String rendererId,
            String transportId,
            String expectedText,
            boolean rightClick
    ) {
        this.artifactName = artifactName;
        this.rendererId = rendererId;
        this.transportId = transportId;
        this.expectedText = expectedText;
        this.rightClick = rightClick;
    }

    @Override
    public String description() {
        return (rightClick ? "right-click" : "Ctrl+C") + " terminal selection copy";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (before == null) {
            before = runtime.observeTerminalInteraction(rendererId, transportId);
            if (before.selection().isEmpty()) {
                throw new IllegalStateException("Terminal copy evidence began without a selection");
            }
            runtime.copyTerminalSelection(rightClick);
            return false;
        }
        SFMTerminalInteractionPuppetProbe.Observation after =
                runtime.observeTerminalInteraction(rendererId, transportId);
        boolean clipboardMatches = expectedText.equals(runtime.terminalClipboard());
        if (after.selection().isPresent() || !clipboardMatches) return false;
        runtime.writeTerminalInteractionEvidence(
                artifactName,
                SFMTerminalInteractionPuppetProbe.copyArtifact(
                        before, after, clipboardMatches, rightClick));
        return true;
    }
}
