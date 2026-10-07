package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Captures the contextual command palette over the settled title screen. */
@SFMGamePuppet
public final class TitleScreenCommandPaletteGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;

    private TitleScreenCommandPaletteGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        puppet.openCommandPalette();
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        puppet.exerciseCommandPaletteViewport();
        puppet.capture(
                "command-palette-scrolled",
                Component.literal("SFM ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("suggestions reached through wheel, keyboard, track, and thumb."))
        );
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
        puppet.setCommandPaletteInput("sfm action invoke open");
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        puppet.focusCommandPaletteCancel();
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
        puppet.capture(
                "command-palette-fuzzy-open",
                Component.literal("SFM ")
                        .withStyle(ChatFormatting.GOLD)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                        .append(Component.literal(
                                "fuzzy action discovery for open; the visible focused Cancel control is narrated and Tab-reachable."
                        ))
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                        .append(Component.literal("fuzzy action discovery for open."))
{% endcase %}
        );
        puppet.capture(
                "command-palette",
                Component.literal("SFM ")
                        .withStyle(ChatFormatting.GOLD)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                        .append(Component.literal(
                                "command palette on the title screen with its shared Vanilla-like Cancel control."
                        ))
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                        .append(Component.literal("command palette on the title screen."))
{% endcase %}
        );
    }
}
