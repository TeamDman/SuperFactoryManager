package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.SFM;
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
            if (failure != null) {
                SFM.LOGGER.warn(
                        "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=FAILED epoch={} expected_generation={} failure_type={}",
                        epoch,
                        documentGeneration,
                        failure.getClass().getSimpleName()
                );
                return;
            }
            if (lookup == null) {
                SFM.LOGGER.warn(
                        "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=REJECTED_NULL epoch={} expected_generation={}",
                        epoch,
                        documentGeneration
                );
                return;
            }
            synchronized (SFMJavaInteractionMapSession.this) {
                if (closed || requestEpoch != epoch || active != submitted) {
                    SFM.LOGGER.info(
                            "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=REJECTED_STALE epoch={} current_epoch={} closed={}",
                            epoch,
                            requestEpoch,
                            closed
                    );
                    return;
                }
                SFMJavaInteractionMap.Result result = lookup.result();
                if (result.outcome() != SFMJavaInteractionMap.Outcome.SUCCESS) {
                    SFM.LOGGER.warn(
                            "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=REJECTED_OUTCOME epoch={} outcome={}",
                            epoch,
                            result.outcome()
                    );
                    return;
                }
                if (result.documentGeneration() != documentGeneration) {
                    SFM.LOGGER.warn(
                            "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=REJECTED_GENERATION epoch={} expected={} actual={}",
                            epoch,
                            documentGeneration,
                            result.documentGeneration()
                    );
                    return;
                }
                if (!result.document().contentHash().equals(contentHash)) {
                    SFM.LOGGER.warn(
                            "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=REJECTED_CONTENT epoch={} generation={}",
                            epoch,
                            documentGeneration
                    );
                    return;
                }
                publication = new Publication(documentGeneration, contentHash, result);
                SFM.LOGGER.info(
                        "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=PUBLISHED epoch={} generation={} semantic_generation={}",
                        epoch,
                        documentGeneration,
                        result.semanticGeneration()
                );
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
