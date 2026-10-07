package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMSizeDisplayWorkspace;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

/** Puppet-only adapter for the composable size-display proof. */
public record OpenSizeDisplayPuppetAction(
        SFMSizeDisplayWorkspace.Allocation allocation
) implements SFMPuppetAction {
    @Override
    public String description() {
        return "open size display " + allocation.name().toLowerCase();
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(SFMSizeDisplayWorkspace.create(minecraft.screen, allocation));
        return true;
    }
}
