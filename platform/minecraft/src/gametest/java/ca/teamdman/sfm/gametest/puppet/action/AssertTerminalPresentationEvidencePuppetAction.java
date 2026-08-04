package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Captures one pushed state and proves its complete renderer/transport identity. */
public record AssertTerminalPresentationEvidencePuppetAction(
        String artifactName,
        String rendererId,
        String transportId,
        String requiredContentLine,
        boolean freshPresentationExpected
) implements SFMPuppetAction {
    public AssertTerminalPresentationEvidencePuppetAction {
        if (artifactName == null || artifactName.isBlank()) {
            throw new IllegalArgumentException("Presentation artifact name must not be blank");
        }
        if (rendererId == null || rendererId.isBlank()) {
            throw new IllegalArgumentException("Expected renderer id must not be blank");
        }
        if (transportId == null || transportId.isBlank()) {
            throw new IllegalArgumentException("Expected transport id must not be blank");
        }
    }

    @Override
    public String description() {
        return "assert terminal presentation " + rendererId + " / " + transportId;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.assertTerminalPresentationEvidence(
                artifactName,
                rendererId,
                transportId,
                requiredContentLine,
                freshPresentationExpected
        );
        return true;
    }
}
