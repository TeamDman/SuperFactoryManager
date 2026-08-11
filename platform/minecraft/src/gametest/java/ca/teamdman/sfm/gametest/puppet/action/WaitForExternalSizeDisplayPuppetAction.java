package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMSizeDisplayPanel;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

/** Waits for the independently launched {@code sfm.exe} to open the diagnostic panel. */
public final class WaitForExternalSizeDisplayPuppetAction implements SFMPuppetAction {
    private static final int TIMEOUT_TICKS = 1200;
    private int ticks;

    @Override
    public String description() {
        return "wait for external sfm invoke to open sfm:size_display";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace
                && workspace.panels().stream().anyMatch(SFMSizeDisplayPanel.class::isInstance)) {
            return true;
        }
        ticks++;
        if (ticks > TIMEOUT_TICKS) {
            throw new IllegalStateException(
                    "Timed out waiting for `sfm invoke sfm:panel/open sfm:size_display`"
            );
        }
        return false;
    }
}
