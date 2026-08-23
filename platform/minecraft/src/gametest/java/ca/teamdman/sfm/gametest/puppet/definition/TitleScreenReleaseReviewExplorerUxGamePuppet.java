package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.puppet.action.ReleaseReviewJourneyPuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/** GUI-scale-matrix mouse journey for the generic release-review workbench shell. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.GUI_SCALE_MATRIX,
        timeoutTicks = 20 * 10 * 60
)
public final class TitleScreenReleaseReviewExplorerUxGamePuppet {
    private TitleScreenReleaseReviewExplorerUxGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.releaseReviewJourney(ReleaseReviewJourneyPuppetAction.Operation.PREPARE);

        Path reviewFile = ReleaseReviewJourneyPuppetAction.stagedPath();
        SFMPath parent = SFMPath.fromNative(reviewFile.getParent());
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:explorer " + parent.canonical(),
                SFMScreenMultiplexer.class
        );
        puppet.exerciseReleaseReviewExplorerUx(reviewFile);
        puppet.capture(
                "release-review-explorer-mouse-ux",
                Component.literal("SFM Release Review — ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(
                                "Mouse-complete lazy diffs, pinned approval, six lenses, and close/reopen resume."
                        ).withStyle(ChatFormatting.BLACK))
        );
        puppet.releaseReviewJourney(ReleaseReviewJourneyPuppetAction.Operation.CLEANUP);
        puppet.closeScreenNaturally();
    }
}
