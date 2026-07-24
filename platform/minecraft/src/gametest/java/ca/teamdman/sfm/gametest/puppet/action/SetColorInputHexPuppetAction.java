package ca.teamdman.sfm.gametest.puppet.action;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
public record SetColorInputHexPuppetAction(String hex, boolean rgbaOrder) implements SFMPuppetAction {
    @Override public String description() { return "set colour input hex " + hex + (rgbaOrder ? " RGBA" : " ARGB"); }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime) { runtime.setColorInputHex(hex, rgbaOrder); return true; }
}
