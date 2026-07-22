package ca.teamdman.sfm.gametest.puppet.definition;

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
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.PALETTE);
        puppet.capture("dynamic-bindings-palette-a", caption("Two shortcuts share one action; the compact badge cycles."));
        puppet.waitTicks(21);
        puppet.capture("dynamic-bindings-palette-b", caption("The row remains one line while the second shortcut is shown."));
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.DETAILS);
        puppet.capture("dynamic-bindings-details", caption("Action details shows availability, description, and every shortcut."));
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.RECORDING);
        puppet.capture("dynamic-bindings-recording", caption("Shortcut capture accepts a key sequence and Enter commits it."));
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.CONFLICT);
        puppet.capture("dynamic-bindings-conflict", caption("An exact active shortcut conflict is visible before execution."));
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.INCOMPLETE);
        puppet.capture("dynamic-bindings-incomplete", caption("An incomplete bound command returns to Brigadier-backed prompting."));
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.DETAILS_AFTER_REMOVAL);
        puppet.capture("dynamic-bindings-removed", caption("One shortcut was removed at runtime without restarting Minecraft."));
    }

    private static Component caption(String text) {
        return Component.literal("SFM ").withStyle(ChatFormatting.GOLD).append(Component.literal(text));
    }
}
