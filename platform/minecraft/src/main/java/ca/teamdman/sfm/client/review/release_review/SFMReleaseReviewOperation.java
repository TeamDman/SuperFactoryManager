package ca.teamdman.sfm.client.review.release_review;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

/** A cancellation request must win before commit authority, never hide a completed write. */
public final class SFMReleaseReviewOperation {
    public enum Phase { PREPARING, CANCELLATION_REQUESTED, COMMITTING, COMPLETE }

    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.PREPARING);

    public Phase phase() {
        return phase.get();
    }

    public boolean requestCancellation() {
        return phase.compareAndSet(Phase.PREPARING, Phase.CANCELLATION_REQUESTED)
                || phase.get() == Phase.CANCELLATION_REQUESTED;
    }

    void checkCancelled() {
        if (phase.get() == Phase.CANCELLATION_REQUESTED) {
            throw new CancellationException("Review operation cancelled before commit");
        }
    }

    void beginCommit() {
        if (!phase.compareAndSet(Phase.PREPARING, Phase.COMMITTING)) {
            checkCancelled();
            throw new IllegalStateException("Review operation already reached its commit boundary");
        }
    }

    void complete() {
        phase.set(Phase.COMPLETE);
    }
}
