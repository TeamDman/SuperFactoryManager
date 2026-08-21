package ca.teamdman.sfm.client.history.replay;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.ActionInvocation;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.ActionLineage;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.Archive;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.BindingDecision;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.BindingDecisionStatus;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.BindingDefinition;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.BindingSnapshot;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.EventKind;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.EventOrigin;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.Frame;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.HeadMovement;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.HeadMovementKind;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.InvocationStatus;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.KeyStroke;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.Observation;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.ReplayMode;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.ReplayReport;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.ReplayStatus;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.SelectionExpression;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.SelectionWitness;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.SemanticTransition;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.SourceEvent;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.TransitionStatus;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.TypedArgument;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.WitnessRegion;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Strict, deterministic JSON interchange for one Java-local temporal replay archive. */
public final class SFMTemporalReplayArchiveJsonCodec {
    static final int MAX_JSON_NESTING_DEPTH = 128;

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private SFMTemporalReplayArchiveJsonCodec() {
    }

    public static String write(Archive archive) {
        Objects.requireNonNull(archive, "archive");
        JsonObject root = new JsonObject();
        root.addProperty("schema", archive.schema());
        root.addProperty("episode_id", archive.episodeId());
        root.addProperty("document_id", archive.documentId());
        root.addProperty("generation", archive.generation());
        root.add("binding_snapshots", writeList(archive.bindingSnapshots(),
                SFMTemporalReplayArchiveJsonCodec::writeBindingSnapshot));
        root.add("source_events", writeList(archive.sourceEvents(),
                SFMTemporalReplayArchiveJsonCodec::writeSourceEvent));
        root.add("binding_decisions", writeList(archive.bindingDecisions(),
                SFMTemporalReplayArchiveJsonCodec::writeBindingDecision));
        root.add("invocations", writeList(archive.invocations(),
                SFMTemporalReplayArchiveJsonCodec::writeInvocation));
        root.add("selection_expressions", writeList(archive.selectionExpressions(),
                SFMTemporalReplayArchiveJsonCodec::writeSelectionExpression));
        root.add("selection_witnesses", writeList(archive.selectionWitnesses(),
                SFMTemporalReplayArchiveJsonCodec::writeSelectionWitness));
        root.add("frames", writeList(archive.frames(), SFMTemporalReplayArchiveJsonCodec::writeFrame));
        root.add("transitions", writeList(archive.transitions(),
                SFMTemporalReplayArchiveJsonCodec::writeTransition));
        root.add("observations", writeList(archive.observations(),
                SFMTemporalReplayArchiveJsonCodec::writeObservation));
        root.add("head_movements", writeList(archive.headMovements(),
                SFMTemporalReplayArchiveJsonCodec::writeHeadMovement));
        root.add("replay_reports", writeList(archive.replayReports(),
                SFMTemporalReplayArchiveJsonCodec::writeReplayReport));
        root.addProperty("current_state_id", archive.currentStateId());
        return GSON.toJson(root) + "\n";
    }

    public static Archive read(String text) {
        Objects.requireNonNull(text, "text");
        JsonElement parsed = parseStrict(text);
        JsonObject root = object(parsed, "archive");
        fields(root, "archive",
                "schema", "episode_id", "document_id", "generation", "binding_snapshots", "source_events",
                "binding_decisions", "invocations", "selection_expressions", "selection_witnesses", "frames",
                "transitions", "observations", "head_movements", "replay_reports", "current_state_id");
        return new Archive(
                string(root, "schema"),
                string(root, "episode_id"),
                string(root, "document_id"),
                exactLong(root, "generation"),
                readList(root, "binding_snapshots", "binding snapshot",
                        SFMTemporalReplayArchiveJsonCodec::readBindingSnapshot),
                readList(root, "source_events", "source event",
                        SFMTemporalReplayArchiveJsonCodec::readSourceEvent),
                readList(root, "binding_decisions", "binding decision",
                        SFMTemporalReplayArchiveJsonCodec::readBindingDecision),
                readList(root, "invocations", "invocation",
                        SFMTemporalReplayArchiveJsonCodec::readInvocation),
                readList(root, "selection_expressions", "selection expression",
                        SFMTemporalReplayArchiveJsonCodec::readSelectionExpression),
                readList(root, "selection_witnesses", "selection witness",
                        SFMTemporalReplayArchiveJsonCodec::readSelectionWitness),
                readList(root, "frames", "frame", SFMTemporalReplayArchiveJsonCodec::readFrame),
                readList(root, "transitions", "transition", SFMTemporalReplayArchiveJsonCodec::readTransition),
                readList(root, "observations", "observation", SFMTemporalReplayArchiveJsonCodec::readObservation),
                readList(root, "head_movements", "head movement",
                        SFMTemporalReplayArchiveJsonCodec::readHeadMovement),
                readList(root, "replay_reports", "replay report",
                        SFMTemporalReplayArchiveJsonCodec::readReplayReport),
                string(root, "current_state_id")
        );
    }

    private static JsonObject writeBindingSnapshot(BindingSnapshot snapshot) {
        JsonObject value = new JsonObject();
        value.addProperty("schema", snapshot.schema());
        value.addProperty("id", snapshot.id());
        value.addProperty("revision", snapshot.revision());
        value.addProperty("digest", snapshot.digest());
        value.add("bindings", writeList(snapshot.bindings(),
                SFMTemporalReplayArchiveJsonCodec::writeBindingDefinition));
        return value;
    }

    private static BindingSnapshot readBindingSnapshot(JsonObject value) {
        fields(value, "binding snapshot", "schema", "id", "revision", "digest", "bindings");
        return new BindingSnapshot(
                string(value, "schema"),
                string(value, "id"),
                exactLong(value, "revision"),
                string(value, "digest"),
                readList(value, "bindings", "binding", SFMTemporalReplayArchiveJsonCodec::readBindingDefinition)
        );
    }

    private static JsonObject writeBindingDefinition(BindingDefinition binding) {
        JsonObject value = new JsonObject();
        value.addProperty("binding_id", binding.bindingId());
        value.addProperty("action_id", binding.actionId());
        value.addProperty("command_draft", binding.commandDraft());
        value.addProperty("situation_id", binding.situationId());
        value.add("sequence", writeList(binding.sequence(), SFMTemporalReplayArchiveJsonCodec::writeKeyStroke));
        value.addProperty("enabled", binding.enabled());
        return value;
    }

    private static BindingDefinition readBindingDefinition(JsonObject value) {
        fields(value, "binding", "binding_id", "action_id", "command_draft", "situation_id", "sequence",
                "enabled");
        return new BindingDefinition(
                string(value, "binding_id"),
                string(value, "action_id"),
                string(value, "command_draft"),
                string(value, "situation_id"),
                readList(value, "sequence", "key stroke", SFMTemporalReplayArchiveJsonCodec::readKeyStroke),
                bool(value, "enabled")
        );
    }

    private static JsonObject writeKeyStroke(KeyStroke stroke) {
        JsonObject value = new JsonObject();
        value.addProperty("key_code", stroke.keyCode());
        value.add("modifiers", strings(stroke.modifiers()));
        return value;
    }

    private static KeyStroke readKeyStroke(JsonObject value) {
        fields(value, "key stroke", "key_code", "modifiers");
        return new KeyStroke(exactInt(value, "key_code"), stringList(value, "modifiers"));
    }

    private static JsonObject writeSourceEvent(SourceEvent event) {
        JsonObject value = new JsonObject();
        value.addProperty("id", event.id());
        value.addProperty("sequence", event.sequence());
        value.addProperty("kind", wire(event.kind()));
        value.addProperty("origin", wire(event.origin()));
        value.addProperty("deterministic_tick", event.deterministicTick());
        addOptionalInteger(value, "key_code", event.keyCode());
        addOptionalString(value, "key_event_type", event.keyEventType());
        addOptionalInteger(value, "code_point", event.codePoint());
        value.add("modifiers", strings(event.modifiers()));
        addOptionalString(value, "payload", event.payload());
        return value;
    }

    private static SourceEvent readSourceEvent(JsonObject value) {
        fields(value, "source event", "id", "sequence", "kind", "origin", "deterministic_tick", "key_code",
                "key_event_type", "code_point", "modifiers", "payload");
        return new SourceEvent(
                string(value, "id"),
                exactLong(value, "sequence"),
                enumValue(EventKind.class, string(value, "kind")),
                enumValue(EventOrigin.class, string(value, "origin")),
                exactLong(value, "deterministic_tick"),
                optionalInteger(value, "key_code"),
                optionalString(value, "key_event_type"),
                optionalInteger(value, "code_point"),
                stringList(value, "modifiers"),
                optionalString(value, "payload")
        );
    }

    private static JsonObject writeBindingDecision(BindingDecision decision) {
        JsonObject value = new JsonObject();
        value.addProperty("id", decision.id());
        value.addProperty("sequence", decision.sequence());
        value.add("source_event_ids", strings(decision.sourceEventIds()));
        value.addProperty("status", wire(decision.status()));
        addOptionalString(value, "binding_snapshot_id", decision.bindingSnapshotId());
        addOptionalString(value, "matched_binding_id", decision.matchedBindingId());
        value.add("active_situation_ids", strings(decision.activeSituationIds()));
        value.add("invocation_ids", strings(decision.invocationIds()));
        value.addProperty("consumed", decision.consumed());
        value.add("diagnostics", strings(decision.diagnostics()));
        return value;
    }

    private static BindingDecision readBindingDecision(JsonObject value) {
        fields(value, "binding decision", "id", "sequence", "source_event_ids", "status",
                "binding_snapshot_id", "matched_binding_id", "active_situation_ids", "invocation_ids", "consumed",
                "diagnostics");
        return new BindingDecision(
                string(value, "id"),
                exactLong(value, "sequence"),
                stringList(value, "source_event_ids"),
                enumValue(BindingDecisionStatus.class, string(value, "status")),
                optionalString(value, "binding_snapshot_id"),
                optionalString(value, "matched_binding_id"),
                stringList(value, "active_situation_ids"),
                stringList(value, "invocation_ids"),
                bool(value, "consumed"),
                stringList(value, "diagnostics")
        );
    }

    private static JsonObject writeInvocation(ActionInvocation invocation) {
        JsonObject value = new JsonObject();
        value.addProperty("id", invocation.id());
        value.addProperty("sequence", invocation.sequence());
        value.addProperty("origin", wire(invocation.origin()));
        value.addProperty("action_id", invocation.actionId());
        value.addProperty("command_draft", invocation.commandDraft());
        value.add("arguments", writeList(invocation.arguments(),
                SFMTemporalReplayArchiveJsonCodec::writeTypedArgument));
        addOptionalString(value, "binding_decision_id", invocation.bindingDecisionId());
        value.add("source_event_ids", strings(invocation.sourceEventIds()));
        value.addProperty("availability", wire(invocation.availability()));
        value.addProperty("authorization", wire(invocation.authorization()));
        value.addProperty("status", wire(invocation.status()));
        value.add("diagnostics", strings(invocation.diagnostics()));
        return value;
    }

    private static ActionInvocation readInvocation(JsonObject value) {
        fields(value, "invocation", "id", "sequence", "origin", "action_id", "command_draft", "arguments",
                "binding_decision_id", "source_event_ids", "availability", "authorization", "status",
                "diagnostics");
        return new ActionInvocation(
                string(value, "id"),
                exactLong(value, "sequence"),
                enumValue(EventOrigin.class, string(value, "origin")),
                string(value, "action_id"),
                string(value, "command_draft"),
                readList(value, "arguments", "argument", SFMTemporalReplayArchiveJsonCodec::readTypedArgument),
                optionalString(value, "binding_decision_id"),
                stringList(value, "source_event_ids"),
                enumValue(SFMTemporalReplayArchive.AvailabilityStatus.class, string(value, "availability")),
                enumValue(SFMTemporalReplayArchive.AuthorizationStatus.class, string(value, "authorization")),
                enumValue(InvocationStatus.class, string(value, "status")),
                stringList(value, "diagnostics")
        );
    }

    private static JsonObject writeTypedArgument(TypedArgument argument) {
        JsonObject value = new JsonObject();
        value.addProperty("name", argument.name());
        value.addProperty("type", argument.type());
        value.addProperty("value", argument.value());
        return value;
    }

    private static TypedArgument readTypedArgument(JsonObject value) {
        fields(value, "argument", "name", "type", "value");
        return new TypedArgument(string(value, "name"), string(value, "type"), string(value, "value"));
    }

    private static JsonObject writeSelectionExpression(SelectionExpression expression) {
        JsonObject value = new JsonObject();
        value.addProperty("schema", expression.schema());
        value.addProperty("id", expression.id());
        value.addProperty("document_id", expression.documentId());
        value.addProperty("kind", expression.kind());
        value.addProperty("seed_text", expression.seedText());
        value.addProperty("evaluator_id", expression.evaluatorId());
        value.addProperty("evaluator_revision", expression.evaluatorRevision());
        value.addProperty("ordering_policy", expression.orderingPolicy());
        value.addProperty("geometry_revision", expression.geometryRevision());
        return value;
    }

    private static SelectionExpression readSelectionExpression(JsonObject value) {
        fields(value, "selection expression", "schema", "id", "document_id", "kind", "seed_text",
                "evaluator_id", "evaluator_revision", "ordering_policy", "geometry_revision");
        return new SelectionExpression(
                string(value, "schema"),
                string(value, "id"),
                string(value, "document_id"),
                string(value, "kind"),
                string(value, "seed_text"),
                string(value, "evaluator_id"),
                string(value, "evaluator_revision"),
                string(value, "ordering_policy"),
                string(value, "geometry_revision")
        );
    }

    private static JsonObject writeSelectionWitness(SelectionWitness witness) {
        JsonObject value = new JsonObject();
        value.addProperty("id", witness.id());
        value.addProperty("expression_id", witness.expressionId());
        value.addProperty("parent_state_id", witness.parentStateId());
        value.addProperty("source_text_hash", witness.sourceTextHash());
        value.addProperty("evaluator_id", witness.evaluatorId());
        value.addProperty("evaluator_revision", witness.evaluatorRevision());
        value.addProperty("ordering_policy", witness.orderingPolicy());
        value.addProperty("geometry_revision", witness.geometryRevision());
        value.add("regions", writeList(witness.regions(), SFMTemporalReplayArchiveJsonCodec::writeWitnessRegion));
        return value;
    }

    private static SelectionWitness readSelectionWitness(JsonObject value) {
        fields(value, "selection witness", "id", "expression_id", "parent_state_id", "source_text_hash",
                "evaluator_id", "evaluator_revision", "ordering_policy", "geometry_revision", "regions");
        return new SelectionWitness(
                string(value, "id"),
                string(value, "expression_id"),
                string(value, "parent_state_id"),
                string(value, "source_text_hash"),
                string(value, "evaluator_id"),
                string(value, "evaluator_revision"),
                string(value, "ordering_policy"),
                string(value, "geometry_revision"),
                readList(value, "regions", "witness region",
                        SFMTemporalReplayArchiveJsonCodec::readWitnessRegion)
        );
    }

    private static JsonObject writeWitnessRegion(WitnessRegion region) {
        JsonObject value = new JsonObject();
        value.addProperty("ordinal", region.ordinal());
        value.addProperty("start_code_point_offset", region.startCodePointOffset());
        value.addProperty("end_code_point_offset", region.endCodePointOffset());
        value.addProperty("line_one_based", region.lineOneBased());
        value.addProperty("column_code_point_one_based", region.columnCodePointOneBased());
        value.addProperty("expected_text", region.expectedText());
        return value;
    }

    private static WitnessRegion readWitnessRegion(JsonObject value) {
        fields(value, "witness region", "ordinal", "start_code_point_offset", "end_code_point_offset",
                "line_one_based", "column_code_point_one_based", "expected_text");
        return new WitnessRegion(
                exactInt(value, "ordinal"),
                exactInt(value, "start_code_point_offset"),
                exactInt(value, "end_code_point_offset"),
                exactInt(value, "line_one_based"),
                exactInt(value, "column_code_point_one_based"),
                string(value, "expected_text")
        );
    }

    private static JsonObject writeFrame(Frame frame) {
        JsonObject value = new JsonObject();
        value.addProperty("state_id", frame.stateId());
        addOptionalString(value, "parent_state_id", frame.parentStateId());
        value.addProperty("state_hash", frame.stateHash());
        value.addProperty("text_hash", frame.textHash());
        value.addProperty("text", frame.text());
        addOptionalString(value, "selection_witness_id", frame.selectionWitnessId());
        return value;
    }

    private static Frame readFrame(JsonObject value) {
        fields(value, "frame", "state_id", "parent_state_id", "state_hash", "text_hash", "text",
                "selection_witness_id");
        return new Frame(
                string(value, "state_id"),
                optionalString(value, "parent_state_id"),
                string(value, "state_hash"),
                string(value, "text_hash"),
                string(value, "text"),
                optionalString(value, "selection_witness_id")
        );
    }

    private static JsonObject writeTransition(SemanticTransition transition) {
        JsonObject value = new JsonObject();
        value.addProperty("id", transition.id());
        value.addProperty("sequence", transition.sequence());
        value.addProperty("invocation_id", transition.invocationId());
        value.addProperty("intent_id", transition.intentId());
        value.addProperty("evaluation_id", transition.evaluationId());
        value.addProperty("outcome_id", transition.outcomeId());
        value.addProperty("parent_state_id", transition.parentStateId());
        value.addProperty("result_state_id", transition.resultStateId());
        value.addProperty("action_id", transition.actionId());
        value.add("arguments", writeList(transition.arguments(),
                SFMTemporalReplayArchiveJsonCodec::writeTypedArgument));
        value.addProperty("evaluation_policy", wire(transition.evaluationPolicy()));
        value.addProperty("effect_class", wire(transition.effectClass()));
        addOptionalString(value, "witness_id", transition.witnessId());
        value.addProperty("status", wire(transition.status()));
        value.add("evidence", strings(transition.evidence()));
        return value;
    }

    private static SemanticTransition readTransition(JsonObject value) {
        fields(value, "transition", "id", "sequence", "invocation_id", "intent_id", "evaluation_id",
                "outcome_id", "parent_state_id", "result_state_id", "action_id", "arguments",
                "evaluation_policy", "effect_class", "witness_id", "status", "evidence");
        return new SemanticTransition(
                string(value, "id"),
                exactLong(value, "sequence"),
                string(value, "invocation_id"),
                string(value, "intent_id"),
                string(value, "evaluation_id"),
                string(value, "outcome_id"),
                string(value, "parent_state_id"),
                string(value, "result_state_id"),
                string(value, "action_id"),
                readList(value, "arguments", "argument", SFMTemporalReplayArchiveJsonCodec::readTypedArgument),
                enumValue(SFMHistoryGraphContract.EvaluationPolicy.class, string(value, "evaluation_policy")),
                enumValue(SFMHistoryGraphContract.EffectClass.class, string(value, "effect_class")),
                optionalString(value, "witness_id"),
                enumValue(TransitionStatus.class, string(value, "status")),
                stringList(value, "evidence")
        );
    }

    private static JsonObject writeObservation(Observation observation) {
        JsonObject value = new JsonObject();
        value.addProperty("id", observation.id());
        value.addProperty("sequence", observation.sequence());
        value.addProperty("kind", observation.kind());
        value.addProperty("related_record_id", observation.relatedRecordId());
        value.addProperty("value", observation.value());
        return value;
    }

    private static Observation readObservation(JsonObject value) {
        fields(value, "observation", "id", "sequence", "kind", "related_record_id", "value");
        return new Observation(
                string(value, "id"),
                exactLong(value, "sequence"),
                string(value, "kind"),
                string(value, "related_record_id"),
                string(value, "value")
        );
    }

    private static JsonObject writeHeadMovement(HeadMovement movement) {
        JsonObject value = new JsonObject();
        value.addProperty("id", movement.id());
        value.addProperty("sequence", movement.sequence());
        value.addProperty("kind", wire(movement.kind()));
        value.addProperty("from_state_id", movement.fromStateId());
        value.addProperty("to_state_id", movement.toStateId());
        value.addProperty("actor", movement.actor());
        value.addProperty("request_id", movement.requestId());
        return value;
    }

    private static HeadMovement readHeadMovement(JsonObject value) {
        fields(value, "head movement", "id", "sequence", "kind", "from_state_id", "to_state_id", "actor",
                "request_id");
        return new HeadMovement(
                string(value, "id"),
                exactLong(value, "sequence"),
                enumValue(HeadMovementKind.class, string(value, "kind")),
                string(value, "from_state_id"),
                string(value, "to_state_id"),
                string(value, "actor"),
                string(value, "request_id")
        );
    }

    private static JsonObject writeReplayReport(ReplayReport report) {
        JsonObject value = new JsonObject();
        value.addProperty("id", report.id());
        value.addProperty("sequence", report.sequence());
        value.addProperty("mode", wire(report.mode()));
        value.addProperty("status", wire(report.status()));
        value.addProperty("source_boundary_state_id", report.sourceBoundaryStateId());
        value.addProperty("target_parent_state_id", report.targetParentStateId());
        value.add("source_transition_ids", strings(report.sourceTransitionIds()));
        value.add("resulting_transition_ids", strings(report.resultingTransitionIds()));
        value.add("action_lineage", writeList(report.actionLineage(),
                SFMTemporalReplayArchiveJsonCodec::writeActionLineage));
        addOptionalString(value, "resulting_state_id", report.resultingStateId());
        value.add("diagnostics", strings(report.diagnostics()));
        return value;
    }

    private static ReplayReport readReplayReport(JsonObject value) {
        fields(value, "replay report", "id", "sequence", "mode", "status", "source_boundary_state_id",
                "target_parent_state_id", "source_transition_ids", "resulting_transition_ids", "action_lineage",
                "resulting_state_id", "diagnostics");
        return new ReplayReport(
                string(value, "id"),
                exactLong(value, "sequence"),
                enumValue(ReplayMode.class, string(value, "mode")),
                enumValue(ReplayStatus.class, string(value, "status")),
                string(value, "source_boundary_state_id"),
                string(value, "target_parent_state_id"),
                stringList(value, "source_transition_ids"),
                stringList(value, "resulting_transition_ids"),
                readList(value, "action_lineage", "action lineage",
                        SFMTemporalReplayArchiveJsonCodec::readActionLineage),
                optionalString(value, "resulting_state_id"),
                stringList(value, "diagnostics")
        );
    }

    private static JsonObject writeActionLineage(ActionLineage lineage) {
        JsonObject value = new JsonObject();
        value.addProperty("source_transition_id", lineage.sourceTransitionId());
        addOptionalString(value, "source_witness_id", lineage.sourceWitnessId());
        addOptionalString(value, "resulting_transition_id", lineage.resultingTransitionId());
        addOptionalString(value, "resulting_witness_id", lineage.resultingWitnessId());
        value.addProperty("status", wire(lineage.status()));
        value.add("diagnostics", strings(lineage.diagnostics()));
        return value;
    }

    private static ActionLineage readActionLineage(JsonObject value) {
        fields(value, "action lineage", "source_transition_id", "source_witness_id", "resulting_transition_id",
                "resulting_witness_id", "status", "diagnostics");
        return new ActionLineage(
                string(value, "source_transition_id"),
                optionalString(value, "source_witness_id"),
                optionalString(value, "resulting_transition_id"),
                optionalString(value, "resulting_witness_id"),
                enumValue(ReplayStatus.class, string(value, "status")),
                stringList(value, "diagnostics")
        );
    }

    private static <T> JsonArray writeList(List<T> values, Function<T, JsonObject> writer) {
        JsonArray answer = new JsonArray();
        values.forEach(value -> answer.add(writer.apply(value)));
        return answer;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray answer = new JsonArray();
        values.forEach(answer::add);
        return answer;
    }

    private static JsonElement parseStrict(String text) {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement value = readElement(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Replay archive JSON must contain exactly one value");
            }
            return value;
        } catch (IOException | IllegalStateException | NumberFormatException invalid) {
            throw new IllegalArgumentException("Malformed replay archive JSON", invalid);
        }
    }

    private static JsonElement readElement(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_JSON_NESTING_DEPTH) {
            throw new IllegalArgumentException("Replay archive JSON exceeds the bounded nesting depth");
        }
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> readObject(reader, depth);
            case BEGIN_ARRAY -> readArray(reader, depth);
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IllegalArgumentException("Unexpected JSON token " + reader.peek());
        };
    }

    private static JsonObject readObject(JsonReader reader, int depth) throws IOException {
        reader.beginObject();
        JsonObject answer = new JsonObject();
        int count = 0;
        while (reader.hasNext()) {
            if (++count > SFMTemporalReplayArchive.MAX_RECORDS_PER_KIND) {
                throw new IllegalArgumentException("JSON object exceeds the bounded member count");
            }
            String name = reader.nextName();
            if (answer.has(name)) throw new IllegalArgumentException("Duplicate JSON field: " + name);
            answer.add(name, readElement(reader, depth + 1));
        }
        reader.endObject();
        return answer;
    }

    private static JsonArray readArray(JsonReader reader, int depth) throws IOException {
        reader.beginArray();
        JsonArray answer = new JsonArray();
        while (reader.hasNext()) {
            if (answer.size() >= SFMTemporalReplayArchive.MAX_RECORDS_PER_KIND) {
                throw new IllegalArgumentException("JSON array exceeds the bounded element count");
            }
            answer.add(readElement(reader, depth + 1));
        }
        reader.endArray();
        return answer;
    }

    private static <T> List<T> readList(
            JsonObject owner,
            String key,
            String itemLabel,
            Function<JsonObject, T> reader
    ) {
        ArrayList<T> answer = new ArrayList<>();
        for (JsonElement element : array(owner, key)) {
            answer.add(reader.apply(object(element, itemLabel)));
        }
        return List.copyOf(answer);
    }

    private static List<String> stringList(JsonObject owner, String key) {
        ArrayList<String> answer = new ArrayList<>();
        int index = 0;
        for (JsonElement value : array(owner, key)) {
            answer.add(string(value, key + "[" + index + "]"));
            index++;
        }
        return List.copyOf(answer);
    }

    private static void addOptionalString(JsonObject owner, String key, Optional<String> value) {
        if (value.isPresent()) owner.addProperty(key, value.orElseThrow());
        else owner.add(key, JsonNull.INSTANCE);
    }

    private static void addOptionalInteger(JsonObject owner, String key, Optional<Integer> value) {
        if (value.isPresent()) owner.addProperty(key, value.orElseThrow());
        else owner.add(key, JsonNull.INSTANCE);
    }

    private static Optional<String> optionalString(JsonObject owner, String key) {
        JsonElement value = required(owner, key);
        return value.isJsonNull() ? Optional.empty() : Optional.of(string(value, key));
    }

    private static Optional<Integer> optionalInteger(JsonObject owner, String key) {
        JsonElement value = required(owner, key);
        return value.isJsonNull() ? Optional.empty() : Optional.of(exactInt(value, key));
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject owner, String key) {
        JsonElement value = required(owner, key);
        if (!value.isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        JsonArray answer = value.getAsJsonArray();
        if (answer.size() > SFMTemporalReplayArchive.MAX_RECORDS_PER_KIND) {
            throw new IllegalArgumentException(key + " exceeds the bounded element count");
        }
        return answer;
    }

    private static String string(JsonObject owner, String key) {
        return string(required(owner, key), key);
    }

    private static String string(JsonElement value, String label) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(label + " must be a string");
        }
        return value.getAsString();
    }

    private static boolean bool(JsonObject owner, String key) {
        JsonElement value = required(owner, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static int exactInt(JsonObject owner, String key) {
        return exactInt(required(owner, key), key);
    }

    private static int exactInt(JsonElement value, String label) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(label + " must be an integer");
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new IllegalArgumentException(label + " must be an exact 32-bit integer", invalid);
        }
    }

    private static long exactLong(JsonObject owner, String key) {
        JsonElement value = required(owner, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        try {
            return value.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new IllegalArgumentException(key + " must be an exact 64-bit integer", invalid);
        }
    }

    private static JsonElement required(JsonObject owner, String key) {
        JsonElement value = owner.get(key);
        if (value == null) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private static void fields(JsonObject value, String label, String... allowedNames) {
        Set<String> allowed = new HashSet<>(Arrays.asList(allowedNames));
        for (String present : value.keySet()) {
            if (!allowed.remove(present)) {
                throw new IllegalArgumentException("Unknown " + label + " field: " + present);
            }
        }
        if (!allowed.isEmpty()) {
            throw new IllegalArgumentException("Missing " + label + " fields: " + String.join(", ", allowed));
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String encoded) {
        for (T value : type.getEnumConstants()) {
            if (wire(value).equals(encoded)) return value;
        }
        throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " value: " + encoded);
    }

    private static String wire(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
