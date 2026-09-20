package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Injectable registry-shaped resolver with no Minecraft runtime dependency. */
public final class SFMInMemoryRegistryExplorerResolver implements SFMExplorerResolver {
    public record Node(SFMExplorerEntry entry, List<SFMPath> children) {
        public Node {
            Objects.requireNonNull(entry, "entry");
            children = List.copyOf(children);
            children.forEach(child -> Objects.requireNonNull(child, "child"));
        }
    }

    private final Executor executor;
    private final int maximumPageSize;
    private Map<SFMPath, Node> nodes;
    private long generation = 1;

    public SFMInMemoryRegistryExplorerResolver(
            Collection<Node> nodes,
            Executor executor,
            int maximumPageSize
    ) {
        this.executor = Objects.requireNonNull(executor, "executor");
        if (maximumPageSize <= 0) {
            throw new IllegalArgumentException("Maximum page size must be positive");
        }
        this.maximumPageSize = maximumPageSize;
        this.nodes = immutableNodes(nodes);
    }

    @Override
    public String scheme() {
        return "registry";
    }

    @Override
    public synchronized long generation() {
        return generation;
    }

    public synchronized long replace(Collection<Node> replacement) {
        nodes = immutableNodes(replacement);
        return ++generation;
    }

    @Override
    public CompletableFuture<SFMExplorerEntry> describe(
            SFMPath path,
            SFMExplorerCancellationToken cancellation
    ) {
        requireScheme(path);
        Objects.requireNonNull(cancellation, "cancellation");
        return CompletableFuture.supplyAsync(() -> {
            cancellation.throwIfCancelled();
            Node node = requireNode(path);
            cancellation.throwIfCancelled();
            return node.entry();
        }, executor);
    }

    @Override
    public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
        requireScheme(request.parent());
        int offset = parseContinuation(request.continuation());
        int limit = Math.min(request.pageSize(), maximumPageSize);
        return CompletableFuture.supplyAsync(() -> {
            request.cancellation().throwIfCancelled();
            Snapshot snapshot = capture(request.expectedResolverGeneration());
            Node parent = snapshot.nodes().get(request.parent());
            if (parent == null) {
                throw new IllegalArgumentException("Unknown registry explorer path: " + request.parent());
            }
            ArrayList<SFMPath> childPaths = new ArrayList<>(parent.children());
            childPaths.sort(Comparator.naturalOrder());
            int end = Math.min(childPaths.size(), offset + limit);
            if (offset > childPaths.size()) {
                throw new IllegalArgumentException("Continuation is beyond the registry child set");
            }
            ArrayList<SFMExplorerEntry> entries = new ArrayList<>();
            for (SFMPath child : childPaths.subList(offset, end)) {
                request.cancellation().throwIfCancelled();
                Node childNode = snapshot.nodes().get(child);
                if (childNode == null) {
                    throw new IllegalStateException("Registry parent references an unknown child: " + child);
                }
                entries.add(childNode.entry());
            }
            assertGeneration(request.expectedResolverGeneration());
            Optional<String> continuation = end < childPaths.size()
                    ? Optional.of("offset-" + end)
                    : Optional.empty();
            return new ChildPage(
                    request.parent(),
                    entries,
                    continuation,
                    request.expectedResolverGeneration(),
                    List.of(),
                    Math.min(childPaths.size(), end + (continuation.isPresent() ? 1 : 0))
            );
        }, executor);
    }

    private synchronized Snapshot capture(long expectedGeneration) {
        assertGeneration(expectedGeneration);
        return new Snapshot(generation, nodes);
    }

    private synchronized Node requireNode(SFMPath path) {
        Node node = nodes.get(path);
        if (node == null) throw new IllegalArgumentException("Unknown registry explorer path: " + path);
        return node;
    }

    private synchronized void assertGeneration(long expected) {
        if (generation != expected) throw new StaleGenerationException(expected, generation);
    }

    private static Map<SFMPath, Node> immutableNodes(Collection<Node> nodes) {
        Objects.requireNonNull(nodes, "nodes");
        TreeMap<SFMPath, Node> answer = new TreeMap<>();
        for (Node node : nodes) {
            Objects.requireNonNull(node, "node");
            requireScheme(node.entry().path());
            if (answer.put(node.entry().path(), node) != null) {
                throw new IllegalArgumentException("Duplicate registry explorer path: " + node.entry().path());
            }
        }
        for (Node node : answer.values()) {
            for (SFMPath child : node.children()) {
                requireScheme(child);
                if (!answer.containsKey(child)) {
                    throw new IllegalArgumentException("Registry node references an unknown child: " + child);
                }
            }
        }
        return Collections.unmodifiableMap(answer);
    }

    private static int parseContinuation(Optional<String> continuation) {
        if (continuation.isEmpty()) return 0;
        String value = continuation.orElseThrow();
        if (!value.startsWith("offset-") || value.length() == "offset-".length()) {
            throw new IllegalArgumentException("Unsupported registry continuation token: " + value);
        }
        try {
            int offset = Integer.parseInt(value.substring("offset-".length()));
            if (offset <= 0) throw new NumberFormatException();
            return offset;
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("Invalid registry continuation token: " + value);
        }
    }

    private static void requireScheme(SFMPath path) {
        Objects.requireNonNull(path, "path");
        if (!path.scheme().equals("registry")) {
            throw new IllegalArgumentException("Registry resolver only accepts registry paths");
        }
    }

    private record Snapshot(long generation, Map<SFMPath, Node> nodes) {
    }
}
