package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.action.ShowRuntimeThemePuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

@SFMGamePuppet
public final class TitleScreenRuntimeThemeGamePuppet {
    private TitleScreenRuntimeThemeGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.openCommandPalette();
        puppet.showRuntimeTheme(ShowRuntimeThemePuppetAction.View.DEFAULT);
        puppet.capture("runtime-theme-default", caption("The shipped theme resolves into one immutable render snapshot."));
        puppet.showRuntimeTheme(ShowRuntimeThemePuppetAction.View.SWITCHED);
        puppet.capture("runtime-theme-switched", caption("A runtime TOML reload changes palette colours and action ItemStacks."));
        puppet.showRuntimeTheme(ShowRuntimeThemePuppetAction.View.SYNTAX);
        puppet.capture("runtime-theme-syntax", caption("Live SFML tokens use the switched gold keyword and pink italic string styles."));
        puppet.showRuntimeTheme(ShowRuntimeThemePuppetAction.View.MALFORMED);
        puppet.capture("runtime-theme-malformed", caption("Malformed TOML reports diagnostics while the prior valid colours remain."));
    }

    private static Component caption(String text) {
        return Component.literal("SFM Theme: ").withStyle(ChatFormatting.GOLD).append(Component.literal(text));
    }
}
