package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record CreateFreshWorldPuppetAction() implements SFMPuppetAction {
    @Override
    public String description() {

        return "create fresh flat world";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {

        return runtime.createFreshFlatWorld();
    }

}
