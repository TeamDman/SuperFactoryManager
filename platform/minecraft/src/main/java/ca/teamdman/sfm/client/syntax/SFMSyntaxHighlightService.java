package ca.teamdman.sfm.client.syntax;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Client-facing query seam used by editor sessions independently of process transport. */
public interface SFMSyntaxHighlightService {
    Submission query(String originId, long originGeneration, String language, String exactSource);

    record Submission(
            SFMSyntaxHighlightRequest request,
            CompletableFuture<SFMSyntaxHighlightResult> result,
            Runnable cancellation
    ) implements AutoCloseable {
        public Submission {
            Objects.requireNonNull(request, "request");
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
}
