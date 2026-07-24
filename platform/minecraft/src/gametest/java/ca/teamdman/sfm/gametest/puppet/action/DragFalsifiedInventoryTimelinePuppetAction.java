package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record DragFalsifiedInventoryTimelinePuppetAction(
        int fromTimestep,
        int toTimestep
) implements SFMPuppetAction {
    @Override
    public String description() {
        return "drag falsified inventory timeline from " + fromTimestep + " to " + toTimestep;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.dragFalsifiedInventoryTimeline(fromTimestep, toTimestep);
        return true;
    }
}
