package ca.teamdman.sfm.client.control;

import java.util.Objects;
import java.util.UUID;

/** Client-thread ownership shared by external actions and the puppet runner. */
public final class SFMActionArbiter {
    private Object puppet;
    private long revision;
    private String puppetId = "";
    private String puppetName = "";
    private String owner = "";
    private long leaseDeadlineNanos;

    public synchronized void observePuppet(Object current, String name) {
        if (puppet == current) return;
        puppet = current;
        revision++;
        puppetId = current == null ? "" : UUID.randomUUID().toString();
        puppetName = current == null ? "" : Objects.requireNonNull(name);
        owner = "";
        leaseDeadlineNanos = 0;
    }

    public synchronized long revision() { return revision; }
    public synchronized void requireRevision(long expected) {
        if (expected != revision) throw new IllegalStateException("stale_target: puppet changed after admission");
    }

    public synchronized String puppetId() { return puppetId; }
    public synchronized String puppetName() { return puppetName; }
    public synchronized String owner(long now) { expire(now); return owner; }

    public synchronized void pause(String requester, String expectedPuppetId, long now, long durationNanos, boolean quiescent) {
        requirePuppet(expectedPuppetId);
        expire(now);
        if (!quiescent) throw new IllegalStateException("busy: active action has not acknowledged a quiescent handoff");
        if (!owner.isEmpty() && !owner.equals(requester)) throw new IllegalStateException("busy: another controller owns this puppet");
        if (durationNanos <= 0 || durationNanos > 300_000_000_000L) throw new IllegalArgumentException("Lease must be between 1 and 300 seconds");
        owner = Objects.requireNonNull(requester);
        leaseDeadlineNanos = now + durationNanos;
    }

    public synchronized void resume(String requester, String expectedPuppetId, long now) {
        requirePuppet(expectedPuppetId);
        expire(now);
        if (!owner.isEmpty() && !owner.equals(requester)) throw new IllegalStateException("busy: another controller owns this puppet");
        owner = "";
        leaseDeadlineNanos = 0;
    }

    public synchronized void release(String requester) {
        if (owner.equals(requester)) { owner = ""; leaseDeadlineNanos = 0; }
    }

    public synchronized boolean mayTickPuppet(long now) { expire(now); return owner.isEmpty(); }

    public synchronized void requireMutation(String requester, String expectedPuppetId, long now) {
        if (expectedPuppetId != null && !puppetId.equals(expectedPuppetId)) throw new IllegalStateException("stale_target: puppet changed after admission");
        expire(now);
        if (puppet != null && !owner.equals(requester)) throw new IllegalStateException("busy: pause the exact active puppet before mutating its client");
        if (!owner.isEmpty() && !owner.equals(requester)) throw new IllegalStateException("busy: another controller owns client mutation");
    }

    private void requirePuppet(String expected) {
        if (puppet == null || !puppetId.equals(expected)) throw new IllegalStateException("stale_target: active puppet changed or yielded");
    }

    private void expire(long now) {
        if (!owner.isEmpty() && now - leaseDeadlineNanos >= 0) { owner = ""; leaseDeadlineNanos = 0; }
    }
}
