package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

public final class SetCommandPaletteInputPuppetAction implements SFMPuppetAction {
    private final String input;
    private final String expectedAfterActivation;

    public SetCommandPaletteInputPuppetAction(String input, String expectedAfterActivation) {
        this.input = input;
        this.expectedAfterActivation = expectedAfterActivation;
    }

    @Override
    public String description() {
        return "set command palette input to " + input;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Expected command palette while setting puppet input");
        }
        if (expectedAfterActivation == null) palette.setInputForAutomation(input);
        else palette.prepareIncompleteInputForAutomation(input, expectedAfterActivation);
        return true;
    }
}
