package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMViewportCalibrationWorkspace;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

/** Puppet-only adapter; viewport profile selection remains owned by the harness. */
public record OpenViewportCalibrationPuppetAction(
        SFMViewportCalibrationWorkspace.Allocation allocation
) implements SFMPuppetAction {
    @Override
    public String description() {
        return "open viewport calibration " + allocation.name().toLowerCase();
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(SFMViewportCalibrationWorkspace.create(minecraft.screen, allocation));
        return true;
    }
}
