package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** Captioned proof for the preference-agnostic composable ARGB input panel. */
@SFMGamePuppet
public final class TitleScreenColorInputGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;

    private TitleScreenColorInputGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        puppet.openColorInput(false);
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        settle(puppet);
        puppet.capture("full-screen-initial", caption("The reusable panel fills one workspace with ARGB preview, hex, channels, mouse field, value slider, recents, and lifecycle actions."));

        puppet.setColorInputHueSaturation(0.08D, 0.85D);
        puppet.setColorInputValue(0.90D);
        settle(puppet);
        puppet.capture("mouse-field-and-value", caption("Real multiplexer mouse dispatch selects hue/saturation in two dimensions and brightness on the value slider."));

        puppet.adjustColorInputChannel(0, -1, 48);
        settle(puppet);
        puppet.capture("alpha-channel", caption("The A channel buttons reduce opacity while the checkerboard-backed swatch and hexadecimal value update live."));

        puppet.selectColorInputRecent(1);
        settle(puppet);
        puppet.capture("recent-colour", caption("A recent colour is selected through its mouse-accessible swatch."));

        puppet.resetColorInput();
        settle(puppet);
        puppet.capture("reset", caption("Reset restores the caller-provided initial colour without consulting theme persistence."));

        puppet.setColorInputHex("#E34A78A0", true);
        settle(puppet);
        puppet.capture("rgba-hex", caption("RGBA mode parses #E34A78A0 into typed ARGB #A0E34A78 and synchronizes every channel and HSV control."));

        puppet.confirmColorInput();
        settle(puppet);
        puppet.capture("typed-confirmation", caption("Confirm invokes the typed colour callback, promotes the result to recents, and visibly records the resolved value."));

        puppet.closeScreenNaturally();
        puppet.waitForScreen(TitleScreen.class);
        puppet.openColorInput(true);
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        settle(puppet);
        puppet.capture("composable-side-panel", caption("The same responsive picker is embedded beside a parked vanilla title screen without becoming a Screen subclass."));
    }

    private static void settle(SFMGamePuppetHelper puppet) {
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
    }

    private static Component caption(String text) {
        return Component.literal("SFM Colour Input — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
