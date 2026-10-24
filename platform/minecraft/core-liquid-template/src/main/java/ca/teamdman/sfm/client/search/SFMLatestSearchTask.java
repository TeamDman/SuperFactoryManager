package ca.teamdman.sfm.client.search;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** One current immutable search context. Consumers recheck their complete key on the UI thread. */
public final class SFMLatestSearchTask<K, V> implements AutoCloseable {
    public record Outcome<V>(V value, String failure) { }
    private static final class Job<K, V> {
        final K key;
        final AtomicBoolean cancelled = new AtomicBoolean();
        volatile Outcome<V> outcome;
        Job(K key) { this.key = key; }
    }
    private final Executor executor;
    private Job<K, V> current;
    private boolean closed;
    public SFMLatestSearchTask(Executor executor) { this.executor = Objects.requireNonNull(executor); }
    public synchronized boolean current(K key) {
        return !closed && current != null && current.key.equals(key) && !current.cancelled.get();
    }
    public synchronized Optional<Outcome<V>> result(K key) {
        return current(key) ? Optional.ofNullable(current.outcome) : Optional.empty();
    }
    public void submit(K key, Function<BooleanSupplier, V> compute) {
        Objects.requireNonNull(key); Objects.requireNonNull(compute);
        Job<K, V> job;
        synchronized (this) {
            if (closed) throw new IllegalStateException("Search task is closed");
            if (current(key)) return;
            cancel();
            current = job = new Job<>(key);
        }
        executor.execute(() -> {
            if (job.cancelled.get()) return;
            Outcome<V> outcome;
            try { outcome = new Outcome<>(Objects.requireNonNull(compute.apply(job.cancelled::get)), null); }
            catch (CancellationException ignored) { return; }
            catch (RuntimeException failure) {
                outcome = new Outcome<>(null, failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
            }
            synchronized (this) {
                if (!closed && current == job && !job.cancelled.get()) job.outcome = outcome;
            }
        });
    }
    public synchronized void cancel() {
        if (current != null) current.cancelled.set(true);
        current = null;
    }
    @Override public synchronized void close() { closed = true; cancel(); }
}
