package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.action.AssertReviewExplorerPuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Captioned proof of the panelized multi-lane review explorer projections. */
@SFMGamePuppet
public final class TitleScreenReviewExplorerGamePuppet {
    private TitleScreenReviewExplorerGamePuppet() {}

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);

        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:explorer/changes \"mod 4.34.0\" \"HEAD\"",
                ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer.class);
        puppet.assertReviewExplorer(AssertReviewExplorerPuppetAction.Projection.CHANGES);
        puppet.capture("review-explorer-changes", caption(
                "Changes retain file → lane → before/after leaves, including explicit missing tombstones."));
        puppet.pressScreenKey(GLFW.GLFW_KEY_DOWN, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_DOWN, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_DOWN, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_DOWN, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL);
        puppet.assertWorkspaceState(2, 1, 2, "Review", -1);
        puppet.capture("review-explorer-stacked-source", caption(
                "Ctrl+Enter opens an immutable source leaf as a new stacked panel."));

        puppet.closeScreenNaturally();
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:explorer/comments",
                ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer.class);
        puppet.assertReviewExplorer(AssertReviewExplorerPuppetAction.Projection.COMMENTS);
        puppet.capture("review-explorer-comments", caption(
                "Comments project to selectable source regions without the retired review ledger screen."));

        puppet.closeScreenNaturally();
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:explorer/comments/hashtags",
                ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer.class);
        puppet.assertReviewExplorer(AssertReviewExplorerPuppetAction.Projection.HASHTAGS);
        puppet.capture("review-explorer-hashtags", caption(
                "Hashtags provide a separate hashtag → file → region projection of the same comment session."));
    }

    private static Component caption(String text) {
        return Component.literal("SFM Review Explorer: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
