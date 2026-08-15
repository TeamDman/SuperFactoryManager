package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import net.minecraft.SharedConstants;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Lazily owns the one supervised symbol worker used by client navigation. */
public final class SFMSymbolNavigationRuntime implements SFMDefinitionLookupService, AutoCloseable {
    public static final String BRANCH_PROPERTY = "sfm.symbol.workerBranch";
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(10);
    // A fresh worker parses the complete branch surface before its reusable
    // resolution cache exists. The installed-process contract permits that
    // bounded cold start to take up to 30 seconds; subsequent requests are
    // warm and remain independently cancellable by the originating action.
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(30);

    private final SFMSymbolServerNavigationProvider provider;
    private final SFMSymbolNavigationProviderRegistry providers;
    private final ScheduledExecutorService scheduler;
    private final SFMDefinitionQueryCoordinator coordinator;
    private final SFMDefinitionContextAdapter adapter = new SFMDefinitionContextAdapter();
    private final AtomicBoolean closed = new AtomicBoolean();

    private SFMSymbolNavigationRuntime() {
        String configuredBranch = System.getProperty(BRANCH_PROPERTY, "").trim();
        String branch = configuredBranch.isEmpty()
                ? SharedConstants.getCurrentVersion().getName()
                : configuredBranch;
        provider = new SFMSymbolServerNavigationProvider(
                SFMSymbolServerSupervisor.Configuration.defaults(branch)
                        .withRequestTimeout(QUERY_TIMEOUT));
        providers = new SFMSymbolNavigationProviderRegistry();
        providers.register(100, provider);
        scheduler = Executors.newSingleThreadScheduledExecutor(daemonThreads("sfm-definition-query"));
        coordinator = new SFMDefinitionQueryCoordinator(providers, scheduler);
        Runtime.getRuntime().addShutdownHook(new Thread(
                this::close,
                "sfm-symbol-navigation-shutdown"
        ));
    }

    public static SFMSymbolNavigationRuntime get() {
        return Holder.INSTANCE;
    }

    @Override
    public Submission query(SFMContextContribution contribution) {
        Objects.requireNonNull(contribution, "contribution");
        if (closed.get()) {
            return new Submission(CompletableFuture.failedFuture(
                    new IllegalStateException("SFM symbol navigation is closed")), () -> { });
        }

        CompletableFuture<Lookup> answer = new CompletableFuture<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Runnable> activeCancellation = new AtomicReference<>(() -> { });
        provider.start(HANDSHAKE_TIMEOUT).whenComplete((hello, startupFailure) -> {
            try {
                SFM.LOGGER.info("SFM_DEFINITION_RUNTIME_READY completed={} cancelled={}",
                        startupFailure == null, cancelled.get());
                if (cancelled.get()) {
                    answer.cancel(false);
                    return;
                }
                if (startupFailure != null) {
                    answer.completeExceptionally(unwrap(startupFailure));
                    return;
                }
                SFMDefinitionContextAdapter.Adaptation adaptation = adapter.adapt(
                        contribution,
                        Optional.of(hello),
                        1,
                        0
                );
                if (!adaptation.success()) {
                    SFM.LOGGER.info("SFM_DEFINITION_RUNTIME_ADAPT rejected={}",
                            adaptation.diagnostics().get(0).code());
                    answer.completeExceptionally(new ContextUnavailableException(adaptation));
                    return;
                }
                SFMDefinitionQueryCoordinator.Handle handle = coordinator.submit(
                        contribution.originId().toString(),
                        adaptation.request().orElseThrow(),
                        QUERY_TIMEOUT
                );
                SFM.LOGGER.info(
                        "SFM_DEFINITION_RUNTIME_SUBMITTED request={} generation={} workspace_generation={}",
                        handle.request().requestId(),
                        handle.request().requestGeneration(),
                        handle.request().workspace().workspaceGeneration()
                );
                activeCancellation.set(handle::cancel);
                if (cancelled.get()) {
                    handle.cancel();
                    answer.cancel(false);
                    return;
                }
                handle.result().whenComplete((result, queryFailure) -> {
                    SFM.LOGGER.info(
                            "SFM_DEFINITION_RUNTIME_COMPLETED request={} success={} failure_type={}",
                            handle.request().requestId(),
                            queryFailure == null,
                            queryFailure == null ? "none" : unwrap(queryFailure).getClass().getSimpleName()
                    );
                    if (queryFailure != null) answer.completeExceptionally(unwrap(queryFailure));
                    else answer.complete(new Lookup(hello, result));
                });
            } catch (RuntimeException failure) {
                SFM.LOGGER.warn("SFM_DEFINITION_RUNTIME_FAILED failure_type={}",
                        failure.getClass().getSimpleName());
                answer.completeExceptionally(failure);
            }
        });
        return new Submission(answer, () -> {
            if (!cancelled.compareAndSet(false, true)) return;
            activeCancellation.get().run();
            answer.cancel(false);
        });
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        coordinator.close();
        providers.close();
        scheduler.shutdownNow();
    }

    public static final class ContextUnavailableException extends IllegalStateException {
        private final SFMDefinitionContextAdapter.Adaptation adaptation;

        private ContextUnavailableException(SFMDefinitionContextAdapter.Adaptation adaptation) {
            super(adaptation.diagnostics().stream()
                    .map(SFMDefinitionContextAdapter.Diagnostic::message)
                    .findFirst()
                    .orElse("The focused editor cannot be used for definition lookup"));
            this.adaptation = adaptation;
        }

        public SFMDefinitionContextAdapter.Adaptation adaptation() {
            return adaptation;
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static ThreadFactory daemonThreads(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class Holder {
        private static final SFMSymbolNavigationRuntime INSTANCE = new SFMSymbolNavigationRuntime();
    }
}
