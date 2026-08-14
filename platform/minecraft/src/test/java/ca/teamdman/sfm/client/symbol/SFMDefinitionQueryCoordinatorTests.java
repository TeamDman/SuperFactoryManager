package ca.teamdman.sfm.client.symbol;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDefinitionQueryCoordinatorTests {
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);

    @AfterEach
    void stopScheduler() throws InterruptedException {
        scheduler.shutdownNow();
        assertTrue(scheduler.awaitTermination(1, TimeUnit.SECONDS));
    }

    @Test
    void newerQueryCancelsOnlyTheSameOriginAndRejectsLateResults() {
        ControlledProvider provider = new ControlledProvider();
        SFMSymbolNavigationProviderRegistry registry = registry(provider);
        SFMDefinitionQueryCoordinator coordinator = new SFMDefinitionQueryCoordinator(registry, scheduler);

        var old = coordinator.submit("editor:a", template(), Duration.ofSeconds(1));
        var other = coordinator.submit("editor:b", template(), Duration.ofSeconds(1));
        var current = coordinator.submit("editor:a", template(), Duration.ofSeconds(1));

        assertInstanceOf(SFMDefinitionQueryCoordinator.StaleResponseException.class, failure(old.result()));
        assertTrue(provider.entries.get(0).cancelled.get());
        provider.complete(other.request(), result(other.request()));
        provider.complete(current.request(), result(current.request()));
        assertEquals(other.request().requestId(), other.result().join().requestId());
        assertEquals(current.request().requestGeneration(), current.result().join().requestGeneration());

        SFMDefinitionQueryCoordinator.Telemetry telemetry = coordinator.telemetry();
        assertEquals(3, telemetry.submitted());
        assertEquals(2, telemetry.completed());
        assertTrue(telemetry.staleResponses() >= 1);
        assertEquals(0, telemetry.active());
        coordinator.close();
        registry.close();
    }

    @Test
    void mismatchedResponseFailsClosedAndDoesNotBecomeLatencyEvidence() {
        ControlledProvider provider = new ControlledProvider();
        SFMSymbolNavigationProviderRegistry registry = registry(provider);
        SFMDefinitionQueryCoordinator coordinator = new SFMDefinitionQueryCoordinator(registry, scheduler);
        var query = coordinator.submit("editor:a", template(), Duration.ofSeconds(1));

        SFMDefinitionRequest wrong = query.request().withIdentity(
                query.request().requestId() + 1,
                query.request().requestGeneration()
        );
        provider.complete(query.request(), result(wrong));

        assertInstanceOf(SFMDefinitionQueryCoordinator.MismatchedResponseException.class, failure(query.result()));
        assertEquals(1, coordinator.telemetry().mismatchedResponses());
        assertEquals(0, coordinator.telemetry().warmSampleCount());
        coordinator.close();
        registry.close();
    }

    @Test
    void timeoutCancelsProviderAndNoProviderFailsBeforeSubmitting() {
        SFMSymbolNavigationProviderRegistry empty = new SFMSymbolNavigationProviderRegistry();
        SFMDefinitionQueryCoordinator noProvider = new SFMDefinitionQueryCoordinator(empty, scheduler);
        assertThrows(SFMDefinitionQueryCoordinator.NoProviderException.class,
                () -> noProvider.submit("editor:a", template(), Duration.ofSeconds(1)));
        noProvider.close();

        ControlledProvider provider = new ControlledProvider();
        SFMSymbolNavigationProviderRegistry registry = registry(provider);
        SFMDefinitionQueryCoordinator coordinator = new SFMDefinitionQueryCoordinator(registry, scheduler);
        var query = coordinator.submit("editor:a", template(), Duration.ofMillis(5));

        assertInstanceOf(java.util.concurrent.TimeoutException.class, failure(query.result()));
        assertTrue(provider.entries.get(0).cancelled.get());
        assertEquals(1, coordinator.telemetry().timedOut());
        coordinator.close();
        registry.close();
    }

    private static Throwable failure(CompletableFuture<?> future) {
        CompletionException failure = assertThrows(CompletionException.class, future::join);
        return failure.getCause();
    }

    private static SFMSymbolNavigationProviderRegistry registry(ControlledProvider provider) {
        SFMSymbolNavigationProviderRegistry registry = new SFMSymbolNavigationProviderRegistry();
        registry.register(10, provider);
        return registry;
    }

    private static SFMDefinitionRequest template() {
        String text = "class A {}\n";
        SFMDefinitionRequest.SourceRoot root = new SFMDefinitionRequest.SourceRoot(
                "custom-0", "custom", "source", "custom", true
        );
        return new SFMDefinitionRequest(
                0,
                0,
                new SFMDefinitionRequest.Workspace(
                        "1.19.2",
                        SFMDefinitionRequest.ClasspathMode.ISOLATED,
                        List.of(root),
                        "blake3:workspace",
                        Optional.empty(),
                        3
                ),
                SFMDefinitionRequest.Document.sha256(
                        "file:///source/example/A.java",
                        "custom-0",
                        "example/A.java",
                        "source/example/A.java",
                        "custom",
                        text,
                        Optional.empty()
                ),
                SFMDefinitionRequest.Position.fromText(text, 1, 7)
        );
    }

    private static SFMDefinitionResult result(SFMDefinitionRequest request) {
        return new SFMDefinitionResult(
                SFMDefinitionResult.SCHEMA,
                request.requestId(),
                request.requestGeneration(),
                request.workspace().workspaceGeneration(),
                SFMDefinitionResult.Outcome.NO_SYMBOL,
                new SFMDefinitionResult.AnalysisContext(
                        "1.19.2", "1.19.2", "17", "jdk",
                        request.workspace().sourceRoots(),
                        List.of(new SFMDefinitionResult.SourceSet("custom", List.of("custom"))),
                        List.of(), request.workspace().classpathMode(),
                        request.workspace().classpathFingerprint(), "arborium", "blake3:index"
                ),
                new SFMDefinitionResult.DocumentIdentity(
                        request.document().address(), request.document().rootId(),
                        request.document().rootRelativePath(), request.document().reportPath(),
                        request.document().sourceSet(), request.document().contentHash(),
                        request.document().diskContentHash()
                ),
                request.position(),
                List.of(), List.of(), SFMDefinitionResult.Completeness.COMPLETE,
                List.of(), List.of(), Optional.empty()
        );
    }

    private static final class ControlledProvider implements SFMSymbolNavigationProvider {
        private final List<Entry> entries = new ArrayList<>();

        @Override public ResourceLocation id() { return new ResourceLocation("sfm", "controlled"); }
        @Override public boolean available() { return true; }

        @Override
        public Query query(SFMDefinitionRequest request) {
            Entry entry = new Entry(request);
            entries.add(entry);
            return new Query(request, entry.result, () -> entry.cancelled.set(true));
        }

        void complete(SFMDefinitionRequest request, SFMDefinitionResult result) {
            entries.stream()
                    .filter(entry -> entry.request.equals(request))
                    .findFirst()
                    .orElseThrow()
                    .result.complete(result);
        }

        private static final class Entry {
            private final SFMDefinitionRequest request;
            private final CompletableFuture<SFMDefinitionResult> result = new CompletableFuture<>();
            private final AtomicBoolean cancelled = new AtomicBoolean();
            private Entry(SFMDefinitionRequest request) { this.request = request; }
        }
    }
}
