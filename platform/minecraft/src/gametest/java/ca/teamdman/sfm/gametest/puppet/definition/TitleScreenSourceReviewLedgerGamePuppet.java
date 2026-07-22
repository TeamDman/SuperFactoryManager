package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Captioned proof of source-operation presentation, persistence, policy independence, and hash staleness. */
@SFMGamePuppet
public final class TitleScreenSourceReviewLedgerGamePuppet {
    private TitleScreenSourceReviewLedgerGamePuppet() {}

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:developer/open_source_review", SFMScreenMultiplexer.class);
        puppet.applySourceReviewFixtureCommand("clear");
        puppet.capture("operation-families", caption(
                "Insert, delete, rename, and modified-body operations use themed Java ItemStack icons."));

        puppet.applySourceReviewFixtureCommand("select:2");
        puppet.applySourceReviewFixtureCommand("review");
        puppet.capture("reviewed-not-approved", caption(
                "The rename is reviewed while approval remains explicitly undecided."));
        puppet.applySourceReviewFixtureCommand("approve");
        puppet.applySourceReviewFixtureCommand("reload");
        puppet.capture("persisted-approval-restored", caption(
                "Reloading the JSON ledger restores the approved rename from disk."));

        puppet.applySourceReviewFixtureCommand("select:3");
        puppet.applySourceReviewFixtureCommand("approve");
        puppet.capture("approved-but-audit-forbidden", caption(
                "Human approval does not override the independent FORBIDDEN audit policy."));
        puppet.applySourceReviewFixtureCommand("change-body-source");
        puppet.capture("source-hash-stale", caption(
                "Changing the reviewed source hash preserves but visibly invalidates the old approval."));
    }

    private static Component caption(String text) {
        return Component.literal("SFM Source Review: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
