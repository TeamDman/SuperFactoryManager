import ca.teamdman.sfm.client.inspection.SFMInspectionTree;
import ca.teamdman.sfm.client.explorer.lazy.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Deterministic malformed-input, allocation-bound and mount-publication checks. No game UI. */
public class QualificationProbe {
    static int checks;
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    static void reject(String json, String expected) {
        try { SFMInspectionTree.parse(json, "rejected"); throw new AssertionError("accepted: " + expected); }
        catch (IllegalArgumentException failure) {
            check(failure.getMessage().toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT)), failure.toString());
        }
    }
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--reject-open")) {
            try { SFMInspectionTree.read(Path.of(args[1]), "special"); throw new AssertionError("Special input accepted"); }
            catch (IllegalArgumentException expected) { System.out.println("PASS special input rejected before blocking read: " + expected.getMessage()); }
            return;
        }
        String json = Files.readString(Path.of(args[0]));
        reject(json.replaceFirst("\\{", "{\"schema\":\"duplicate\","), "duplicate");
        reject("[".repeat(66) + "null" + "]".repeat(66), "nesting");
        reject(json + " {}", "malformed");
        reject("{\"schema\":\"\\uD800\",\"roots\":[]}", "surrogate");
        var document = JsonParser.parseString(json).getAsJsonObject();
        var pair = document.getAsJsonArray("roots").get(0).getAsJsonObject().getAsJsonArray("pairs").get(0).getAsJsonObject();
        pair.getAsJsonArray("representations").get(0).getAsJsonObject().addProperty("filename", "../escape");
        reject(document.toString(), "filename");
        var root = document.getAsJsonArray("roots").get(0).getAsJsonObject();
        var pairs = new JsonArray(); root.add("pairs", pairs);
        var view = new JsonObject();
        view.addProperty("filename", "view.txt"); view.addProperty("label", "view"); view.addProperty("text", "x");
        view.add("surface_json", JsonNull.INSTANCE); view.add("surface_request_json", JsonNull.INSTANCE);
        var views = new JsonArray(); for (int i=0;i<40;i++) views.add(view);
        var densePair = new JsonObject(); densePair.addProperty("path", "a.txt"); densePair.addProperty("classification", "added"); densePair.add("representations", views);
        for (int i=0;i<1000;i++) pairs.add(densePair);
        reject(document.toString(), "node budget");

        var tree = SFMInspectionTree.read(Path.of(args[0]), "prepared").get(0);
        var resolver = new SFMInMemoryTextExplorerResolver(SFMInspectionTree.SCHEME, Runnable::run, 2);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var blocking = new AbstractCollection<SFMInMemoryTextExplorerResolver.Node>() {
            public int size() { return tree.nodes().size(); }
            public Iterator<SFMInMemoryTextExplorerResolver.Node> iterator() {
                entered.countDown();
                try { if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("Preparation deadlock"); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return tree.nodes().iterator();
            }
        };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var preparing = pool.submit(() -> resolver.prepareMount(tree.root().authority(), blocking));
            check(entered.await(2, TimeUnit.SECONDS), "preparation entered");
            check(pool.submit(resolver::generation).get(2, TimeUnit.SECONDS) == 1, "preparation cannot hold resolver monitor");
            release.countDown();
            var prepared = preparing.get(2, TimeUnit.SECONDS);
            check(resolver.root(tree.root().authority()).isEmpty(), "prepared graph not published");
            try (var lease = resolver.mount(prepared)) {
                check(resolver.root(tree.root().authority()).isPresent(), "atomic mount publication");
            }
            check(resolver.root(tree.root().authority()).isEmpty(), "lease cleanup");
        } finally { release.countDown(); pool.shutdownNow(); }
        System.out.println("PASS " + checks + " strict input / aggregate bound / publication assertions");
    }
}
