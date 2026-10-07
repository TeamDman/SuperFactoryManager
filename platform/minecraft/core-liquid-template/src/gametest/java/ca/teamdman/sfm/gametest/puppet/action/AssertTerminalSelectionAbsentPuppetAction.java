package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.terminal.SFMTerminalInteractionPuppetProbe;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Proves a child mouse-reporting gesture was not converted to host selection. */
public record AssertTerminalSelectionAbsentPuppetAction(
        String artifactName,
        String rendererId,
        String transportId,
        boolean childMouseForwarded
) implements SFMPuppetAction {
    @Override
    public String description() {
        return "assert terminal selection absent after child mouse reporting";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMTerminalInteractionPuppetProbe.Observation observation =
                runtime.observeTerminalInteraction(rendererId, transportId);
        if (observation.selection().isPresent()) return false;
        runtime.writeTerminalInteractionEvidence(
                artifactName,
                SFMTerminalInteractionPuppetProbe.absentSelectionArtifact(
                        observation, childMouseForwarded));
        return true;
    }
}
