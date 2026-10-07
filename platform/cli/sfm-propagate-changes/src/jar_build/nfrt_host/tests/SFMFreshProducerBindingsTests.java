package ca.teamdman.sfm.toolchain.nfrt;

import net.neoforged.neoform.runtime.artifacts.ClasspathItem;
import net.neoforged.neoform.runtime.graph.ExecutionNode;
import net.neoforged.neoform.runtime.graph.NodeOutput;
import net.neoforged.neoform.runtime.graph.NodeOutputType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.neoform.runtime.actions.DownloadFromVersionManifestAction;
import java.util.concurrent.atomic.AtomicInteger;

/** Pure graph boundary regression: no real tools, files or native execution. */
public final class SFMFreshProducerBindingsTests {
    public static void main(String[] args) throws Exception {
        Path workspace = Path.of("fixture/work/producer").toAbsolutePath().normalize();
        var node = node();
        var calls = new AtomicInteger();
        var bindings = new SFMFreshProducerBindings(List.of(node), Map.of("producer", workspace), requests -> {
            calls.incrementAndGet();
            return requests.stream().map(request -> new SFMFreshProducerBindings.Snapshot(
                    request, workspace.resolve("sealed.bin"), 3, "0".repeat(64))).toList();
        });
        var item = new ClasspathItem.NodeOutputItem(node.getRequiredOutput("output"));
        refuse(() -> bindings.preflight(List.of(item)));
        if (calls.get() != 0) throw new AssertionError("Incomplete producer invoked the sealer");
        node.start();
        node.complete(Map.of("output", workspace.resolve("output.jar")), false);
        var requests = bindings.preflight(List.of(item, item));
        if (requests.size() != 2 || !requests.get(0).equals(requests.get(1))) {
            throw new AssertionError("Duplicate consumer order changed");
        }
        if (bindings.seal(requests).size() != 2 || calls.get() != 1) throw new AssertionError("Wrong sealer batch");
        var foreign = node();
        foreign.start();
        foreign.complete(Map.of("output", workspace.resolve("output.jar")), false);
        refuse(() -> bindings.preflight(List.of(item, new ClasspathItem.NodeOutputItem(foreign.getRequiredOutput("output")))));
        if (calls.get() != 1) throw new AssertionError("Foreign batch invoked the sealer");
        refuse(() -> new SFMFreshProducerBindings(List.of(node), Map.of("producer", workspace), ignored -> List.of()));
        var wrong = node();
        var wrongBindings = new SFMFreshProducerBindings(List.of(wrong), Map.of("producer", workspace), ignored -> List.of());
        wrong.start();
        wrong.complete(Map.of("output", workspace.resolve("other.jar")), false);
        refuse(() -> wrongBindings.preflight(List.of(new ClasspathItem.NodeOutputItem(wrong.getRequiredOutput("output")))));
        var changed = node();
        var changedBindings = new SFMFreshProducerBindings(List.of(changed), Map.of("producer", workspace), ignored -> List.of());
        changed.start();
        changed.complete(Map.of("output", workspace.resolve("output.jar")), false);
        changed.setAction(environment -> { throw new AssertionError("Never execute"); });
        refuse(() -> changedBindings.preflight(List.of(new ClasspathItem.NodeOutputItem(changed.getRequiredOutput("output")))));
        refuse(() -> wrongBindings.seal(requests));
        Path original = workspace.getParent().getParent().resolve("inputs/artifacts/0001.bin");
        var download = new ExecutionNode("downloadClient", Map.of(),
                Map.of("output", new NodeOutput("output", NodeOutputType.JAR, "original input")),
                new DownloadFromVersionManifestAction(null, "client"));
        var aliases = new SFMFreshProducerBindings(List.of(download),
                Map.of("downloadClient", workspace.resolveSibling("downloadClient")), Set.of(original), ignored -> List.of());
        download.start();
        download.complete(Map.of("output", original), false);
        var aliasRequests = aliases.preflight(List.of(new ClasspathItem.NodeOutputItem(download.getRequiredOutput("output"))));
        if (!aliasRequests.get(0).originalInput() || !aliasRequests.get(0).outputPath().equals(original)) {
            throw new AssertionError("Original download alias lost pinned path identity");
        }
        refuse(() -> aliases.seal(aliasRequests));
        var counterfeit = node();
        var counterfeitBindings = new SFMFreshProducerBindings(List.of(counterfeit), Map.of("producer", workspace),
                Set.of(original), ignored -> List.of());
        counterfeit.start();
        counterfeit.complete(Map.of("output", original), false);
        refuse(() -> counterfeitBindings.preflight(List.of(new ClasspathItem.NodeOutputItem(counterfeit.getRequiredOutput("output")))));
        System.out.println("PASS producer binding: incomplete/foreign/stale/wrong-path/changed-action/cardinality refusal; duplicate order; no tools executed");
    }

    private static ExecutionNode node() {
        return new ExecutionNode("producer", Map.of(), Map.of("output", new NodeOutput("output", NodeOutputType.JAR, "fixture")),
                environment -> { throw new AssertionError("No graph action may run"); });
    }
    @FunctionalInterface private interface Attempt { void run() throws IOException; }
    private static void refuse(Attempt operation) throws IOException {
        try { operation.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Unsafe producer binding accepted");
    }
}
