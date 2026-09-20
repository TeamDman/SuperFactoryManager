package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Exercises the visible palette's real suggestion scrolling event paths. */
public record ExerciseCommandPaletteViewportPuppetAction() implements SFMPuppetAction {
    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.exerciseCommandPaletteViewport();
        return true;
    }

    @Override
    public String description() {
        return "exercise command palette suggestion viewport";
    }
}
