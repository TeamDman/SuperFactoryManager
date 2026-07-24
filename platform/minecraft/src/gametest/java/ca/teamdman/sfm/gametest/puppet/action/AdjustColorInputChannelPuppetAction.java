package ca.teamdman.sfm.gametest.puppet.action;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
public record AdjustColorInputChannelPuppetAction(int channel, int direction, int clicks) implements SFMPuppetAction {
    @Override public String description() { return "click colour channel " + channel + " direction " + direction + " x" + clicks; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) { runtime.adjustColorInputChannel(channel, direction, clicks); return true; }
}
