package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record JumpFalsifiedInventoryKeyframePuppetAction(int direction) implements SFMPuppetAction {
    public JumpFalsifiedInventoryKeyframePuppetAction {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Direction must be -1 or 1");
    }
    @Override public String description() { return direction < 0 ? "jump to previous inventory keyframe" : "jump to next inventory keyframe"; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.jumpFalsifiedInventoryKeyframe(direction);
        return true;
    }
}
