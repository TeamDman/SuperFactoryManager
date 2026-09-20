package ca.teamdman.sfm.client.review.release_review;

import com.google.gson.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Strict source-evidence codec, shared by standalone golden tests and the release envelope. */
public final class SFMWorkingTreeCaptureV1Codec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private SFMWorkingTreeCaptureV1Codec() {}

    public static SFMWorkingTreeCaptureV1 parse(String json) {
        return readObject(JsonParser.parseString(json).getAsJsonObject());
    }

    public static String write(SFMWorkingTreeCaptureV1 capture) {
        return GSON.toJson(writeObject(capture)) + "\n";
    }

    static SFMWorkingTreeCaptureV1 readObject(JsonObject value) {
        fields(value, Set.of("schema", "id", "observed_head_commit", "observed_head_tree",
                "captured_at_unix_ms", "consistency", "scope_paths", "excluded_paths", "include_untracked", "entries"));
        List<SFMWorkingTreeCaptureV1.Entry> entries = new ArrayList<>();
        for (var element : value.getAsJsonArray("entries")) {
            var entry = element.getAsJsonObject();
            fields(entry, Set.of("path", "kind", "tracked", "byte_length", "sha256", "executable",
                    "materialization", "document_revision_id", "diagnostic"));
            entries.add(new SFMWorkingTreeCaptureV1.Entry(text(entry, "path"), text(entry, "kind"),
                    bool(entry, "tracked"), optionalNumber(entry, "byte_length"), optionalText(entry, "sha256"),
                    bool(entry, "executable"), text(entry, "materialization"),
                    optionalText(entry, "document_revision_id"), optionalText(entry, "diagnostic")));
        }
        return new SFMWorkingTreeCaptureV1(text(value, "schema"), text(value, "id"),
                text(value, "observed_head_commit"), text(value, "observed_head_tree"),
                number(value, "captured_at_unix_ms"), text(value, "consistency"),
                strings(value, "scope_paths"), strings(value, "excluded_paths"), bool(value, "include_untracked"), entries);
    }

    static JsonObject writeObject(SFMWorkingTreeCaptureV1 capture) {
        var value = new JsonObject();
        value.addProperty("schema", capture.schema());
        value.addProperty("id", capture.id());
        value.addProperty("observed_head_commit", capture.observedHeadCommit());
        value.addProperty("observed_head_tree", capture.observedHeadTree());
        value.addProperty("captured_at_unix_ms", capture.capturedAtUnixMs());
        value.addProperty("consistency", capture.consistency());
        value.add("scope_paths", strings(capture.scopePaths()));
        value.add("excluded_paths", strings(capture.excludedPaths()));
        value.addProperty("include_untracked", capture.includeUntracked());
        var entries = new JsonArray();
        for (var entry : capture.entries()) {
            var item = new JsonObject();
            item.addProperty("path", entry.path());
            item.addProperty("kind", entry.kind());
            item.addProperty("tracked", entry.tracked());
            entry.byteLength().ifPresent(length -> item.addProperty("byte_length", length));
            entry.sha256().ifPresent(hash -> item.addProperty("sha256", hash));
            item.addProperty("executable", entry.executable());
            item.addProperty("materialization", entry.materialization());
            entry.documentRevisionId().ifPresent(id -> item.addProperty("document_revision_id", id));
            entry.diagnostic().ifPresent(diagnostic -> item.addProperty("diagnostic", diagnostic));
            entries.add(item);
        }
        value.add("entries", entries);
        return value;
    }

    private static void fields(JsonObject value, Set<String> allowed) {
        for (String key : value.keySet()) if (!allowed.contains(key))
            throw new IllegalArgumentException("Unknown working-tree capture field: " + key);
    }

    private static String text(JsonObject value, String key) {
        var item = value.get(key);
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected capture string: " + key);
        return item.getAsString();
    }

    private static long number(JsonObject value, String key) {
        var item = value.get(key);
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber()
                || !item.getAsString().matches("0|[1-9][0-9]*"))
            throw new IllegalArgumentException("Expected nonnegative capture integer: " + key);
        return Long.parseLong(item.getAsString());
    }

    private static boolean bool(JsonObject value, String key) {
        var item = value.get(key);
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Expected capture Boolean: " + key);
        return item.getAsBoolean();
    }

    private static Optional<String> optionalText(JsonObject value, String key) {
        return value.has(key) && !value.get(key).isJsonNull() ? Optional.of(text(value, key)) : Optional.empty();
    }

    private static Optional<Long> optionalNumber(JsonObject value, String key) {
        return value.has(key) && !value.get(key).isJsonNull() ? Optional.of(number(value, key)) : Optional.empty();
    }

    private static List<String> strings(JsonObject value, String key) {
        List<String> result = new ArrayList<>();
        for (var item : value.getAsJsonArray(key)) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Expected capture string array: " + key);
            result.add(item.getAsString());
        }
        return result;
    }

    private static JsonArray strings(List<String> strings) {
        var values = new JsonArray();
        strings.forEach(values::add);
        return values;
    }
}
