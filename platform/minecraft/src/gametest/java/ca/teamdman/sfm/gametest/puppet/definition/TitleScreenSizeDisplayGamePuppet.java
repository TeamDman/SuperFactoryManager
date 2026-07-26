package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMSizeDisplayWorkspace;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Proof that the reusable size-display leaf composes in four workspace shapes. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE)
public final class TitleScreenSizeDisplayGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;

    private TitleScreenSizeDisplayGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        capture(puppet, SFMSizeDisplayWorkspace.Allocation.FULL, "size-display-full",
                "One size-display leaf receives the complete workspace content bounds.");
        capture(puppet, SFMSizeDisplayWorkspace.Allocation.HALF, "size-display-half",
                "Two solid-colour size-display leaves expose equal half allocations.");
        capture(puppet, SFMSizeDisplayWorkspace.Allocation.THIRD, "size-display-third",
                "Three solid-colour leaves expose equal one-third allocations.");
        capture(puppet, SFMSizeDisplayWorkspace.Allocation.NESTED, "size-display-nested",
                "A horizontal split contains a vertical split with independent colours.");
    }

    private static void capture(
            SFMGamePuppetHelper puppet,
            SFMSizeDisplayWorkspace.Allocation allocation,
            String id,
            String text
    ) {
        puppet.openSizeDisplay(allocation);
        puppet.capture(id, Component.literal("SFM ").withStyle(ChatFormatting.GOLD).append(text));
    }
}
