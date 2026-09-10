package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewSurfaceRuntimeTests {
    @Test
    void generationIsLazyOffCallerThreadCachedAndSourceMappedByImmutableDocumentIdentity() {
        AtomicInteger invocations = new AtomicInteger();
        AtomicReference<Thread> generatorThread = new AtomicReference<>();
        Thread caller = Thread.currentThread();
        SFMReleaseReviewSurfaceRuntime runtime = runtime((configuration, json, cancellation) -> {
            invocations.incrementAndGet();
            generatorThread.set(Thread.currentThread());
            var request = SFMReleaseReviewSurfaceJsonCodec.decodeRequest(json);
            return new SFMReleaseReviewSurfaceRuntime.ProcessResult(
                    0,
                    SFMReleaseReviewSurfaceJsonCodec.encodeSurface(
                            SFMReleaseReviewSurfaceJsonCodecTests.afterSurface(request)),
                    ""
            );
        });
        try {
            var recipe = recipe();
            SFMExplorerCancellationToken cancellation = new SFMExplorerCancellationToken();
            var firstFuture = runtime.generate(recipe, 7, cancellation);
            var first = firstFuture.join();
            var secondFuture = runtime.generate(recipe, 7, new SFMExplorerCancellationToken());
            var second = secondFuture.join();

            assertFalse(caller == generatorThread.get(), "generator must never run on the render/caller thread");
            assertEquals(1, invocations.get());
            assertSame(firstFuture, secondFuture, "stable immutable inputs must reuse one cache future");
            assertEquals(first.identity(), second.identity());
            assertEquals(runtime.stableCacheIdentity(recipe), runtime.stableCacheIdentity(recipe()));

            SFMTextDocumentSnapshot snapshot = first.snapshot();
            assertTrue(snapshot.ready());
            assertTrue(snapshot.readOnly());
            assertEquals(SFMTextDocumentLanguage.diff(), snapshot.language());
            assertFalse(snapshot.language().usesLocalSfmlHighlighting());
            assertEquals(first.surface(), runtime.sourceMap(snapshot).orElseThrow());
            int length = first.surface().text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            assertEquals(1, runtime.projectToSources(snapshot,
                    new SFMReleaseReviewSurfaceV1.Utf8Range(0, length)).size());
            assertTrue(runtime.sourceMap(snapshot.withSavedText(snapshot.text() + "stale")).isEmpty());
        } finally {
            runtime.close();
        }
    }

    @Test
    void invalidAndStaleWorkerOutputBecomeTypedReadOnlyDiagnosticDocumentsAndAreNotCached() {
        AtomicInteger invocations = new AtomicInteger();
        SFMReleaseReviewSurfaceRuntime runtime = runtime((configuration, json, cancellation) -> {
            invocations.incrementAndGet();
            var request = SFMReleaseReviewSurfaceJsonCodec.decodeRequest(json);
            var valid = SFMReleaseReviewSurfaceJsonCodecTests.afterSurface(request);
            var stale = new SFMReleaseReviewSurfaceV1.Surface(
                    valid.schema(), valid.requestId() + 1, valid.requestGeneration(), valid.filePairId(),
                    valid.surfaceKind(), valid.algorithm(), valid.outcome(), valid.complete(), valid.fallbackKind(),
                    valid.text(), valid.textSha256(), valid.mappings(), valid.regions(), valid.correspondence(),
                    valid.diagnostics());
            return new SFMReleaseReviewSurfaceRuntime.ProcessResult(
                    0, SFMReleaseReviewSurfaceJsonCodec.encodeSurface(stale), "");
        });
        try {
            SFMTextDocumentSnapshot first = runtime.loadDocument(
                    recipe(), 3, new SFMExplorerCancellationToken()).join();
            SFMTextDocumentSnapshot second = runtime.loadDocument(
                    recipe(), 3, new SFMExplorerCancellationToken()).join();
            assertEquals(SFMTextDocumentSnapshot.State.UNAVAILABLE, first.state());
            assertTrue(first.readOnly());
            assertTrue(first.diagnostics().get(0).contains("review.surface.invalid-output"));
            assertEquals(2, invocations.get(), "failed output must not poison the stable cache");
            assertEquals(SFMTextDocumentSnapshot.State.UNAVAILABLE, second.state());
        } finally {
            runtime.close();
        }
    }

    @Test
    void cancellationStopsPendingGenerationAndRemovesItsCacheEntry() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger();
        SFMReleaseReviewSurfaceRuntime runtime = runtime((configuration, json, cancellation) -> {
            invocations.incrementAndGet();
            started.countDown();
            while (!cancellation.isCancelled()) Thread.sleep(2);
            throw new CancellationException("fixture cancelled");
        });
        try {
            SFMExplorerCancellationToken cancellation = new SFMExplorerCancellationToken();
            var future = runtime.generate(recipe(), 2, cancellation);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            cancellation.cancel();
            assertThrows(CompletionException.class, future::join);
            await(() -> runtime.telemetry().cachedSurfaces() == 0);
            assertEquals(1, runtime.telemetry().cancelled());
            assertEquals(1, invocations.get());
        } finally {
            runtime.close();
        }
    }

    @Test
    void timeoutAndBoundedOutputFailuresRemainInspectable() {
        SFMReleaseReviewSurfaceRuntime timeout = runtime((configuration, json, cancellation) -> {
            throw new TimeoutException("fixture deadline");
        });
        try {
            SFMTextDocumentSnapshot snapshot = timeout.loadDocument(
                    recipe(), 1, new SFMExplorerCancellationToken()).join();
            assertEquals(SFMTextDocumentSnapshot.State.UNAVAILABLE, snapshot.state());
            assertTrue(snapshot.diagnostics().get(0).contains("review.surface.timeout"));
        } finally {
            timeout.close();
        }

        SFMReleaseReviewSurfaceRuntime overflow = runtime((configuration, json, cancellation) ->
                new SFMReleaseReviewSurfaceRuntime.ProcessResult(0, "{}", "", true, false));
        try {
            SFMTextDocumentSnapshot snapshot = overflow.loadDocument(
                    recipe(), 1, new SFMExplorerCancellationToken()).join();
            assertTrue(snapshot.diagnostics().get(0).contains("review.surface.stdout-limit"));
        } finally {
            overflow.close();
        }
    }

    @Test
    void explicitUnsupportedJavaFallbackDiagnosticsSurviveInTheReadyGeneratedDocument() {
        SFMReleaseReviewSurfaceRuntime runtime = runtime((configuration, json, cancellation) -> {
            var request = SFMReleaseReviewSurfaceJsonCodec.decodeRequest(json);
            var text = SFMReleaseReviewSurfaceJsonCodecTests.afterSurface(request);
            var diagnostic = new SFMReleaseReviewSurfaceV1.Diagnostic(
                    "review.java-diff.unsupported-language",
                    SFMReleaseReviewSurfaceV1.Severity.WARNING,
                    "Structured review supports Java; emitted complete text fallback",
                    java.util.Optional.empty(),
                    java.util.Optional.empty());
            var fallback = new SFMReleaseReviewSurfaceV1.Surface(
                    text.schema(), text.requestId(), text.requestGeneration(), text.filePairId(),
                    request.surfaceKind(), "fixture-java;fallback=text", SFMReleaseReviewSurfaceV1.Outcome.FALLBACK,
                    true, java.util.Optional.of(SFMReleaseReviewSurfaceV1.SurfaceKind.TEXT_DIFF), text.text(),
                    text.textSha256(), text.mappings(), text.regions(),
                    new SFMReleaseReviewSurfaceV1.CorrespondenceReport(
                            SFMReleaseReviewSurfaceV1.CORRESPONDENCE_SCHEMA,
                            request.filePair().id(), true, List.of(), List.of(diagnostic)),
                    List.of(diagnostic));
            return new SFMReleaseReviewSurfaceRuntime.ProcessResult(
                    0, SFMReleaseReviewSurfaceJsonCodec.encodeSurface(fallback), "");
        });
        try {
            var base = recipe();
            var structured = new SFMReleaseReviewSurfaceV1.Recipe(
                    base.filePair(), SFMReleaseReviewSurfaceV1.SurfaceKind.JAVA_STRUCTURED_DIFF);
            SFMTextDocumentSnapshot snapshot = runtime.loadDocument(
                    structured, 5, new SFMExplorerCancellationToken()).join();
            assertTrue(snapshot.ready());
            assertTrue(snapshot.readOnly());
            assertTrue(snapshot.diagnostics().stream()
                    .anyMatch(value -> value.contains("review.java-diff.unsupported-language")));
        } finally {
            runtime.close();
        }
    }

    private static SFMReleaseReviewSurfaceV1.Recipe recipe() {
        var request = SFMReleaseReviewSurfaceJsonCodecTests.request(1, 1);
        return new SFMReleaseReviewSurfaceV1.Recipe(request.filePair(), request.surfaceKind(),
                request.contextLines(), request.maximumOutputBytes(), request.maximumMappings(),
                request.maximumRegions(), request.maximumDiagnostics());
    }

    private static SFMReleaseReviewSurfaceRuntime runtime(SFMReleaseReviewSurfaceRuntime.Generator generator) {
        return new SFMReleaseReviewSurfaceRuntime(
                new SFMReleaseReviewSurfaceRuntime.Configuration(
                        "fixture-worker", Duration.ofSeconds(2), Duration.ofMillis(100),
                        16 * 1024 * 1024, 64 * 1024),
                Executors.newSingleThreadExecutor(action -> {
                    Thread thread = new Thread(action, "sfm-review-surface-test");
                    thread.setDaemon(true);
                    return thread;
                }),
                generator,
                true
        );
    }

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        assertTrue(condition.getAsBoolean());
    }
}
