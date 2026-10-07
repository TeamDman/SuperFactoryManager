package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

@SFMGamePuppet
public final class TitleScreenItemIconFileExplorerGamePuppet {
    private TitleScreenItemIconFileExplorerGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.openItemIconGallery();
        puppet.capture("itemstack-icons", caption(
                "Minecraft items identify directories, source/config/archive types, extensionless and unknown files."
        ));
        puppet.pressFileExplorerKey(org.lwjgl.glfw.GLFW.GLFW_KEY_END);
        puppet.capture("missing-item-fallback", caption(
                "An unavailable themed item deterministically falls back to paper without losing its text identity."
        ));
    }

    private static Component caption(String text) {
        return Component.literal("SFM File Icons — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
