package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record SeekFalsifiedInventoryKeyframePositionPuppetAction(double position) implements SFMPuppetAction {
    @Override public String description() { return "seek falsified inventory keyframe position to " + position; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.seekFalsifiedInventoryKeyframePosition(position);
        return true;
    }
}
