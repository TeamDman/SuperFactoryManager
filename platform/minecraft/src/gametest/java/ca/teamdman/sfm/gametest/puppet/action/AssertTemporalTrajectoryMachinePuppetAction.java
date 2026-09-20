package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;
import ca.teamdman.sfm.client.history.chamber.SFMChamberDocumentState;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingChamber;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingTrajectoryController;
import ca.teamdman.sfm.client.screen.history.chamber.SFMDecimalNumberingChamberPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Contract-level witness for the live natural temporal-numbering chamber journey. */
public record AssertTemporalTrajectoryMachinePuppetAction(Stage stage, String artifactName)
        implements SFMPuppetAction {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SOURCE_TWO = "- apples\n- bananas\n";
    private static final String NUMBERED_TWO = "1. apples\n2. bananas\n";
    private static final String SOURCE_THREE = "- apples\n- apricots\n- bananas\n";
    private static final String NUMBERED_THREE = "1. apples\n2. apricots\n3. bananas\n";
    private static final Map<String, Integer> PRE_STALE_STATE_COUNTS = new ConcurrentHashMap<>();

    public AssertTemporalTrajectoryMachinePuppetAction {
        Objects.requireNonNull(stage, "stage");
        if (artifactName == null || artifactName.isBlank()) {
            throw new IllegalArgumentException("Temporal trajectory artifact name must not be blank");
        }
    }

    @Override
    public String description() {
        return "assert temporal trajectory stage " + stage;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMDecimalNumberingTrajectoryController controller = requireController();
        SFMHistoryGraphRuntime.MachineSnapshot snapshot = controller.snapshot();
        assertStage(controller, snapshot);
        runtime.writeArtifact(
                artifactName,
                SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(stageEvidence(controller, snapshot))
        );
        if (stage == Stage.NUMBERED_THREE) writeFinalArtifacts(runtime, controller, snapshot);
        return true;
    }

    private void assertStage(
            SFMDecimalNumberingTrajectoryController controller,
            SFMHistoryGraphRuntime.MachineSnapshot snapshot
    ) {
        switch (stage) {
            case INITIAL -> {
                PRE_STALE_STATE_COUNTS.remove(snapshot.machineId());
                require(SOURCE_TWO.equals(controller.currentText()), "initial document bytes differ");
                require(snapshot.history().states().size() == 1, "initial history must contain one root");
                require(snapshot.planBook().plans().isEmpty(), "initial chamber must not contain a plan");
            }
            case PLANNED -> {
                require(SOURCE_TWO.equals(controller.currentText()), "planning mutated the document");
                require(snapshot.planBook().plans().size() == 1, "planning must retain exactly one plan");
                require(snapshot.machine().status() == SFMTrajectoryContract.MachineStatus.READY,
                        "planned machine must be ready");
                require(pointer(snapshot) == 0, "planned instruction pointer must target the first step");
                requireCanonicalSemanticRoute(selectedPlan(snapshot));
            }
            case FIRST_STEP -> {
                require(SOURCE_TWO.equals(controller.currentText()), "semantic selection changed document bytes");
                require(controller.currentState().selection().orElseThrow().regions().size() == 2,
                        "first step must select both hyphen markers");
                require(pointer(snapshot) == 1, "first step must advance one instruction");
                require(snapshot.machine().status() == SFMTrajectoryContract.MachineStatus.RUNNING,
                        "first step must leave the machine running");
            }
            case NUMBERED_TWO -> {
                require(NUMBERED_TWO.equals(controller.currentText()), "two-item numbering result differs");
                requireCompleteSupervision(snapshot);
                require(pointer(snapshot) == 2, "completed two-step route must point past its final step");
            }
            case POST_UNDO -> {
                require(SOURCE_TWO.equals(controller.currentText()), "undo did not restore selected source text");
                require(controller.currentState().selection().orElseThrow().regions().size() == 2,
                        "undo must restore the two-region semantic selection");
                require(snapshot.machine().status() == SFMTrajectoryContract.MachineStatus.PAUSED,
                        "undo must pause the trajectory machine");
                require(controller.childRevisionIds(controller.currentState().revisionId()).stream()
                                .map(id -> state(controller, id).text())
                                .anyMatch(NUMBERED_TWO::equals),
                        "undo must retain the old numbered child");
            }
            case THREE_ITEM_FORK -> {
                require(SOURCE_THREE.equals(controller.currentText()), "natural insertion result differs");
                SFMChamberDocumentState selected = selectedTwoItemState(controller);
                List<SFMChamberDocumentState> children = controller.childRevisionIds(selected.revisionId()).stream()
                        .map(id -> state(controller, id))
                        .toList();
                require(children.stream().anyMatch(value -> NUMBERED_TWO.equals(value.text())),
                        "the old numbered child is no longer reachable");
                require(children.stream().anyMatch(value -> "- apples\n\n- bananas\n".equals(value.text())),
                        "the first natural insertion must be a sibling of the old numbered child");
                require(snapshot.planBook().plans().size() == 1, "natural edit must retain the first plan");
                PRE_STALE_STATE_COUNTS.put(snapshot.machineId(), snapshot.history().states().size());
            }
            case STALE_STEP -> {
                require(SOURCE_THREE.equals(controller.currentText()), "stale step mutated the document");
                require(snapshot.machine().status() == SFMTrajectoryContract.MachineStatus.STALE_PRECONDITION,
                        "stale step must pause with STALE_PRECONDITION");
                Integer beforeStale = PRE_STALE_STATE_COUNTS.get(snapshot.machineId());
                require(beforeStale != null, "the pre-stale state count was not captured");
                require(snapshot.history().states().size() == beforeStale,
                        "stale validation must not add a document revision");
            }
            case REPLANNED -> {
                require(SOURCE_THREE.equals(controller.currentText()), "replanning mutated the document");
                require(snapshot.planBook().plans().size() == 2, "replan must retain both immutable plans");
                require(controller.replanLineage().size() == 1, "replan lineage must identify the retained source plan");
                require(snapshot.machine().status() == SFMTrajectoryContract.MachineStatus.READY,
                        "replanned machine must be ready");
                require(pointer(snapshot) == 0, "replanned instruction pointer must target the first new step");
                requireCanonicalSemanticRoute(selectedPlan(snapshot));
            }
            case NUMBERED_THREE -> {
                require(NUMBERED_THREE.equals(controller.currentText()), "three-item numbering result differs");
                requireCompleteSupervision(snapshot);
                require(snapshot.planBook().plans().size() == 2, "final state must retain both plan revisions");
                require(controller.retainedStates().stream().anyMatch(value -> NUMBERED_TWO.equals(value.text())),
                        "final state must retain the old numbered document branch");
                SFMDecimalNumberingTrajectoryController.CheckoutEvidence checkout =
                        controller.measureCheckoutEvidence();
                require(checkout.unchanged(), "protected checkout changed during the chamber journey");
                PRE_STALE_STATE_COUNTS.remove(snapshot.machineId());
            }
        }
    }

    private static void requireCanonicalSemanticRoute(SFMTrajectoryContract.TrajectoryPlanRevision plan) {
        SFMTrajectoryContract.TrajectoryRoute route = selectedRoute(plan);
        require(route.totalCost() == 2, "selected route must have exact semantic cost 2");
        require(route.steps().stream().map(step -> step.actionIntent().actionId()).toList().equals(List.of(
                        SFMDecimalNumberingChamber.SELECT_ALL_HYPHENS_ACTION_ID,
                        SFMDecimalNumberingChamber.REPLACE_DECIMAL_SEQUENCE_ACTION_ID
                )),
                "selected route must be semantic select followed by decimal replacement");
        require(plan.algorithm() == SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                "chamber must use deterministic A*");
        require(plan.heuristic().admissible(), "chamber heuristic must be admissible");
        require(plan.optimalityClaim()
                        == SFMTrajectoryContract.OptimalityClaim.MINIMUM_COST_UNDER_DECLARED_GRAPH_AND_POLICY,
                "plan must carry its bounded minimum-cost claim");
    }

    private static void requireCompleteSupervision(SFMHistoryGraphRuntime.MachineSnapshot snapshot) {
        require(snapshot.machine().status() == SFMTrajectoryContract.MachineStatus.COMPLETE,
                "completed document must mark the machine complete");
        SFMTrajectoryContract.SupervisionContract supervision = snapshot.supervision().orElseThrow();
        require(supervision.status() == SFMTrajectoryContract.SupervisionStatus.SUPERVISION_READY,
                "automation may only reach supervision-ready");
        require(supervision.approval().isEmpty(), "automation must not manufacture human approval");
    }

    private Map<String, Object> stageEvidence(
            SFMDecimalNumberingTrajectoryController controller,
            SFMHistoryGraphRuntime.MachineSnapshot snapshot
    ) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", "sfm.temporal-numbering-stage/1");
        answer.put("stage", stage.name());
        answer.put("machine_id", snapshot.machineId());
        answer.put("revision", snapshot.revision());
        answer.put("current_state_id", controller.currentState().revisionId());
        answer.put("current_state_hash", controller.currentState().stateHash());
        answer.put("current_text", controller.currentText());
        answer.put("selection_regions", controller.currentState().selection()
                .map(witness -> witness.regions().stream().map(AssertTemporalTrajectoryMachinePuppetAction::region).toList())
                .orElse(List.of()));
        answer.put("machine", machine(snapshot.machine()));
        answer.put("supervision_status", snapshot.supervision().map(value -> value.status().name()).orElse(null));
        answer.put("plan_ids", snapshot.planBook().plans().stream().map(SFMTrajectoryContract.TrajectoryPlanRevision::id).toList());
        answer.put("history_state_ids", snapshot.history().states().stream().map(SFMHistoryGraphContract.StateRevision::id).toList());
        answer.put("history_state_count", snapshot.history().states().size());
        answer.put("history_edge_ids", snapshot.history().edges().stream().map(SFMHistoryGraphContract.BranchEdge::id).toList());
        answer.put("replan_lineage", controller.replanLineage().stream().map(value -> Map.of(
                "id", value.id(),
                "source_plan", value.sourcePlanRevisionId(),
                "resulting_plan", value.resultingPlanRevisionId(),
                "start_state", value.authoritativeStartStateId(),
                "reason", value.reason()
        )).toList());
        return answer;
    }

    private void writeFinalArtifacts(
            ISFMGamePuppetRuntime runtime,
            SFMDecimalNumberingTrajectoryController controller,
            SFMHistoryGraphRuntime.MachineSnapshot snapshot
    ) {
        runtime.writeArtifact("documents", SFMGamePuppetArtifactFormat.UTF8, documents(controller));
        runtime.writeArtifact("transcript", SFMGamePuppetArtifactFormat.UTF8, transcript(snapshot));
        runtime.writeArtifact("episode", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(episode(snapshot)));
        runtime.writeArtifact("trajectory", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(trajectory(snapshot, controller)));
        runtime.writeArtifact("search-evidence", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(searchEvidence(snapshot)));
        runtime.writeArtifact("supervision", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(supervision(snapshot)));
        runtime.writeArtifact("checkout-unchanged", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(checkout(controller)));
    }

    private static String documents(SFMDecimalNumberingTrajectoryController controller) {
        StringBuilder text = new StringBuilder("schema: sfm.temporal-numbering-documents/1\n");
        for (SFMChamberDocumentState state : controller.retainedStates()) {
            text.append("\nrevision: ").append(state.revisionId()).append('\n');
            text.append("parent: ").append(state.parentRevisionId().orElse("root")).append('\n');
            text.append("state-hash: ").append(state.stateHash()).append('\n');
            text.append("selection-regions: ")
                    .append(state.selection().map(value -> value.regions().size()).orElse(0)).append('\n');
            text.append("--- document ---\n").append(state.text()).append("--- end ---\n");
        }
        return text.toString();
    }

    private static String transcript(SFMHistoryGraphRuntime.MachineSnapshot snapshot) {
        StringBuilder text = new StringBuilder("schema: sfm.temporal-numbering-transcript/1\n");
        for (SFMHistoryGraphContract.BranchEdge edge : snapshot.history().edges()) {
            text.append(edge.parentStateRevisionId()).append(" -> ")
                    .append(edge.childStateRevisionId()).append(" via ")
                    .append(edge.intentId().orElse("none")).append('\n');
        }
        return text.toString();
    }

    private static Map<String, Object> episode(SFMHistoryGraphRuntime.MachineSnapshot snapshot) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", "sfm.temporal-numbering-episode/1");
        answer.put("machine_id", snapshot.machineId());
        answer.put("states", snapshot.history().states().stream().map(value -> Map.of(
                "id", value.id(),
                "parents", value.parentRevisionIds(),
                "state_hash", value.stateHash(),
                "committed", value.committed(),
                "status", value.status().name()
        )).toList());
        answer.put("edges", snapshot.history().edges().stream().map(value -> {
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", value.id());
            edge.put("parent", value.parentStateRevisionId());
            edge.put("child", value.childStateRevisionId());
            edge.put("intent", value.intentId().orElse(null));
            edge.put("effect", value.effectClass().name());
            edge.put("status", value.status().name());
            edge.put("committed", value.committed());
            return edge;
        }).toList());
        answer.put("heads", snapshot.history().heads().stream().map(value -> Map.of(
                "id", value.id(),
                "state", value.stateRevisionId(),
                "domain", value.domain().domainId()
        )).toList());
        answer.put("head_movements", snapshot.history().headMovements().stream().map(value -> Map.of(
                "id", value.id(),
                "kind", value.kind().name(),
                "from", value.fromStateRevisionId(),
                "to", value.toStateRevisionId(),
                "candidates", value.candidateStateRevisionIds()
        )).toList());
        return answer;
    }

    private static Map<String, Object> trajectory(
            SFMHistoryGraphRuntime.MachineSnapshot snapshot,
            SFMDecimalNumberingTrajectoryController controller
    ) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", "sfm.temporal-numbering-trajectory/1");
        answer.put("selected_plan", snapshot.planBook().selectedPlanRevisionId().orElse(null));
        answer.put("machine", machine(snapshot.machine()));
        answer.put("plans", snapshot.planBook().plans().stream().map(plan -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", plan.id());
            value.put("parent_plan", plan.parentPlanRevisionId().orElse(null));
            value.put("start_state", plan.startStateId());
            value.put("algorithm", plan.algorithm().name());
            value.put("result", plan.result().name());
            value.put("optimality", plan.optimalityClaim().name());
            value.put("selected_route", plan.selectedRouteId().orElse(null));
            value.put("routes", plan.routes().stream().map(AssertTemporalTrajectoryMachinePuppetAction::route).toList());
            return value;
        }).toList());
        answer.put("replan_lineage", controller.replanLineage().stream().map(value -> Map.of(
                "id", value.id(),
                "source_plan", value.sourcePlanRevisionId(),
                "resulting_plan", value.resultingPlanRevisionId(),
                "start_state", value.authoritativeStartStateId(),
                "reason", value.reason()
        )).toList());
        return answer;
    }

    private static Map<String, Object> searchEvidence(SFMHistoryGraphRuntime.MachineSnapshot snapshot) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", "sfm.temporal-numbering-search/1");
        answer.put("frontier", snapshot.machine().projectionFrontier().map(value -> Map.of(
                "plan", value.planRevisionId(),
                "expanded", value.expanded(),
                "generated", value.generated(),
                "state_ids", value.frontierStateIds(),
                "status", value.status().name()
        )).orElse(null));
        answer.put("plans", snapshot.planBook().plans().stream().map(plan -> Map.of(
                "id", plan.id(),
                "generator", plan.actionGenerator().id() + "@" + plan.actionGenerator().revision(),
                "cost_policy", plan.costPolicy().id() + "@" + plan.costPolicy().revision(),
                "heuristic", plan.heuristic().id() + "@" + plan.heuristic().revision(),
                "candidates", plan.exploredCandidates().stream().map(candidate -> Map.of(
                        "state", candidate.stateId(),
                        "cost", candidate.accumulatedCost(),
                        "remaining", candidate.estimatedRemainingCost(),
                        "status", candidate.status().name(),
                        "reason", candidate.reason()
                )).toList()
        )).toList());
        return answer;
    }

    private static Map<String, Object> supervision(SFMHistoryGraphRuntime.MachineSnapshot snapshot) {
        SFMTrajectoryContract.SupervisionContract value = snapshot.supervision().orElseThrow();
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", "sfm.temporal-numbering-supervision/1");
        answer.put("id", value.id());
        answer.put("revision", value.revision());
        answer.put("start_state", value.startStateRevisionId());
        answer.put("status", value.status().name());
        answer.put("approval_requirement", value.approvalRequirement().name());
        answer.put("approval", value.approval().orElse(null));
        answer.put("goal_predicates", value.goalPredicates().stream().map(predicate -> predicate.id()).toList());
        answer.put("hard_invariants", value.hardInvariants().stream().map(predicate -> predicate.id()).toList());
        answer.put("required_evidence", value.requiredEvidence().stream().map(evidence -> evidence.id()).toList());
        return answer;
    }

    private static Map<String, Object> checkout(SFMDecimalNumberingTrajectoryController controller) {
        SFMDecimalNumberingTrajectoryController.CheckoutEvidence value = controller.measureCheckoutEvidence();
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", value.schema());
        answer.put("before_hash", value.beforeHash());
        answer.put("after_hash", value.afterHash());
        answer.put("unchanged", value.unchanged());
        return answer;
    }

    private static Map<String, Object> route(SFMTrajectoryContract.TrajectoryRoute route) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("id", route.id());
        answer.put("start_state", route.startStateId());
        answer.put("total_cost", route.totalCost());
        answer.put("status", route.status().name());
        answer.put("steps", route.steps().stream().map(step -> Map.of(
                "id", step.id(),
                "parent", step.expectedParentStateId(),
                "action_intent", step.actionIntent().id(),
                "action_id", step.actionIntent().actionId(),
                "predicted_state", step.predictedStateId(),
                "cost", step.stepCost(),
                "accumulated_cost", step.accumulatedCost(),
                "effect", step.effectClass().name()
        )).toList());
        return answer;
    }

    private static Map<String, Object> machine(SFMTrajectoryContract.TrajectoryMachineState machine) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("actual_history_head", machine.actualHistoryHeadId());
        answer.put("selected_plan", machine.selectedTrajectoryRevisionId().orElse(null));
        answer.put("status", machine.status().name());
        answer.put("instruction_pointer", machine.instructionPointer().map(value -> Map.of(
                "plan", value.planRevisionId(),
                "route", value.routeId(),
                "next_step", value.nextStepIndex()
        )).orElse(null));
        return answer;
    }

    private static Map<String, Object> region(SFMChamberDocumentState.SourceRegion region) {
        return Map.of(
                "start_codepoint", region.startCodePointOffset(),
                "end_codepoint", region.endCodePointOffset(),
                "line", region.lineOneBased(),
                "column", region.columnCodePointOneBased(),
                "expected_text", region.expectedText()
        );
    }

    private static int pointer(SFMHistoryGraphRuntime.MachineSnapshot snapshot) {
        return snapshot.machine().instructionPointer().orElseThrow().nextStepIndex();
    }

    private static SFMTrajectoryContract.TrajectoryPlanRevision selectedPlan(
            SFMHistoryGraphRuntime.MachineSnapshot snapshot
    ) {
        String selected = snapshot.planBook().selectedPlanRevisionId().orElseThrow();
        return snapshot.planBook().plans().stream()
                .filter(plan -> plan.id().equals(selected))
                .findFirst()
                .orElseThrow();
    }

    private static SFMTrajectoryContract.TrajectoryRoute selectedRoute(
            SFMTrajectoryContract.TrajectoryPlanRevision plan
    ) {
        String selected = plan.selectedRouteId().orElseThrow();
        return plan.routes().stream().filter(route -> route.id().equals(selected)).findFirst().orElseThrow();
    }

    private static SFMChamberDocumentState selectedTwoItemState(
            SFMDecimalNumberingTrajectoryController controller
    ) {
        return controller.retainedStates().stream()
                .filter(value -> SOURCE_TWO.equals(value.text()))
                .filter(value -> value.selection().map(witness -> witness.regions().size() == 2).orElse(false))
                .findFirst()
                .orElseThrow();
    }

    private static SFMChamberDocumentState state(
            SFMDecimalNumberingTrajectoryController controller,
            String id
    ) {
        return controller.retainedStates().stream()
                .filter(value -> value.revisionId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static SFMDecimalNumberingTrajectoryController requireController() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) {
            throw new IllegalStateException("Expected an SFM workspace for temporal trajectory evidence");
        }
        return workspace.panelIds().stream()
                .map(workspace::panelInstance)
                .filter(SFMDecimalNumberingChamberPanel.class::isInstance)
                .map(SFMDecimalNumberingChamberPanel.class::cast)
                .map(SFMDecimalNumberingChamberPanel::controller)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Temporal numbering chamber panel is not open"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public enum Stage {
        INITIAL,
        PLANNED,
        FIRST_STEP,
        NUMBERED_TWO,
        POST_UNDO,
        THREE_ITEM_FORK,
        STALE_STEP,
        REPLANNED,
        NUMBERED_THREE
    }
}
