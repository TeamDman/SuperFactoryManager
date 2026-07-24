package ca.teamdman.sfm.gametest.puppet.action;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
public final class ConfirmColorInputPuppetAction implements SFMPuppetAction {
    @Override public String description() { return "confirm typed colour input"; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) { runtime.confirmColorInput(); return true; }
}
