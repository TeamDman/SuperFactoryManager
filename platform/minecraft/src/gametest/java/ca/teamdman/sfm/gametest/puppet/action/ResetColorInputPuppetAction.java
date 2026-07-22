package ca.teamdman.sfm.gametest.puppet.action;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
public final class ResetColorInputPuppetAction implements SFMPuppetAction {
    @Override public String description() { return "reset colour input"; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) { runtime.resetColorInput(); return true; }
}
