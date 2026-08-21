package ca.teamdman.sfm.client.explorer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/** Strict deterministic JSON interchange for one session selection undo tree. */
public final class SFMSelectionArchiveJsonCodec {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private SFMSelectionArchiveJsonCodec() {
    }

    public static String write(SFMSelectionRepository.Archive archive) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", archive.schema());
        root.addProperty("generation", archive.generation());
        JsonArray selections = new JsonArray();
        archive.selections().forEach(selection -> selections.add(writeSelection(selection)));
        root.add("selections", selections);
        JsonArray revisions = new JsonArray();
        archive.revisions().forEach(revision -> revisions.add(writeRevision(revision)));
        root.add("revisions", revisions);
        JsonArray events = new JsonArray();
        archive.headEvents().forEach(event -> events.add(writeEvent(event)));
        root.add("head_events", events);
        return GSON.toJson(root) + "\n";
    }

    public static SFMSelectionRepository.Archive read(String text) {
        JsonObject root = object(JsonParser.parseString(text), "archive");
        ArrayList<SFMSelection> selections = new ArrayList<>();
        for (JsonElement value : array(root, "selections")) {
            selections.add(readSelection(object(value, "selection")));
        }
        ArrayList<SFMSelectionRevision> revisions = new ArrayList<>();
        for (JsonElement value : array(root, "revisions")) {
            revisions.add(readRevision(object(value, "revision")));
        }
        ArrayList<SFMSelectionHeadEvent> events = new ArrayList<>();
        for (JsonElement value : array(root, "head_events")) {
            events.add(readEvent(object(value, "head event")));
        }
        return new SFMSelectionRepository.Archive(
                string(root, "schema"),
                nonNegativeLong(root, "generation"),
                selections,
                revisions,
                events
        );
    }

    private static JsonObject writeSelection(SFMSelection selection) {
        JsonObject value = new JsonObject();
        value.addProperty("id", selection.id().value());
        addOptional(value, "name", selection.name());
        value.addProperty("lifetime", wire(selection.lifetime()));
        value.addProperty("head_revision_id", selection.headRevisionId());
        JsonObject named = new JsonObject();
        selection.namedHeadRevisionIds().forEach(named::addProperty);
        value.add("named_head_revision_ids", named);
        JsonArray preferred = new JsonArray();
        selection.preferredChildRevisionIds().forEach((parent, child) -> {
            JsonObject edge = new JsonObject();
            edge.addProperty("parent_revision_id", parent);
            edge.addProperty("child_revision_id", child);
            preferred.add(edge);
        });
        value.add("preferred_child_revision_ids", preferred);
        return value;
    }

    private static SFMSelection readSelection(JsonObject value) {
        TreeMap<String, Long> named = new TreeMap<>();
        object(value.get("named_head_revision_ids"), "named heads").entrySet()
                .forEach(entry -> named.put(entry.getKey(), positiveLong(entry.getValue(), entry.getKey())));
        TreeMap<Long, Long> preferred = new TreeMap<>();
        for (JsonElement element : array(value, "preferred_child_revision_ids")) {
            JsonObject edge = object(element, "preferred child");
            long parent = positiveLong(edge, "parent_revision_id");
            long child = positiveLong(edge, "child_revision_id");
            if (preferred.put(parent, child) != null) {
                throw new IllegalArgumentException("Duplicate preferred child parent: " + parent);
            }
        }
        return new SFMSelection(
                new SFMSelectionId(string(value, "id")),
                optionalString(value, "name"),
                enumValue(SFMSelection.Lifetime.class, string(value, "lifetime")),
                positiveLong(value, "head_revision_id"),
                named,
                preferred
        );
    }

    private static JsonObject writeRevision(SFMSelectionRevision revision) {
        JsonObject value = new JsonObject();
        value.addProperty("id", revision.id());
        value.addProperty("selection_id", revision.selectionId().value());
        value.add("parent_revision_ids", longs(revision.parentRevisionIds()));
        JsonArray members = new JsonArray();
        revision.members().forEach(path -> members.add(path.canonical()));
        value.add("members", members);
        JsonObject operation = new JsonObject();
        operation.addProperty("kind", wire(revision.operation().kind()));
        JsonArray sources = new JsonArray();
        revision.operation().sourceSelections().forEach(source -> sources.add(source.value()));
        operation.add("source_selection_ids", sources);
        JsonArray operands = new JsonArray();
        revision.operation().operandPaths().forEach(path -> operands.add(path.canonical()));
        operation.add("operand_paths", operands);
        value.add("operation", operation);
        value.addProperty("actor", revision.actor());
        value.addProperty("request_id", revision.requestId());
        value.addProperty("created_at", revision.createdAt().toString());
        return value;
    }

    private static SFMSelectionRevision readRevision(JsonObject value) {
        JsonObject operation = object(value.get("operation"), "operation");
        ArrayList<SFMSelectionId> sources = new ArrayList<>();
        for (JsonElement source : array(operation, "source_selection_ids")) {
            sources.add(new SFMSelectionId(source.getAsString()));
        }
        TreeSet<SFMPath> operands = paths(array(operation, "operand_paths"));
        return new SFMSelectionRevision(
                positiveLong(value, "id"),
                new SFMSelectionId(string(value, "selection_id")),
                longValues(array(value, "parent_revision_ids")),
                paths(array(value, "members")),
                new SFMSelectionRevision.Operation(
                        enumValue(SFMSelectionRevision.OperationKind.class, string(operation, "kind")),
                        sources,
                        operands
                ),
                string(value, "actor"),
                string(value, "request_id"),
                Instant.parse(string(value, "created_at"))
        );
    }

    private static JsonObject writeEvent(SFMSelectionHeadEvent event) {
        JsonObject value = new JsonObject();
        value.addProperty("id", event.id());
        value.addProperty("selection_id", event.selectionId().value());
        value.addProperty("from_revision_id", event.fromRevisionId());
        value.addProperty("to_revision_id", event.toRevisionId());
        value.addProperty("kind", wire(event.kind()));
        addOptional(value, "head_name", event.headName());
        value.addProperty("actor", event.actor());
        value.addProperty("request_id", event.requestId());
        value.addProperty("created_at", event.createdAt().toString());
        return value;
    }

    private static SFMSelectionHeadEvent readEvent(JsonObject value) {
        return new SFMSelectionHeadEvent(
                positiveLong(value, "id"),
                new SFMSelectionId(string(value, "selection_id")),
                positiveLong(value, "from_revision_id"),
                positiveLong(value, "to_revision_id"),
                enumValue(SFMSelectionHeadEvent.Kind.class, string(value, "kind")),
                optionalString(value, "head_name"),
                string(value, "actor"),
                string(value, "request_id"),
                Instant.parse(string(value, "created_at"))
        );
    }

    private static JsonArray longs(List<Long> values) {
        JsonArray answer = new JsonArray();
        values.forEach(answer::add);
        return answer;
    }

    private static List<Long> longValues(JsonArray values) {
        ArrayList<Long> answer = new ArrayList<>();
        for (JsonElement value : values) answer.add(positiveLong(value, "revision id"));
        return List.copyOf(answer);
    }

    private static TreeSet<SFMPath> paths(JsonArray values) {
        TreeSet<SFMPath> answer = new TreeSet<>();
        for (JsonElement value : values) answer.add(SFMPath.parse(value.getAsString()));
        return answer;
    }

    private static void addOptional(JsonObject owner, String key, Optional<String> value) {
        if (value.isPresent()) owner.addProperty(key, value.orElseThrow());
        else owner.add(key, com.google.gson.JsonNull.INSTANCE);
    }

    private static Optional<String> optionalString(JsonObject owner, String key) {
        JsonElement value = owner.get(key);
        if (value == null) throw new IllegalArgumentException("Missing " + key);
        return value.isJsonNull() ? Optional.empty() : Optional.of(value.getAsString());
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(label + " must be an object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject owner, String key) {
        JsonElement value = owner.get(key);
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        return value.getAsJsonArray();
    }

    private static String string(JsonObject owner, String key) {
        JsonElement value = owner.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return value.getAsString();
    }

    private static long positiveLong(JsonObject owner, String key) {
        return positiveLong(owner.get(key), key);
    }

    private static long positiveLong(JsonElement value, String label) {
        long answer = exactLong(value, label);
        if (answer <= 0) throw new IllegalArgumentException(label + " must be positive");
        return answer;
    }

    private static long nonNegativeLong(JsonObject owner, String key) {
        long answer = exactLong(owner.get(key), key);
        if (answer < 0) throw new IllegalArgumentException(key + " must not be negative");
        return answer;
    }

    private static long exactLong(JsonElement value, String label) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(label + " must be an integer");
        }
        try {
            return value.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException(label + " must be an exact 64-bit integer", invalid);
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String wire) {
        try {
            return Enum.valueOf(type, wire.replace('-', '_').toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " value: " + wire, invalid);
        }
    }

    private static String wire(Enum<?> value) {
        return value.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
