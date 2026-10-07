package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Verifies and writes bounded, zero-polling Vox push evidence. */
public record AssertTerminalPushEvidencePuppetAction(
        String artifactName,
        boolean reconnectExpected
) implements SFMPuppetAction {
    @Override
    public String description() {
        return "assert terminal push evidence " + artifactName;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.assertTerminalPushEvidence(artifactName, reconnectExpected);
        return true;
    }
}
