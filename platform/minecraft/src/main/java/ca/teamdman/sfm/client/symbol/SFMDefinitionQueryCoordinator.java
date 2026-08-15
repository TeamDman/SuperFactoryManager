package ca.teamdman.sfm.client.symbol;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Coordinates independent editor-origin queries without allowing a late
 * response to retarget a newer cursor snapshot.
 */
public final class SFMDefinitionQueryCoordinator implements AutoCloseable {
    public record Handle(
            String originId,
            SFMDefinitionRequest request,
            CompletableFuture<SFMDefinitionResult> result,
            Runnable cancellation
    ) implements AutoCloseable {
        public Handle {
            if (originId == null || originId.isBlank()) throw new IllegalArgumentException("originId is blank");
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(cancellation, "cancellation");
        }

        public void cancel() {
            cancellation.run();
        }

        @Override public void close() {
            cancel();
        }
    }

    /** Bounded, content-free telemetry safe to expose in diagnostics. */
    public record Telemetry(
            long submitted,
            long completed,
            long cancelled,
            long timedOut,
            long staleResponses,
            long mismatchedResponses,
            long failed,
            int active,
            long warmSampleCount,
            long warmMedianNanos,
            long warmP95Nanos,
            long warmMaximumNanos
    ) {
    }

    public static final class NoProviderException extends IllegalStateException {
        public NoProviderException() {
            super("No available SFM symbol-navigation provider");
        }
    }

    public static final class StaleResponseException extends IllegalStateException {
        public StaleResponseException() {
            super("Definition response belongs to a superseded editor context");
        }
    }

    public static final class MismatchedResponseException extends IllegalStateException {
        public MismatchedResponseException() {
            super("Definition response identity does not match its request");
        }
    }

    private static final int MAXIMUM_LATENCY_SAMPLES = 64;
    private final SFMSymbolNavigationProviderRegistry providers;
    private final ScheduledExecutorService scheduler;
    private final LongSupplier nanoTime;
    private final Object lock = new Object();
    private final Map<String, Pending> active = new HashMap<>();
    private final Map<String, Long> generations = new HashMap<>();
    private final ArrayDeque<Long> warmLatencyNanos = new ArrayDeque<>();
    private long nextRequestId;
    private long submitted;
    private long completed;
    private long cancelled;
    private long timedOut;
    private long staleResponses;
    private long mismatchedResponses;
    private long failed;
    private boolean closed;

    public SFMDefinitionQueryCoordinator(
            SFMSymbolNavigationProviderRegistry providers,
            ScheduledExecutorService scheduler
    ) {
        this(providers, scheduler, System::nanoTime);
    }

    SFMDefinitionQueryCoordinator(
            SFMSymbolNavigationProviderRegistry providers,
            ScheduledExecutorService scheduler,
            LongSupplier nanoTime
    ) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public Handle submit(String originId, SFMDefinitionRequest template, Duration timeout) {
        if (originId == null || originId.isBlank()) throw new IllegalArgumentException("originId is blank");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");

        Pending previous;
        Pending pending;
        synchronized (lock) {
            if (closed) throw new IllegalStateException("Definition query coordinator is closed");
            SFMSymbolNavigationProvider provider = providers.preferredAvailable()
                    .map(SFMSymbolNavigationProviderRegistry.Entry::provider)
                    .orElseThrow(NoProviderException::new);
            long requestId = increment(nextRequestId);
            nextRequestId = requestId;
            long generation = increment(generations.getOrDefault(originId, 0L));
            generations.put(originId, generation);
            SFMDefinitionRequest request = template.withIdentity(requestId, generation);
            SFMSymbolNavigationProvider.Query providerQuery = provider.query(request);
            pending = new Pending(originId, request, providerQuery, nanoTime.getAsLong());
            previous = active.put(originId, pending);
            submitted = increment(submitted);
        }

        if (previous != null) previous.cancel(new StaleResponseException(), CancellationKind.STALE);
        Pending captured = pending;
        ScheduledFuture<?> timeoutTask = scheduler.schedule(
                () -> captured.cancel(
                        new TimeoutException("Definition query exceeded " + timeout.toMillis() + " ms"),
                        CancellationKind.TIMEOUT
                ),
                timeout.toNanos(),
                TimeUnit.NANOSECONDS
        );
        pending.timeoutTask = timeoutTask;
        pending.providerQuery.result().whenComplete((result, failure) -> complete(pending, result, failure));
        return new Handle(originId, pending.request, pending.result,
                () -> pending.cancel(new java.util.concurrent.CancellationException(
                        "Definition query cancelled"), CancellationKind.EXPLICIT));
    }

    public Telemetry telemetry() {
        synchronized (lock) {
            List<Long> samples = new ArrayList<>(warmLatencyNanos);
            samples.sort(Long::compare);
            return new Telemetry(
                    submitted,
                    completed,
                    cancelled,
                    timedOut,
                    staleResponses,
                    mismatchedResponses,
                    failed,
                    active.size(),
                    samples.size(),
                    percentile(samples, 0.50),
                    percentile(samples, 0.95),
                    samples.isEmpty() ? 0 : samples.get(samples.size() - 1)
            );
        }
    }

    @Override
    public void close() {
        List<Pending> stopping;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            stopping = List.copyOf(active.values());
            active.clear();
        }
        stopping.forEach(pending -> pending.cancel(
                new java.util.concurrent.CancellationException("Definition provider is stopping"),
                CancellationKind.SHUTDOWN
        ));
    }

    private void complete(Pending pending, SFMDefinitionResult result, Throwable failure) {
        if (!pending.finished.compareAndSet(false, true)) {
            if (failure == null) {
                synchronized (lock) {
                    staleResponses = increment(staleResponses);
                }
            }
            return;
        }
        cancelTimer(pending);
        Throwable completionFailure = null;
        synchronized (lock) {
            if (active.get(pending.originId) != pending) {
                staleResponses = increment(staleResponses);
                completionFailure = new StaleResponseException();
            } else {
                active.remove(pending.originId);
            }
            if (completionFailure == null && failure != null) {
                failed = increment(failed);
                completionFailure = unwrap(failure);
            }
            if (completionFailure == null && (result == null || !result.matches(pending.request))) {
                mismatchedResponses = increment(mismatchedResponses);
                completionFailure = new MismatchedResponseException();
            }
            if (completionFailure == null) {
                long latency = Math.max(0, nanoTime.getAsLong() - pending.startedNanos);
                warmLatencyNanos.addLast(latency);
                while (warmLatencyNanos.size() > MAXIMUM_LATENCY_SAMPLES) warmLatencyNanos.removeFirst();
                completed = increment(completed);
            }
        }
        if (completionFailure == null) pending.result.complete(result);
        else pending.result.completeExceptionally(completionFailure);
    }

    private enum CancellationKind { EXPLICIT, STALE, TIMEOUT, SHUTDOWN }

    private final class Pending {
        private final String originId;
        private final SFMDefinitionRequest request;
        private final SFMSymbolNavigationProvider.Query providerQuery;
        private final CompletableFuture<SFMDefinitionResult> result = new CompletableFuture<>();
        private final long startedNanos;
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile ScheduledFuture<?> timeoutTask;

        private Pending(
                String originId,
                SFMDefinitionRequest request,
                SFMSymbolNavigationProvider.Query providerQuery,
                long startedNanos
        ) {
            this.originId = originId;
            this.request = request;
            this.providerQuery = providerQuery;
            this.startedNanos = startedNanos;
        }

        private void cancel(Throwable reason, CancellationKind kind) {
            if (!finished.compareAndSet(false, true)) return;
            cancelTimer(this);
            providerQuery.cancel();
            synchronized (lock) {
                active.remove(originId, this);
                switch (kind) {
                    case EXPLICIT -> cancelled = increment(cancelled);
                    case STALE -> staleResponses = increment(staleResponses);
                    case TIMEOUT -> timedOut = increment(timedOut);
                    case SHUTDOWN -> { }
                }
            }
            result.completeExceptionally(reason);
        }
    }

    private static void cancelTimer(Pending pending) {
        ScheduledFuture<?> task = pending.timeoutTask;
        if (task != null) task.cancel(false);
    }

    private static long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static long increment(long value) {
        if (value == Long.MAX_VALUE) throw new IllegalStateException("Definition identity counter exhausted");
        return value + 1;
    }
}
