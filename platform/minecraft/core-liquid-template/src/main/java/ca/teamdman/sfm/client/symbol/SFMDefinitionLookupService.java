package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextContribution;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Asynchronous UI-facing seam over one supervised symbol provider. */
public interface SFMDefinitionLookupService {
    record Lookup(
            SFMSymbolServerProtocol.ServerHello hello,
            SFMDefinitionResult result
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

        @Override public void close() {
            cancel();
        }
    }

    Submission query(SFMContextContribution contribution);
}
