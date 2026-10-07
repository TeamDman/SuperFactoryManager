package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record SeekFalsifiedInventoryTimelinePuppetAction(int timestep) implements SFMPuppetAction {
    @Override
    public String description() {
        return "seek falsified inventory timeline to " + timestep;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.seekFalsifiedInventoryTimeline(timestep);
        return true;
    }
}
