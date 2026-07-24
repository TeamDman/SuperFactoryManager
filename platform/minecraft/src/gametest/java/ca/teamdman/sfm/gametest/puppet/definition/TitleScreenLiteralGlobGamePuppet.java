package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

@SFMGamePuppet
public final class TitleScreenLiteralGlobGamePuppet {
    private TitleScreenLiteralGlobGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.showLiteralGlobDiagnostic();
        puppet.capture("literal-dot-wildcard", Component.literal("SFML literal wildcard — ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("*.java requires a real dot: Example.java matches while Examplexjava does not.")
                        .withStyle(ChatFormatting.BLACK)));
    }
}
