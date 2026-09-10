package ca.teamdman.sfm.client.review.release_review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SFMReleaseReviewAsyncRuntimeTests {
    @Test
    void asynchronousQueryActivationPersistsTheExactQueueAcrossIndependentRuntimes(@TempDir Path directory) throws Exception {
        Path file = copyFixture(directory, "query-resume");
        String expression = "remaining intersect 1.19.2 HEAD";
        ArrayDeque<Runnable> worker = new ArrayDeque<>();
        SFMReleaseReviewV1.ResumeState saved;
        List<String> expectedUnits;
        try (var runtime = new SFMReleaseReviewRuntime(worker::add)) {
            runtime.open(file, true);
            var before = runtime.snapshot();
            expectedUnits = runtime.query(expression).reviewUnitIds();
            var pending = runtime.activateQueryAsync(Optional.empty(), expression);
            assertFalse(pending.isDone());
            assertEquals(before, runtime.snapshot(), "old queue stays authoritative until durable save");
            assertFalse(runtime.activateQueryAsync(Optional.empty(), "#approved").join().saved(),
                    "a second activation cannot silently replace pending work");
            worker.remove().run();
            assertTrue(pending.join().saved());
            saved = runtime.document().orElseThrow().resumeState();
            assertEquals(Optional.of(expression), saved.activeQueryExpression());
            assertEquals(expectedUnits.stream().findFirst(), saved.currentUnitId());
            assertEquals(before.document().orElseThrow().resumeState().deferredUnitIds(), saved.deferredUnitIds());
            assertEquals(before.document().orElseThrow().reviewSession().comments(),
                    runtime.document().orElseThrow().reviewSession().comments());
        }
        try (var reopened = new SFMReleaseReviewRuntime()) {
            reopened.open(file, false);
            assertEquals(saved, reopened.document().orElseThrow().resumeState());
            assertEquals(expectedUnits, reopened.query(saved.activeQueryExpression().orElseThrow()).reviewUnitIds());
            assertFalse(reopened.activateQueryAsync(Optional.empty(), "#approved").join().saved());
            assertEquals(saved, reopened.document().orElseThrow().resumeState());
        }
    }

    @Test
    void cancelledOrUnknownQueryKeepsThePriorPortableQueue(@TempDir Path directory) throws Exception {
        Path file = copyFixture(directory, "cancelled-query");
        ArrayDeque<Runnable> worker = new ArrayDeque<>();
        try (var runtime = new SFMReleaseReviewRuntime(worker::add)) {
            runtime.open(file, true);
            var before = runtime.snapshot();
            String original = Files.readString(file);
            var cancelled = runtime.activateQueryAsync(Optional.empty(), "remaining");
            assertTrue(runtime.cancelOperation(runtime.pendingOperation().orElseThrow().id()));
            worker.remove().run();
            assertFalse(cancelled.join().saved());
            assertEquals(before.document(), runtime.snapshot().document());
            assertEquals(original, Files.readString(file));
            var invalid = runtime.activateQueryAsync(Optional.empty(), "not-a-configured-query-atom");
            worker.remove().run();
            assertFalse(invalid.join().saved());
            assertEquals(before.document(), runtime.snapshot().document());
            assertEquals(original, Files.readString(file));
        }
    }

    @Test
    void stagedOpenPreservesTheUsableSnapshotAndRejectsOverlappingWork(@TempDir Path directory) throws Exception {
        Path first = copyFixture(directory, "first");
        Path second = copyFixture(directory, "second");
        ArrayDeque<Runnable> worker = new ArrayDeque<>();
        try (var runtime = new SFMReleaseReviewRuntime(worker::add)) {
            runtime.open(first, true);
            var before = runtime.snapshot();
            var opening = runtime.openAsync(second, true);
            assertFalse(opening.isDone());
            assertEquals(before, runtime.snapshot());
            assertTrue(runtime.openAsync(first, false).isCompletedExceptionally());
            assertFalse(runtime.mutateAsync(value -> value).join().saved());
            worker.remove().run();
            assertTrue(opening.join().document().isPresent());
            assertEquals(second.toAbsolutePath(), runtime.path().orElseThrow());
            assertEquals(before.openEpoch() + 1, runtime.snapshot().openEpoch());
            assertFalse(runtime.persistencePending());
            try (var released = SFMReleaseReviewStore.open(first, SFMReleaseReviewStore.Access.WRITABLE)) {
                assertEquals(first, released.path());
            }
        }
    }

    @Test
    void sameWritablePathReusesItsLeaseButStillAdvancesTheOpenEpoch(@TempDir Path directory) throws Exception {
        Path file = copyFixture(directory, "same");
        ArrayDeque<Runnable> worker = new ArrayDeque<>();
        try (var runtime = new SFMReleaseReviewRuntime(worker::add)) {
            runtime.open(file, true);
            long epoch = runtime.snapshot().openEpoch();
            var reload = runtime.openAsync(file, true);
            worker.remove().run();
            assertTrue(reload.join().document().isPresent());
            assertEquals(epoch + 1, runtime.snapshot().openEpoch());
            assertThrows(java.io.IOException.class, () ->
                    SFMReleaseReviewStore.open(file, SFMReleaseReviewStore.Access.WRITABLE));
        }
    }

    @Test
    void cancelledAndInvalidOpenKeepThePreviousReview(@TempDir Path directory) throws Exception {
        Path first = copyFixture(directory, "first");
        Path invalid = directory.resolve("invalid.sfm-review.json");
        Files.writeString(invalid, "not JSON");
        ArrayDeque<Runnable> worker = new ArrayDeque<>();
        try (var runtime = new SFMReleaseReviewRuntime(worker::add)) {
            runtime.open(first, true);
            var before = runtime.snapshot();
            var cancelled = runtime.openAsync(invalid, true);
            long request = runtime.pendingOperation().orElseThrow().id();
            assertFalse(runtime.cancelOperation(request + 1));
            assertTrue(runtime.cancelOperation(request));
            worker.remove().run();
            assertTrue(cancelled.isCompletedExceptionally());
            assertEquals(before, runtime.snapshot());
            var failed = runtime.openAsync(invalid, true);
            worker.remove().run();
            assertTrue(failed.join().document().isEmpty());
            assertEquals(before, runtime.snapshot());
            try (var released = SFMReleaseReviewStore.open(invalid, SFMReleaseReviewStore.Access.WRITABLE)) {
                assertEquals(invalid, released.path());
            }
        }
    }

    @Test
    void blockedOpenDoesNotBlockSnapshotsAndCannotReplaceAReopenedSession(@TempDir Path directory) throws Exception {
        Path first = copyFixture(directory, "first");
        Path second = copyFixture(directory, "second");
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try (var runtime = new SFMReleaseReviewRuntime(executor, (path, access) -> {
            var acquired = SFMReleaseReviewStore.open(path, access);
            if (path.equals(second)) {
                loading.countDown();
                await(release);
            }
            return acquired;
        })) {
            runtime.open(first, true);
            var old = runtime.snapshot();
            var stale = runtime.openAsync(second, true);
            assertTrue(loading.await(5, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertEquals(old, runtime.snapshot()));
            runtime.discardAndClose();
            var fresh = runtime.openAsync(first, true);
            long freshRequest = runtime.pendingOperation().orElseThrow().id();
            release.countDown();
            assertThrows(Exception.class, () -> stale.get(5, TimeUnit.SECONDS));
            assertTrue(fresh.get(5, TimeUnit.SECONDS).document().isPresent());
            assertFalse(runtime.cancelOperation(freshRequest));
            assertEquals(first, runtime.path().orElseThrow());
            try (var released = SFMReleaseReviewStore.open(second, SFMReleaseReviewStore.Access.WRITABLE)) {
                assertEquals(second, released.path());
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void blockedMutationCanBeCancelledWithoutPublishingOrHoldingTheSnapshotMonitor(@TempDir Path directory)
            throws Exception {
        Path file = copyFixture(directory, "cancel-save");
        byte[] beforeBytes = Files.readAllBytes(file);
        CountDownLatch preparing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try (var runtime = new SFMReleaseReviewRuntime(executor)) {
            runtime.open(file, true);
            var before = runtime.snapshot();
            var save = runtime.mutateAsync(value -> {
                preparing.countDown();
                await(release);
                return changedResume(value);
            });
            assertTrue(preparing.await(5, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertEquals(before, runtime.snapshot()));
            assertFalse(runtime.mutateAsync(value -> value).join().saved());
            assertTrue(runtime.cancelOperation(runtime.pendingOperation().orElseThrow().id()));
            release.countDown();
            var result = save.get(5, TimeUnit.SECONDS);
            assertFalse(result.saved());
            assertTrue(result.failure().orElseThrow().contains("review.cancelled"));
            assertEquals(before, runtime.snapshot());
            assertArrayEquals(beforeBytes, Files.readAllBytes(file));
            assertTrue(runtime.mutateAsync(SFMReleaseReviewAsyncRuntimeTests::changedResume)
                    .get(5, TimeUnit.SECONDS).saved(), "cancelled work must remain retryable");
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void failedMutationRetainsCommittedStateAndAllowsRetry(@TempDir Path directory) throws Exception {
        Path file = copyFixture(directory, "failure");
        ArrayDeque<Runnable> worker = new ArrayDeque<>();
        try (var runtime = new SFMReleaseReviewRuntime(worker::add)) {
            runtime.open(file, true);
            var before = runtime.snapshot();
            var failed = runtime.mutateAsync(value -> { throw new IllegalArgumentException("injected"); });
            worker.remove().run();
            assertFalse(failed.join().saved());
            assertTrue(failed.join().failure().orElseThrow().contains("injected"));
            assertEquals(before, runtime.snapshot());
            var retry = runtime.mutateAsync(SFMReleaseReviewAsyncRuntimeTests::changedResume);
            worker.remove().run();
            assertTrue(retry.join().saved());
        }
    }

    @Test
    void cancellationAtTheStoreBoundaryLeavesAuthorityUntouchedButCannotUndoAGrantedCommit(@TempDir Path directory)
            throws Exception {
        Path file = copyFixture(directory, "commit");
        byte[] before = Files.readAllBytes(file);
        try (var store = SFMReleaseReviewStore.open(file, SFMReleaseReviewStore.Access.WRITABLE)) {
            var loaded = store.load();
            var updated = changedResume(loaded.document().orElseThrow());
            var cancelled = new SFMReleaseReviewOperation();
            assertTrue(cancelled.requestCancellation());
            assertThrows(java.util.concurrent.CancellationException.class,
                    () -> store.save(updated, loaded.openedContentHash(), cancelled::beginCommit));
            assertArrayEquals(before, Files.readAllBytes(file));
            try (var paths = Files.list(directory)) {
                assertFalse(paths.anyMatch(path -> path.getFileName().toString().contains(".tmp-")));
            }
            var committed = new SFMReleaseReviewOperation();
            var saved = store.save(updated, loaded.openedContentHash(), () -> {
                committed.beginCommit();
                assertFalse(committed.requestCancellation(), "commit authority makes cancellation too late");
            });
            assertNotEquals(loaded.openedContentHash().orElseThrow(), saved.contentHash());
            assertEquals(updated, store.load().document().orElseThrow());
        }
    }

    private static SFMReleaseReviewV1 changedResume(SFMReleaseReviewV1 value) {
        var resume = value.resumeState();
        return new SFMReleaseReviewV1(value.schema(), value.reviewSession(), value.repositoryBindings(),
                value.corpusDocuments(), value.reviewUnits(), value.selectorBindings(), value.migrationReports(),
                value.namedQueries(), new SFMReleaseReviewV1.ResumeState(resume.activeQueryId(),
                resume.activeQueryExpression(), resume.currentUnitId(), resume.deferredUnitIds(), resume.generation() + 1),
                value.producerGenerations(), value.completionAttestations());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test worker was not released");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    private static Path copyFixture(Path directory, String name) throws Exception {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path fixture = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(fixture)) return Files.copy(fixture, directory.resolve(name + ".sfm-review.json"));
        }
        throw new IllegalStateException("Canonical review fixture unavailable");
    }
}
