package ca.teamdman.sfm.gametest.puppet.definition;
import ca.teamdman.sfm.gametest.puppet.*;
import net.minecraft.client.gui.screens.LoadingOverlay;
@SFMGamePuppet(viewportProfile=SFMGamePuppetViewportProfile.REVIEW_READINESS,timeoutTicks=20*15*60)
public final class TitleScreenItemstackPreviewRulesGamePuppet {
    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);puppet.waitTicks(20);puppet.exerciseItemstackPreviewRules(false);
    }
}
