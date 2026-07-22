package ca.teamdman.sfm.gametest.puppet.action;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
public record SelectColorInputRecentPuppetAction(int index) implements SFMPuppetAction {
    @Override public String description() { return "select recent colour " + index; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) { runtime.selectColorInputRecent(index); return true; }
}
