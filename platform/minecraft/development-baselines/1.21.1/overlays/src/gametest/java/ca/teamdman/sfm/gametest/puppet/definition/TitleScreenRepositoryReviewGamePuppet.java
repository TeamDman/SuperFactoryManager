package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.puppet.action.PrepareRepositoryReviewFixturePuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Join-ready walkthrough; loader integration replaces fixture reset/bootstrap, not the UI actions. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE)
public final class TitleScreenRepositoryReviewGamePuppet {
    public static final String SEARCH_PROPERTY = "sfm.repositoryReviewPuppetSearch";

    private TitleScreenRepositoryReviewGamePuppet() {}

    public static void run(SFMGamePuppetHelper p) {
        String openCommand = "sfm action invoke sfm:review/open_bundle "
                + PrepareRepositoryReviewFixturePuppetAction.requestedBundle();
        p.prepareRepositoryReviewFixture();
        p.waitForOverlayToNotBePresent(LoadingOverlay.class);
        p.waitTicks(20);
        p.openCommandPalette();
        p.setCommandPaletteInput("sfm action invoke sfm:review/open_bundle");
        p.capture("repository-review-palette", cap("The command palette visibly offers the real managed review-session command before opening it."));
        p.executeCommandPaletteAndWaitForScreen(openCommand, SFMScreenMultiplexer.class);
        p.capture("repository-review-open",cap("The managed SFM d07bef66c → 8e9946d9f bundle opens as responsive workspace leaves with changed-file counts and Minecraft item identities."));
        p.applyRepositoryReviewCommand("select:1");
        p.waitTicks(10);
        p.capture("repository-review-browse", cap("Browsing a selected changed file updates the responsive before/after source panels."));
        p.applyRepositoryReviewCommand("search:" + requestedSearch());
        p.waitTicks(10);
        p.capture("repository-review-search", cap("Search narrows the bundle while preserving the same selected changed source and complete path affordance."));
        p.applyRepositoryReviewCommand("change:after");
        p.waitTicks(10);
        p.capture("repository-review-change", cap("The after-source leaf reveals and highlights the actual generated comparison range; Ctrl+M can maximize it."));
        p.capture("repository-review-selection", cap("The generated change is also a nonempty literal selection with a visible UTF-8 byte range and source highlight."));
        p.applyRepositoryReviewCommand("comment:#question Confirm the restored repository review comment");
        p.waitTicks(10);
        p.capture("repository-review-comment", cap("The dedicated comment-details leaf shows complete comment text and its literal UTF-8 range without covering source."));
        p.closeScreenNaturally();
        p.waitTicks(10);
        p.capture("repository-review-closed", cap("The review workspace is explicitly closed before the persisted session is reopened."));
        p.openCommandPalette();
        p.setCommandPaletteInput("sfm action invoke sfm:review/open_bundle");
        p.capture("repository-review-reopen", cap("The command palette visibly reopens the same managed review session."));
        p.executeCommandPaletteAndWaitForScreen(openCommand, SFMScreenMultiplexer.class);
        p.applyRepositoryReviewCommand("search:" + requestedSearch());
        p.applyRepositoryReviewCommand("show:comments");
        p.applyRepositoryReviewCommand("refresh");
        p.waitTicks(10);
        p.capture("repository-review-restored",cap("Closing and reopening the bundle restores the persisted user comment in the selected source file."));
    }

    private static Component cap(String text) {
        return Component.literal("SFM Repository Review: " + text).withStyle(ChatFormatting.GOLD);
    }

    private static String requestedSearch() {
        return System.getProperty(SEARCH_PROPERTY, "B.java").strip();
    }
}
