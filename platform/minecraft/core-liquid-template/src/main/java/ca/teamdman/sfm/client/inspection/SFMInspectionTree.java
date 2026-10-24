package ca.teamdman.sfm.client.inspection;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryTextExplorerResolver;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceJsonCodec;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceV1;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.*;
import java.util.function.Predicate;

/** Comparison → strategy → file pair → representation. No filesystem roots are inferred. */
public record SFMInspectionTree(SFMPath root, List<SFMInMemoryTextExplorerResolver.Node> nodes,
                                Map<SFMPath, Surface> surfaces) {
    public static final String SCHEME = "inspection";
    public record Surface(SFMReleaseReviewSurfaceV1.Request request, SFMReleaseReviewSurfaceV1.Surface surface) {}
    private record Strategy(String id, String label, Predicate<JsonObject> accepts) {}

    public static List<SFMInspectionTree> parse(String json, String authorityPrefix) {
        JsonObject document = JsonParser.parseString(json).getAsJsonObject();
        if (!"sfm:explorer-inspection@1".equals(document.get("schema").getAsString()))
            throw new IllegalArgumentException("Unsupported Explorer inspection schema");
        var roots = document.getAsJsonArray("roots");
        if (roots.isEmpty() || roots.size() > 8) throw new IllegalArgumentException("Expected one to eight comparison roots");
        List<SFMInspectionTree> answer = new ArrayList<>();
        for (int index = 0; index < roots.size(); index++) answer.add(parseRoot(roots.get(index).getAsJsonObject(), authorityPrefix + "-" + index));
        return List.copyOf(answer);
    }
    private static SFMInspectionTree parseRoot(JsonObject input, String authority) {
        SFMPath root = path(authority, List.of());
        var nodes = new ArrayList<SFMInMemoryTextExplorerResolver.Node>();
        var surfaces = new HashMap<SFMPath, Surface>();
        var children = new ArrayList<SFMPath>();
        var pairs = input.getAsJsonArray("pairs");
        if (pairs.size() > 50000) throw new IllegalArgumentException("Comparison pair budget exceeded");
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
        for (Strategy strategy : strategies) {
            SFMPath group = child(root, strategy.id()); children.add(group);
            var matches = new ArrayList<SFMPath>();
            for (int i = 0; i < pairs.size(); i++) {
                JsonObject pair = pairs.get(i).getAsJsonObject();
                if (!strategy.accepts().test(pair)) continue;
                SFMPath pairPath = child(group, String.format(Locale.ROOT, "%06d", i)); matches.add(pairPath);
                var views = new ArrayList<SFMPath>();
                var representations = pair.getAsJsonArray("representations");
                if (representations.size() > 64) throw new IllegalArgumentException("Representation budget exceeded");
                for (int j = 0; j < representations.size(); j++) {
                    JsonObject representation = representations.get(j).getAsJsonObject();
                    String filename = representation.get("filename").getAsString();
                    if (filename.contains("/") || filename.contains("\\") || filename.length() > 255)
                        throw new IllegalArgumentException("Representation filename must be one bounded segment");
                    SFMPath view = child(pairPath, String.format(Locale.ROOT, "%02d-", j) + filename); views.add(view);
                    String text = representation.get("text").getAsString();
                    nodes.add(leaf(view, representation.get("label").getAsString(), text));
                    if (representation.has("surface_json") && !representation.get("surface_json").isJsonNull()) {
                        var request = SFMReleaseReviewSurfaceJsonCodec.decodeRequest(representation.get("surface_request_json").getAsString());
                        var surface = SFMReleaseReviewSurfaceJsonCodec.decodeSurface(representation.get("surface_json").getAsString());
                        surface.validateAgainst(request);
                        if (!text.equals(surface.text())) throw new IllegalArgumentException("Representation text differs from its source map");
                        surfaces.put(view, new Surface(request, surface));
                    }
                }
                nodes.add(directory(pairPath, "[" + classification(pair) + "] " + pair.get("path").getAsString(), views));
            }
            nodes.add(directory(group, strategy.label() + " (" + matches.size() + ")", matches));
        }
        SFMPath provenance = child(root, "09-provenance"); children.add(provenance);
        SFMPath details = child(provenance, "identity.txt");
        nodes.add(leaf(details, "Input identities and limitations", input.get("identity").getAsString() + "\n" + input.get("provenance").getAsString()));
        var evidence = new ArrayList<SFMPath>(List.of(details));
        boolean failed = input.has("error") && !input.get("error").isJsonNull();
        if (failed) {
            SFMPath error = child(provenance, "error.txt"); evidence.add(error);
            nodes.add(leaf(error, "Comparison unavailable · missing or invalid input", input.get("error").getAsString()));
        }
        nodes.add(directory(provenance, failed ? "ERROR · comparison unavailable" : "Provenance and limitations", evidence));
        nodes.add(directory(root, (failed ? "[ERROR] " : "") + input.get("label").getAsString(), children));
        return new SFMInspectionTree(root, List.copyOf(nodes), Map.copyOf(surfaces));
    }
    private static String classification(JsonObject pair) { return pair.get("classification").getAsString(); }
    private static SFMInMemoryTextExplorerResolver.Node directory(SFMPath path, String label, List<SFMPath> children) {
        return SFMInMemoryTextExplorerResolver.Node.directory(SFMExplorerEntry.simple(path, label, true, Optional.of("minecraft:chest")), children);
    }
    private static SFMInMemoryTextExplorerResolver.Node leaf(SFMPath path, String label, String text) {
        return SFMInMemoryTextExplorerResolver.Node.text(SFMExplorerEntry.simple(path, label, false, Optional.of("minecraft:paper")), text);
    }
    private static SFMPath child(SFMPath root, String segment) {
        var segments = new ArrayList<>(root.segments()); segments.add(segment); return path(root.authority(), segments);
    }
    private static SFMPath path(String authority, List<String> segments) {
        return new SFMPath(SFMPath.Kind.CONTRIBUTED, SCHEME, authority, segments, Optional.empty(), false);
    }
}
