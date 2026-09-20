package ca.teamdman.sfm.client.semantic;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** One mod-qualified contributor to a spatial interaction query. */
public interface SFMSpatialSemanticProvider extends AutoCloseable {
    String id();

    int priority();

    long generation();

    default boolean available() {
        return true;
    }

    Contribution probe(Request request, Cancellation cancellation);

    @Override
    default void close() {
    }

    record Request(
            SFMSpatialSemanticContract.SnapshotIdentity snapshot,
            String queryDomainId,
            List<Double> queryPoint,
            SFMSpatialSemanticContract.Intent intent
    ) {
        public Request {
            Objects.requireNonNull(snapshot, "snapshot");
            if (queryDomainId == null || queryDomainId.isBlank()) {
                throw new IllegalArgumentException("query domain id must not be blank");
            }
            queryPoint = List.copyOf(Objects.requireNonNull(queryPoint, "query point"));
            if (queryPoint.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new IllegalArgumentException("query point must be finite");
            }
            Objects.requireNonNull(intent, "intent");
        }
    }

    record Contribution(
            SFMSpatialSemanticContract.Region certifiedRegion,
            SFMSpatialSemanticContract.Classification classification,
            List<SFMSpatialSemanticContract.Outlink> outlinks,
            List<SFMSpatialSemanticContract.ActionDraft> actionDrafts,
            String provenance
    ) {
        public Contribution {
            Objects.requireNonNull(certifiedRegion, "certified region");
            Objects.requireNonNull(classification, "classification");
            outlinks = List.copyOf(Objects.requireNonNull(outlinks, "outlinks"));
            actionDrafts = List.copyOf(Objects.requireNonNull(actionDrafts, "action drafts"));
            if (provenance == null || provenance.isBlank()) {
                throw new IllegalArgumentException("provenance must not be blank");
            }
            if (classification.status() == SFMSpatialSemanticContract.ClassificationStatus.ACTIONABLE
                    && outlinks.isEmpty() && actionDrafts.isEmpty()) {
                throw new IllegalArgumentException("actionable contribution requires an outlink or action");
            }
        }
    }

    final class Cancellation {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        public boolean cancel() {
            return cancelled.compareAndSet(false, true);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void throwIfCancelled() {
            if (isCancelled()) throw new java.util.concurrent.CancellationException("Spatial query cancelled");
        }
    }
}
