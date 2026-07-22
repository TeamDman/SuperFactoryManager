package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.screen.item_picker.SFMItemPickerPanel;
import ca.teamdman.sfm.client.screen.item_picker.SFMItemPickerScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.client.Minecraft;

public final class OpenItemPickerPuppetAction implements SFMPuppetAction {
    private final boolean multiplexed;
    private boolean requested;
    private int ticks;

    public OpenItemPickerPuppetAction(boolean multiplexed) {
        this.multiplexed = multiplexed;
    }

    @Override
    public String description() {
        return "open " + (multiplexed ? "multiplexed " : "full-screen ") + "item picker";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!requested) {
            requested = true;
            SFMItemIcon current = SFMItemIcon.vanilla("chest", "Chest");
            if (multiplexed) {
                SFMScreenMultiplexer.openToSide(minecraft.screen,
                        SFMItemPickerPanel.fromRegistry(current, ignored -> {}, () -> {}));
            } else {
                minecraft.setScreen(new SFMItemPickerScreen(minecraft.screen, current, ignored -> {}));
            }
        }
        if (ConfigureItemPickerPuppetAction.findPanel() != null) return true;
        if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out opening item picker");
        }
        return false;
    }
}
