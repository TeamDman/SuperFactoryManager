import ca.teamdman.sfm.client.inspection.SFMInspectionTree;
import ca.teamdman.sfm.client.explorer.lazy.*;
import java.nio.file.*;
import java.util.*;

/** Exercises production parser, strategies, paginated resolver and mapped diff text without Minecraft. */
public class InspectionProbe {
    static int checks;
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--workspace")) {
            var release = SFMInspectionTree.parse(Files.readString(Path.of(args[1])), "release");
            check(release.size() == 2, "two release comparison roots");
            for (var root : release) {
                var node = root.nodes().stream().filter(n -> n.entry().path().equals(root.root())).findFirst().orElseThrow();
                check(node.entry().expandable() && node.children().size() == 10, "strategy roots");
                System.out.println(node.entry().label() + " · nodes=" + root.nodes().size() + " · mapped views=" + root.surfaces().size());
            }
            System.out.println("PASS production parser accepted actual release workspace; no UI claim");
            return;
        }
        var trees = SFMInspectionTree.parse(Files.readString(Path.of(args[0])), "fixture");
        check(trees.size() == 2, "two focused roots");
        var resolver = new SFMInMemoryTextExplorerResolver(SFMInspectionTree.SCHEME, Runnable::run, 2);
        var tree = trees.get(0); var lease = resolver.mount(tree.root().authority(), tree.nodes());
        var root = resolver.describe(tree.root(), new SFMExplorerCancellationToken()).join();
        check(root.expandable(), "comparison root expandable");
        var page = resolver.resolveChildren(new SFMExplorerResolver.ChildRequest(tree.root(), Optional.empty(), 2,
                resolver.generation(), new SFMExplorerCancellationToken())).join();
        check(page.entries().size() == 2 && page.continuation().isPresent(), "bounded pagination");
        var instruction = tree.nodes().stream().filter(n -> n.entry().label().startsWith("Instruction changes only")).findFirst().orElseThrow();
        check(instruction.children().size() == 1, "instruction strategy excludes debug/exact/unknown/resources");
        var otherClass = tree.nodes().stream().filter(n -> n.entry().label().startsWith("Other printed class changes")).findFirst().orElseThrow();
        check(otherClass.children().size() == 1, "declaration-only class has a separate strategy");
        var pair = tree.nodes().stream().filter(n -> n.entry().path().equals(instruction.children().get(0))).findFirst().orElseThrow();
        check(pair.entry().label().contains("A.class") && pair.entry().expandable(), "file pair expandable");
        check(pair.children().size() >= 3, "representation children");
        for (var sourceTree : trees) for (var surface : sourceTree.surfaces().values()) {
            surface.surface().validateAgainst(surface.request());
            check(!surface.surface().algorithm().isEmpty(), "real algorithm provenance");
        }
        var firstSurface = tree.surfaces().entrySet().iterator().next();
        var request = new SFMResolverTextRequest(firstSurface.getKey(), tree.root(), Optional.empty(), 8 * 1024 * 1024, resolver.generation(), new SFMExplorerCancellationToken());
        var text = resolver.readText(request).join();
        check(text.status() == SFMResolverTextResult.Status.READY, "representation read");
        check(text.text().orElseThrow().equals(firstSurface.getValue().surface().text()), "rendered diff matches exact mapped bytes");
        lease.close();
        check(resolver.readText(new SFMResolverTextRequest(firstSurface.getKey(), tree.root(), Optional.empty(), 8 * 1024 * 1024, resolver.generation(), new SFMExplorerCancellationToken())).join().status() == SFMResolverTextResult.Status.REMOVED_ROOT, "lease removal");
        var missing = SFMInspectionTree.parse(Files.readString(Path.of(args[1])), "missing");
        check(missing.get(0).nodes().stream().anyMatch(n -> n.entry().label().startsWith("[ERROR]")), "visible missing input root");
        boolean rejected = false;
        try { SFMInspectionTree.parse("{\"schema\":\"wrong\",\"roots\":[]}", "bad"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid schema rejected");
        var tampered = com.google.gson.JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        outer: for (var r : tampered.getAsJsonArray("roots")) for (var p : r.getAsJsonObject().getAsJsonArray("pairs"))
            for (var v : p.getAsJsonObject().getAsJsonArray("representations")) {
                var view = v.getAsJsonObject();
                if (!view.get("surface_json").isJsonNull()) { view.addProperty("text", "tampered"); break outer; }
            }
        rejected = false;
        try { SFMInspectionTree.parse(tampered.toString(), "tampered"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "mismatched representation/source map rejected");
        System.out.println("PASS " + checks + " production tree/resolver/source-map assertions (headless; no UI screenshot claim)");
    }
}
