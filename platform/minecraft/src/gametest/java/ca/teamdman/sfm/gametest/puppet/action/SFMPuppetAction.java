package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public interface SFMPuppetAction {
    String description();

    boolean tick(ISFMGamePuppetRuntime runtime);

}
