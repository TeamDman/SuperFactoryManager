package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Uses the visible panel's Presentation popover rather than invoking an action directly. */
public record SelectTerminalPresentationUiPuppetAction(
        boolean rendererAxis,
        String optionId
) implements SFMPuppetAction {
    public SelectTerminalPresentationUiPuppetAction {
        if (optionId == null || optionId.isBlank()) {
            throw new IllegalArgumentException("Presentation option id must not be blank");
        }
    }

    @Override
    public String description() {
        return "select terminal " + (rendererAxis ? "renderer" : "transport")
                + " through Presentation UI: " + optionId;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.selectTerminalPresentationThroughUi(rendererAxis, optionId);
        return true;
    }
}
