package ca.teamdman.sfm.client.symbol;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Asynchronous, cancellable provider contract independent of process or Vox transport. */
public interface SFMSymbolNavigationProvider extends AutoCloseable {
    ResourceLocation id();

    boolean available();

    Query query(SFMDefinitionRequest request);

    @Override
    default void close() {
    }

    final class Query implements AutoCloseable {
        private final SFMDefinitionRequest request;
        private final CompletableFuture<SFMDefinitionResult> result;
        private final Runnable cancellation;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        public Query(
                SFMDefinitionRequest request,
                CompletableFuture<SFMDefinitionResult> result,
                Runnable cancellation
        ) {
            this.request = Objects.requireNonNull(request, "request");
            this.result = Objects.requireNonNull(result, "result");
            this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        }

        public SFMDefinitionRequest request() {
            return request;
        }

        public CompletableFuture<SFMDefinitionResult> result() {
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
