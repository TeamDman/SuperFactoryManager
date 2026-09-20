package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingChamber;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingTrajectoryController;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.history.chamber.SFMDecimalNumberingChamberPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Shared read-only addressing helpers for the natural temporal replay puppet. */
final class SFMTemporalReplayPuppetSupport {
    private SFMTemporalReplayPuppetSupport() {
    }

    static SFMDecimalNumberingTrajectoryController requireController() {
        Object host = Minecraft.getInstance().screen;
        if (host instanceof SFMCommandPaletteScreen palette) {
            host = palette.actionContextForAutomation().originatingHost();
        }
        if (!(host instanceof SFMScreenMultiplexer workspace)) {
            throw new IllegalStateException("Expected an SFM workspace for temporal replay evidence");
        }
        return workspace.panelIds().stream()
                .map(workspace::panelInstance)
                .filter(SFMDecimalNumberingChamberPanel.class::isInstance)
                .map(SFMDecimalNumberingChamberPanel.class::cast)
                .map(SFMDecimalNumberingChamberPanel::controller)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Temporal numbering chamber panel is not open"));
    }

    static String realInputSourceBoundary(SFMDecimalNumberingTrajectoryController controller) {
        SFMTemporalReplayArchive.Archive archive = controller.replayArchive();
        List<String> candidates = archive.transitions().stream()
                .filter(transition -> transition.actionId().equals(
                        SFMDecimalNumberingChamber.SELECT_ALL_HYPHENS_ACTION_ID))
                .filter(transition -> archive.invocations().stream().anyMatch(invocation ->
                        invocation.id().equals(transition.invocationId())
                                && invocation.origin() == SFMTemporalReplayArchive.EventOrigin.DYNAMIC_KEY_BINDING))
                .map(SFMTemporalReplayArchive.SemanticTransition::parentStateId)
                .distinct()
                .toList();
        if (candidates.size() != 1) {
            throw new IllegalStateException(
                    "Expected one real-input numbering source boundary but found " + candidates.size()
            );
        }
        return candidates.get(0);
    }

    static String command(
            SFMDecimalNumberingTrajectoryController controller,
            SFMTemporalReplayArchive.ReplayMode mode,
            String targetParentStateId
    ) {
        String sourceBoundary = realInputSourceBoundary(controller);
        String selector = SFMEntitySelector.exact(
                SFMEntitySelector.Domain.EPISODE,
                controller.machineId()
        ).canonical();
        String actionId = mode == SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY
                ? "sfm:episode/replay/exact"
                : "sfm:episode/replay/semantic_rebase";
        return "sfm action invoke " + actionId + " " + selector + " "
                + sourceBoundary + " " + targetParentStateId;
    }
}
