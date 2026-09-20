package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextContribution;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Asynchronous UI-facing seam for one immutable Rust semantic map. */
public interface SFMJavaInteractionMapLookupService {
    record Lookup(
            SFMSymbolServerProtocol.ServerHello hello,
            SFMJavaInteractionMap.Result result
    ) {
        public Lookup {
            Objects.requireNonNull(hello, "hello");
            Objects.requireNonNull(result, "result");
        }
    }

    record Submission(CompletableFuture<Lookup> result, Runnable cancellation) implements AutoCloseable {
        public Submission {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(cancellation, "cancellation");
        }

        public void cancel() {
            cancellation.run();
        }

        @Override
        public void close() {
            cancel();
        }
    }

    Submission queryInteractionMap(SFMContextContribution contribution);
}
