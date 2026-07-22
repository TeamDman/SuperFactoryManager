package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.action.PrepareRepositoryReviewFixturePuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Join-ready walkthrough; loader integration replaces fixture reset/bootstrap, not the UI actions. */
@SFMGamePuppet
public final class TitleScreenRepositoryReviewGamePuppet {
    private TitleScreenRepositoryReviewGamePuppet() {}

    public static void run(SFMGamePuppetHelper p) {
        p.prepareRepositoryReviewFixture();
        p.waitForOverlayToNotBePresent(LoadingOverlay.class);
        p.waitTicks(20);
        p.openCommandPalette();
        p.executeCommandPaletteAndWaitForScreen("sfm action invoke sfm:review/open_bundle "
                + PrepareRepositoryReviewFixturePuppetAction.requestedBundle(), SFMScreenMultiplexer.class);
        p.capture("repository-review-open",cap("The managed SFM d07bef66c → 8e9946d9f bundle opens from the command palette with changed-file counts and Minecraft item identities."));
        p.applyRepositoryReviewCommand("select:1");
        p.waitTicks(10);
        p.capture("repository-review-browse",cap("Browsing added src/B.java updates the responsive before/after source panels."));
        p.applyRepositoryReviewCommand("search:B.java");
        p.waitTicks(10);
        p.capture("repository-review-search",cap("Changed-file search narrows the bundle to the matching Java source."));
        p.applyRepositoryReviewCommand("search:");
        p.applyRepositoryReviewCommand("select:0");
        p.applyRepositoryReviewCommand("line:after:1");
        p.waitTicks(10);
        p.capture("repository-review-selection",cap("The reviewer explicitly selects after line 2, shown with its UTF-8 byte range and source highlight."));
        p.applyRepositoryReviewCommand("comment:#question Confirm the restored repository review comment");
        p.waitTicks(10);
        p.capture("repository-review-comment",cap("A literal UTF-8 user comment is created through the existing review-session kernel."));
        p.closeScreenNaturally();
        p.waitTicks(10);
        p.openCommandPalette();
        p.executeCommandPaletteAndWaitForScreen("sfm action invoke sfm:review/open_bundle "
                + PrepareRepositoryReviewFixturePuppetAction.requestedBundle(), SFMScreenMultiplexer.class);
        p.applyRepositoryReviewCommand("refresh");
        p.waitTicks(10);
        p.capture("repository-review-restored",cap("Closing and reopening the bundle restores the persisted user comment in the selected source file."));
    }

    private static Component cap(String text) {
        return Component.literal("SFM Repository Review: " + text).withStyle(ChatFormatting.GOLD);
    }
}
