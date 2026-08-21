package ca.teamdman.sfm.client.history.workspace;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.Artifact;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.Barrier;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.HeadMovement;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.NarrativeKind;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.Operation;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.WorkspaceDocument;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.WorkspaceFrame;
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

/** Strict deterministic JSON and readable transcript codec for {@code sfm.workspace-counterfactual/1}. */
public final class SFMWorkspaceCounterfactualJsonCodec {
    static final int MAX_JSON_NESTING_DEPTH = 128;

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private SFMWorkspaceCounterfactualJsonCodec() {
    }

    public static String write(Artifact artifact) {
        Objects.requireNonNull(artifact, "artifact");
        JsonObject root = new JsonObject();
        root.addProperty("schema", artifact.schema());
        root.addProperty("episode_id", artifact.episodeId());
        root.addProperty("generation", artifact.generation());
        root.addProperty("current_state_id", artifact.currentStateId());
        root.addProperty("ambient_before_hash", artifact.ambientBeforeHash());
        root.addProperty("ambient_after_hash", artifact.ambientAfterHash());
        root.add("workspace_frames", writeList(
                artifact.workspaceFrames(),
                SFMWorkspaceCounterfactualJsonCodec::writeWorkspaceFrame
        ));
        root.add("operations", writeList(artifact.operations(), SFMWorkspaceCounterfactualJsonCodec::writeOperation));
        root.add("head_movements", writeList(
                artifact.headMovements(),
                SFMWorkspaceCounterfactualJsonCodec::writeHeadMovement
        ));
        root.add("barriers", writeList(artifact.barriers(), SFMWorkspaceCounterfactualJsonCodec::writeBarrier));
        return GSON.toJson(root) + "\n";
    }

    public static Artifact read(String text) {
        Objects.requireNonNull(text, "text");
        if (SFMWorkspaceCounterfactualContract.utf8Length(text)
                > SFMWorkspaceCounterfactualContract.MAX_JSON_UTF8_BYTES) {
            throw new IllegalArgumentException("Workspace counterfactual JSON exceeds the bounded UTF-8 size");
        }
        JsonObject root = object(parseStrict(text), "workspace counterfactual artifact");
        fields(
                root,
                "workspace counterfactual artifact",
                "schema",
                "episode_id",
                "generation",
                "current_state_id",
                "ambient_before_hash",
                "ambient_after_hash",
                "workspace_frames",
                "operations",
                "head_movements",
                "barriers"
        );
        return new Artifact(
                string(root, "schema"),
                string(root, "episode_id"),
                exactLong(root, "generation"),
                string(root, "current_state_id"),
                string(root, "ambient_before_hash"),
                string(root, "ambient_after_hash"),
                readList(root, "workspace_frames", "workspace frame",
                        SFMWorkspaceCounterfactualJsonCodec::readWorkspaceFrame),
                readList(root, "operations", "operation", SFMWorkspaceCounterfactualJsonCodec::readOperation),
                readList(root, "head_movements", "head movement",
                        SFMWorkspaceCounterfactualJsonCodec::readHeadMovement),
                readList(root, "barriers", "barrier", SFMWorkspaceCounterfactualJsonCodec::readBarrier)
        );
    }

    /** A deterministic UTF-8-friendly projection intended for people and artifact inspection, not reparsing. */
    public static String transcript(Artifact artifact) {
        Objects.requireNonNull(artifact, "artifact");
        StringBuilder answer = new StringBuilder();
        line(answer, "Workspace counterfactual " + artifact.episodeId());
        line(answer, "schema: " + artifact.schema());
        line(answer, "generation: " + artifact.generation());
        line(answer, "current-state: " + artifact.currentStateId());
        line(answer, "ambient-before: " + artifact.ambientBeforeHash());
        line(answer, "ambient-after: " + artifact.ambientAfterHash() + " (unchanged)");
        line(answer, "");
        line(answer, "Workspace frames (" + artifact.workspaceFrames().size() + "):");
        for (WorkspaceFrame frame : artifact.workspaceFrames()) {
            line(answer, "- " + frame.id() + " parent=" + optional(frame.parentId()) + " hash=" + frame.stateHash());
            line(answer, "  layout=" + frame.panelLayoutId() + " focused=" + frame.focusedPanelId());
            line(answer, "  explorer=" + quoted(frame.explorerLocation()));
            line(answer, "  selected=" + optionalQuoted(frame.selectedPath())
                    + " open-document=" + optionalQuoted(frame.openDocumentPath()));
            line(answer, "  documents (" + frame.documents().size() + "):");
            for (WorkspaceDocument document : frame.documents()) {
                line(answer, "    - " + document.logicalPath() + " " + document.textHash());
                line(answer, "      full-text=" + quoted(document.text()));
            }
        }
        line(answer, "");
        line(answer, "Operations (" + artifact.operations().size() + "):");
        for (Operation operation : artifact.operations()) {
            line(answer, "- " + operation.id() + " action=" + operation.actionId()
                    + " narrative=" + wire(operation.narrativeKind()));
            line(answer, "  parent=" + operation.parentStateId()
                    + " result=" + optional(operation.resultStateId())
                    + " policy=" + wire(operation.evaluationPolicy()));
            line(answer, "  effect=" + wire(operation.effectClass())
                    + " outcome=" + wire(operation.outcomeStatus()));
            line(answer, "  query=" + quoted(operation.query()) + " arguments=" + quotedList(operation.arguments()));
            line(answer, "  witnesses=" + operation.dependencyWitnesses());
            line(answer, "  compatible-suffix=" + operation.compatibleSuffixActionIds());
            line(answer, "  rejected-suffix=" + operation.rejectedSuffixActionIds());
            line(answer, "  diagnostics=" + operation.diagnostics());
        }
        line(answer, "");
        line(answer, "Head movements (" + artifact.headMovements().size() + "):");
        for (HeadMovement movement : artifact.headMovements()) {
            line(answer, "- " + movement.id() + " " + wire(movement.kind()) + " "
                    + movement.fromStateId() + " -> " + movement.toStateId()
                    + " narrative=" + wire(movement.narrativeKind())
                    + " label=" + quoted(movement.label()));
        }
        line(answer, "");
        line(answer, "Barriers (" + artifact.barriers().size() + "):");
        for (Barrier barrier : artifact.barriers()) {
            line(answer, "- " + barrier.id() + " parent=" + barrier.parentStateId()
                    + " action=" + barrier.actionId()
                    + " effect=" + wire(barrier.effectClass())
                    + " outcome=" + wire(barrier.outcomeStatus()));
            line(answer, "  reason=" + quoted(barrier.reason()));
        }
        return answer.toString();
    }

    private static JsonObject writeWorkspaceFrame(WorkspaceFrame frame) {
        JsonObject value = new JsonObject();
        value.addProperty("id", frame.id());
        addOptionalString(value, "parent_id", frame.parentId());
        value.addProperty("panel_layout_id", frame.panelLayoutId());
        value.addProperty("focused_panel_id", frame.focusedPanelId());
        value.addProperty("explorer_location", frame.explorerLocation());
        addOptionalString(value, "selected_path", frame.selectedPath());
        addOptionalString(value, "open_document_path", frame.openDocumentPath());
        value.add("documents", writeList(frame.documents(), SFMWorkspaceCounterfactualJsonCodec::writeDocument));
        value.addProperty("state_hash", frame.stateHash());
        return value;
    }

    private static WorkspaceFrame readWorkspaceFrame(JsonObject value) {
        fields(value, "workspace frame", "id", "parent_id", "panel_layout_id", "focused_panel_id",
                "explorer_location", "selected_path", "open_document_path", "documents", "state_hash");
        return new WorkspaceFrame(
                string(value, "id"),
                optionalString(value, "parent_id"),
                string(value, "panel_layout_id"),
                string(value, "focused_panel_id"),
                string(value, "explorer_location"),
                optionalString(value, "selected_path"),
                optionalString(value, "open_document_path"),
                readList(value, "documents", "workspace document", SFMWorkspaceCounterfactualJsonCodec::readDocument),
                string(value, "state_hash")
        );
    }

    private static JsonObject writeDocument(WorkspaceDocument document) {
        JsonObject value = new JsonObject();
        value.addProperty("logical_path", document.logicalPath());
        value.addProperty("text", document.text());
        value.addProperty("text_hash", document.textHash());
        return value;
    }

    private static WorkspaceDocument readDocument(JsonObject value) {
        fields(value, "workspace document", "logical_path", "text", "text_hash");
        return new WorkspaceDocument(
                string(value, "logical_path"),
                string(value, "text"),
                string(value, "text_hash")
        );
    }

    private static JsonObject writeOperation(Operation operation) {
        JsonObject value = new JsonObject();
        value.addProperty("id", operation.id());
        value.addProperty("action_id", operation.actionId());
        value.addProperty("parent_state_id", operation.parentStateId());
        addOptionalString(value, "result_state_id", operation.resultStateId());
        value.addProperty("evaluation_policy", wire(operation.evaluationPolicy()));
        value.addProperty("effect_class", wire(operation.effectClass()));
        value.addProperty("outcome_status", wire(operation.outcomeStatus()));
        value.addProperty("query", operation.query());
        value.add("arguments", strings(operation.arguments()));
        value.add("dependency_witnesses", writeList(
                operation.dependencyWitnesses(),
                SFMWorkspaceCounterfactualJsonCodec::writeDependencyWitness
        ));
        value.add("compatible_suffix_action_ids", strings(operation.compatibleSuffixActionIds()));
        value.add("rejected_suffix_action_ids", strings(operation.rejectedSuffixActionIds()));
        value.addProperty("narrative_kind", wire(operation.narrativeKind()));
        value.add("diagnostics", strings(operation.diagnostics()));
        return value;
    }

    private static Operation readOperation(JsonObject value) {
        fields(value, "operation", "id", "action_id", "parent_state_id", "result_state_id",
                "evaluation_policy", "effect_class", "outcome_status", "query", "arguments",
                "dependency_witnesses", "compatible_suffix_action_ids", "rejected_suffix_action_ids",
                "narrative_kind", "diagnostics");
        return new Operation(
                string(value, "id"),
                string(value, "action_id"),
                string(value, "parent_state_id"),
                optionalString(value, "result_state_id"),
                enumValue(SFMHistoryGraphContract.EvaluationPolicy.class, string(value, "evaluation_policy")),
                enumValue(SFMHistoryGraphContract.EffectClass.class, string(value, "effect_class")),
                enumValue(SFMHistoryGraphContract.OutcomeStatus.class, string(value, "outcome_status")),
                string(value, "query"),
                stringList(value, "arguments"),
                readList(value, "dependency_witnesses", "dependency witness",
                        SFMWorkspaceCounterfactualJsonCodec::readDependencyWitness),
                stringList(value, "compatible_suffix_action_ids"),
                stringList(value, "rejected_suffix_action_ids"),
                enumValue(NarrativeKind.class, string(value, "narrative_kind")),
                stringList(value, "diagnostics")
        );
    }

    private static JsonObject writeDependencyWitness(SFMHistoryGraphContract.DependencyWitness witness) {
        JsonObject value = new JsonObject();
        value.addProperty("kind", witness.kind());
        value.addProperty("identity", witness.identity());
        value.addProperty("revision", witness.revision());
        return value;
    }

    private static SFMHistoryGraphContract.DependencyWitness readDependencyWitness(JsonObject value) {
        fields(value, "dependency witness", "kind", "identity", "revision");
        return new SFMHistoryGraphContract.DependencyWitness(
                string(value, "kind"),
                string(value, "identity"),
                string(value, "revision")
        );
    }

    private static JsonObject writeHeadMovement(HeadMovement movement) {
        JsonObject value = new JsonObject();
        value.addProperty("id", movement.id());
        value.addProperty("kind", wire(movement.kind()));
        value.addProperty("label", movement.label());
        value.addProperty("from_state_id", movement.fromStateId());
        value.addProperty("to_state_id", movement.toStateId());
        value.addProperty("narrative_kind", wire(movement.narrativeKind()));
        return value;
    }

    private static HeadMovement readHeadMovement(JsonObject value) {
        fields(value, "head movement", "id", "kind", "label", "from_state_id", "to_state_id",
                "narrative_kind");
        return new HeadMovement(
                string(value, "id"),
                enumValue(SFMHistoryGraphContract.HeadMovementKind.class, string(value, "kind")),
                string(value, "label"),
                string(value, "from_state_id"),
                string(value, "to_state_id"),
                enumValue(NarrativeKind.class, string(value, "narrative_kind"))
        );
    }

    private static JsonObject writeBarrier(Barrier barrier) {
        JsonObject value = new JsonObject();
        value.addProperty("id", barrier.id());
        value.addProperty("parent_state_id", barrier.parentStateId());
        value.addProperty("action_id", barrier.actionId());
        value.addProperty("effect_class", wire(barrier.effectClass()));
        value.addProperty("outcome_status", wire(barrier.outcomeStatus()));
        value.addProperty("reason", barrier.reason());
        return value;
    }

    private static Barrier readBarrier(JsonObject value) {
        fields(value, "barrier", "id", "parent_state_id", "action_id", "effect_class", "outcome_status",
                "reason");
        return new Barrier(
                string(value, "id"),
                string(value, "parent_state_id"),
                string(value, "action_id"),
                enumValue(SFMHistoryGraphContract.EffectClass.class, string(value, "effect_class")),
                enumValue(SFMHistoryGraphContract.OutcomeStatus.class, string(value, "outcome_status")),
                string(value, "reason")
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
                throw new IllegalArgumentException("Workspace counterfactual JSON must contain exactly one value");
            }
            return value;
        } catch (IOException | IllegalStateException | NumberFormatException invalid) {
            throw new IllegalArgumentException("Malformed workspace counterfactual JSON", invalid);
        }
    }

    private static JsonElement readElement(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_JSON_NESTING_DEPTH) {
            throw new IllegalArgumentException("Workspace counterfactual JSON exceeds the bounded nesting depth");
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
            if (++count > SFMWorkspaceCounterfactualContract.MAX_RECORDS_PER_KIND) {
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
            if (answer.size() >= SFMWorkspaceCounterfactualContract.MAX_RECORDS_PER_KIND) {
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

    private static Optional<String> optionalString(JsonObject owner, String key) {
        JsonElement value = required(owner, key);
        return value.isJsonNull() ? Optional.empty() : Optional.of(string(value, key));
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(label + " must be an object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject owner, String key) {
        JsonElement value = required(owner, key);
        if (!value.isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        JsonArray answer = value.getAsJsonArray();
        if (answer.size() > SFMWorkspaceCounterfactualContract.MAX_RECORDS_PER_KIND) {
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
            if (!allowed.remove(present)) throw new IllegalArgumentException("Unknown " + label + " field: " + present);
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

    private static String quoted(String value) {
        return GSON.toJson(new JsonPrimitive(value));
    }

    private static String quotedList(List<String> values) {
        return GSON.toJson(strings(values));
    }

    private static String optional(Optional<String> value) {
        return value.orElse("<none>");
    }

    private static String optionalQuoted(Optional<String> value) {
        return value.map(SFMWorkspaceCounterfactualJsonCodec::quoted).orElse("<none>");
    }

    private static void line(StringBuilder target, String value) {
        target.append(value).append('\n');
    }
}
