package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Transparent resolver decorator with a deterministic one-shot publication
 * gate. Normal operation has no armed gate and delegates directly.
 */
public final class SFMGatedExplorerResolver implements SFMExplorerResolver {
    public final class Gate {
        private final SFMPath parent;
        private final CompletableFuture<Void> captured = new CompletableFuture<>();
        private final CompletableFuture<Void> released = new CompletableFuture<>();

        private Gate(SFMPath parent) {
            this.parent = parent;
        }

        public SFMPath parent() {
            return parent;
        }

        /** Completes after delegated resolver work finishes but before publication. */
        public CompletableFuture<Void> captured() {
            return captured;
        }

        public boolean release() {
            return released.complete(null);
        }
    }

    private final SFMExplorerResolver delegate;
    private Gate armed;

    public SFMGatedExplorerResolver(SFMExplorerResolver delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public synchronized Gate armNext(SFMPath parent) {
        Objects.requireNonNull(parent, "parent");
        if (!parent.scheme().equals(scheme())) {
            throw new IllegalArgumentException("Gate parent is owned by another resolver scheme");
        }
        if (armed != null) throw new IllegalStateException("A resolver publication gate is already armed");
        armed = new Gate(parent);
        return armed;
    }

    @Override
    public String scheme() {
        return delegate.scheme();
    }

    @Override
    public long generation() {
        return delegate.generation();
    }

    @Override
    public CompletableFuture<SFMExplorerEntry> describe(
            SFMPath path,
            SFMExplorerCancellationToken cancellation
    ) {
        return delegate.describe(path, cancellation);
    }

    @Override
    public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
        Gate selected;
        synchronized (this) {
            selected = armed != null && armed.parent.equals(request.parent()) ? armed : null;
            if (selected != null) armed = null;
        }
        CompletableFuture<ChildPage> delegated = delegate.resolveChildren(request);
        if (selected == null) return delegated;
        Gate gate = selected;
        return delegated.thenCompose(page -> {
            gate.captured.complete(null);
            return gate.released.thenApply(ignored -> page);
        });
    }

    @Override
    public boolean supportsTextRead() {
        return delegate.supportsTextRead();
    }

    @Override
    public CompletableFuture<SFMResolverTextResult> readText(SFMResolverTextRequest request) {
        return delegate.readText(request);
    }
}
