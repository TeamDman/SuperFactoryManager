package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextContribution;

import java.util.Objects;
import java.util.Optional;

/**
 * One editor-owned asynchronous semantic-map cache. Publications are accepted
 * only for the exact document generation and content hash that requested them.
 */
public final class SFMJavaInteractionMapSession implements AutoCloseable {
    private final SFMJavaInteractionMapLookupService service;
    private SFMJavaInteractionMapLookupService.Submission active;
    private volatile Publication publication;
    private long requestEpoch;
    private boolean closed;

    public SFMJavaInteractionMapSession(SFMJavaInteractionMapLookupService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public synchronized void refresh(
            SFMContextContribution contribution,
            long documentGeneration,
            String contentHash
    ) {
        Objects.requireNonNull(contribution, "contribution");
        if (documentGeneration < 0) throw new IllegalArgumentException("documentGeneration must not be negative");
        requireText(contentHash, "contentHash");
        if (closed) return;
        long epoch = ++requestEpoch;
        if (active != null) active.cancel();
        publication = null;
        SFMJavaInteractionMapLookupService.Submission submitted = service.queryInteractionMap(contribution);
        active = submitted;
        submitted.result().whenComplete((lookup, failure) -> {
            if (failure != null || lookup == null) return;
            synchronized (SFMJavaInteractionMapSession.this) {
                if (closed || requestEpoch != epoch || active != submitted) return;
                SFMJavaInteractionMap.Result result = lookup.result();
                if (result.outcome() != SFMJavaInteractionMap.Outcome.SUCCESS) return;
                if (result.documentGeneration() != documentGeneration
                        || !result.document().contentHash().equals(contentHash)) return;
                publication = new Publication(documentGeneration, contentHash, result);
            }
        });
    }

    public Optional<SFMJavaInteractionMap.Result> current(
            long documentGeneration,
            String contentHash
    ) {
        Publication value = publication;
        if (value == null
                || value.documentGeneration != documentGeneration
                || !value.contentHash.equals(contentHash)) return Optional.empty();
        return Optional.of(value.result);
    }

    public boolean pending() {
        SFMJavaInteractionMapLookupService.Submission value = active;
        return value != null && !value.result().isDone();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        requestEpoch++;
        if (active != null) active.cancel();
        active = null;
        publication = null;
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
    }

    private record Publication(
            long documentGeneration,
            String contentHash,
            SFMJavaInteractionMap.Result result
    ) {
    }
}
