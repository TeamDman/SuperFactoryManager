package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextCursorProjection;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextPosition;
import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import net.minecraft.SharedConstants;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** Lazily owns the one supervised symbol worker used by client navigation. */
public final class SFMSymbolNavigationRuntime
        implements SFMDefinitionLookupService, SFMReferenceLookupService,
        SFMJavaInteractionMapLookupService, AutoCloseable {
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
    private final SFMUsageQueryCoordinator usageCoordinator;
    private final SFMDefinitionContextAdapter adapter = new SFMDefinitionContextAdapter();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong requestSequence = new AtomicLong();

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
        LongSupplier requestIds = () -> requestSequence.updateAndGet(SFMSymbolNavigationRuntime::incrementRequestId);
        coordinator = new SFMDefinitionQueryCoordinator(providers, scheduler, System::nanoTime, requestIds);
        usageCoordinator = new SFMUsageQueryCoordinator(provider, scheduler, System::nanoTime, requestIds);
        Runtime.getRuntime().addShutdownHook(new Thread(
                this::close,
                "sfm-symbol-navigation-shutdown"
        ));
    }

    public static SFMSymbolNavigationRuntime get() {
        return Holder.INSTANCE;
    }

    @Override
    public SFMDefinitionLookupService.Submission query(SFMContextContribution contribution) {
        Objects.requireNonNull(contribution, "contribution");
        if (closed.get()) {
            return new SFMDefinitionLookupService.Submission(CompletableFuture.failedFuture(
                    new IllegalStateException("SFM symbol navigation is closed")), () -> { });
        }

        CompletableFuture<SFMDefinitionLookupService.Lookup> answer = new CompletableFuture<>();
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
                        "SFM_DEFINITION_RUNTIME_SUBMITTED request={} generation={} workspace_generation={} branch={} address={} root_id={} report_path={} source_set={} line={} column={} byte={} content_hash={}",
                        handle.request().requestId(),
                        handle.request().requestGeneration(),
                        handle.request().workspace().workspaceGeneration(),
                        handle.request().workspace().branch(),
                        handle.request().document().address(),
                        handle.request().document().rootId(),
                        handle.request().document().reportPath(),
                        handle.request().document().sourceSet(),
                        handle.request().position().line(),
                        handle.request().position().column(),
                        handle.request().position().byteOffset(),
                        handle.request().document().contentHash()
                );
                activeCancellation.set(handle::cancel);
                if (cancelled.get()) {
                    handle.cancel();
                    answer.cancel(false);
                    return;
                }
                handle.result().whenComplete((result, queryFailure) -> {
                    if (queryFailure == null && result != null) {
                        SFM.LOGGER.info(
                                "SFM_DEFINITION_RUNTIME_COMPLETED request={} success=true outcome={} definitions={} completeness={} diagnostic_codes={} report_path={} line={} column={} byte={}",
                                handle.request().requestId(),
                                result.outcome(),
                                result.definitions().size(),
                                result.completeness(),
                                String.join(",", result.diagnostics().stream()
                                        .map(SFMDefinitionResult.Diagnostic::code)
                                        .distinct()
                                        .limit(8)
                                        .toList()),
                                handle.request().document().reportPath(),
                                handle.request().position().line(),
                                handle.request().position().column(),
                                handle.request().position().byteOffset()
                        );
                    } else {
                        SFM.LOGGER.info(
                                "SFM_DEFINITION_RUNTIME_COMPLETED request={} success=false failure_type={} report_path={} line={} column={} byte={}",
                                handle.request().requestId(),
                                queryFailure == null ? "null-result" : unwrap(queryFailure).getClass().getSimpleName(),
                                handle.request().document().reportPath(),
                                handle.request().position().line(),
                                handle.request().position().column(),
                                handle.request().position().byteOffset()
                        );
                    }
                    if (queryFailure != null) answer.completeExceptionally(unwrap(queryFailure));
                    else answer.complete(new SFMDefinitionLookupService.Lookup(hello, result));
                });
            } catch (RuntimeException failure) {
                SFM.LOGGER.warn("SFM_DEFINITION_RUNTIME_FAILED failure_type={}",
                        failure.getClass().getSimpleName());
                answer.completeExceptionally(failure);
            }
        });
        return new SFMDefinitionLookupService.Submission(answer, () -> {
            if (!cancelled.compareAndSet(false, true)) return;
            activeCancellation.get().run();
            answer.cancel(false);
        });
    }

    @Override
    public SFMReferenceLookupService.Submission queryReferences(SFMContextContribution contribution) {
        Objects.requireNonNull(contribution, "contribution");
        if (closed.get()) {
            return new SFMReferenceLookupService.Submission(CompletableFuture.failedFuture(
                    new IllegalStateException("SFM symbol navigation is closed")), () -> { });
        }

        CompletableFuture<SFMReferenceLookupService.Lookup> answer = new CompletableFuture<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Runnable> activeCancellation = new AtomicReference<>(() -> { });
        provider.start(HANDSHAKE_TIMEOUT).whenComplete((hello, startupFailure) -> {
            try {
                SFM.LOGGER.info("SFM_REFERENCE_RUNTIME_READY completed={} cancelled={}",
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
                    answer.completeExceptionally(new ContextUnavailableException(adaptation));
                    return;
                }
                SFMUsageQueryCoordinator.Handle handle = usageCoordinator.submit(
                        contribution.originId().toString(),
                        SFMUsageAtPositionRequest.fromDefinition(adaptation.request().orElseThrow()),
                        QUERY_TIMEOUT
                );
                SFM.LOGGER.info(
                        "SFM_REFERENCE_RUNTIME_SUBMITTED request={} generation={} workspace_generation={}",
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
                            "SFM_REFERENCE_RUNTIME_COMPLETED request={} success={} failure_type={}",
                            handle.request().requestId(),
                            queryFailure == null,
                            queryFailure == null ? "none" : unwrap(queryFailure).getClass().getSimpleName()
                    );
                    if (queryFailure != null) answer.completeExceptionally(unwrap(queryFailure));
                    else answer.complete(new SFMReferenceLookupService.Lookup(hello, result));
                });
            } catch (RuntimeException failure) {
                SFM.LOGGER.warn("SFM_REFERENCE_RUNTIME_FAILED failure_type={}",
                        failure.getClass().getSimpleName());
                answer.completeExceptionally(failure);
            }
        });
        return new SFMReferenceLookupService.Submission(answer, () -> {
            if (!cancelled.compareAndSet(false, true)) return;
            activeCancellation.get().run();
            answer.cancel(false);
        });
    }

    @Override
    public SFMJavaInteractionMapLookupService.Submission queryInteractionMap(
            SFMContextContribution contribution
    ) {
        Objects.requireNonNull(contribution, "contribution");
        if (closed.get()) {
            return new SFMJavaInteractionMapLookupService.Submission(CompletableFuture.failedFuture(
                    new IllegalStateException("SFM symbol navigation is closed")), () -> { });
        }
        if (!(contribution.projection() instanceof SFMContextDocumentProjection document)) {
            return new SFMJavaInteractionMapLookupService.Submission(CompletableFuture.failedFuture(
                    new IllegalArgumentException("Java interaction maps require a text document")), () -> { });
        }

        long requestId = requestSequence.updateAndGet(SFMSymbolNavigationRuntime::incrementRequestId);
        long requestGeneration = contribution.generations().contentGeneration();
        SFMContextContribution documentContribution = withDocumentOriginCursor(contribution, document);
        CompletableFuture<SFMJavaInteractionMapLookupService.Lookup> answer = new CompletableFuture<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Runnable> activeCancellation = new AtomicReference<>(() -> { });
        provider.start(HANDSHAKE_TIMEOUT).whenComplete((hello, startupFailure) -> {
            try {
                if (cancelled.get()) {
                    answer.cancel(false);
                    return;
                }
                if (startupFailure != null) {
                    answer.completeExceptionally(unwrap(startupFailure));
                    return;
                }
                SFMDefinitionContextAdapter.Adaptation adaptation = adapter.adapt(
                        documentContribution,
                        Optional.of(hello),
                        requestId,
                        requestGeneration
                );
                if (!adaptation.success()) {
                    answer.completeExceptionally(new ContextUnavailableException(adaptation));
                    return;
                }
                SFMDefinitionRequest definition = adaptation.request().orElseThrow();
                SFMJavaInteractionMap.Request request = new SFMJavaInteractionMap.Request(
                        requestId,
                        requestGeneration,
                        definition.workspace(),
                        definition.document()
                );
                SFMJavaInteractionMapPager.Submission submission = SFMJavaInteractionMapPager.collect(
                        request,
                        () -> requestSequence.updateAndGet(SFMSymbolNavigationRuntime::incrementRequestId),
                        pageRequest -> {
                            SFMSymbolServerSupervisor.InteractionMapSubmission page =
                                    provider.queryInteractionMap(pageRequest);
                            return new SFMJavaInteractionMapPager.PageSubmission(
                                    page.result(), page.cancellation());
                        }
                );
                activeCancellation.set(submission.cancellation());
                if (cancelled.get()) {
                    submission.cancellation().run();
                    answer.cancel(false);
                    return;
                }
                submission.result().whenComplete((result, queryFailure) -> {
                    if (queryFailure != null) answer.completeExceptionally(unwrap(queryFailure));
                    else answer.complete(new SFMJavaInteractionMapLookupService.Lookup(hello, result));
                });
            } catch (RuntimeException failure) {
                answer.completeExceptionally(failure);
            }
        });
        return new SFMJavaInteractionMapLookupService.Submission(answer, () -> {
            if (!cancelled.compareAndSet(false, true)) return;
            activeCancellation.get().run();
            answer.cancel(false);
        });
    }

    private static SFMContextContribution withDocumentOriginCursor(
            SFMContextContribution contribution,
            SFMContextDocumentProjection document
    ) {
        var origin = SFMContextTextCoordinates.atUtf16Offset(document.currentText(), 0);
        SFMContextDocumentProjection projected = SFMContextDocumentProjection.capture(
                document.editorId(),
                document.baseline(),
                document.currentText(),
                document.dirty(),
                document.readOnly(),
                java.util.List.of(new SFMContextCursorProjection(
                        "interaction-map-origin",
                        new SFMContextPosition.Text(origin),
                        true,
                        true
                )),
                document.selections()
        );
        return new SFMContextContribution(contribution.originId(), contribution.generations(), projected);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        usageCoordinator.close();
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

    private static long incrementRequestId(long value) {
        if (value == Long.MAX_VALUE) throw new IllegalStateException("Symbol request identity counter exhausted");
        return value + 1;
    }

    private static final class Holder {
        private static final SFMSymbolNavigationRuntime INSTANCE = new SFMSymbolNavigationRuntime();
    }
}
