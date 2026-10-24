package ca.teamdman.sfm.client.inspection;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryTextExplorerResolver;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceJsonCodec;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceV1;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewDiffDecorations;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentDecoration;
import com.google.gson.*;
import com.google.gson.stream.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.*;
import java.util.function.Predicate;

/** Comparison → strategy → file pair → representation. No filesystem roots are inferred. */
public record SFMInspectionTree(SFMPath root, List<SFMInMemoryTextExplorerResolver.Node> nodes,
                                Map<SFMPath, Surface> surfaces) {
    public static final String SCHEME = "inspection";
    public static final int MAX_BYTES = 128 * 1024 * 1024;
    private static final int MAX_NODES = 100_000, MAX_STRING = 8 * 1024 * 1024;
    public record Surface(SFMReleaseReviewSurfaceV1.Request request, SFMReleaseReviewSurfaceV1.Surface surface,
                          List<SFMTextDocumentDecoration> decorations) {
        public Surface(SFMReleaseReviewSurfaceV1.Request request, SFMReleaseReviewSurfaceV1.Surface surface) {
            this(request, surface, SFMReleaseReviewDiffDecorations.create(surface));
        }
    }
    private record Representation(String filename, String label, String text, Surface surface) {}
    private record Pair(String path, String classification, List<Representation> representations) {}
    private static final class Budget { int nodes, jsonValues; long characters; }
    private record Strategy(String id, String label, Predicate<JsonObject> accepts) {}

    /** Reject special files before opening, and detect ordinary concurrent replacement/editing. */
    public static List<SFMInspectionTree> read(java.nio.file.Path path, String authorityPrefix) throws java.io.IOException {
        var before = java.nio.file.Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || before.size() > MAX_BYTES) throw new IllegalArgumentException("Workspace must be a regular file within 128 MiB");
        JsonObject document;
        try (var stream = new java.io.FilterInputStream(java.nio.file.Files.newInputStream(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            long count;
            private void account(int size) throws java.io.IOException {
                if (size > 0 && (count += size) > MAX_BYTES) throw new java.io.IOException("Workspace exceeds 128 MiB");
            }
            @Override public int read() throws java.io.IOException { int value = in.read(); if (value >= 0) account(1); return value; }
            @Override public int read(byte[] bytes, int offset, int length) throws java.io.IOException {
                int size = in.read(bytes, offset, length); account(size); return size;
            }
        }) {
            document = strictJson(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8.newDecoder())).getAsJsonObject();
        }
        var after = java.nio.file.Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile() || after.size() != before.size() || !Objects.equals(before.fileKey(), after.fileKey())
                || !before.lastModifiedTime().equals(after.lastModifiedTime()))
            throw new IllegalArgumentException("Workspace changed during capture or exceeded its budget");
        return parseDocument(document, authorityPrefix);
    }
    public static List<SFMInspectionTree> parse(String json, String authorityPrefix) {
        if (json.length() > MAX_BYTES) throw new IllegalArgumentException("Workspace exceeds input budget");
        return parseDocument(strictJson(json).getAsJsonObject(), authorityPrefix);
    }
    private static List<SFMInspectionTree> parseDocument(JsonObject document, String authorityPrefix) {
        fields(document, "schema", "roots");
        if (!"sfm:explorer-inspection@1".equals(document.get("schema").getAsString()))
            throw new IllegalArgumentException("Unsupported Explorer inspection schema");
        var roots = document.getAsJsonArray("roots");
        if (roots.isEmpty() || roots.size() > 8) throw new IllegalArgumentException("Expected one to eight comparison roots");
        List<SFMInspectionTree> answer = new ArrayList<>();
        Budget budget = new Budget();
        for (int index = 0; index < roots.size(); index++) answer.add(parseRoot(roots.get(index).getAsJsonObject(), authorityPrefix + "-" + index, budget));
        return List.copyOf(answer);
    }
    private static SFMInspectionTree parseRoot(JsonObject input, String authority, Budget budget) {
        fields(input, "label", "identity", "provenance", "error", "pairs");
        SFMPath root = path(authority, List.of());
        var nodes = new ArrayList<SFMInMemoryTextExplorerResolver.Node>();
        var surfaces = new HashMap<SFMPath, Surface>();
        var children = new ArrayList<SFMPath>();
        var pairs = input.getAsJsonArray("pairs");
        if (pairs.size() > 20000) throw new IllegalArgumentException("Comparison pair budget exceeded");
        List<Strategy> strategies = List.of(
            new Strategy("01-changes", "All changes", p -> !classification(p).equals("exact")),
            new Strategy("02-instructions", "Instruction changes only · evidenced printed view", p -> classification(p).equals("instruction-view-changed")),
            new Strategy("03-debug", "Printed debug only · instruction view equal", p -> classification(p).equals("printed-debug-only")),
            new Strategy("03-other-class", "Other printed class changes · instruction bodies equal", p -> classification(p).equals("class-view-changed")),
            new Strategy("04-resources", "Changed resources / source files", p -> !p.get("path").getAsString().endsWith(".class") && !classification(p).equals("exact")),
            new Strategy("05-unknown", "Class changes · unclassified / unrepresented", p -> p.get("path").getAsString().endsWith(".class") && Set.of("changed", "unrepresented-byte-change").contains(classification(p))),
            new Strategy("06-membership", "Added / removed entries", p -> Set.of("added", "removed").contains(classification(p))),
            new Strategy("07-whitespace", "Certified source whitespace", p -> classification(p).equals("source-whitespace")),
            new Strategy("08-exact", "Exact bytes · no semantic claim", p -> classification(p).equals("exact")));
        var parsed = new ArrayList<Pair>();
        budget.nodes += 13;
        // Check aggregate expansion before allocating aliases; decode each surface only once.
        for (var element : pairs) {
            JsonObject pair = element.getAsJsonObject(); fields(pair, "path", "classification", "representations");
            String pairPath = string(pair, "path"), classification = string(pair, "classification");
            if (!Set.of("exact", "changed", "moved", "added", "removed", "allowed-byte-difference", "source-whitespace",
                    "instruction-view-changed", "printed-debug-only", "unrepresented-byte-change", "class-view-changed").contains(classification))
                throw new IllegalArgumentException("Unknown pair classification: " + classification);
            var representations = pair.getAsJsonArray("representations");
            if (representations.size() > 64) throw new IllegalArgumentException("Representation budget exceeded");
            int copies = (int) strategies.stream().filter(s -> s.accepts().test(pair)).count();
            budget.nodes += copies * (1 + representations.size());
            if (budget.nodes > MAX_NODES) throw new IllegalArgumentException("Expanded workspace node budget exceeded");
            var views = new ArrayList<Representation>();
            for (var value : representations) {
                JsonObject representation = value.getAsJsonObject();
                fields(representation, "filename", "label", "text", "surface_json", "surface_request_json");
                String filename = string(representation, "filename"), text = string(representation, "text");
                if (filename.isBlank() || filename.equals(".") || filename.equals("..") || filename.contains("/")
                        || filename.contains("\\") || filename.length() > 252 || filename.indexOf('\0') >= 0)
                    throw new IllegalArgumentException("Representation filename must be one bounded segment");
                budget.characters += text.length();
                Surface mapped = null;
                if (representation.get("surface_json").isJsonNull() != representation.get("surface_request_json").isJsonNull())
                    throw new IllegalArgumentException("Mapped view requires both request and surface");
                if (!representation.get("surface_json").isJsonNull()) {
                    String requestJson = string(representation, "surface_request_json"), surfaceJson = string(representation, "surface_json");
                    budget.characters += requestJson.length() + surfaceJson.length();
                    if (budget.characters > MAX_BYTES) throw new IllegalArgumentException("Workspace text budget exceeded");
                    strictJson(requestJson); strictJson(surfaceJson);
                    var request = SFMReleaseReviewSurfaceJsonCodec.decodeRequest(requestJson);
                    var surface = SFMReleaseReviewSurfaceJsonCodec.decodeSurface(surfaceJson);
                    surface.validateAgainst(request);
                    if (!text.equals(surface.text())) throw new IllegalArgumentException("Representation text differs from its source map");
                    mapped = new Surface(request, surface);
                }
                if (budget.characters > MAX_BYTES) throw new IllegalArgumentException("Workspace text budget exceeded");
                views.add(new Representation(filename, string(representation, "label"), text, mapped));
            }
            parsed.add(new Pair(pairPath, classification, List.copyOf(views)));
        }
        for (Strategy strategy : strategies) {
            SFMPath group = child(root, strategy.id()); children.add(group);
            var matches = new ArrayList<SFMPath>();
            for (int i = 0; i < pairs.size(); i++) {
                JsonObject pair = pairs.get(i).getAsJsonObject();
                if (!strategy.accepts().test(pair)) continue;
                SFMPath pairPath = child(group, String.format(Locale.ROOT, "%06d", i)); matches.add(pairPath);
                var views = new ArrayList<SFMPath>();
                var representations = parsed.get(i).representations();
                for (int j = 0; j < representations.size(); j++) {
                    var representation = representations.get(j);
                    String filename = representation.filename();
                    SFMPath view = child(pairPath, String.format(Locale.ROOT, "%02d-", j) + filename, false); views.add(view);
                    nodes.add(leaf(view, representation.label(), representation.text()));
                    if (representation.surface() != null) surfaces.put(view, representation.surface());
                }
                nodes.add(directory(pairPath, "[" + classification(pair) + "] " + pair.get("path").getAsString(), views));
            }
            nodes.add(directory(group, strategy.label() + " (" + matches.size() + ")", matches));
        }
        SFMPath provenance = child(root, "09-provenance"); children.add(provenance);
        SFMPath details = child(provenance, "identity.txt", false);
        nodes.add(leaf(details, "Input identities and limitations", input.get("identity").getAsString() + "\n" + input.get("provenance").getAsString()));
        var evidence = new ArrayList<SFMPath>(List.of(details));
        boolean failed = input.has("error") && !input.get("error").isJsonNull();
        if (failed) {
            SFMPath error = child(provenance, "error.txt", false); evidence.add(error);
            nodes.add(leaf(error, "Comparison unavailable · missing or invalid input", input.get("error").getAsString()));
        }
        nodes.add(directory(provenance, failed ? "ERROR · comparison unavailable" : "Provenance and limitations", evidence));
        nodes.add(directory(root, (failed ? "[ERROR] " : "") + input.get("label").getAsString(), children));
        return new SFMInspectionTree(root, List.copyOf(nodes), Map.copyOf(surfaces));
    }
    private static String classification(JsonObject pair) { return pair.get("classification").getAsString(); }
    private static String string(JsonObject value, String key) {
        if (!value.get(key).isJsonPrimitive() || !value.get(key).getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string: " + key);
        return value.get(key).getAsString();
    }
    private static void fields(JsonObject value, String... expected) {
        if (!value.keySet().equals(Set.of(expected))) throw new IllegalArgumentException("Unexpected or missing workspace fields");
    }
    private static JsonElement strictJson(String json) {
        return strictJson(new java.io.StringReader(json));
    }
    private static JsonElement strictJson(java.io.Reader source) {
        try (var reader = new JsonReader(source)) {
            reader.setLenient(false);
            var value = readJson(reader, 0, new Budget());
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON content");
            return value;
        } catch (java.io.IOException error) { throw new IllegalArgumentException("Malformed workspace JSON", error); }
    }
    private static JsonElement readJson(JsonReader reader, int depth, Budget budget) throws java.io.IOException {
        if (depth > 64) throw new IllegalArgumentException("JSON nesting budget exceeded");
        if (++budget.jsonValues > 500000) throw new IllegalArgumentException("JSON value budget exceeded");
        switch (reader.peek()) {
            case BEGIN_OBJECT: {
                var object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (key.length() > 256 || object.has(key)) throw new IllegalArgumentException("Invalid or duplicate JSON key");
                    object.add(key, readJson(reader, depth + 1, budget));
                }
                reader.endObject(); return object;
            }
            case BEGIN_ARRAY: {
                var array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) {
                    if (array.size() >= 262144) throw new IllegalArgumentException("JSON array budget exceeded");
                    array.add(readJson(reader, depth + 1, budget));
                }
                reader.endArray(); return array;
            }
            case STRING: {
                String value = reader.nextString();
                budget.characters += value.length();
                if (budget.characters > MAX_BYTES) throw new IllegalArgumentException("JSON text budget exceeded");
                if (value.length() > MAX_STRING) throw new IllegalArgumentException("Workspace string budget exceeded");
                for (int i = 0; i < value.length(); i++) if (Character.isSurrogate(value.charAt(i))) {
                    if (!Character.isHighSurrogate(value.charAt(i)) || i + 1 == value.length()
                            || !Character.isLowSurrogate(value.charAt(++i))) throw new IllegalArgumentException("Unpaired JSON surrogate");
                }
                return new JsonPrimitive(value);
            }
            case NUMBER: {
                String number = reader.nextString();
                if (number.length() > 64) throw new IllegalArgumentException("JSON number budget exceeded");
                return new JsonPrimitive(new java.math.BigDecimal(number));
            }
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw new IllegalArgumentException("Malformed workspace JSON token");
        }
    }
    private static SFMInMemoryTextExplorerResolver.Node directory(SFMPath path, String label, List<SFMPath> children) {
        return SFMInMemoryTextExplorerResolver.Node.directory(SFMExplorerEntry.simple(path, label, true, Optional.of("minecraft:chest")), children);
    }
    private static SFMInMemoryTextExplorerResolver.Node leaf(SFMPath path, String label, String text) {
        return SFMInMemoryTextExplorerResolver.Node.text(SFMExplorerEntry.simple(path, label, false, Optional.of("minecraft:paper")), text);
    }
    private static SFMPath child(SFMPath root, String segment) {
        return child(root, segment, true);
    }
    private static SFMPath child(SFMPath root, String segment, boolean container) {
        var segments = new ArrayList<>(root.segments()); segments.add(segment);
        return new SFMPath(SFMPath.Kind.CONTRIBUTED, SCHEME, root.authority(), segments, Optional.empty(), container);
    }
    private static SFMPath path(String authority, List<String> segments) {
        return new SFMPath(SFMPath.Kind.CONTRIBUTED, SCHEME, authority, segments, Optional.empty(), true);
    }
}
