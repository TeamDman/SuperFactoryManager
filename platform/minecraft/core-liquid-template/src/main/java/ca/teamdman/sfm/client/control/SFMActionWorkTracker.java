package ca.teamdman.sfm.client.control;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Tracks queued and running puppet-owned mutations before cooperative handoff. */
public final class SFMActionWorkTracker {
    private final AtomicInteger pending = new AtomicInteger();

    public boolean isIdle() { return pending.get() == 0; }

    public void execute(Executor executor, Runnable work) {
        pending.incrementAndGet();
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) pending.decrementAndGet();
        };
        try {
            executor.execute(() -> {
                try { work.run(); }
                finally { release.run(); }
            });
        } catch (RuntimeException | Error rejected) {
            release.run();
            throw rejected;
        }
    }
}
