package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.common.util.SFMResourceLocation;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.presentation.SFMItemIconResolver;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

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
        if (input.contains("sfm:developer/open_")) {
            var action = SFMClientActions.registry().get(SFMResourceLocation.fromNamespaceAndPath("sfm", "developer/open_file_explorer"))
                    .map(reference -> reference.value()).orElse(null);
            if (action == null) throw new IllegalStateException("File explorer action was not registered");
            var icon = action.itemIcon(SFMClientActionContext.create(Minecraft.getInstance().screen, () -> true))
                    .orElseThrow(() -> new IllegalStateException("File explorer action had no ItemStack icon metadata"));
            var resolved = SFMItemIconResolver.resolve(icon);
            Identifier itemId = SFMWellKnownRegistries.ITEMS.getId(resolved.stack().getItem());
            if (!SFMResourceLocation.fromNamespaceAndPath("minecraft", "chest").equals(itemId)) {
                throw new IllegalStateException("File explorer action icon resolved to " + itemId);
            }
        }
        return true;
    }
}
