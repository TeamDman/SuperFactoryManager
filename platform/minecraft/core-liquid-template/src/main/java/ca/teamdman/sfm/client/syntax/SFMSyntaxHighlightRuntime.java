package ca.teamdman.sfm.client.syntax;

import ca.teamdman.sfm.client.syntax.process.SFMSyntaxServerHighlightProvider;
import ca.teamdman.sfm.client.syntax.process.SFMSyntaxServerSupervisor;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/** Lazily owns the one reusable Arborium worker shared by all Java editor panels. */
public final class SFMSyntaxHighlightRuntime implements SFMSyntaxHighlightService, AutoCloseable {
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(5);
    private static final long MAXIMUM_SPANS = SFMSyntaxHighlightLimits.DEFAULT_MAXIMUM_SPANS;

    private final SFMSyntaxServerHighlightProvider provider;
    private final SFMSyntaxHighlightProviderRegistry providers;
    private final ScheduledExecutorService scheduler;
    private final SFMSyntaxHighlightCoordinator coordinator;
    private final AtomicBoolean closed = new AtomicBoolean();

    private SFMSyntaxHighlightRuntime() {
        provider = new SFMSyntaxServerHighlightProvider(
                SFMSyntaxServerSupervisor.Configuration.defaults()
        );
        providers = new SFMSyntaxHighlightProviderRegistry();
        providers.register(100, provider);
        scheduler = Executors.newSingleThreadScheduledExecutor(daemonThreads("sfm-syntax-query"));
        coordinator = new SFMSyntaxHighlightCoordinator(providers, scheduler);
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "sfm-syntax-highlight-shutdown"));
    }

    public static SFMSyntaxHighlightRuntime get() {
        return Holder.INSTANCE;
    }

    @Override
    public Submission query(String originId, long originGeneration, String language, String exactSource) {
        if (closed.get()) throw new IllegalStateException("SFM syntax highlighting is closed");
        SFMSyntaxHighlightCoordinator.Handle handle = coordinator.submit(
                originId,
                originGeneration,
                language,
                exactSource,
                MAXIMUM_SPANS,
                QUERY_TIMEOUT
        );
        return new Submission(handle.request(), handle.result(), handle::cancel);
    }

    public SFMSyntaxHighlightCoordinator.Telemetry coordinatorTelemetry() {
        return coordinator.telemetry();
    }

    public SFMSyntaxServerSupervisor.Telemetry workerTelemetry() {
        return provider.telemetry();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        coordinator.close();
        providers.close();
        scheduler.shutdownNow();
    }

    private static ThreadFactory daemonThreads(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class Holder {
        private static final SFMSyntaxHighlightRuntime INSTANCE = new SFMSyntaxHighlightRuntime();
    }
}
