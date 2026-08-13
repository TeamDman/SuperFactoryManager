package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;

/** Writes one generic machine-readable game-puppet artifact. */
public record WriteGamePuppetArtifactPuppetAction(
        String artifactName,
        SFMGamePuppetArtifactFormat format,
        String contents
) implements SFMPuppetAction {
    @Override
    public String description() {
        return "write " + format.id() + " game puppet artifact " + artifactName;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.writeArtifact(artifactName, format, contents);
        return true;
    }
}
