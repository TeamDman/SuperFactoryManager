package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.SFMKeyBindingScreen;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.action.ShowDynamicKeyBindingPuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_screen_actions %}
import net.minecraft.client.gui.screens.controls.ControlsScreen;
{% endif %}
{% endcase %}
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% when "1.19.2", "1.19.4" %}
import org.lwjgl.glfw.GLFW;
{% endcase %}

@SFMGamePuppet
public final class TitleScreenDynamicKeyBindingsGamePuppet {
    private TitleScreenDynamicKeyBindingsGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_screen_actions %}
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:minecraft/screen/open ControlsScreen",
                ControlsScreen.class
        );
        puppet.capture("minecraft-controls-screen", caption("Minecraft Controls remains available as a separately named action."));
        puppet.closeScreenNaturally();
{% endif %}
{% endcase %}
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
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% when "1.19.2", "1.19.4" %}
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.RECORDING);
        puppet.pressScreenKey(GLFW.GLFW_KEY_H, GLFW.GLFW_MOD_CONTROL);
        puppet.capture("dynamic-bindings-recording", caption("Recording a physical Ctrl+H sequence renders the captured stroke as an editable pink keycap."));
        puppet.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.capture("dynamic-bindings-recording-cancelled", caption("Three Escapes within the capture window cancel recording without saving a binding."));
{% endcase %}
        puppet.showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View.ACTIVATE_FIRST);
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        puppet.capture("dynamic-bindings-first-activation", caption("Ctrl+H invokes Manage SFM shortcuts through the shared contextual Brigadier executor."));
{% when "1.19.2", "1.19.4" %}
        puppet.capture("dynamic-bindings-first-activation", caption("Ctrl+H invokes SFM Key Binds through the shared contextual Brigadier executor."));
{% endcase %}
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
