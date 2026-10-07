package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.terminal.SFMTerminalInteractionPuppetProbe;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Selects a deterministic visible cell range and proves selection-only work stays off the raster path. */
public final class SelectTerminalTextPuppetAction implements SFMPuppetAction {
    private final String artifactName;
    private final String rendererId;
    private final String transportId;
    private final String exactText;
    private final boolean reverse;
    private SFMTerminalInteractionPuppetProbe.TextRange range;
    private SFMTerminalInteractionPuppetProbe.Observation before;

    public SelectTerminalTextPuppetAction(
            String artifactName,
            String rendererId,
            String transportId,
            String exactText,
            boolean reverse
    ) {
        this.artifactName = artifactName;
        this.rendererId = rendererId;
        this.transportId = transportId;
        this.exactText = exactText;
        this.reverse = reverse;
    }

    @Override
    public String description() {
        return "select deterministic terminal text for " + rendererId + " / " + transportId;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (before == null) {
            range = runtime.locateTerminalText(exactText);
            before = runtime.observeTerminalInteraction(rendererId, transportId);
            runtime.dragTerminalRange(range, reverse);
            return false;
        }
        SFMTerminalInteractionPuppetProbe.Observation after =
                runtime.observeTerminalInteraction(rendererId, transportId);
        if (!SFMTerminalInteractionPuppetProbe.matchesSelection(after, range, reverse)) return false;
        runtime.writeTerminalInteractionEvidence(
                artifactName,
                SFMTerminalInteractionPuppetProbe.selectionArtifact(before, after, range, reverse));
        return true;
    }
}
