package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Bounded lazy-explorer adapter over session-persistent symbol-reference results. */
public final class SFMSymbolReferenceExplorerResolver implements SFMExplorerResolver {
    private static final String CONTINUATION_PREFIX = "offset-";

    private final SFMSymbolReferenceResultRepository repository;
    private final Executor executor;
    private final int maximumPageSize;

    public SFMSymbolReferenceExplorerResolver(
            SFMSymbolReferenceResultRepository repository,
            Executor executor,
            int maximumPageSize
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.executor = Objects.requireNonNull(executor, "executor");
        if (maximumPageSize <= 0) throw new IllegalArgumentException("Maximum page size must be positive");
        this.maximumPageSize = maximumPageSize;
    }

    @Override
    public String scheme() {
        return SFMSymbolReferenceResultRepository.SCHEME;
    }

    @Override
    public long generation() {
        return repository.generation();
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
            SFMExplorerEntry entry = repository.node(path)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown symbol-reference explorer path: " + path
                    ))
                    .entry();
            cancellation.throwIfCancelled();
            return entry;
        }, executor);
    }

    @Override
    public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
        requireScheme(request.parent());
        int offset = parseContinuation(request.continuation());
        int limit = Math.min(request.pageSize(), maximumPageSize);
        return CompletableFuture.supplyAsync(() -> {
            request.cancellation().throwIfCancelled();
            SFMSymbolReferenceResultRepository.Snapshot snapshot = repository.snapshot(
                    request.expectedResolverGeneration()
            );
            SFMSymbolReferenceResultRepository.Node parent = Optional.ofNullable(
                            snapshot.nodes().get(request.parent())
                    )
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown symbol-reference explorer path: " + request.parent()
                    ));
            if (offset > parent.children().size()) {
                throw new IllegalArgumentException("Continuation is beyond the reference child set");
            }
            int end = Math.min(parent.children().size(), offset + limit);
            ArrayList<SFMExplorerEntry> entries = new ArrayList<>();
            for (SFMPath child : parent.children().subList(offset, end)) {
                request.cancellation().throwIfCancelled();
                SFMSymbolReferenceResultRepository.Node childNode = snapshot.nodes().get(child);
                if (childNode == null) {
                    throw new IllegalStateException("Reference parent points to an unknown child: " + child);
                }
                entries.add(childNode.entry());
            }
            if (repository.generation() != request.expectedResolverGeneration()) {
                throw new StaleGenerationException(
                        request.expectedResolverGeneration(),
                        repository.generation()
                );
            }
            request.cancellation().throwIfCancelled();
            Optional<String> continuation = end < parent.children().size()
                    ? Optional.of(CONTINUATION_PREFIX + end)
                    : Optional.empty();
            return new ChildPage(
                    request.parent(),
                    entries,
                    continuation,
                    snapshot.generation(),
                    List.of(),
                    parent.children().size()
            );
        }, executor);
    }

    public Optional<SFMSymbolReferenceResultRepository.LeafLookup> lookupLeaf(SFMPath path) {
        requireScheme(path);
        return repository.lookupLeaf(path);
    }

    private static int parseContinuation(Optional<String> continuation) {
        if (continuation.isEmpty()) return 0;
        String value = continuation.orElseThrow();
        if (!value.startsWith(CONTINUATION_PREFIX) || value.length() == CONTINUATION_PREFIX.length()) {
            throw new IllegalArgumentException("Unsupported reference continuation token: " + value);
        }
        try {
            int offset = Integer.parseInt(value.substring(CONTINUATION_PREFIX.length()));
            if (offset <= 0) throw new NumberFormatException();
            return offset;
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("Invalid reference continuation token: " + value);
        }
    }

    private static void requireScheme(SFMPath path) {
        Objects.requireNonNull(path, "path");
        if (!SFMSymbolReferenceResultRepository.SCHEME.equals(path.scheme())) {
            throw new IllegalArgumentException("Reference resolver only accepts symbol-references paths");
        }
    }
}
