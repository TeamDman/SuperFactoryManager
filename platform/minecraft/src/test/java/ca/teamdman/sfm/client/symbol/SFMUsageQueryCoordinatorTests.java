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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMUsageQueryCoordinatorTests {
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);

    @AfterEach
    void stopScheduler() throws InterruptedException {
        scheduler.shutdownNow();
        assertTrue(scheduler.awaitTermination(1, TimeUnit.SECONDS));
    }

    @Test
    void newerReferenceQueryCancelsOnlyTheSameOrigin() {
        ControlledProvider provider = new ControlledProvider();
        SFMUsageQueryCoordinator coordinator = new SFMUsageQueryCoordinator(provider, scheduler);

        var old = coordinator.submit("editor:a", template(), Duration.ofSeconds(1));
        var other = coordinator.submit("editor:b", template(), Duration.ofSeconds(1));
        var current = coordinator.submit("editor:a", template(), Duration.ofSeconds(1));

        assertInstanceOf(SFMUsageQueryCoordinator.StaleResponseException.class, failure(old.result()));
        assertTrue(provider.entries.get(0).cancelled.get());
        provider.complete(other.request(), result(other.request()));
        provider.complete(current.request(), result(current.request()));
        assertEquals(other.request().requestId(), other.result().join().requestId());
        assertEquals(current.request().requestGeneration(), current.result().join().requestGeneration());
        assertEquals(2, coordinator.telemetry().completed());
        assertEquals(0, coordinator.telemetry().active());
        coordinator.close();
    }

    @Test
    void mismatchedReferenceResultFailsClosed() {
        ControlledProvider provider = new ControlledProvider();
        SFMUsageQueryCoordinator coordinator = new SFMUsageQueryCoordinator(provider, scheduler);
        var query = coordinator.submit("editor:a", template(), Duration.ofSeconds(1));
        SFMUsageAtPositionRequest wrong = withIdentity(
                query.request(), query.request().requestId() + 1, query.request().requestGeneration());

        provider.complete(query.request(), result(wrong));

        assertInstanceOf(SFMUsageQueryCoordinator.MismatchedResponseException.class, failure(query.result()));
        assertEquals(1, coordinator.telemetry().mismatchedResponses());
        assertEquals(0, coordinator.telemetry().warmSampleCount());
        coordinator.close();
    }

    @Test
    void timeoutCancelsTheProvider() {
        ControlledProvider provider = new ControlledProvider();
        SFMUsageQueryCoordinator coordinator = new SFMUsageQueryCoordinator(provider, scheduler);
        var query = coordinator.submit("editor:a", template(), Duration.ofMillis(5));

        assertInstanceOf(java.util.concurrent.TimeoutException.class, failure(query.result()));
        assertTrue(provider.entries.get(0).cancelled.get());
        assertEquals(1, coordinator.telemetry().timedOut());
        coordinator.close();
    }

    @Test
    void definitionAndReferenceCoordinatorsCanShareOneCollisionFreeRequestSequence() {
        ControlledProvider provider = new ControlledProvider();
        SFMSymbolNavigationProviderRegistry registry = new SFMSymbolNavigationProviderRegistry();
        registry.register(10, provider);
        AtomicLong sequence = new AtomicLong();
        LongSupplier requestIds = () -> sequence.incrementAndGet();
        SFMDefinitionQueryCoordinator definitions = new SFMDefinitionQueryCoordinator(
                registry, scheduler, System::nanoTime, requestIds);
        SFMUsageQueryCoordinator references = new SFMUsageQueryCoordinator(
                provider, scheduler, System::nanoTime, requestIds);

        var definition = definitions.submit("editor:a", template().asDefinitionRequest(), Duration.ofSeconds(1));
        var reference = references.submit("editor:a", template(), Duration.ofSeconds(1));

        assertEquals(1, definition.request().requestId());
        assertEquals(2, reference.request().requestId());
        definition.cancel();
        reference.cancel();
        definitions.close();
        references.close();
        registry.close();
    }

    private static Throwable failure(CompletableFuture<?> future) {
        CompletionException failure = assertThrows(CompletionException.class, future::join);
        return failure.getCause();
    }

    private static SFMUsageAtPositionRequest template() {
        String text = "class A { void f() { int value = 1; } }\n";
        SFMDefinitionRequest.SourceRoot root = new SFMDefinitionRequest.SourceRoot(
                "custom-0", "custom", "source", "custom", true);
        SFMDefinitionRequest definition = new SFMDefinitionRequest(
                0,
                0,
                new SFMDefinitionRequest.Workspace(
                        "1.19.2", SFMDefinitionRequest.ClasspathMode.ISOLATED,
                        List.of(root), "blake3:workspace", Optional.empty(),
                        "blake3:" + "0".repeat(64), 3),
                SFMDefinitionRequest.Document.sha256(
                        "file:///source/example/A.java", "custom-0", "example/A.java",
                        "source/example/A.java", "custom", text, Optional.empty()),
                SFMDefinitionRequest.Position.fromText(text, 1, text.indexOf("value") + 1L)
        );
        return SFMUsageAtPositionRequest.fromDefinition(definition);
    }

    private static SFMUsageAtPositionRequest withIdentity(
            SFMUsageAtPositionRequest template,
            long requestId,
            long requestGeneration
    ) {
        return new SFMUsageAtPositionRequest(
                SFMUsageAtPositionRequest.SCHEMA, requestId, requestGeneration,
                template.workspace(), template.document(), template.position());
    }

    private static SFMUsageAtPositionResult result(SFMUsageAtPositionRequest request) {
        SFMDefinitionResult definition = SFMSymbolServerProtocolTests.result(request.asDefinitionRequest());
        return new SFMUsageAtPositionResult(
                SFMUsageAtPositionResult.SCHEMA,
                request.requestId(), request.requestGeneration(),
                request.workspace().workspaceGeneration(),
                definition.outcome(), definition.context(), definition.document(), definition.position(),
                definition.symbols(), definition.definitions(), List.of(), List.of(),
                definition.completeness(), definition.diagnostics(), definition.recoveryActions(),
                definition.dependencyIndex());
    }

    private static final class ControlledProvider
            implements SFMSymbolNavigationProvider, SFMSymbolReferenceProvider {
        private final List<Entry> entries = new ArrayList<>();

        @Override public ResourceLocation id() { return new ResourceLocation("sfm", "controlled-mixed"); }
        @Override public boolean available() { return true; }
        @Override public void close() { }

        @Override
        public SFMSymbolNavigationProvider.Query query(SFMDefinitionRequest request) {
            return new SFMSymbolNavigationProvider.Query(
                    request, new CompletableFuture<>(), () -> { });
        }

        @Override
        public ReferenceQuery query(SFMUsageAtPositionRequest request) {
            Entry entry = new Entry(request);
            entries.add(entry);
            return new ReferenceQuery(request, entry.result, () -> entry.cancelled.set(true));
        }

        void complete(SFMUsageAtPositionRequest request, SFMUsageAtPositionResult result) {
            entries.stream().filter(entry -> entry.request.equals(request)).findFirst().orElseThrow()
                    .result.complete(result);
        }

        private static final class Entry {
            private final SFMUsageAtPositionRequest request;
            private final CompletableFuture<SFMUsageAtPositionResult> result = new CompletableFuture<>();
            private final AtomicBoolean cancelled = new AtomicBoolean();
            private Entry(SFMUsageAtPositionRequest request) { this.request = request; }
        }
    }
}
