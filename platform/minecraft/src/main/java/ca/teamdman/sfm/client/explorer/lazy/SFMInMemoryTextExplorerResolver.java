package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Bounded contributed resolver for leased, UTF-8 in-memory document trees.
 * Mounts never expose a native {@code Path} or any host-write capability.
 */
public final class SFMInMemoryTextExplorerResolver implements SFMExplorerResolver {
    public record Node(SFMExplorerEntry entry, List<SFMPath> children, Optional<String> text) {
        public Node {
            Objects.requireNonNull(entry, "entry");
            children = List.copyOf(children);
            children.forEach(child -> Objects.requireNonNull(child, "child"));
            Objects.requireNonNull(text, "text");
            if (entry.expandable() == text.isPresent()) {
                throw new IllegalArgumentException("In-memory nodes are either expandable or textual leaves");
            }
        }

        public static Node directory(SFMExplorerEntry entry, List<SFMPath> children) {
            return new Node(entry, children, Optional.empty());
        }

        public static Node text(SFMExplorerEntry entry, String text) {
            return new Node(entry, List.of(), Optional.of(Objects.requireNonNull(text, "text")));
        }
    }

    @FunctionalInterface
    public interface MountLease extends AutoCloseable {
        @Override
        void close();
    }

    private record Mount(long id, String authority, Map<SFMPath, Node> nodes) {
    }

    private final String scheme;
    private final Executor executor;
    private final int maximumPageSize;
    private final TreeMap<String, Mount> mounts = new TreeMap<>();
    private long generation = 1;
    private long nextMountId = 1;

    public SFMInMemoryTextExplorerResolver(String scheme, Executor executor, int maximumPageSize) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        if (!scheme.matches("[a-z][a-z0-9+.-]*")
                || List.of("file", "registry", "selection").contains(scheme)) {
            throw new IllegalArgumentException("In-memory text resolver requires a contributed URI scheme");
        }
        this.executor = Objects.requireNonNull(executor, "executor");
        if (maximumPageSize <= 0) throw new IllegalArgumentException("Maximum page size must be positive");
        this.maximumPageSize = maximumPageSize;
    }

    @Override
    public String scheme() {
        return scheme;
    }

    @Override
    public synchronized long generation() {
        return generation;
    }

    public synchronized MountLease mount(String authority, Collection<Node> nodes) {
        authority = requireAuthority(authority);
        if (mounts.containsKey(authority)) {
            throw new IllegalArgumentException("An in-memory tree is already mounted at " + authority);
        }
        Map<SFMPath, Node> canonical = canonicalNodes(authority, nodes);
        long id = nextMountId++;
        mounts.put(authority, new Mount(id, authority, canonical));
        generation++;
        String retainedAuthority = authority;
        return new MountLease() {
            private boolean closed;

            @Override
            public void close() {
                synchronized (SFMInMemoryTextExplorerResolver.this) {
                    if (closed) return;
                    closed = true;
                    Mount current = mounts.get(retainedAuthority);
                    if (current == null || current.id() != id) return;
                    mounts.remove(retainedAuthority);
                    generation++;
                }
            }
        };
    }

    public synchronized Optional<SFMPath> root(String authority) {
        Mount mount = mounts.get(Objects.requireNonNull(authority, "authority"));
        if (mount == null) return Optional.empty();
        return mount.nodes().keySet().stream()
                .filter(path -> path.segments().isEmpty())
                .findFirst();
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
            Node node = captureNode(path);
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
            Snapshot snapshot = capture(request.parent(), request.expectedResolverGeneration());
            Node parent = snapshot.nodes().get(request.parent());
            if (parent == null) throw new IllegalArgumentException("Unknown in-memory path " + request.parent());
            if (!parent.entry().expandable()) {
                throw new IllegalArgumentException("In-memory text leaves do not have children");
            }
            ArrayList<SFMPath> paths = new ArrayList<>(parent.children());
            paths.sort(Comparator.naturalOrder());
            if (offset > paths.size()) throw new IllegalArgumentException("Continuation exceeds child set");
            int end = Math.min(paths.size(), offset + limit);
            ArrayList<SFMExplorerEntry> entries = new ArrayList<>();
            for (SFMPath path : paths.subList(offset, end)) {
                request.cancellation().throwIfCancelled();
                Node child = snapshot.nodes().get(path);
                if (child == null) throw new IllegalStateException("Mounted parent references a missing child");
                entries.add(child.entry());
            }
            assertGeneration(request.expectedResolverGeneration());
            return new ChildPage(
                    request.parent(),
                    entries,
                    end < paths.size() ? Optional.of("offset-" + end) : Optional.empty(),
                    request.expectedResolverGeneration(),
                    List.of(),
                    paths.size()
            );
        }, executor);
    }

    @Override
    public boolean supportsTextRead() {
        return true;
    }

    @Override
    public CompletableFuture<SFMResolverTextResult> readText(SFMResolverTextRequest request) {
        Objects.requireNonNull(request, "request");
        return CompletableFuture.supplyAsync(() -> readTextNow(request), executor);
    }

    private SFMResolverTextResult readTextNow(SFMResolverTextRequest request) {
        long observed = generation();
        if (!request.path().scheme().equals(scheme)
                || !request.authorizedRoot().scheme().equals(scheme)) {
            return SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.UNSUPPORTED_RESOLVER,
                    observed,
                    "The request belongs to another resolver"
            );
        }
        if (request.cancellation().isCancelled()) {
            return SFMResolverTextResult.failure(
                    request, SFMResolverTextResult.Status.CANCELLED, observed, "Text request was cancelled");
        }
        if (request.expectedResolverGeneration() != observed) {
            return SFMResolverTextResult.failure(
                    request, SFMResolverTextResult.Status.STALE_GENERATION, observed,
                    "Resolver generation changed from " + request.expectedResolverGeneration() + " to " + observed);
        }
        Snapshot snapshot;
        try {
            snapshot = capture(request.path(), observed);
        } catch (RuntimeException unavailable) {
            return SFMResolverTextResult.failure(
                    request, SFMResolverTextResult.Status.REMOVED_ROOT, observed, unavailable.getMessage());
        }
        if (!contains(request.authorizedRoot(), request.path())) {
            return SFMResolverTextResult.failure(
                    request, SFMResolverTextResult.Status.UNAVAILABLE, observed,
                    "The requested path is outside its exact in-memory mount root");
        }
        Node node = snapshot.nodes().get(request.path());
        if (node == null) {
            return SFMResolverTextResult.failure(
                    request, SFMResolverTextResult.Status.UNAVAILABLE, observed, "Unknown in-memory path");
        }
        if (node.text().isEmpty()) {
            return SFMResolverTextResult.failure(
                    request, SFMResolverTextResult.Status.DIRECTORY, observed,
                    Optional.empty(), OptionalLong.empty(), Optional.empty(),
                    "Directories cannot be opened as text");
        }
        String text = node.text().orElseThrow();
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > request.maximumBytes()) {
            return SFMResolverTextResult.failure(
                    request, SFMResolverTextResult.Status.OVERSIZED, observed,
                    Optional.empty(), OptionalLong.of(bytes.length), Optional.empty(),
                    "Document exceeds the bounded text-read limit");
        }
        String hash = sha256(bytes);
        if (request.expectedSha256().filter(expected -> !expected.equals(hash)).isPresent()) {
            return SFMResolverTextResult.failure(
                    request, SFMResolverTextResult.Status.STALE_CONTENT, observed,
                    Optional.of(hash), OptionalLong.of(bytes.length), Optional.empty(),
                    "Document content no longer matches the expected hash");
        }
        return SFMResolverTextResult.ready(
                request,
                observed,
                text,
                hash,
                bytes.length,
                Optional.empty(),
                lineEndings(text)
        );
    }

    private synchronized Snapshot capture(SFMPath path, long expectedGeneration) {
        assertGeneration(expectedGeneration);
        Mount mount = mounts.get(path.authority());
        if (mount == null) throw new IllegalArgumentException("In-memory mount is unavailable: " + path.authority());
        return new Snapshot(generation, mount.nodes());
    }

    private synchronized Node captureNode(SFMPath path) {
        Mount mount = mounts.get(path.authority());
        if (mount == null) throw new IllegalArgumentException("In-memory mount is unavailable: " + path.authority());
        Node node = mount.nodes().get(path);
        if (node == null) throw new IllegalArgumentException("Unknown in-memory path " + path);
        return node;
    }

    private synchronized void assertGeneration(long expected) {
        if (generation != expected) throw new StaleGenerationException(expected, generation);
    }

    private Map<SFMPath, Node> canonicalNodes(String authority, Collection<Node> nodes) {
        Objects.requireNonNull(nodes, "nodes");
        TreeMap<SFMPath, Node> answer = new TreeMap<>();
        for (Node node : nodes) {
            Objects.requireNonNull(node, "node");
            SFMPath path = node.entry().path();
            requireScheme(path);
            if (!path.authority().equals(authority)) {
                throw new IllegalArgumentException("Mounted node belongs to another authority: " + path);
            }
            if (answer.put(path, node) != null) throw new IllegalArgumentException("Duplicate mounted path " + path);
        }
        long roots = answer.keySet().stream().filter(path -> path.segments().isEmpty()).count();
        if (roots != 1) throw new IllegalArgumentException("An in-memory mount requires exactly one root");
        for (Node node : answer.values()) {
            for (SFMPath child : node.children()) {
                if (!answer.containsKey(child)) throw new IllegalArgumentException("Mounted child is absent: " + child);
                if (!child.authority().equals(authority)) {
                    throw new IllegalArgumentException("Mounted child crosses an authority boundary");
                }
            }
        }
        return Map.copyOf(answer);
    }

    private void requireScheme(SFMPath path) {
        Objects.requireNonNull(path, "path");
        if (!path.scheme().equals(scheme) || path.kind() != SFMPath.Kind.CONTRIBUTED) {
            throw new IllegalArgumentException("Resolver accepts only " + scheme + ":// paths");
        }
    }

    private static boolean contains(SFMPath root, SFMPath candidate) {
        return root.scheme().equals(candidate.scheme())
                && root.authority().equals(candidate.authority())
                && root.segments().size() <= candidate.segments().size()
                && candidate.segments().subList(0, root.segments().size()).equals(root.segments());
    }

    private static int parseContinuation(Optional<String> continuation) {
        if (continuation.isEmpty()) return 0;
        String value = continuation.orElseThrow();
        if (!value.startsWith("offset-")) throw new IllegalArgumentException("Unsupported continuation " + value);
        try {
            int offset = Integer.parseInt(value.substring("offset-".length()));
            if (offset <= 0) throw new NumberFormatException();
            return offset;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid continuation " + value);
        }
    }

    private static String requireAuthority(String value) {
        value = Objects.requireNonNull(value, "authority");
        if (value.isEmpty()) throw new IllegalArgumentException("Mount authority must not be empty");
        return value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static SFMResolverTextResult.LineEndingKind lineEndings(String text) {
        boolean lf = false;
        boolean crlf = false;
        boolean cr = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '\r') {
                if (index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    crlf = true;
                    index++;
                } else cr = true;
            } else if (value == '\n') lf = true;
        }
        int kinds = (lf ? 1 : 0) + (crlf ? 1 : 0) + (cr ? 1 : 0);
        if (kinds == 0) return SFMResolverTextResult.LineEndingKind.NONE;
        if (kinds > 1) return SFMResolverTextResult.LineEndingKind.MIXED;
        if (crlf) return SFMResolverTextResult.LineEndingKind.CRLF;
        return cr ? SFMResolverTextResult.LineEndingKind.CR : SFMResolverTextResult.LineEndingKind.LF;
    }

    private record Snapshot(long generation, Map<SFMPath, Node> nodes) {
    }
}
