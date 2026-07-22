package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.SFMKeyBindingScreen;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.action.ShowDynamicKeyBindingPuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

@SFMGamePuppet
public final class TitleScreenDynamicKeyBindingsGamePuppet {
    private TitleScreenDynamicKeyBindingsGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.SETUP);
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:keybindings/manage",
                SFMKeyBindingScreen.class
        );
        puppet.capture("dynamic-bindings-manager", caption("The searchable SFM shortcut manager opens through the command palette and lists zero-or-more bindings per action."));
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.PALETTE);
        puppet.capture("dynamic-bindings-palette-a", caption("Two shortcuts share one action in a fixed-width, single-line badge area."));
        puppet.waitTicks(21);
        puppet.capture("dynamic-bindings-palette-b", caption("The stable row cycles to the second shortcut after one second without reordering."));
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.DETAILS);
        puppet.capture("dynamic-bindings-details", caption("Action details shows availability, command drafts, every shortcut, and edit, disable, remove, and add controls."));
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.ACTIVATE_FIRST);
        puppet.capture("dynamic-bindings-first-activation", caption("Ctrl+H invokes Manage SFM shortcuts through the shared contextual Brigadier executor."));
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.ACTIVATE_SECOND);
        puppet.capture("dynamic-bindings-second-activation", caption("Ctrl+K Ctrl+H invokes the same manager action through that executor."));
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.INCOMPLETE);
        puppet.capture("dynamic-bindings-incomplete", caption("Incomplete Echo solicits its Brigadier-derived message : string parameter."));
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.CONFIRMATION);
        puppet.capture("dynamic-bindings-confirmation", caption("The typed value becomes an editable final command and properties summary requiring explicit confirmation."));
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.DETAILS_AFTER_REMOVAL);
        puppet.capture("dynamic-bindings-removed", caption("One shortcut was removed at runtime without restarting Minecraft."));
    }

    private static Component caption(String text) {
        return Component.literal("SFM ").withStyle(ChatFormatting.GOLD).append(Component.literal(text));
    }
}
