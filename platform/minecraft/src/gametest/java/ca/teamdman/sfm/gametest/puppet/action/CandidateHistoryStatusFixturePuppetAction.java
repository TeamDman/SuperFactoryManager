package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;
import ca.teamdman.sfm.client.screen.history.SFMCandidateHistoryPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMTimelinePanel;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Installs and validates a deterministic route containing every unavailable-frame class. */
public record CandidateHistoryStatusFixturePuppetAction(Stage stage, Optional<String> artifactName)
        implements SFMPuppetAction {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MACHINE_ID = "sfm:fixture/candidate-statuses";
    private static final String PLAN_ID = MACHINE_ID + "/plan/1";
    private static final String ROUTE_ID = MACHINE_ID + "/route/1";
    private static final String HEAD_ID = MACHINE_ID + "/head";
    private static final String START_STATE = MACHINE_ID + "/state/materialized";
    private static final String START_HASH = "sha256:fixture-materialized";
    private static final List<SFMHistoryGraphContract.ProjectionStatus> FRAME_STATUSES = List.of(
            SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
            SFMHistoryGraphContract.ProjectionStatus.CANCELLED,
            SFMHistoryGraphContract.ProjectionStatus.CONFLICT,
            SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER,
            SFMHistoryGraphContract.ProjectionStatus.UNKNOWN
    );
    private static FixtureController controller;
    private static SFMHistoryGraphRuntime.Registration registration;

    public CandidateHistoryStatusFixturePuppetAction {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(artifactName, "artifactName");
        if (stage.requiresArtifact() && artifactName.filter(value -> !value.isBlank()).isEmpty()) {
            throw new IllegalArgumentException("A status-frame assertion requires an artifact name");
        }
    }

    public static CandidateHistoryStatusFixturePuppetAction register() {
        return new CandidateHistoryStatusFixturePuppetAction(Stage.REGISTER, Optional.empty());
    }

    public static CandidateHistoryStatusFixturePuppetAction assertStage(Stage stage, String artifactName) {
        if (!stage.requiresArtifact()) throw new IllegalArgumentException("Stage is not assertable: " + stage);
        return new CandidateHistoryStatusFixturePuppetAction(stage, Optional.of(artifactName));
    }

    public static CandidateHistoryStatusFixturePuppetAction unregister() {
        return new CandidateHistoryStatusFixturePuppetAction(Stage.UNREGISTER, Optional.empty());
    }

    @Override
    public String description() {
        return "candidate-history status fixture " + stage;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime puppetRuntime) {
        SFMHistoryGraphRuntime historyRuntime = SFMHistoryGraphRuntime.get();
        if (stage == Stage.REGISTER) {
            if (registration != null) registration.close();
            controller = new FixtureController();
            registration = historyRuntime.register(controller);
            historyRuntime.setActiveMachine(MACHINE_ID);
            return true;
        }
        if (stage == Stage.UNREGISTER) {
            if (registration != null) registration.close();
            registration = null;
            controller = null;
            return true;
        }

        FixtureController fixture = Objects.requireNonNull(controller, "status fixture controller");
        SFMCandidateHistoryPanel panel = requireFocusedCandidatePanel();
        if (panel.loadStatus() != SFMCandidateHistoryPanel.LoadStatus.READY) return false;
        SFMCandidateHistoryContract.CandidateFrame frame = panel.currentFrame().orElseThrow();
        int expectedPosition = stage.position();
        SFMHistoryGraphContract.ProjectionStatus expectedStatus = FRAME_STATUSES.get(expectedPosition);
        require(panel.currentPosition() == expectedPosition,
                "Expected status frame " + expectedPosition + " but saw " + panel.currentPosition());
        require(frame.address().projectionStatus() == expectedStatus,
                "Expected " + expectedStatus + " but saw " + frame.address().projectionStatus());
        require(fixture.revision == 0, "Candidate seeking mutated the fixture controller");
        require(fixture.snapshot().machine().actualHistoryHeadId().equals(HEAD_ID),
                "Candidate seeking moved the fixture history head");
        require(fixture.snapshot().machine().instructionPointer().orElseThrow().nextStepIndex() == 0,
                "Candidate seeking moved the fixture instruction pointer");
        if (expectedStatus == SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED) {
            require(frame.document().orElseThrow().text().equals("candidate status fixture\n"),
                    "Materialized fixture bytes differ");
        } else {
            require(frame.document().isEmpty(), "Unavailable status frame fabricated document bytes");
            require(frame.lastTrustworthyPredecessorStateId().equals(Optional.of(START_STATE)),
                    "Unavailable status frame lost its trustworthy predecessor");
        }

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schema", "sfm.candidate-history-status-puppet/1");
        evidence.put("stage", stage.name());
        evidence.put("frame_position", panel.currentPosition());
        evidence.put("projection_status", expectedStatus.name());
        evidence.put("document_present", frame.document().isPresent());
        evidence.put("last_trustworthy_predecessor", frame.lastTrustworthyPredecessorStateId().orElse(null));
        evidence.put("actual_head", HEAD_ID);
        evidence.put("actual_instruction_pointer", 0);
        evidence.put("controller_revision", fixture.revision);
        puppetRuntime.writeArtifact(
                artifactName.orElseThrow(),
                SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence)
        );
        return true;
    }

    private static SFMCandidateHistoryPanel requireFocusedCandidatePanel() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) {
            throw new IllegalStateException("Expected an SFM workspace for candidate-status evidence");
        }
        if (!(workspace.focusedPanelInstance() instanceof SFMTimelinePanel timeline)
                || !(timeline.child() instanceof SFMCandidateHistoryPanel candidate)) {
            throw new IllegalStateException("Focused panel is not a Candidate History timeline");
        }
        return candidate;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public enum Stage {
        REGISTER(-1),
        MATERIALIZED(0),
        UNAVAILABLE(1),
        INVALIDATED(2),
        EXTERNAL_BARRIER(3),
        UNKNOWN(4),
        UNREGISTER(-1);

        private final int position;

        Stage(int position) {
            this.position = position;
        }

        int position() {
            return position;
        }

        boolean requiresArtifact() {
            return position >= 0;
        }
    }

    private static final class FixtureController implements SFMHistoryGraphRuntime.CandidateFrameController {
        private final SFMHistoryGraphRuntime.MachineSnapshot snapshot =
                CandidateHistoryStatusFixturePuppetAction.snapshot();
        private final SFMCandidateHistoryContract.CandidateRouteProjection projection =
                CandidateHistoryStatusFixturePuppetAction.projection();
        private long revision;

        @Override
        public String machineId() {
            return MACHINE_ID;
        }

        @Override
        public SFMHistoryGraphRuntime.MachineSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public SFMHistoryGraphRuntime.OperationResult apply(SFMHistoryGraphRuntime.Operation operation) {
            return SFMHistoryGraphRuntime.OperationResult.rejected("The candidate-status fixture is read-only");
        }

        @Override
        public SFMCandidateHistoryContract.CandidateRouteProjection projectCandidateRoute(
                String planRevisionId,
                String routeId
        ) {
            if (!PLAN_ID.equals(planRevisionId) || !ROUTE_ID.equals(routeId)) {
                throw new IllegalArgumentException("Unknown candidate-status fixture route");
            }
            return projection;
        }
    }

    private static SFMHistoryGraphRuntime.MachineSnapshot snapshot() {
        SFMHistoryGraphContract.StateRevision root = new SFMHistoryGraphContract.StateRevision(
                START_STATE, List.of(), START_HASH, true,
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
        );
        SFMHistoryGraphContract.Graph history = new SFMHistoryGraphContract.Graph(
                SFMHistoryGraphContract.SCHEMA,
                List.of(), List.of(), List.of(), List.of(root),
                List.of(new SFMHistoryGraphContract.HistoryHead(
                        HEAD_ID,
                        new SFMHistoryGraphContract.UndoDomain(
                                SFMHistoryGraphContract.UndoDomainKind.DOCUMENT,
                                "document://candidate-status-fixture"
                        ),
                        START_STATE,
                        Optional.of("candidate-status-fixture")
                )),
                List.of(), List.of(), List.of()
        );

        ArrayList<SFMTrajectoryContract.TrajectoryStep> steps = new ArrayList<>();
        String parent = START_STATE;
        for (int index = 1; index < FRAME_STATUSES.size(); index++) {
            String state = MACHINE_ID + "/state/" + index;
            SFMHistoryGraphContract.ActionIntent intent = new SFMHistoryGraphContract.ActionIntent(
                    MACHINE_ID + "/intent/" + index,
                    "sfm:fixture/project-status/" + FRAME_STATUSES.get(index).name().toLowerCase(java.util.Locale.ROOT),
                    List.of(),
                    "fixture-order-" + index
            );
            steps.add(new SFMTrajectoryContract.TrajectoryStep(
                    MACHINE_ID + "/step/" + index,
                    parent,
                    intent,
                    SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                    MACHINE_ID + "/outcome/" + index,
                    state,
                    FRAME_STATUSES.get(index) == SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER
                            ? SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE
                            : SFMHistoryGraphContract.EffectClass.UNKNOWN,
                    1,
                    index,
                    FRAME_STATUSES.size() - index - 1
            ));
            parent = state;
        }
        SFMTrajectoryContract.TrajectoryRoute route = new SFMTrajectoryContract.TrajectoryRoute(
                ROUTE_ID, START_STATE, steps, steps.size(), SFMHistoryGraphContract.ProjectionStatus.UNKNOWN
        );
        SFMTrajectoryContract.TrajectoryPlanRevision plan = new SFMTrajectoryContract.TrajectoryPlanRevision(
                PLAN_ID,
                Optional.empty(),
                START_STATE,
                "candidate-status-supervision-v1",
                new SFMTrajectoryContract.ActionGeneratorIdentity("candidate-status-fixture", "v1", true),
                new SFMTrajectoryContract.CostPolicyIdentity("candidate-status-fixture", "v1"),
                new SFMTrajectoryContract.HeuristicIdentity("candidate-status-fixture", "v1", true),
                SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                new SFMTrajectoryContract.SearchBudget(8, 8, 1_000),
                SFMTrajectoryContract.PlanResult.FOUND,
                SFMTrajectoryContract.OptimalityClaim.NONE,
                List.of(route),
                Optional.of(ROUTE_ID),
                List.of(),
                "Fixture route exposes non-materialized candidate statuses"
        );
        SFMTrajectoryContract.PlanBook planBook = new SFMTrajectoryContract.PlanBook(
                SFMTrajectoryContract.SCHEMA, List.of(plan), Optional.of(PLAN_ID)
        );
        SFMTrajectoryContract.TrajectoryMachineState machine = new SFMTrajectoryContract.TrajectoryMachineState(
                HEAD_ID,
                Optional.of(PLAN_ID),
                Optional.of(new SFMTrajectoryContract.InstructionPointer(PLAN_ID, ROUTE_ID, 0)),
                Optional.empty(),
                "candidate-status-supervision-v1",
                SFMTrajectoryContract.MachineStatus.READY,
                new SFMTrajectoryContract.RemainingBudget(8, 8, 1_000)
        );
        return new SFMHistoryGraphRuntime.MachineSnapshot(
                MACHINE_ID, 0, history, planBook, machine, Optional.empty(),
                "Candidate projection status fixture"
        );
    }

    private static SFMCandidateHistoryContract.CandidateRouteProjection projection() {
        ArrayList<SFMCandidateHistoryContract.CandidateFrame> frames = new ArrayList<>();
        frames.add(new SFMCandidateHistoryContract.CandidateFrame(
                new SFMCandidateHistoryContract.CandidateFrameAddress(
                        PLAN_ID, ROUTE_ID, 0, Optional.empty(), START_STATE, Optional.of(START_HASH),
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                        Optional.of("fixture-evaluator-v1"),
                        List.of(new SFMCandidateHistoryContract.EvaluatorEvidence("fixture", "materialized"))
                ),
                Optional.of(new SFMCandidateHistoryContract.CandidateDocument(
                        "document://candidate-status-fixture",
                        "candidate status fixture\n",
                        START_HASH,
                        0
                )),
                Optional.empty(),
                Optional.empty(),
                "Materialized route start"
        ));
        for (int index = 1; index < FRAME_STATUSES.size(); index++) {
            SFMHistoryGraphContract.ProjectionStatus status = FRAME_STATUSES.get(index);
            frames.add(new SFMCandidateHistoryContract.CandidateFrame(
                    new SFMCandidateHistoryContract.CandidateFrameAddress(
                            PLAN_ID,
                            ROUTE_ID,
                            index,
                            Optional.of(MACHINE_ID + "/step/" + index),
                            MACHINE_ID + "/state/" + index,
                            Optional.empty(),
                            status,
                            Optional.of("fixture-evaluator-v1"),
                            List.of(new SFMCandidateHistoryContract.EvaluatorEvidence(
                                    "status", status.name().toLowerCase(java.util.Locale.ROOT)))
                    ),
                    Optional.empty(),
                    Optional.of(MACHINE_ID + "/intent/" + index),
                    Optional.of(START_STATE),
                    switch (status) {
                        case CANCELLED -> "Unavailable: candidate projection was cancelled";
                        case CONFLICT -> "Invalidated: candidate witness conflicts with current evidence";
                        case EXTERNAL_BARRIER -> "External barrier: no document bytes can be projected";
                        case UNKNOWN -> "Unknown: evaluator cannot prove a candidate document";
                        default -> throw new IllegalStateException("Unexpected fixture status " + status);
                    }
            ));
        }
        return new SFMCandidateHistoryContract.CandidateRouteProjection(
                SFMCandidateHistoryContract.SCHEMA,
                MACHINE_ID,
                0,
                HEAD_ID,
                Optional.of(PLAN_ID),
                Optional.of(new SFMTrajectoryContract.InstructionPointer(PLAN_ID, ROUTE_ID, 0)),
                PLAN_ID,
                ROUTE_ID,
                frames
        );
    }
}
