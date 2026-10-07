package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationModel;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualController;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualJsonCodec;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualRuntime;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Assertions and durable artifacts for X5's natural whole-workspace journey. */
public record AssertWorkspaceCounterfactualPuppetAction(
        Stage stage,
        String artifactName,
        String screenshotCaptureId
) implements SFMPuppetAction {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public AssertWorkspaceCounterfactualPuppetAction {
        Objects.requireNonNull(stage, "stage");
        artifactName = requireText(artifactName, "artifactName");
        screenshotCaptureId = requireText(screenshotCaptureId, "screenshotCaptureId");
    }

    @Override public String description() { return "assert workspace counterfactual stage " + stage; }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMWorkspaceCounterfactualController controller = requireController();
        assertStage(controller);
        SFMHistoryGraphRuntime.MachineSnapshot snapshot = controller.snapshot();
        runtime.writeArtifact(
                artifactName,
                SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(stageEvidence(controller, snapshot))
        );
        if (stage == Stage.REEVALUATED_BARRIER) writeFinalArtifacts(runtime, controller, snapshot);
        return true;
    }

    private void assertStage(SFMWorkspaceCounterfactualController controller) {
        var artifact = controller.artifact();
        require(artifact.ambientBeforeHash().equals(artifact.ambientAfterHash()),
                "ambient checkout hash changed");
        switch (stage) {
            case INITIAL -> requireState(controller, SFMWorkspaceCounterfactualController.BEFORE_SELECTION);
            case A_OPEN -> {
                requireState(controller, SFMWorkspaceCounterfactualController.A_OPEN);
                require(controller.selectedPath().orElseThrow().equals(SFMWorkspaceCounterfactualController.A_PATH),
                        "A.java must be selected");
            }
            case A_EDITED -> {
                requireState(controller, SFMWorkspaceCounterfactualController.A_EDITED);
                require(controller.documentText(SFMWorkspaceCounterfactualController.A_PATH).equals(
                                SFMWorkspaceCounterfactualController.INITIAL_A
                                        + SFMWorkspaceCounterfactualController.EDIT_SUFFIX),
                        "ordinary Text Editor V3 input did not commit the exact A suffix");
            }
            case B_SELECTED -> {
                requireState(controller, SFMWorkspaceCounterfactualController.B_SELECTED);
                require(artifact.requireFrame(SFMWorkspaceCounterfactualController.A_EDITED) != null,
                        "recorded A branch was not retained");
            }
            case RECORDED_CHECKOUT -> {
                requireState(controller, SFMWorkspaceCounterfactualController.A_EDITED);
                require(artifact.headMovements().stream().anyMatch(movement ->
                                movement.narrativeKind()
                                        == SFMWorkspaceCounterfactualContract.NarrativeKind.RECORDED_CHECKOUT),
                        "recorded checkout did not publish a head movement");
                require(artifact.operations().stream().noneMatch(operation ->
                                operation.id().contains("checkout-recorded")),
                        "recorded checkout fabricated an execution operation");
            }
            case FROZEN_A -> {
                requireState(controller, SFMWorkspaceCounterfactualController.FROZEN_A_EDITED);
                require(controller.documentText(SFMWorkspaceCounterfactualController.A_PATH).endsWith(
                                SFMWorkspaceCounterfactualController.EDIT_SUFFIX),
                        "frozen witness did not target A.java");
                require(controller.documentText(SFMWorkspaceCounterfactualController.B_PATH).equals(
                                SFMWorkspaceCounterfactualController.INITIAL_B),
                        "frozen A replay mutated B.java");
            }
            case REEVALUATED_BARRIER -> {
                requireState(controller, SFMWorkspaceCounterfactualController.REEVALUATED_B_EDITED);
                require(controller.documentText(SFMWorkspaceCounterfactualController.B_PATH).equals(
                                SFMWorkspaceCounterfactualController.INITIAL_B
                                        + SFMWorkspaceCounterfactualController.EDIT_SUFFIX),
                        "intent re-evaluation did not apply the compatible suffix to B.java");
                require(artifact.barriers().size() == 1, "exactly one external barrier is required");
                require(artifact.operations().stream()
                                .filter(operation -> operation.actionId().equals(
                                        SFMWorkspaceCounterfactualController.EXTERNAL_ACTION_ID))
                                .allMatch(operation -> operation.resultStateId().isEmpty()),
                        "external barrier must not have a result state");
                require(controller.snapshot().history().actionAttempts().stream().anyMatch(attempt ->
                                attempt.status() == SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER),
                        "History Graph omitted the childless external barrier");
            }
        }
    }

    private Map<String, Object> stageEvidence(
            SFMWorkspaceCounterfactualController controller,
            SFMHistoryGraphRuntime.MachineSnapshot snapshot
    ) {
        var artifact = controller.artifact();
        LinkedHashMap<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", "sfm.workspace-counterfactual-puppet-stage/1");
        answer.put("stage", stage.name());
        answer.put("mandatory_screenshot_capture_id", screenshotCaptureId);
        answer.put("episode_id", artifact.episodeId());
        answer.put("generation", artifact.generation());
        answer.put("current_state_id", artifact.currentStateId());
        answer.put("ambient_checkout_unchanged", artifact.ambientBeforeHash().equals(artifact.ambientAfterHash()));
        answer.put("selected_path", controller.selectedPath().orElse(""));
        answer.put("open_document_path", controller.currentFrame().openDocumentPath().orElse(""));
        answer.put("retained_frame_ids", artifact.workspaceFrames().stream()
                .map(SFMWorkspaceCounterfactualContract.WorkspaceFrame::id).toList());
        answer.put("operation_ids", artifact.operations().stream()
                .map(SFMWorkspaceCounterfactualContract.Operation::id).toList());
        answer.put("head_movement_ids", artifact.headMovements().stream()
                .map(SFMWorkspaceCounterfactualContract.HeadMovement::id).toList());
        answer.put("barrier_ids", artifact.barriers().stream()
                .map(SFMWorkspaceCounterfactualContract.Barrier::id).toList());
        answer.put("presentation_labels", snapshot.presentation().nodes().stream()
                .map(SFMHistoryGraphPresentationModel.Node::label).toList());
        return answer;
    }

    private static void writeFinalArtifacts(
            ISFMGamePuppetRuntime runtime,
            SFMWorkspaceCounterfactualController controller,
            SFMHistoryGraphRuntime.MachineSnapshot snapshot
    ) {
        var artifact = controller.artifact();
        String canonical = SFMWorkspaceCounterfactualJsonCodec.write(artifact);
        var decoded = SFMWorkspaceCounterfactualJsonCodec.read(canonical);
        var restored = SFMWorkspaceCounterfactualController.restore(
                decoded,
                decoded::ambientAfterHash
        );
        require(canonical.equals(SFMWorkspaceCounterfactualJsonCodec.write(restored.artifact())),
                "fresh workspace controller did not reproduce canonical bytes");
        require(restored.snapshot().history().equals(snapshot.history()),
                "fresh workspace controller did not reproduce the History Graph contract");

        runtime.writeArtifact("workspace-counterfactual-episode", SFMGamePuppetArtifactFormat.JSON, canonical);
        runtime.writeArtifact(
                "workspace-counterfactual-transcript",
                SFMGamePuppetArtifactFormat.UTF8,
                SFMWorkspaceCounterfactualJsonCodec.transcript(decoded)
        );
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schema", "sfm.workspace-counterfactual-puppet-evidence/1");
        evidence.put("canonical_round_trip", true);
        evidence.put("ambient_checkout", Map.of(
                "before_hash", artifact.ambientBeforeHash(),
                "after_hash", artifact.ambientAfterHash(),
                "unchanged", artifact.ambientBeforeHash().equals(artifact.ambientAfterHash())
        ));
        evidence.put("documents", artifact.requireFrame(artifact.currentStateId()).documents().stream().map(document ->
                Map.of(
                        "logical_path", document.logicalPath(),
                        "text_hash", document.textHash(),
                        "text", document.text()
                )).toList());
        evidence.put("action_attempts", snapshot.history().actionAttempts().stream().map(attempt -> Map.of(
                "id", attempt.id(),
                "parent", attempt.parentStateRevisionId(),
                "effect", attempt.effectClass().name(),
                "status", attempt.status().name()
        )).toList());
        evidence.put("head_movements", snapshot.history().headMovements().stream().map(movement -> Map.of(
                "id", movement.id(),
                "kind", movement.kind().name(),
                "from", movement.fromStateRevisionId(),
                "to", movement.toStateRevisionId()
        )).toList());
        evidence.put("presentation_nodes", snapshot.presentation().nodes().stream().map(node -> Map.of(
                "id", node.id(),
                "label", node.label(),
                "origins", node.origins().stream().map(Enum::name).toList(),
                "roles", node.roles().stream().map(Enum::name).toList()
        )).toList());
        runtime.writeArtifact(
                "workspace-counterfactual-evidence",
                SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence)
        );
    }

    private static SFMWorkspaceCounterfactualController requireController() {
        List<String> ids = SFMWorkspaceCounterfactualRuntime.get().episodeIds();
        if (ids.isEmpty()) throw new IllegalStateException("No whole-workspace episode is loaded");
        String id = ids.get(ids.size() - 1);
        return SFMWorkspaceCounterfactualRuntime.get().controller(id).orElseThrow();
    }

    private static void requireState(SFMWorkspaceCounterfactualController controller, String stateId) {
        require(controller.currentFrame().id().equals(stateId),
                "expected " + stateId + " but found " + controller.currentFrame().id());
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public enum Stage {
        INITIAL,
        A_OPEN,
        A_EDITED,
        B_SELECTED,
        RECORDED_CHECKOUT,
        FROZEN_A,
        REEVALUATED_BARRIER
    }
}
