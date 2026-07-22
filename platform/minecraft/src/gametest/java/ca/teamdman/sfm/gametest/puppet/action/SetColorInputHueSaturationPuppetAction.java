package ca.teamdman.sfm.gametest.puppet.action;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
public record SetColorInputHueSaturationPuppetAction(double hue, double saturation) implements SFMPuppetAction {
    @Override public String description() { return "mouse-select colour hue " + hue + " saturation " + saturation; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) { runtime.setColorInputHueSaturation(hue, saturation); return true; }
}
