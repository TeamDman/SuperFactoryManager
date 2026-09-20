package ca.teamdman.sfm.client.search;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Owned by one synchronous search, never a timer that leaves work running after failure. */
public final class SFMMatchBudget {
    private final long maximum;
    private final BooleanSupplier cancelled;
    private long used;
    private boolean exhausted;

    public SFMMatchBudget(long maximum, BooleanSupplier cancelled) {
        if (maximum < 1) throw new IllegalArgumentException("Match budget must be positive");
        this.maximum = maximum;
        this.cancelled = Objects.requireNonNull(cancelled);
    }

    public SFMMatchBudget(long maximum) { this(maximum, () -> false); }

    public void spend(long operations) {
        if (operations < 0) throw new IllegalArgumentException("Negative match work");
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean())
            throw new CancellationException("Matching cancelled");
        if (exhausted || operations > maximum - used) {
            exhausted = true;
            throw new SFMTextMatcher.LimitExceeded("Matching work limit exceeded (" + maximum + " operations)");
        }
        used += operations;
    }

    public long used() { return used; }
    public boolean exhausted() { return exhausted; }
}
