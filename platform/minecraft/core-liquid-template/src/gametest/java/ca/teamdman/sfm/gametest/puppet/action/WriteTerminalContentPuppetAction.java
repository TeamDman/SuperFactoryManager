package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Writes a machine-readable terminal witness and checks optional text constraints. */
public record WriteTerminalContentPuppetAction(
        String artifactName,
        String requiredText,
        String forbiddenText
) implements SFMPuppetAction {
    @Override
    public String description() {
        return "write terminal content artifact " + artifactName;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.writeTerminalContent(artifactName, requiredText, forbiddenText);
        return true;
    }
}
