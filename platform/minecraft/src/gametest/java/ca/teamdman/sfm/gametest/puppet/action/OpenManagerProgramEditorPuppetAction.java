package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record OpenManagerProgramEditorPuppetAction() implements SFMPuppetAction {
    @Override
    public String description() {

        return "open manager program editor";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {

        runtime.openManagerProgramEditor();
        return true;
    }

}
