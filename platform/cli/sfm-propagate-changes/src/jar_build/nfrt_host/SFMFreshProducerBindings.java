package ca.teamdman.sfm.toolchain.nfrt;

import net.neoforged.neoform.runtime.artifacts.ClasspathItem;
import net.neoforged.neoform.runtime.graph.ExecutionNode;
import net.neoforged.neoform.runtime.graph.NodeOutput;
import net.neoforged.neoform.runtime.graph.NodeState;
import net.neoforged.neoform.runtime.actions.DownloadVersionManifestAction;
import net.neoforged.neoform.runtime.actions.DownloadFromVersionManifestAction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fresh graph identity checks; the Rust host still owns file reads and sealing. */
final class SFMFreshProducerBindings {
    record Request(String producerId, String outputId, String outputType, Path outputPath, boolean originalInput) { }
    record Snapshot(Request request, Path path, long bytes, String sha256) { }

    @FunctionalInterface
    interface Sealer {
        List<Snapshot> seal(List<Request> requests) throws IOException;
    }

    private final IdentityHashMap<NodeOutput, Request> declarations = new IdentityHashMap<>();
    private final IdentityHashMap<ExecutionNode, Object> actions = new IdentityHashMap<>();
    private final Sealer sealer;
    private final Set<Path> originalInputs;

    SFMFreshProducerBindings(List<ExecutionNode> nodes, Map<String, Path> workspaces, Sealer sealer)
            throws IOException {
        this(nodes, workspaces, Set.of(), sealer);
    }

    SFMFreshProducerBindings(List<ExecutionNode> nodes, Map<String, Path> workspaces,
            Set<Path> originalInputs, Sealer sealer) throws IOException {
        if (sealer == null || nodes.isEmpty() || nodes.size() > 256 || workspaces.size() != nodes.size()) {
            throw new IOException("Incomplete producer graph binding");
        }
        for (var node : nodes) {
            Path workspace = workspaces.get(node.id());
            if (node.getState() != NodeState.NOT_STARTED || workspace == null
                    || actions.put(node, node.action()) != null) {
                throw new IOException("Producer registration requires the declared fresh graph");
            }
            for (var entry : node.outputs().entrySet()) {
                String outputId = entry.getKey();
                var output = entry.getValue();
                if (!outputId.matches("[A-Za-z0-9_-]{1,128}") || output.getNode() != node) {
                    throw new IOException("Unsafe or foreign producer output");
                }
                var request = new Request(node.id(), outputId, output.type().name(),
                        workspace.resolve(outputId + output.type().getExtension()), false);
                if (declarations.put(output, request) != null || declarations.size() > 256) {
                    throw new IOException("Duplicate or oversized producer output graph");
                }
            }
        }
        this.sealer = sealer;
        this.originalInputs = Set.copyOf(originalInputs);
    }

    List<Request> preflight(List<ClasspathItem.NodeOutputItem> items) throws IOException {
        if (items.size() > 256) throw new IOException("Oversized producer consumer batch");
        var requests = new ArrayList<Request>(items.size());
        for (var item : items) {
            var output = item.output();
            var request = declarations.get(output);
            var node = output.getNode();
            if (request == null || node.getState() != NodeState.COMPLETED
                    || node.action() != actions.get(node)
                    || node.getRequiredOutput(request.outputId()) != output
                    || !output.type().name().equals(request.outputType())) {
                throw new IOException("Producer is foreign, changed, incomplete or at another output path");
            }
            Path actual = output.getResultPath();
            if (!request.outputPath().equals(actual)) {
                // These exact pinned built-ins forward an already authenticated
                // Artifact path. Other actions cannot claim input aliases.
                Class<?> action = node.action().getClass();
                if ((!action.equals(DownloadVersionManifestAction.class)
                        && !action.equals(DownloadFromVersionManifestAction.class))
                        || !request.outputId().equals("output") || !originalInputs.contains(actual)) {
                    throw new IOException("Producer output is neither its declared file nor an exact built-in input alias");
                }
                request = new Request(request.producerId(), request.outputId(), request.outputType(), actual, true);
            }
            requests.add(request);
        }
        return List.copyOf(requests);
    }

    List<Snapshot> seal(List<Request> requests) throws IOException {
        if (requests.stream().anyMatch(Request::originalInput)) {
            throw new IOException("Original input alias cannot become a fresh producer snapshot");
        }
        var snapshots = sealer.seal(requests);
        if (snapshots == null || snapshots.size() != requests.size()) {
            throw new IOException("Rust producer snapshot response changed consumer cardinality");
        }
        for (int index = 0; index < requests.size(); index++) {
            if (snapshots.get(index) == null || !requests.get(index).equals(snapshots.get(index).request())) {
                throw new IOException("Rust producer snapshot response changed consumer identity/order");
            }
        }
        return List.copyOf(snapshots);
    }
}
