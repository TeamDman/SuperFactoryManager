package ca.teamdman.sfm.client.syntax;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Coordinates independent editor origins and prevents a late provider response
 * from replacing the styles for a newer immutable source snapshot.
 */
public final class SFMSyntaxHighlightCoordinator implements AutoCloseable {
    public record Handle(
            String originId,
            SFMSyntaxHighlightRequest request,
            CompletableFuture<SFMSyntaxHighlightResult> result,
            Runnable cancellation
    ) implements AutoCloseable {
        public Handle {
            SFMSyntaxHighlightRequest.validateOriginId(originId);
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(cancellation, "cancellation");
        }

        public void cancel() {
            cancellation.run();
        }

        @Override
        public void close() {
            cancel();
        }
    }

    /** Bounded, content-free telemetry: no source, origin, language, or hash values are retained here. */
    public record Telemetry(
            long submitted,
            long completed,
            long explicitlyCancelled,
            long superseded,
            long timedOut,
            long staleResponses,
            long mismatchedResponses,
            long invalidResponses,
            long providerFailures,
            int active,
            int latencySamples,
            long medianNanos,
            long p95Nanos,
            long maximumNanos
    ) {
    }

    public static final class NoProviderException extends IllegalStateException {
        public NoProviderException() {
            super("No available SFM syntax-highlight provider");
        }
    }

    public static final class StaleResponseException extends IllegalStateException {
        public StaleResponseException() {
            super("Syntax response belongs to a superseded editor origin");
        }
    }

    public static final class MismatchedResponseException extends IllegalStateException {
        public MismatchedResponseException() {
            super("Syntax response identity does not match its request");
        }
    }

    public static final class InvalidResponseException extends IllegalStateException {
        public InvalidResponseException(Throwable cause) {
            super("Syntax response violates the negotiated contract", cause);
        }
    }

    private enum CancellationKind { EXPLICIT, SUPERSEDED, TIMEOUT, SHUTDOWN }

    private static final int MAXIMUM_LATENCY_SAMPLES = 64;
    private final SFMSyntaxHighlightProviderRegistry providers;
    private final ScheduledExecutorService scheduler;
    private final SFMSyntaxHighlightLimits limits;
    private final LongSupplier nanoTime;
    private final Object lock = new Object();
    private final Map<String, Pending> active = new HashMap<>();
    private final Map<String, Long> generations = new HashMap<>();
    private final ArrayDeque<Long> latencyNanos = new ArrayDeque<>();
    private long nextRequestId;
    private long submitted;
    private long completed;
    private long explicitlyCancelled;
    private long superseded;
    private long timedOut;
    private long staleResponses;
    private long mismatchedResponses;
    private long invalidResponses;
    private long providerFailures;
    private boolean closed;

    public SFMSyntaxHighlightCoordinator(
            SFMSyntaxHighlightProviderRegistry providers,
            ScheduledExecutorService scheduler
    ) {
        this(providers, scheduler, SFMSyntaxHighlightLimits.defaults(), System::nanoTime);
    }

    public SFMSyntaxHighlightCoordinator(
            SFMSyntaxHighlightProviderRegistry providers,
            ScheduledExecutorService scheduler,
            SFMSyntaxHighlightLimits limits
    ) {
        this(providers, scheduler, limits, System::nanoTime);
    }

    SFMSyntaxHighlightCoordinator(
            SFMSyntaxHighlightProviderRegistry providers,
            ScheduledExecutorService scheduler,
            SFMSyntaxHighlightLimits limits,
            LongSupplier nanoTime
    ) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public Handle submit(
            String originId,
            long originGeneration,
            String language,
            String source,
            long maximumSpans,
            Duration timeout
    ) {
        SFMSyntaxHighlightRequest.validateOriginId(originId);
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Syntax query timeout must be positive");
        }

        Pending previous;
        Pending pending;
        synchronized (lock) {
            if (closed) throw new IllegalStateException("Syntax query coordinator is closed");
            SFMSyntaxHighlightProvider provider = providers.preferredAvailable()
                    .map(SFMSyntaxHighlightProviderRegistry.Entry::provider)
                    .orElseThrow(NoProviderException::new);
            long requestId = increment(nextRequestId, "Syntax request identity counter exhausted");
            nextRequestId = requestId;
            long requestGeneration = increment(
                    generations.getOrDefault(originId, 0L),
                    "Syntax request generation counter exhausted"
            );
            generations.put(originId, requestGeneration);
            SFMSyntaxHighlightRequest request = SFMSyntaxHighlightRequest.create(
                    requestId,
                    requestGeneration,
                    originId,
                    originGeneration,
                    language,
                    source,
                    maximumSpans
            );
            request.validate(limits);
            SFMSyntaxHighlightProvider.Query providerQuery = Objects.requireNonNull(
                    provider.query(request),
                    "Syntax provider returned a null query"
            );
            if (!providerQuery.request().equals(request)) {
                providerQuery.cancel();
                throw new IllegalStateException("Syntax provider query changed the submitted request");
            }
            pending = new Pending(originId, request, providerQuery, nanoTime.getAsLong());
            previous = active.put(originId, pending);
            submitted = increment(submitted, "Syntax submitted telemetry counter exhausted");
        }

        if (previous != null) {
            previous.cancel(new StaleResponseException(), CancellationKind.SUPERSEDED);
        }
        Pending captured = pending;
        ScheduledFuture<?> timeoutTask = scheduler.schedule(
                () -> captured.cancel(
                        new TimeoutException("Syntax query exceeded " + timeout.toMillis() + " ms"),
                        CancellationKind.TIMEOUT
                ),
                timeout.toNanos(),
                TimeUnit.NANOSECONDS
        );
        pending.timeoutTask = timeoutTask;
        pending.providerQuery.result().whenComplete((result, failure) -> complete(pending, result, failure));
        return new Handle(
                originId,
                pending.request,
                pending.result,
                () -> pending.cancel(new CancellationException("Syntax query cancelled"), CancellationKind.EXPLICIT)
        );
    }

    public Telemetry telemetry() {
        synchronized (lock) {
            List<Long> samples = new ArrayList<>(latencyNanos);
            samples.sort(Long::compare);
            return new Telemetry(
                    submitted,
                    completed,
                    explicitlyCancelled,
                    superseded,
                    timedOut,
                    staleResponses,
                    mismatchedResponses,
                    invalidResponses,
                    providerFailures,
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
            generations.clear();
        }
        stopping.forEach(pending -> pending.cancel(
                new CancellationException("Syntax coordinator is stopping"),
                CancellationKind.SHUTDOWN
        ));
    }

    private void complete(Pending pending, SFMSyntaxHighlightResult result, Throwable providerFailure) {
        if (!pending.finished.compareAndSet(false, true)) return;
        cancelTimer(pending);

        Throwable completionFailure = unwrap(providerFailure);
        CompletionKind completionKind = completionFailure == null ? CompletionKind.SUCCESS : CompletionKind.PROVIDER;
        if (completionFailure == null && result == null) {
            completionFailure = new NullPointerException("Syntax provider completed with a null result");
            completionKind = CompletionKind.PROVIDER;
        }
        if (completionFailure == null && !result.matchesIdentity(pending.request)) {
            completionFailure = new MismatchedResponseException();
            completionKind = CompletionKind.MISMATCHED;
        }
        if (completionFailure == null) {
            try {
                result.validateAgainst(pending.request, limits);
            } catch (RuntimeException invalid) {
                completionFailure = new InvalidResponseException(invalid);
                completionKind = CompletionKind.INVALID;
            }
        }

        synchronized (lock) {
            if (active.get(pending.originId) != pending) {
                staleResponses = increment(staleResponses, "Syntax stale telemetry counter exhausted");
                completionFailure = new StaleResponseException();
                completionKind = CompletionKind.STALE;
            } else {
                active.remove(pending.originId);
            }
            switch (completionKind) {
                case SUCCESS -> {
                    long latency = Math.max(0, nanoTime.getAsLong() - pending.startedNanos);
                    latencyNanos.addLast(latency);
                    while (latencyNanos.size() > MAXIMUM_LATENCY_SAMPLES) latencyNanos.removeFirst();
                    completed = increment(completed, "Syntax completed telemetry counter exhausted");
                }
                case PROVIDER -> providerFailures = increment(
                        providerFailures,
                        "Syntax provider-failure telemetry counter exhausted"
                );
                case MISMATCHED -> mismatchedResponses = increment(
                        mismatchedResponses,
                        "Syntax mismatch telemetry counter exhausted"
                );
                case INVALID -> invalidResponses = increment(
                        invalidResponses,
                        "Syntax invalid-response telemetry counter exhausted"
                );
                case STALE -> { }
            }
        }

        if (completionFailure == null) pending.result.complete(result);
        else pending.result.completeExceptionally(completionFailure);
    }

    private enum CompletionKind { SUCCESS, PROVIDER, MISMATCHED, INVALID, STALE }

    private final class Pending {
        private final String originId;
        private final SFMSyntaxHighlightRequest request;
        private final SFMSyntaxHighlightProvider.Query providerQuery;
        private final CompletableFuture<SFMSyntaxHighlightResult> result = new CompletableFuture<>();
        private final long startedNanos;
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile ScheduledFuture<?> timeoutTask;

        private Pending(
                String originId,
                SFMSyntaxHighlightRequest request,
                SFMSyntaxHighlightProvider.Query providerQuery,
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
            try {
                providerQuery.cancel();
            } catch (RuntimeException cancellationFailure) {
                reason.addSuppressed(cancellationFailure);
            }
            synchronized (lock) {
                active.remove(originId, this);
                switch (kind) {
                    case EXPLICIT -> explicitlyCancelled = increment(
                            explicitlyCancelled,
                            "Syntax cancellation telemetry counter exhausted"
                    );
                    case SUPERSEDED -> superseded = increment(
                            superseded,
                            "Syntax supersession telemetry counter exhausted"
                    );
                    case TIMEOUT -> timedOut = increment(timedOut, "Syntax timeout telemetry counter exhausted");
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
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static long increment(long value, String exhaustedMessage) {
        if (value == Long.MAX_VALUE) throw new IllegalStateException(exhaustedMessage);
        return value + 1;
    }
}
