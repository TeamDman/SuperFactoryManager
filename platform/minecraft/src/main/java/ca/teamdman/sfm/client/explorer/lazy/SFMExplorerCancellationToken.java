package ca.teamdman.sfm.client.explorer.lazy;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation shared by one resolver request and its caller. */
public final class SFMExplorerCancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void throwIfCancelled() {
        if (isCancelled()) {
            throw new CancellationException("Explorer resolver request was cancelled");
        }
    }
}
