package ca.teamdman.sfm.client.symbol;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Injectable, cancellable definition-availability seam used by the pure hover state machine. */
@FunctionalInterface
public interface SFMSymbolHoverLookup {
    Query submit(SFMSymbolHoverIdentity identity);

    enum Resolution {
        ACTIONABLE,
        UNRESOLVED,
        AMBIGUOUS,
        UNAVAILABLE
    }

    final class Query implements AutoCloseable {
        private final CompletableFuture<Resolution> result;
        private final Runnable cancellation;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        public Query(CompletableFuture<Resolution> result, Runnable cancellation) {
            this.result = Objects.requireNonNull(result, "result");
            this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        }

        public CompletableFuture<Resolution> result() {
            return result;
        }

        public boolean cancel() {
            if (!cancelled.compareAndSet(false, true)) return false;
            cancellation.run();
            return true;
        }

        public boolean cancelled() {
            return cancelled.get();
        }

        @Override
        public void close() {
            cancel();
        }
    }
}
