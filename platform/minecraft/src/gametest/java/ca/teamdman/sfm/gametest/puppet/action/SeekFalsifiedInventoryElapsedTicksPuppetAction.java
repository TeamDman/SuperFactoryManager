package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record SeekFalsifiedInventoryElapsedTicksPuppetAction(double ticks) implements SFMPuppetAction {
    @Override public String description() { return "seek falsified inventory elapsed time to " + ticks + " ticks"; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.seekFalsifiedInventoryElapsedTicks(ticks);
        return true;
    }
}
