package ca.teamdman.sfm.client.symbol;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Asynchronous provider contract for persistent references at one exact source location. */
public interface SFMSymbolReferenceProvider extends AutoCloseable {
    ResourceLocation id();

    boolean available();

    ReferenceQuery query(SFMUsageAtPositionRequest request);

    @Override
    default void close() {
    }

    final class ReferenceQuery implements AutoCloseable {
        private final SFMUsageAtPositionRequest request;
        private final CompletableFuture<SFMUsageAtPositionResult> result;
        private final Runnable cancellation;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        public ReferenceQuery(
                SFMUsageAtPositionRequest request,
                CompletableFuture<SFMUsageAtPositionResult> result,
                Runnable cancellation
        ) {
            this.request = Objects.requireNonNull(request, "request");
            this.result = Objects.requireNonNull(result, "result");
            this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        }

        public SFMUsageAtPositionRequest request() {
            return request;
        }

        public CompletableFuture<SFMUsageAtPositionResult> result() {
            return result;
        }

        public boolean cancel() {
            if (!cancelled.compareAndSet(false, true)) return false;
            cancellation.run();
            result.cancel(false);
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
