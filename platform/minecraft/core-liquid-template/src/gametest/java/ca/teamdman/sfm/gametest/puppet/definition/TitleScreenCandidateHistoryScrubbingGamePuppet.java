package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.puppet.action.AssertCandidateHistoryPuppetAction;
import ca.teamdman.sfm.gametest.puppet.action.CandidateHistoryStatusFixturePuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Natural proof that candidate-route seeking is read-only and survives replanning. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.FIXED_1280X720_AUTO,
        timeoutTicks = 20 * 10 * 60
)
public final class TitleScreenCandidateHistoryScrubbingGamePuppet {
    private TitleScreenCandidateHistoryScrubbingGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);

        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:chamber/temporal-decimal-numbering",
                SFMScreenMultiplexer.class
        );
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open/right sfm:episode/history",
                SFMScreenMultiplexer.class
        );
        palette(puppet, "sfm action invoke sfm:episode/trajectory/plan focused");
        palette(puppet, "sfm action invoke sfm:panel/open sfm:episode/candidate-history focused");
        puppet.waitTicks(20);

        checkpoint(puppet, AssertCandidateHistoryPuppetAction.Stage.OLD_ROUTE_START,
                "candidate-old-start", "candidate-old-start",
                "The pinned old route starts at the actual two-item source without executing it.");
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        checkpoint(puppet, AssertCandidateHistoryPuppetAction.Stage.OLD_ROUTE_SELECTED,
                "candidate-old-selected", "candidate-old-selected",
                "Candidate frame one previews the semantic two-marker selection; actual head and IP stay put.");
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        checkpoint(puppet, AssertCandidateHistoryPuppetAction.Stage.OLD_ROUTE_NUMBERED,
                "candidate-old-numbered", "candidate-old-numbered",
                "Candidate frame two previews exact 1/2 numbering without committing it.");

        palette(puppet, "sfm action invoke sfm:episode/trajectory/run focused 32");
        puppet.pressScreenKey(GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_HOME, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_END, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.typeScreenText("- apricots");
        palette(puppet, "sfm action invoke sfm:episode/trajectory/replan focused");
        puppet.pressScreenKey(GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);
        checkpoint(puppet, AssertCandidateHistoryPuppetAction.Stage.OLD_ROUTE_RETAINED_AFTER_REPLAN,
                "candidate-old-retained", "candidate-old-retained",
                "The open old-plan scrubber remains on its two-item future after a three-item replan.");

        palette(puppet, "sfm action invoke sfm:panel/open sfm:episode/candidate-history");
        puppet.waitTicks(20);
        checkpoint(puppet, AssertCandidateHistoryPuppetAction.Stage.NEW_ROUTE_START,
                "candidate-new-start", "candidate-new-start",
                "A second scrubber pins the newly selected three-item plan independently.");
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        checkpoint(puppet, AssertCandidateHistoryPuppetAction.Stage.NEW_ROUTE_NUMBERED,
                "candidate-new-numbered", "candidate-new-numbered",
                "The new route previews exact 1/2/3 numbering while the actual source and IP remain unchanged.");

        puppet.registerCandidateHistoryStatusFixture();
        palette(puppet, "sfm action invoke sfm:panel/open sfm:episode/candidate-history focused");
        puppet.waitTicks(20);
        statusCheckpoint(puppet, CandidateHistoryStatusFixturePuppetAction.Stage.MATERIALIZED,
                "candidate-status-materialized", "candidate-status-materialized",
                "The status route begins with the last trustworthy materialized document.");
        puppet.pressScreenKey(GLFW.GLFW_KEY_END, 0);
        statusCheckpoint(puppet, CandidateHistoryStatusFixturePuppetAction.Stage.UNKNOWN,
                "candidate-status-unknown", "candidate-status-unknown",
                "An unknown future is explicit and carries no invented document bytes.");
        puppet.pressScreenKey(GLFW.GLFW_KEY_HOME, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        statusCheckpoint(puppet, CandidateHistoryStatusFixturePuppetAction.Stage.INVALIDATED,
                "candidate-status-invalidated", "candidate-status-invalidated",
                "An invalidated witness is explicit while the actual head and IP remain fixed.");
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        statusCheckpoint(puppet, CandidateHistoryStatusFixturePuppetAction.Stage.EXTERNAL_BARRIER,
                "candidate-status-barrier", "candidate-status-barrier",
                "An external barrier names its trustworthy predecessor and fabricates no bytes.");
        puppet.pressScreenKey(GLFW.GLFW_KEY_LEFT, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_LEFT, 0);
        statusCheckpoint(puppet, CandidateHistoryStatusFixturePuppetAction.Stage.UNAVAILABLE,
                "candidate-status-unavailable", "candidate-status-unavailable",
                "A cancelled unavailable frame remains inspectable after reverse seeking.");
        puppet.unregisterCandidateHistoryStatusFixture();
    }

    private static void palette(SFMGamePuppetHelper puppet, String command) {
        puppet.executeCommandPalette(command);
        puppet.executeCommandPalette("sfm action invoke sfm:palette/close");
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
    }

    private static void checkpoint(
            SFMGamePuppetHelper puppet,
            AssertCandidateHistoryPuppetAction.Stage stage,
            String artifact,
            String capture,
            String caption
    ) {
        puppet.assertCandidateHistory(stage, artifact);
        puppet.capture(capture, Component.literal("Candidate history: ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(caption).withStyle(ChatFormatting.BLACK)));
    }

    private static void statusCheckpoint(
            SFMGamePuppetHelper puppet,
            CandidateHistoryStatusFixturePuppetAction.Stage stage,
            String artifact,
            String capture,
            String caption
    ) {
        puppet.assertCandidateHistoryStatus(stage, artifact);
        puppet.capture(capture, Component.literal("Candidate status: ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(caption).withStyle(ChatFormatting.BLACK)));
    }
}
