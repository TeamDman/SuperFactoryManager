package ca.teamdman.sfm.client.syntax;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMSyntaxHighlightCoordinatorTests {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "syntax-highlight-coordinator-test");
        thread.setDaemon(true);
        return thread;
    });

    @AfterEach
    void stopScheduler() {
        scheduler.shutdownNow();
    }

    @Test
    void oneOriginGetsPositiveAssignedIdentityAndValidatedResult() {
        ControlledProvider provider = new ControlledProvider();
        SFMSyntaxHighlightProviderRegistry registry = registry(provider);
        SFMSyntaxHighlightCoordinator coordinator = new SFMSyntaxHighlightCoordinator(registry, scheduler);
        var query = submit(coordinator, "editor:a", 9, "class A {}\n");

        assertEquals(1, query.request().requestId());
        assertEquals(1, query.request().requestGeneration());
        assertEquals(9, query.request().originGeneration());
        provider.complete(query.request(), result(query.request()));

        assertEquals(result(query.request()), query.result().join());
        assertEquals(1, coordinator.telemetry().completed());
        assertEquals(0, coordinator.telemetry().active());
        coordinator.close();
        registry.close();
    }

    @Test
    void severalOriginsRemainConcurrentAndHaveIndependentGenerations() {
        ControlledProvider provider = new ControlledProvider();
        SFMSyntaxHighlightProviderRegistry registry = registry(provider);
        SFMSyntaxHighlightCoordinator coordinator = new SFMSyntaxHighlightCoordinator(registry, scheduler);
        var first = submit(coordinator, "editor:a", 1, "class A {}\n");
        var second = submit(coordinator, "editor:b", 4, "class B {}\n");

        assertEquals(1, first.request().requestGeneration());
        assertEquals(1, second.request().requestGeneration());
        assertEquals(2, coordinator.telemetry().active());
        provider.complete(second.request(), result(second.request()));
        provider.complete(first.request(), result(first.request()));

        first.result().join();
        second.result().join();
        assertEquals(2, coordinator.telemetry().completed());
        coordinator.close();
        registry.close();
    }

    @Test
    void newerRequestSupersedesCancelsAndRejectsLateResultOnlyForSameOrigin() {
        ControlledProvider provider = new ControlledProvider();
        SFMSyntaxHighlightProviderRegistry registry = registry(provider);
        SFMSyntaxHighlightCoordinator coordinator = new SFMSyntaxHighlightCoordinator(registry, scheduler);
        var old = submit(coordinator, "editor:a", 1, "class Old {}\n");
        var other = submit(coordinator, "editor:b", 1, "class Other {}\n");
        var current = submit(coordinator, "editor:a", 2, "class Current {}\n");

        assertInstanceOf(SFMSyntaxHighlightCoordinator.StaleResponseException.class, failure(old.result()));
        assertTrue(provider.entry(old.request()).cancelled.get());
        assertFalse(provider.complete(old.request(), result(old.request())), "cancelled provider future rejects late data");
        provider.complete(current.request(), result(current.request()));
        provider.complete(other.request(), result(other.request()));
        current.result().join();
        other.result().join();

        assertEquals(2, current.request().requestGeneration());
        assertEquals(1, coordinator.telemetry().superseded());
        assertEquals(2, coordinator.telemetry().completed());
        coordinator.close();
        registry.close();
    }

    @Test
    void timeoutAndExplicitCancellationReachProviderAndCannotPublishLateData() throws Exception {
        ControlledProvider provider = new ControlledProvider();
        SFMSyntaxHighlightProviderRegistry registry = registry(provider);
        SFMSyntaxHighlightCoordinator coordinator = new SFMSyntaxHighlightCoordinator(registry, scheduler);
        var timedOut = coordinator.submit(
                "editor:timeout", 1, "java", "class Slow {}\n", 100, Duration.ofMillis(10)
        );

        assertInstanceOf(TimeoutException.class, failureWithin(timedOut.result()));
        assertTrue(provider.entry(timedOut.request()).cancelled.get());
        assertFalse(provider.complete(timedOut.request(), result(timedOut.request())));

        var cancelled = submit(coordinator, "editor:cancel", 1, "class Cancelled {}\n");
        cancelled.cancel();
        assertInstanceOf(CancellationException.class, failure(cancelled.result()));
        assertTrue(provider.entry(cancelled.request()).cancelled.get());
        assertEquals(1, coordinator.telemetry().timedOut());
        assertEquals(1, coordinator.telemetry().explicitlyCancelled());
        coordinator.close();
        registry.close();
    }

    @Test
    void mismatchedLanguageHashAndMalformedUtf8RangesAreRejected() {
        ControlledProvider provider = new ControlledProvider();
        SFMSyntaxHighlightProviderRegistry registry = registry(provider);
        SFMSyntaxHighlightCoordinator coordinator = new SFMSyntaxHighlightCoordinator(registry, scheduler);
        var languageMismatch = submit(coordinator, "editor:language", 1, "class A {}\n");
        provider.complete(languageMismatch.request(), copyIdentity(
                result(languageMismatch.request()),
                "rust",
                languageMismatch.request().sourceSha256()
        ));
        assertInstanceOf(
                SFMSyntaxHighlightCoordinator.MismatchedResponseException.class,
                failure(languageMismatch.result())
        );

        var hashMismatch = submit(coordinator, "editor:hash", 1, "class B {}\n");
        provider.complete(hashMismatch.request(), copyIdentity(
                result(hashMismatch.request()),
                hashMismatch.request().language(),
                SFMSyntaxHighlightRequest.sha256("different source")
        ));
        assertInstanceOf(
                SFMSyntaxHighlightCoordinator.MismatchedResponseException.class,
                failure(hashMismatch.result())
        );

        String unicode = "class Café {}\n";
        var malformed = submit(coordinator, "editor:utf8", 1, unicode);
        int accentStart = unicode.substring(0, unicode.indexOf('é')).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        SFMSyntaxHighlightResult invalidResult = new SFMSyntaxHighlightResult(
                SFMSyntaxHighlightResult.SCHEMA,
                malformed.request().requestId(), malformed.request().requestGeneration(),
                malformed.request().originId(), malformed.request().originGeneration(),
                malformed.request().language(), malformed.request().sourceSha256(), malformed.request().sourceBytes(),
                SFMSyntaxHighlightResult.Outcome.HIGHLIGHTED, true,
                SFMSyntaxHighlightResult.PARSER_FINGERPRINT, SFMSyntaxHighlightResult.FORMATTING_SCHEMA,
                1, SFMSyntaxHighlightResult.CacheEvidence.bypassed(), List.of(),
                List.of(new SFMSyntaxHighlightResult.Span(
                        accentStart + 1L, accentStart + 2L, "type", List.of("aqua")
                ))
        );
        provider.complete(malformed.request(), invalidResult);
        assertInstanceOf(
                SFMSyntaxHighlightCoordinator.InvalidResponseException.class,
                failure(malformed.result())
        );

        assertEquals(2, coordinator.telemetry().mismatchedResponses());
        assertEquals(1, coordinator.telemetry().invalidResponses());
        coordinator.close();
        registry.close();
    }

    @Test
    void noProviderFailsWithoutSubmittingAndTelemetryWindowStaysBounded() {
        SFMSyntaxHighlightProviderRegistry empty = new SFMSyntaxHighlightProviderRegistry();
        SFMSyntaxHighlightCoordinator unavailable = new SFMSyntaxHighlightCoordinator(empty, scheduler);
        assertThrows(SFMSyntaxHighlightCoordinator.NoProviderException.class,
                () -> submit(unavailable, "editor:none", 1, "class A {}\n"));
        assertEquals(0, unavailable.telemetry().submitted());
        unavailable.close();
        empty.close();

        ControlledProvider provider = new ControlledProvider();
        SFMSyntaxHighlightProviderRegistry registry = registry(provider);
        SFMSyntaxHighlightCoordinator coordinator = new SFMSyntaxHighlightCoordinator(registry, scheduler);
        for (int index = 0; index < 70; index++) {
            var query = submit(coordinator, "editor:" + index, 1, "class C" + index + " {}\n");
            provider.complete(query.request(), result(query.request()));
            query.result().join();
        }
        assertEquals(70, coordinator.telemetry().completed());
        assertEquals(64, coordinator.telemetry().latencySamples());
        coordinator.close();
        registry.close();
    }

    private static SFMSyntaxHighlightCoordinator.Handle submit(
            SFMSyntaxHighlightCoordinator coordinator,
            String origin,
            long originGeneration,
            String source
    ) {
        return coordinator.submit(origin, originGeneration, "java", source, 100, Duration.ofSeconds(1));
    }

    private static SFMSyntaxHighlightProviderRegistry registry(ControlledProvider provider) {
        SFMSyntaxHighlightProviderRegistry registry = new SFMSyntaxHighlightProviderRegistry();
        registry.register(10, provider);
        return registry;
    }

    private static SFMSyntaxHighlightResult result(SFMSyntaxHighlightRequest request) {
        return new SFMSyntaxHighlightResult(
                SFMSyntaxHighlightResult.SCHEMA,
                request.requestId(), request.requestGeneration(), request.originId(), request.originGeneration(),
                request.language(), request.sourceSha256(), request.sourceBytes(),
                SFMSyntaxHighlightResult.Outcome.HIGHLIGHTED, true,
                SFMSyntaxHighlightResult.PARSER_FINGERPRINT, SFMSyntaxHighlightResult.FORMATTING_SCHEMA,
                10, SFMSyntaxHighlightResult.CacheEvidence.bypassed(), List.of(), List.of()
        );
    }

    private static SFMSyntaxHighlightResult copyIdentity(
            SFMSyntaxHighlightResult value,
            String language,
            String hash
    ) {
        return new SFMSyntaxHighlightResult(
                value.schema(), value.requestId(), value.requestGeneration(), value.originId(), value.originGeneration(),
                language, hash, value.sourceBytes(), value.outcome(), value.complete(), value.parserFingerprint(),
                value.formattingSchema(), value.elapsedMicros(), value.cache(), value.diagnostics(), value.spans()
        );
    }

    private static Throwable failure(CompletableFuture<?> future) {
        try {
            future.join();
            throw new AssertionError("Expected future to fail");
        } catch (CompletionException failure) {
            return failure.getCause();
        } catch (CancellationException failure) {
            return failure;
        }
    }

    private static Throwable failureWithin(CompletableFuture<?> future) throws Exception {
        try {
            future.get(2, TimeUnit.SECONDS);
            throw new AssertionError("Expected future to fail");
        } catch (java.util.concurrent.ExecutionException failure) {
            return failure.getCause();
        } catch (CancellationException failure) {
            return failure;
        }
    }

    private static final class ControlledProvider implements SFMSyntaxHighlightProvider {
        private final List<Entry> entries = new ArrayList<>();

        @Override public ResourceLocation id() { return new ResourceLocation("sfm", "controlled_syntax"); }
        @Override public boolean available() { return true; }

        @Override
        public synchronized Query query(SFMSyntaxHighlightRequest request) {
            Entry entry = new Entry(request);
            entries.add(entry);
            return new Query(request, entry.result, () -> entry.cancelled.set(true));
        }

        synchronized Entry entry(SFMSyntaxHighlightRequest request) {
            return entries.stream().filter(entry -> entry.request.equals(request)).findFirst().orElseThrow();
        }

        boolean complete(SFMSyntaxHighlightRequest request, SFMSyntaxHighlightResult result) {
            return entry(request).result.complete(result);
        }

        private static final class Entry {
            private final SFMSyntaxHighlightRequest request;
            private final CompletableFuture<SFMSyntaxHighlightResult> result = new CompletableFuture<>();
            private final AtomicBoolean cancelled = new AtomicBoolean();

            private Entry(SFMSyntaxHighlightRequest request) {
                this.request = request;
            }
        }
    }
}
