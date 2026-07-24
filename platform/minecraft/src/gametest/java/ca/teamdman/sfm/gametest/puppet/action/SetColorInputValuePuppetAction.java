package ca.teamdman.sfm.gametest.puppet.action;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
public record SetColorInputValuePuppetAction(double value) implements SFMPuppetAction {
    @Override public String description() { return "mouse-select colour value " + value; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) { runtime.setColorInputValue(value); return true; }
}
