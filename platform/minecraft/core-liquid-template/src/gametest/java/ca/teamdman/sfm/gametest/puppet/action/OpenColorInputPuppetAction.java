package ca.teamdman.sfm.gametest.puppet.action;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
public record OpenColorInputPuppetAction(boolean toSide) implements SFMPuppetAction {
    @Override public String description() { return "open colour input" + (toSide ? " to side" : " full screen"); }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) { runtime.openColorInput(toSide); return true; }
}
