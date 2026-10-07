package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingTrajectoryController;
import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationModel;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchiveJsonCodec;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayRuntime;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Assertions and durable evidence for X4's natural causal replay/rebase journey. */
public record AssertTemporalReplayRebasePuppetAction(
        Stage stage,
        String artifactName,
        String screenshotCaptureId
) implements SFMPuppetAction {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SOURCE_TWO = "- apples\n- bananas\n";
    private static final String NUMBERED_TWO = "1. apples\n2. bananas\n";
    private static final String SOURCE_THREE = "- apples\n- apricots\n- bananas\n";
    private static final String NUMBERED_THREE = "1. apples\n2. apricots\n3. bananas\n";

    public AssertTemporalReplayRebasePuppetAction {
        Objects.requireNonNull(stage, "stage");
        artifactName = requireText(artifactName, "artifactName");
        screenshotCaptureId = requireText(screenshotCaptureId, "screenshotCaptureId");
    }

    @Override
    public String description() {
        return "assert temporal replay/rebase stage " + stage;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMDecimalNumberingTrajectoryController controller =
                SFMTemporalReplayPuppetSupport.requireController();
        SFMHistoryGraphRuntime.MachineSnapshot snapshot = controller.snapshot();
        SFMTemporalReplayArchive.Archive archive = controller.replayArchive();
        assertStage(controller, archive);
        runtime.writeArtifact(
                artifactName,
                SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(stageEvidence(controller, snapshot, archive))
        );
        if (stage == Stage.SEMANTIC_REBASED) writeFinalArtifacts(runtime, controller, snapshot, archive);
        return true;
    }

    private void assertStage(
            SFMDecimalNumberingTrajectoryController controller,
            SFMTemporalReplayArchive.Archive archive
    ) {
        switch (stage) {
            case INITIAL -> {
                require(SOURCE_TWO.equals(controller.currentText()), "initial source document differs");
                require(archive.sourceEvents().isEmpty(), "initial archive must not invent input events");
                require(archive.transitions().isEmpty(), "initial archive must not contain transitions");
            }
            case DYNAMIC_SELECTED -> {
                require(SOURCE_TWO.equals(controller.currentText()), "dynamic selection changed document bytes");
                require(controller.currentState().selection().orElseThrow().regions().size() == 2,
                        "Ctrl+Alt+J must select both hyphen markers");
                List<SFMTemporalReplayArchive.SourceEvent> keys = archive.sourceEvents().stream()
                        .filter(event -> event.origin()
                                == SFMTemporalReplayArchive.EventOrigin.PHYSICAL_KEYBOARD)
                        .toList();
                require(keys.size() == 1, "dynamic selection must retain one exact source key event");
                SFMTemporalReplayArchive.SourceEvent key = keys.get(0);
                require(key.keyCode().orElseThrow() == GLFW.GLFW_KEY_J, "source key must be J");
                require(key.modifiers().containsAll(List.of("ALT", "CONTROL")),
                        "source event must retain Ctrl+Alt modifiers");
                require(archive.bindingSnapshots().size() == 1,
                        "dynamic selection must retain one effective binding snapshot");
                require(archive.bindingDecisions().stream().anyMatch(decision ->
                                decision.status() == SFMTemporalReplayArchive.BindingDecisionStatus.MATCHED
                                        && decision.sourceEventIds().equals(List.of(key.id()))
                                        && decision.bindingSnapshotId().isPresent()
                                        && decision.matchedBindingId().isPresent()),
                        "binding decision must retain the exact source range and effective binding identity");
                require(archive.invocations().stream().anyMatch(invocation ->
                                invocation.origin() == SFMTemporalReplayArchive.EventOrigin.DYNAMIC_KEY_BINDING),
                        "semantic invocation must retain dynamic-binding origin");
            }
            case NUMBERED_TWO -> {
                require(NUMBERED_TWO.equals(controller.currentText()), "two-item numbered document differs");
                require(archive.transitions().size() == 2,
                        "selection and decimal replacement must publish exactly two transitions");
                require(archive.invocations().stream().anyMatch(invocation ->
                                invocation.origin()
                                        == SFMTemporalReplayArchive.EventOrigin.REGISTERED_ACTION_SURFACE),
                        "decimal replacement must retain registered-action provenance");
            }
            case EXACT_REPLAYED -> {
                require(NUMBERED_TWO.equals(controller.currentText()), "exact replay changed the two-item bytes");
                require(archive.transitions().size() == 2,
                        "exact replay must verify rather than duplicate immutable transitions");
                require(latestReport(archive, SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY).status()
                                == SFMTemporalReplayArchive.ReplayStatus.SUCCEEDED,
                        "exact replay report must succeed");
                require(archive.sourceEvents().stream().anyMatch(event ->
                                event.origin() == SFMTemporalReplayArchive.EventOrigin.EXACT_REPLAY),
                        "exact replay must retain an explicit non-keystroke origin");
            }
            case THREE_ITEM_PARENT -> {
                require(SOURCE_THREE.equals(controller.currentText()), "historical insertion result differs");
                require(controller.currentState().selection().isEmpty(),
                        "semantic rebase target parent must be explicitly unselected");
                require(archive.frames().stream().anyMatch(frame -> NUMBERED_TWO.equals(frame.text())),
                        "historical insertion must retain the two-item numbered sibling");
            }
            case EXACT_MISMATCH -> {
                require(SOURCE_THREE.equals(controller.currentText()),
                        "failed exact replay must not mutate its changed target parent");
                SFMTemporalReplayArchive.ReplayReport report = latestReport(
                        archive,
                        SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY
                );
                require(report.status() == SFMTemporalReplayArchive.ReplayStatus.PRECONDITION_MISMATCH,
                        "changed-prefix exact replay must report PRECONDITION_MISMATCH");
                require(report.resultingTransitionIds().isEmpty() && report.resultingStateId().isEmpty(),
                        "failed exact replay must expose no partial result prefix");
            }
            case SEMANTIC_REBASED -> {
                require(NUMBERED_THREE.equals(controller.currentText()), "semantic rebase result differs");
                SFMTemporalReplayArchive.ReplayReport report = latestReport(
                        archive,
                        SFMTemporalReplayArchive.ReplayMode.SEMANTIC_REBASE
                );
                require(report.status() == SFMTemporalReplayArchive.ReplayStatus.SUCCEEDED,
                        "semantic rebase report must succeed");
                require(report.actionLineage().size() == 2,
                        "semantic rebase needs action-level lineage for both suffix actions");
                require(report.actionLineage().stream().allMatch(lineage ->
                                lineage.sourceWitnessId().isPresent()
                                        && lineage.resultingWitnessId().isPresent()
                                        && !lineage.sourceWitnessId().equals(lineage.resultingWitnessId())),
                        "semantic rebase must distinguish old and reevaluated witness identities");
                require(archive.frames().stream().anyMatch(frame -> NUMBERED_TWO.equals(frame.text())),
                        "semantic rebase must retain the original two-item branch");
                require(archive.frames().stream().anyMatch(frame -> NUMBERED_THREE.equals(frame.text())),
                        "semantic rebase must publish the three-item sibling");
                require(controller.measureCheckoutEvidence().unchanged(),
                        "protected checkout bytes changed during replay/rebase");
            }
        }
    }

    private Map<String, Object> stageEvidence(
            SFMDecimalNumberingTrajectoryController controller,
            SFMHistoryGraphRuntime.MachineSnapshot snapshot,
            SFMTemporalReplayArchive.Archive archive
    ) {
        LinkedHashMap<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", "sfm.temporal-replay-puppet-stage/1");
        answer.put("stage", stage.name());
        answer.put("mandatory_screenshot_capture_id", screenshotCaptureId);
        answer.put("machine_id", snapshot.machineId());
        answer.put("revision", snapshot.revision());
        answer.put("current_state_id", archive.currentStateId());
        answer.put("current_text", controller.currentText());
        answer.put("source_event_count", archive.sourceEvents().size());
        answer.put("binding_snapshot_count", archive.bindingSnapshots().size());
        answer.put("binding_decision_count", archive.bindingDecisions().size());
        answer.put("invocation_count", archive.invocations().size());
        answer.put("witness_count", archive.selectionWitnesses().size());
        answer.put("transition_count", archive.transitions().size());
        answer.put("replay_report_count", archive.replayReports().size());
        answer.put("retained_state_ids", archive.frames().stream()
                .map(SFMTemporalReplayArchive.Frame::stateId).toList());
        answer.put("replay_statuses", archive.replayReports().stream()
                .map(report -> report.mode() + ":" + report.status()).toList());
        answer.put("presentation_labels", snapshot.presentation().nodes().stream()
                .map(SFMHistoryGraphPresentationModel.Node::label).toList());
        answer.put("presentation_narration", snapshot.presentation().nodes().stream()
                .map(SFMHistoryGraphPresentationModel.Node::narration).toList());
        return answer;
    }

    private static void writeFinalArtifacts(
            ISFMGamePuppetRuntime runtime,
            SFMDecimalNumberingTrajectoryController controller,
            SFMHistoryGraphRuntime.MachineSnapshot snapshot,
            SFMTemporalReplayArchive.Archive archive
    ) {
        String canonical = SFMTemporalReplayArchiveJsonCodec.write(archive);
        SFMTemporalReplayArchive.Archive decoded = SFMTemporalReplayArchiveJsonCodec.read(canonical);
        require(decoded.equals(archive), "canonical replay artifact changed while parsing");
        SFMTemporalReplayRuntime restored = SFMTemporalReplayRuntime.restore(decoded);
        require(canonical.equals(restored.encodeCanonical()),
                "fresh replay runtime did not reproduce canonical artifact bytes");
        require(restored.frameGraph().frames().keySet().equals(
                        archive.frames().stream().map(SFMTemporalReplayArchive.Frame::stateId)
                                .collect(java.util.stream.Collectors.toSet())),
                "fresh replay runtime did not address every frame");

        runtime.writeArtifact(
                "temporal-replay-archive",
                SFMGamePuppetArtifactFormat.JSON,
                canonical
        );
        runtime.writeArtifact(
                "temporal-replay-transcript",
                SFMGamePuppetArtifactFormat.UTF8,
                transcript(restored)
        );
        runtime.writeArtifact(
                "temporal-replay-evidence",
                SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(finalEvidence(controller, snapshot, archive, restored))
        );
    }

    private static Map<String, Object> finalEvidence(
            SFMDecimalNumberingTrajectoryController controller,
            SFMHistoryGraphRuntime.MachineSnapshot snapshot,
            SFMTemporalReplayArchive.Archive archive,
            SFMTemporalReplayRuntime restored
    ) {
        LinkedHashMap<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", "sfm.temporal-replay-puppet-evidence/1");
        answer.put("archive_schema", archive.schema());
        answer.put("archive_generation", archive.generation());
        answer.put("canonical_round_trip", true);
        answer.put("restored_record_count", restored.addressedRecords().size());
        answer.put("restored_head", restored.head().stateId());
        answer.put("source_events", archive.sourceEvents().stream().map(event -> Map.of(
                "id", event.id(),
                "sequence", event.sequence(),
                "kind", event.kind().name(),
                "origin", event.origin().name(),
                "tick", event.deterministicTick(),
                "key_code", event.keyCode().map(Object::toString).orElse(""),
                "key_event_type", event.keyEventType().orElse(""),
                "modifiers", event.modifiers(),
                "payload", event.payload().orElse("")
        )).toList());
        answer.put("binding_snapshots", archive.bindingSnapshots().stream().map(value -> Map.of(
                "id", value.id(),
                "revision", value.revision(),
                "digest", value.digest(),
                "binding_ids", value.bindings().stream()
                        .map(SFMTemporalReplayArchive.BindingDefinition::bindingId).toList()
        )).toList());
        answer.put("binding_decisions", archive.bindingDecisions().stream().map(value -> Map.of(
                "id", value.id(),
                "sequence", value.sequence(),
                "status", value.status().name(),
                "source_event_ids", value.sourceEventIds(),
                "snapshot_id", value.bindingSnapshotId().orElse(""),
                "binding_id", value.matchedBindingId().orElse(""),
                "consumed", value.consumed()
        )).toList());
        answer.put("invocations", archive.invocations().stream().map(value -> Map.of(
                "id", value.id(),
                "sequence", value.sequence(),
                "origin", value.origin().name(),
                "action_id", value.actionId(),
                "command", value.commandDraft(),
                "source_event_ids", value.sourceEventIds()
        )).toList());
        answer.put("witnesses", archive.selectionWitnesses().stream().map(value -> Map.of(
                "id", value.id(),
                "parent", value.parentStateId(),
                "source_text_hash", value.sourceTextHash(),
                "evaluator", value.evaluatorId() + "@" + value.evaluatorRevision(),
                "ordering", value.orderingPolicy(),
                "geometry", value.geometryRevision(),
                "regions", value.regions().stream().map(region -> Map.of(
                        "ordinal", region.ordinal(),
                        "start_codepoint", region.startCodePointOffset(),
                        "end_codepoint", region.endCodePointOffset(),
                        "line", region.lineOneBased(),
                        "column", region.columnCodePointOneBased(),
                        "expected_text", region.expectedText()
                )).toList()
        )).toList());
        answer.put("reports", archive.replayReports().stream().map(value -> Map.of(
                "id", value.id(),
                "sequence", value.sequence(),
                "mode", value.mode().name(),
                "status", value.status().name(),
                "source_boundary", value.sourceBoundaryStateId(),
                "target_parent", value.targetParentStateId(),
                "source_transitions", value.sourceTransitionIds(),
                "result_transitions", value.resultingTransitionIds(),
                "result_state", value.resultingStateId().orElse(""),
                "lineage", value.actionLineage().stream().map(lineage -> Map.of(
                        "source_transition", lineage.sourceTransitionId(),
                        "source_witness", lineage.sourceWitnessId().orElse(""),
                        "result_transition", lineage.resultingTransitionId().orElse(""),
                        "result_witness", lineage.resultingWitnessId().orElse(""),
                        "status", lineage.status().name()
                )).toList()
        )).toList());
        answer.put("frames", archive.frames().stream().map(value -> Map.of(
                "state_id", value.stateId(),
                "parent_state_id", value.parentStateId().orElse(""),
                "state_hash", value.stateHash(),
                "text_hash", value.textHash(),
                "text", value.text(),
                "selection_witness_id", value.selectionWitnessId().orElse("")
        )).toList());
        answer.put("observations", archive.observations().stream().map(value -> Map.of(
                "id", value.id(),
                "sequence", value.sequence(),
                "kind", value.kind(),
                "related_record", value.relatedRecordId(),
                "value", value.value()
        )).toList());
        answer.put("head_movements", archive.headMovements().stream().map(value -> Map.of(
                "id", value.id(),
                "sequence", value.sequence(),
                "kind", value.kind().name(),
                "from", value.fromStateId(),
                "to", value.toStateId(),
                "request", value.requestId()
        )).toList());
        answer.put("presentation_node_labels", snapshot.presentation().nodes().stream()
                .map(SFMHistoryGraphPresentationModel.Node::label).toList());
        answer.put("presentation_edge_labels", snapshot.presentation().edges().stream()
                .map(SFMHistoryGraphPresentationModel.Edge::label).toList());
        SFMDecimalNumberingTrajectoryController.CheckoutEvidence checkout =
                controller.measureCheckoutEvidence();
        answer.put("ambient_checkout", Map.of(
                "before_hash", checkout.beforeHash(),
                "after_hash", checkout.afterHash(),
                "unchanged", checkout.unchanged()
        ));
        return answer;
    }

    private static String transcript(SFMTemporalReplayRuntime runtime) {
        StringBuilder text = new StringBuilder("schema: sfm.temporal-replay-transcript/1\n");
        text.append("head: ").append(runtime.head().stateId()).append('\n');
        text.append("\ncausal timeline:\n");
        for (SFMTemporalReplayRuntime.AddressedRecord record : runtime.causalTimeline()) {
            text.append(record.logicalSequence().orElseThrow())
                    .append(' ')
                    .append(record.address().kind())
                    .append(' ')
                    .append(record.address().id())
                    .append('\n');
        }
        text.append("\nframes:\n");
        for (SFMTemporalReplayArchive.Frame frame : runtime.archive().frames()) {
            text.append(frame.stateId()).append(" parent=")
                    .append(frame.parentStateId().orElse("root"))
                    .append(" state-hash=").append(frame.stateHash())
                    .append(" text-hash=").append(frame.textHash())
                    .append('\n').append(frame.text()).append("---\n");
        }
        text.append("\nreplay reports:\n");
        for (SFMTemporalReplayArchive.ReplayReport report : runtime.archive().replayReports()) {
            text.append(report.mode()).append(' ').append(report.status())
                    .append(" source=").append(report.sourceBoundaryStateId())
                    .append(" target=").append(report.targetParentStateId())
                    .append(" result=").append(report.resultingStateId().orElse("none"))
                    .append('\n');
        }
        return text.toString();
    }

    private static SFMTemporalReplayArchive.ReplayReport latestReport(
            SFMTemporalReplayArchive.Archive archive,
            SFMTemporalReplayArchive.ReplayMode mode
    ) {
        return archive.replayReports().stream()
                .filter(report -> report.mode() == mode)
                .reduce((left, right) -> right)
                .orElseThrow(() -> new IllegalStateException("Missing " + mode + " report"));
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
        DYNAMIC_SELECTED,
        NUMBERED_TWO,
        EXACT_REPLAYED,
        THREE_ITEM_PARENT,
        EXACT_MISMATCH,
        SEMANTIC_REBASED
    }
}
