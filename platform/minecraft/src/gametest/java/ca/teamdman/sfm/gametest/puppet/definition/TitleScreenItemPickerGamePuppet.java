package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.action.ConfigureItemPickerPuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

@SFMGamePuppet
public final class TitleScreenItemPickerGamePuppet {
    private TitleScreenItemPickerGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.openItemPicker(false);
        puppet.configureItemPicker(ConfigureItemPickerPuppetAction.View.GALLERY);
        puppet.capture("registry-gallery", caption(
                "The registry-backed gallery exposes vanilla items and the SFM Program Disk with text identities."
        ));
        puppet.configureItemPicker(ConfigureItemPickerPuppetAction.View.SEARCH_DISK);
        puppet.capture("search-program-disk", caption(
                "Searching names or registry ids filters to the typed sfm:disk selection."
        ));
        puppet.configureItemPicker(ConfigureItemPickerPuppetAction.View.KEYBOARD_SELECTION);
        puppet.capture("keyboard-selection", caption(
                "Arrow-key navigation moved the current selection to Compass; Enter confirms and Esc cancels."
        ));
        puppet.configureItemPicker(ConfigureItemPickerPuppetAction.View.UNAVAILABLE);
        puppet.capture("unavailable-fallback", caption(
                "An unavailable registry id reports a precise diagnostic and selects the paper fallback."
        ));
        puppet.configureItemPicker(ConfigureItemPickerPuppetAction.View.RESET);
        puppet.capture("reset-fallback", caption(
                "Ctrl+R clears the search and diagnostic, explicitly restoring the configured fallback."
        ));
        puppet.closeScreenNaturally();
        puppet.waitForScreen(TitleScreen.class);

        puppet.openItemPicker(true);
        puppet.configureItemPicker(ConfigureItemPickerPuppetAction.View.MULTIPLEXED_SEARCH);
        puppet.capture("multiplexed-picker", caption(
                "The same responsive picker is composed as a focused panel beside the originating screen."
        ));
    }

    private static Component caption(String text) {
        return Component.literal("SFM Item Picker — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
