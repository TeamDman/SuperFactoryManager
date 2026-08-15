package ca.teamdman.sfm.client.syntax;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Asynchronous, cancellable syntax provider independent of its eventual transport. */
public interface SFMSyntaxHighlightProvider extends AutoCloseable {
    ResourceLocation id();

    boolean available();

    Query query(SFMSyntaxHighlightRequest request);

    @Override
    default void close() {
    }

    final class Query implements AutoCloseable {
        private final SFMSyntaxHighlightRequest request;
        private final CompletableFuture<SFMSyntaxHighlightResult> result;
        private final Runnable cancellation;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        public Query(
                SFMSyntaxHighlightRequest request,
                CompletableFuture<SFMSyntaxHighlightResult> result,
                Runnable cancellation
        ) {
            this.request = Objects.requireNonNull(request, "request");
            this.result = Objects.requireNonNull(result, "result");
            this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        }

        public SFMSyntaxHighlightRequest request() {
            return request;
        }

        public CompletableFuture<SFMSyntaxHighlightResult> result() {
            return result;
        }

        public boolean cancel() {
            if (!cancelled.compareAndSet(false, true)) return false;
            try {
                cancellation.run();
            } finally {
                result.cancel(false);
            }
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
