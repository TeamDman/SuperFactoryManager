package ca.teamdman.sfm.client.syntax;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Last lifecycle guard between the shared query coordinator and one editor.
 * Results are published only if this still-open session owns their exact
 * origin generation and source hash.
 */
public final class SFMTextEditorSyntaxSession implements AutoCloseable {
    public record Publication(
            SFMSyntaxHighlightRequest request,
            SFMSyntaxHighlightResult result
    ) {
        public Publication {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(result, "result");
        }
    }

    public record Telemetry(long submitted, long published, long stale, long failed, boolean active) {
    }

    private final String originId;
    private final SFMSyntaxHighlightService service;
    private final Executor publicationExecutor;
    private final Consumer<Publication> publication;
    private final Consumer<Throwable> failure;
    private SFMSyntaxHighlightService.Submission active;
    private long expectedOriginGeneration;
    private String expectedSourceSha256 = "";
    private long submitted;
    private long published;
    private long stale;
    private long failed;
    private boolean closed;

    public SFMTextEditorSyntaxSession(
            String originId,
            SFMSyntaxHighlightService service,
            Executor publicationExecutor,
            Consumer<Publication> publication,
            Consumer<Throwable> failure
    ) {
        SFMSyntaxHighlightRequest.validateOriginId(originId);
        this.originId = originId;
        this.service = Objects.requireNonNull(service, "service");
        this.publicationExecutor = Objects.requireNonNull(publicationExecutor, "publicationExecutor");
        this.publication = Objects.requireNonNull(publication, "publication");
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    public synchronized void request(long originGeneration, String language, String exactSource) {
        if (closed) throw new IllegalStateException("Text-editor syntax session is closed");
        if (originGeneration <= 0) throw new IllegalArgumentException("originGeneration must be positive");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(exactSource, "exactSource");
        if (active != null) active.cancel();
        expectedOriginGeneration = originGeneration;
        expectedSourceSha256 = SFMSyntaxHighlightRequest.sha256(exactSource);
        SFMSyntaxHighlightService.Submission submission = service.query(
                originId,
                originGeneration,
                language,
                exactSource
        );
        active = submission;
        submitted++;
        submission.result().whenComplete((result, problem) -> publicationExecutor.execute(
                () -> complete(submission, result, problem)
        ));
    }

    public synchronized Optional<SFMSyntaxHighlightRequest> activeRequest() {
        return Optional.ofNullable(active).map(SFMSyntaxHighlightService.Submission::request);
    }

    public synchronized Telemetry telemetry() {
        return new Telemetry(submitted, published, stale, failed, active != null);
    }

    private void complete(
            SFMSyntaxHighlightService.Submission submission,
            SFMSyntaxHighlightResult result,
            Throwable problem
    ) {
        synchronized (this) {
            if (closed || active != submission
                    || submission.request().originGeneration() != expectedOriginGeneration
                    || !submission.request().sourceSha256().equals(expectedSourceSha256)) {
                stale++;
                return;
            }
            active = null;
            if (problem != null) {
                Throwable unwrapped = unwrap(problem);
                if (!(unwrapped instanceof CancellationException)) {
                    failed++;
                    failure.accept(unwrapped);
                }
                return;
            }
            if (result == null || !result.matchesIdentity(submission.request())) {
                failed++;
                failure.accept(new IllegalArgumentException("Syntax publication identity mismatch"));
                return;
            }
            published++;
            publication.accept(new Publication(submission.request(), result));
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        expectedSourceSha256 = "";
        if (active != null) active.cancel();
        active = null;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }
}
