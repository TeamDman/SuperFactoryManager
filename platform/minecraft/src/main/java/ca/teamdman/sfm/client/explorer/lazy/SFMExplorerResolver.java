package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asynchronous, bounded resolver for one {@link SFMPath} scheme. */
public interface SFMExplorerResolver {
    record ChildRequest(
            SFMPath parent,
            Optional<String> continuation,
            int pageSize,
            long expectedResolverGeneration,
            SFMExplorerCancellationToken cancellation
    ) {
        public ChildRequest {
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(continuation, "continuation");
            if (pageSize <= 0) throw new IllegalArgumentException("Page size must be positive");
            if (expectedResolverGeneration < 0) {
                throw new IllegalArgumentException("Resolver generation must not be negative");
            }
            Objects.requireNonNull(cancellation, "cancellation");
        }
    }

    record ChildPage(
            SFMPath parent,
            List<SFMExplorerEntry> entries,
            Optional<String> continuation,
            long resolverGeneration,
            List<String> diagnostics,
            int observedEntries
    ) {
        public ChildPage {
            Objects.requireNonNull(parent, "parent");
            entries = List.copyOf(entries);
            entries.forEach(entry -> {
                if (entry == null) throw new NullPointerException("entry");
            });
            Objects.requireNonNull(continuation, "continuation");
            if (resolverGeneration < 0) {
                throw new IllegalArgumentException("Resolver generation must not be negative");
            }
            diagnostics = List.copyOf(diagnostics);
            if (observedEntries < entries.size()) {
                throw new IllegalArgumentException("Observed count cannot be smaller than returned entries");
            }
        }

        public boolean complete() {
            return continuation.isEmpty();
        }
    }

    /** Raised when a request no longer describes the resolver's current generation. */
    final class StaleGenerationException extends RuntimeException {
        public StaleGenerationException(long expected, long actual) {
            super("Resolver generation changed from " + expected + " to " + actual);
        }
    }

    String scheme();

    long generation();

    CompletableFuture<SFMExplorerEntry> describe(
            SFMPath path,
            SFMExplorerCancellationToken cancellation
    );

    CompletableFuture<ChildPage> resolveChildren(ChildRequest request);

    /** Whether this resolver can satisfy bounded text reads for its paths. */
    default boolean supportsTextRead() {
        return false;
    }

    /**
     * Reads one concrete path as text without requiring callers to interpret
     * resolver-specific exceptions. Implementations that do not own textual
     * content report that capability truthfully through a typed result.
     */
    default CompletableFuture<SFMResolverTextResult> readText(SFMResolverTextRequest request) {
        Objects.requireNonNull(request, "request");
        long actualGeneration = generation();
        SFMResolverTextResult result;
        if (request.cancellation().isCancelled()) {
            result = SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.CANCELLED,
                    actualGeneration,
                    "Resolver text request was cancelled"
            );
        } else if (request.expectedResolverGeneration() != actualGeneration) {
            result = SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.STALE_GENERATION,
                    actualGeneration,
                    "Resolver generation changed from "
                            + request.expectedResolverGeneration() + " to " + actualGeneration
            );
        } else {
            result = SFMResolverTextResult.failure(
                    request,
                    SFMResolverTextResult.Status.UNSUPPORTED_RESOLVER,
                    actualGeneration,
                    "Resolver " + scheme() + " does not support text reads"
            );
        }
        return CompletableFuture.completedFuture(result);
    }
}
