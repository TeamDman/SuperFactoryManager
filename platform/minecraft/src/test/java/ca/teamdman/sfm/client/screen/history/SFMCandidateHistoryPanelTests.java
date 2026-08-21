package ca.teamdman.sfm.client.screen.history;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingTrajectoryController;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SFMCandidateHistoryPanelTests {
    @Test
    void scrubbingPinnedFramesNeverMovesHeadPointerOrPlanAndSurvivesReplan() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        SFMDecimalNumberingTrajectoryController controller = controller();
        runtime.register(controller);
        runtime.execute(
                exact(controller.machineId()),
                Optional.empty(),
                new SFMHistoryGraphRuntime.Plan()
        );
        SFMHistoryGraphRuntime.MachineSnapshot planned = controller.snapshot();
        String firstPlan = planned.machine().selectedTrajectoryRevisionId().orElseThrow();
        SFMTrajectoryContract.InstructionPointer pointer = planned.machine().instructionPointer().orElseThrow();
        String head = controller.currentState().revisionId();
        long revision = controller.revision();

        SFMCandidateHistoryPanel panel = new SFMCandidateHistoryPanel(
                runtime,
                exact(controller.machineId()),
                Optional.empty(),
                Optional.empty(),
                Runnable::run
        );
        panel.applyCatalog(runtime.snapshotEvent());
        assertEquals(SFMCandidateHistoryPanel.LoadStatus.QUEUED, panel.loadStatus());
        panel.tick();
        assertEquals(SFMCandidateHistoryPanel.LoadStatus.RUNNING, panel.loadStatus());
        panel.tick();
        assertEquals(SFMCandidateHistoryPanel.LoadStatus.READY, panel.loadStatus());
        assertEquals(firstPlan, panel.pinnedPlanRevisionId().orElseThrow());
        assertEquals(2, panel.timelineBounds().last());

        List<String> randomSeek = List.of(2, 0, 1, 2, 1).stream().map(position -> {
            panel.setTimelinePosition(position);
            return panel.currentFrame().orElseThrow().document().orElseThrow().text();
        }).toList();
        assertEquals(List.of(
                "1. apples\n2. bananas\n",
                SFMDecimalNumberingTrajectoryController.INITIAL_TEXT,
                SFMDecimalNumberingTrajectoryController.INITIAL_TEXT,
                "1. apples\n2. bananas\n",
                SFMDecimalNumberingTrajectoryController.INITIAL_TEXT
        ), randomSeek);
        assertEquals(revision, controller.revision());
        assertEquals(head, controller.currentState().revisionId());
        assertEquals(firstPlan, controller.snapshot().machine().selectedTrajectoryRevisionId().orElseThrow());
        assertEquals(pointer, controller.snapshot().machine().instructionPointer().orElseThrow());

        runtime.execute(exact(controller.machineId()), Optional.empty(), new SFMHistoryGraphRuntime.Run(8));
        controller.undo("test", "candidate-panel-undo");
        controller.insertText(
                "- apples\n".codePointCount(0, "- apples\n".length()),
                SFMDecimalNumberingTrajectoryController.THIRD_ITEM_TEXT,
                "test"
        );
        runtime.execute(exact(controller.machineId()), Optional.empty(), new SFMHistoryGraphRuntime.Replan());
        String secondPlan = controller.snapshot().machine().selectedTrajectoryRevisionId().orElseThrow();
        assertNotEquals(firstPlan, secondPlan);

        panel.applyCatalog(runtime.snapshotEvent());
        assertEquals(firstPlan, panel.pinnedPlanRevisionId().orElseThrow(),
                "an already-open scrubber must retain its immutable old plan");
        panel.setTimelinePosition(2);
        SFMCandidateHistoryContract.CandidateFrame oldFinal = panel.currentFrame().orElseThrow();
        assertEquals("1. apples\n2. bananas\n", oldFinal.document().orElseThrow().text());
        assertEquals(secondPlan, controller.snapshot().machine().selectedTrajectoryRevisionId().orElseThrow());
    }

    private static SFMDecimalNumberingTrajectoryController controller() {
        return new SFMDecimalNumberingTrajectoryController(
                "sfm:test/candidate-panel",
                "document",
                SFMDecimalNumberingTrajectoryController.INITIAL_TEXT,
                () -> "sha256:ambient"
        );
    }

    private static SFMEntitySelector exact(String machineId) {
        return SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, machineId);
    }
}
