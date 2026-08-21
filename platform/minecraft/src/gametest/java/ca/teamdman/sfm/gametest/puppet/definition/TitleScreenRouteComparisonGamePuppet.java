package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.puppet.action.AssertRouteComparisonPuppetAction;
import ca.teamdman.sfm.gametest.puppet.action.CandidateCommentSessionPuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Natural X3 proof of retained-route comparison, disposition, and explicit selection. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.FIXED_1280X720_AUTO,
        timeoutTicks = 20 * 15 * 60
)
public final class TitleScreenRouteComparisonGamePuppet {
    private TitleScreenRouteComparisonGamePuppet() {
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
        puppet.resetCandidateCommentSession(CandidateCommentSessionPuppetAction.SessionRole.MAIN);
        palette(puppet,
                "sfm action invoke sfm:review/comment/create/candidate/route focused left-route-review");

        // A same-start replan retains a second immutable route while keeping
        // both alternatives selectable from the unchanged authoritative head.
        // Unequal-length cursor mapping is covered exhaustively in the pure
        // kernel tests; this natural journey proves successful explicit choice.
        palette(puppet, "sfm action invoke sfm:episode/trajectory/replan focused");
        palette(puppet, "sfm action invoke sfm:panel/open sfm:episode/candidate-history focused");
        puppet.waitTicks(20);
        palette(puppet,
                "sfm action invoke sfm:review/comment/create/candidate/route focused right-route-review");

        palette(puppet, "sfm action invoke sfm:panel/open sfm:episode/route-comparison focused");
        puppet.waitTicks(20);
        puppet.resetRouteComparisonSession();
        checkpoint(
                puppet,
                AssertRouteComparisonPuppetAction.Stage.INITIAL_LOCKSTEP,
                "route-comparison-initial",
                "Both retained candidate routes are visible at their immutable starts; actual state is unchanged."
        );

        palette(puppet, "sfm action invoke sfm:episode/route-comparison/seek focused both 1");
        checkpoint(
                puppet,
                AssertRouteComparisonPuppetAction.Stage.LOCKSTEP_SCRUBBED,
                "route-comparison-lockstep",
                "Lockstep seeking compares corresponding projected progress without executing either route."
        );

        palette(puppet,
                "sfm action invoke sfm:episode/route-comparison/mode/set focused independent");
        palette(puppet, "sfm action invoke sfm:episode/route-comparison/seek focused left 0");
        palette(puppet, "sfm action invoke sfm:episode/route-comparison/seek focused right 2");
        checkpoint(
                puppet,
                AssertRouteComparisonPuppetAction.Stage.INDEPENDENT_SCRUBBED,
                "route-comparison-independent",
                "Independent cursors compare different frames while retaining both immutable routes."
        );

        palette(puppet,
                "sfm action invoke sfm:episode/route-comparison/disposition/set focused left preferred");
        palette(puppet,
                "sfm action invoke sfm:episode/route-comparison/disposition/set focused right rejected");
        checkpoint(
                puppet,
                AssertRouteComparisonPuppetAction.Stage.DISPOSITION_RECORDED,
                "route-comparison-disposition",
                "Review disposition prefers one route and rejects the other without deleting routes or comments."
        );

        puppet.reloadRouteComparisonSession();
        checkpoint(
                puppet,
                AssertRouteComparisonPuppetAction.Stage.PERSISTENCE_RELOADED,
                "route-comparison-reloaded",
                "Mode, cursors, and dispositions round-trip while immutable route and comment identities remain."
        );

        palette(puppet,
                "sfm action invoke sfm:episode/route-comparison/trajectory/select focused left");
        checkpoint(
                puppet,
                AssertRouteComparisonPuppetAction.Stage.EXPLICIT_SELECTION,
                "route-comparison-explicit-selection",
                "Only the explicit trajectory-selection action may update the machine's selected route."
        );
    }

    private static void palette(SFMGamePuppetHelper puppet, String command) {
        puppet.executeCommandPalette(command);
        puppet.executeCommandPalette("sfm action invoke sfm:palette/close");
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
    }

    private static void checkpoint(
            SFMGamePuppetHelper puppet,
            AssertRouteComparisonPuppetAction.Stage stage,
            String artifact,
            String caption
    ) {
        puppet.assertRouteComparison(stage, artifact);
        puppet.capture(artifact, Component.literal("Route comparison: ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(caption).withStyle(ChatFormatting.BLACK)));
    }
}
