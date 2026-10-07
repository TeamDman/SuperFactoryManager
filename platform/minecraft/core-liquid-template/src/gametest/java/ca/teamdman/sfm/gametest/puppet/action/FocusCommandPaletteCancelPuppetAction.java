package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

/** Focuses and validates the real shared Cancel widget for visual evidence. */
public final class FocusCommandPaletteCancelPuppetAction implements SFMPuppetAction {
    @Override
    public String description() {
        return "focus the command palette Cancel control";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Expected a command palette before focusing Cancel");
        }
        SFMCommandPaletteScreen.CancelControlAutomationSnapshot snapshot =
                palette.focusCancelForAutomation();
        if (!"Cancel".equals(snapshot.label())) {
            throw new IllegalStateException("Palette Cancel narration label was " + snapshot.label());
        }
        if (!"FOCUSED".equals(snapshot.narrationPriority())) {
            throw new IllegalStateException("Palette Cancel narration priority was "
                    + snapshot.narrationPriority());
        }
        return true;
    }
}
