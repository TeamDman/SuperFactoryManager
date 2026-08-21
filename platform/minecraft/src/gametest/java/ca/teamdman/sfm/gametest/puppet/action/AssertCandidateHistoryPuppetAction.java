package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingTrajectoryController;
import ca.teamdman.sfm.client.screen.history.SFMCandidateHistoryPanel;
import ca.teamdman.sfm.client.screen.history.chamber.SFMDecimalNumberingChamberPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMTimelinePanel;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Visual and machine-readable witness for read-only candidate-route scrubbing. */
public record AssertCandidateHistoryPuppetAction(Stage stage, String artifactName) implements SFMPuppetAction {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Baseline> BASELINES = new ConcurrentHashMap<>();

    public AssertCandidateHistoryPuppetAction {
        Objects.requireNonNull(stage, "stage");
        if (artifactName == null || artifactName.isBlank()) {
            throw new IllegalArgumentException("Candidate-history artifact name must not be blank");
        }
    }

    @Override
    public String description() {
        return "assert candidate-history stage " + stage;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMDecimalNumberingTrajectoryController controller = requireController();
        SFMCandidateHistoryPanel panel = requireFocusedCandidatePanel();
        if (panel.loadStatus() != SFMCandidateHistoryPanel.LoadStatus.READY) return false;
        SFMCandidateHistoryContract.CandidateRouteProjection projection = panel.projection().orElseThrow();
        SFMCandidateHistoryContract.CandidateFrame frame = panel.currentFrame().orElseThrow();
        SFMHistoryGraphRuntime.MachineSnapshot actual = controller.snapshot();
        assertStage(controller, panel, projection, frame, actual);
        runtime.writeArtifact(
                artifactName,
                SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence(controller, panel, projection, frame, actual))
        );
        return true;
    }

    private void assertStage(
            SFMDecimalNumberingTrajectoryController controller,
            SFMCandidateHistoryPanel panel,
            SFMCandidateHistoryContract.CandidateRouteProjection projection,
            SFMCandidateHistoryContract.CandidateFrame frame,
            SFMHistoryGraphRuntime.MachineSnapshot actual
    ) {
        String machineId = controller.machineId();
        switch (stage) {
            case OLD_ROUTE_START -> {
                require(panel.currentPosition() == 0, "old route must start at frame zero");
                require(SFMDecimalNumberingTrajectoryController.INITIAL_TEXT.equals(document(frame)),
                        "old route start document differs");
                Baseline baseline = Baseline.capture(controller, actual, projection.trajectoryPlanRevisionId());
                BASELINES.put(machineId, baseline);
                require(projection.frames().size() == 3, "two-step route must expose three candidate frames");
            }
            case OLD_ROUTE_SELECTED -> {
                require(panel.currentPosition() == 1, "old route semantic-selection frame must be one");
                require(SFMDecimalNumberingTrajectoryController.INITIAL_TEXT.equals(document(frame)),
                        "semantic selection must not change candidate bytes");
                require(frame.document().orElseThrow().selectionRegionCount() == 2,
                        "semantic selection frame must contain two regions");
                requireUnchanged(controller, actual, baseline(machineId));
            }
            case OLD_ROUTE_NUMBERED -> {
                require(panel.currentPosition() == 2, "old route numbered frame must be two");
                require("1. apples\n2. bananas\n".equals(document(frame)),
                        "old route numbered candidate differs");
                requireUnchanged(controller, actual, baseline(machineId));
            }
            case OLD_ROUTE_RETAINED_AFTER_REPLAN -> {
                Baseline baseline = baseline(machineId);
                String selected = actual.machine().selectedTrajectoryRevisionId().orElseThrow();
                require(!selected.equals(baseline.planRevisionId()), "replan must select a new plan");
                require(panel.pinnedPlanRevisionId().orElseThrow().equals(baseline.planRevisionId()),
                        "old scrubber silently switched to the new plan");
                require(panel.currentPosition() == 2, "old scrubber must retain its selected frame");
                require("1. apples\n2. bananas\n".equals(document(frame)),
                        "old plan candidate bytes changed after replan");
                require(actual.planBook().plans().size() == 2, "replan must retain both plans");
            }
            case NEW_ROUTE_START -> {
                String selected = actual.machine().selectedTrajectoryRevisionId().orElseThrow();
                require(projection.trajectoryPlanRevisionId().equals(selected),
                        "new scrubber did not pin the selected replan");
                require(panel.currentPosition() == 0, "new route must start at frame zero");
                require("- apples\n- apricots\n- bananas\n".equals(document(frame)),
                        "new route start document differs");
                BASELINES.put(machineId, Baseline.capture(controller, actual, selected));
            }
            case NEW_ROUTE_NUMBERED -> {
                require(panel.currentPosition() == 2, "new route numbered frame must be two");
                require("1. apples\n2. apricots\n3. bananas\n".equals(document(frame)),
                        "new route numbered candidate differs");
                requireUnchanged(controller, actual, baseline(machineId));
                require(frame.address().projectionStatus()
                                == ca.teamdman.sfm.client.history.SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                        "new route final frame must be materialized");
            }
        }
    }

    private Map<String, Object> evidence(
            SFMDecimalNumberingTrajectoryController controller,
            SFMCandidateHistoryPanel panel,
            SFMCandidateHistoryContract.CandidateRouteProjection projection,
            SFMCandidateHistoryContract.CandidateFrame frame,
            SFMHistoryGraphRuntime.MachineSnapshot actual
    ) {
        LinkedHashMap<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", "sfm.candidate-history-puppet-stage/1");
        answer.put("stage", stage.name());
        answer.put("machine_id", controller.machineId());
        answer.put("controller_revision", controller.revision());
        answer.put("actual_state", controller.currentState().revisionId());
        answer.put("actual_selected_plan", actual.machine().selectedTrajectoryRevisionId().orElse(null));
        answer.put("actual_instruction_pointer", pointer(actual));
        answer.put("pinned_plan", projection.trajectoryPlanRevisionId());
        answer.put("pinned_route", projection.routeId());
        answer.put("frame_address", frame.address().canonical());
        answer.put("frame_state", frame.address().predictedStateId());
        answer.put("frame_hash", frame.address().predictedStateHash().orElse(null));
        answer.put("frame_status", frame.address().projectionStatus().name());
        answer.put("frame_document", frame.document().map(value -> value.text()).orElse(null));
        answer.put("last_trustworthy_predecessor",
                frame.lastTrustworthyPredecessorStateId().orElse(null));
        answer.put("evaluator_evidence", frame.address().evaluatorEvidence());
        return answer;
    }

    private static void requireUnchanged(
            SFMDecimalNumberingTrajectoryController controller,
            SFMHistoryGraphRuntime.MachineSnapshot actual,
            Baseline baseline
    ) {
        require(controller.revision() == baseline.controllerRevision(),
                "candidate seeking changed the controller revision");
        require(controller.currentState().revisionId().equals(baseline.actualStateRevisionId()),
                "candidate seeking moved the actual document head");
        require(actual.machine().selectedTrajectoryRevisionId().equals(Optional.of(baseline.planRevisionId())),
                "candidate seeking changed plan selection");
        require(actual.machine().instructionPointer().equals(Optional.of(baseline.instructionPointer())),
                "candidate seeking moved the instruction pointer");
    }

    private static String document(SFMCandidateHistoryContract.CandidateFrame frame) {
        return frame.document().orElseThrow(() -> new IllegalStateException(
                "Expected a materialized candidate frame, got " + frame.address().projectionStatus())).text();
    }

    private static Baseline baseline(String machineId) {
        Baseline baseline = BASELINES.get(machineId);
        if (baseline == null) throw new IllegalStateException("Candidate-history baseline was not captured");
        return baseline;
    }

    private static String pointer(SFMHistoryGraphRuntime.MachineSnapshot snapshot) {
        return snapshot.machine().instructionPointer()
                .map(value -> value.planRevisionId() + "/" + value.routeId() + "@" + value.nextStepIndex())
                .orElse("none");
    }

    private static SFMDecimalNumberingTrajectoryController requireController() {
        SFMScreenMultiplexer workspace = workspace();
        return workspace.panelIds().stream()
                .map(workspace::panelInstance)
                .filter(SFMDecimalNumberingChamberPanel.class::isInstance)
                .map(SFMDecimalNumberingChamberPanel.class::cast)
                .map(SFMDecimalNumberingChamberPanel::controller)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Temporal numbering chamber panel is not open"));
    }

    private static SFMCandidateHistoryPanel requireFocusedCandidatePanel() {
        if (!(workspace().focusedPanelInstance() instanceof SFMTimelinePanel timeline)
                || !(timeline.child() instanceof SFMCandidateHistoryPanel candidate)) {
            throw new IllegalStateException("Focused panel is not a Candidate History timeline");
        }
        return candidate;
    }

    private static SFMScreenMultiplexer workspace() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) {
            throw new IllegalStateException("Expected an SFM workspace for candidate-history evidence");
        }
        return workspace;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Baseline(
            long controllerRevision,
            String actualStateRevisionId,
            String planRevisionId,
            SFMTrajectoryContract.InstructionPointer instructionPointer
    ) {
        private static Baseline capture(
                SFMDecimalNumberingTrajectoryController controller,
                SFMHistoryGraphRuntime.MachineSnapshot snapshot,
                String planRevisionId
        ) {
            return new Baseline(
                    controller.revision(),
                    controller.currentState().revisionId(),
                    planRevisionId,
                    snapshot.machine().instructionPointer().orElseThrow()
            );
        }
    }

    public enum Stage {
        OLD_ROUTE_START,
        OLD_ROUTE_SELECTED,
        OLD_ROUTE_NUMBERED,
        OLD_ROUTE_RETAINED_AFTER_REPLAN,
        NEW_ROUTE_START,
        NEW_ROUTE_NUMBERED
    }
}
