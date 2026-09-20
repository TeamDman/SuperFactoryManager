package ca.teamdman.sfm.client.review.release_review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewWorkQueue.Operation.*;

class SFMReleaseReviewWorkQueueTests {
    @TempDir Path directory;

    @Test
    void firstLastAndSameSelectionDoNotRewriteThePortableFile() throws Exception {
        Path file = fixtureCopy("bounds");
        try (var runtime = new SFMReleaseReviewRuntime(Runnable::run)) {
            runtime.open(file, true);
            assertTrue(runtime.activateQuery(Optional.empty(), "HEAD").saved());
            var queue = runtime.query("HEAD").reviewUnitIds();
            var first = runtime.snapshot();
            String firstBytes = Files.readString(file);
            assertFalse(runtime.workQueueAsync(first, PREVIOUS, Optional.empty()).join().saved());
            assertFalse(runtime.workQueueAsync(first, SELECT, Optional.of(queue.get(0))).join().saved());
            assertEquals(first, runtime.snapshot());
            assertEquals(firstBytes, Files.readString(file));
            assertTrue(runtime.workQueueAsync(first, NEXT, Optional.empty()).join().saved());
            var last = runtime.snapshot();
            String lastBytes = Files.readString(file);
            assertFalse(runtime.workQueueAsync(last, NEXT, Optional.empty()).join().saved());
            assertEquals(last, runtime.snapshot());
            assertEquals(lastBytes, Files.readString(file));
            assertTrue(runtime.workQueueAsync(last, PREVIOUS, Optional.empty()).join().saved());
            assertEquals(Optional.of(queue.get(0)), runtime.document().orElseThrow().resumeState().currentUnitId());
        }
    }

    @Test
    void queryAndSaveAreDeferredAndCancellationPreservesTheOldAuthority() throws Exception {
        Path file = fixtureCopy("cancel");
        ArrayDeque<Runnable> worker = new ArrayDeque<>();
        try (var runtime = new SFMReleaseReviewRuntime(worker::add)) {
            runtime.open(file, true);
            runtime.activateQuery(Optional.empty(), "HEAD");
            var before = runtime.snapshot();
            String bytes = Files.readString(file);
            var pending = runtime.workQueueAsync(before, NEXT, Optional.empty());
            assertFalse(pending.isDone());
            assertEquals(before, runtime.snapshot());
            assertFalse(runtime.workQueueAsync(before, DEFER, Optional.empty()).join().saved());
            assertTrue(runtime.cancelOperation(runtime.pendingOperation().orElseThrow().id()));
            worker.remove().run();
            assertFalse(pending.join().saved());
            assertEquals(before, runtime.snapshot());
            assertEquals(bytes, Files.readString(file));
            var retry = runtime.workQueueAsync(before, NEXT, Optional.empty());
            worker.remove().run();
            assertTrue(retry.join().saved());
        }
    }

    @Test
    void deferralIsNonWrappingAndResumesAfterAnIndependentRuntimeReopen() throws Exception {
        Path file = fixtureCopy("resume");
        SFMReleaseReviewV1.ResumeState saved;
        List<String> units;
        try (var runtime = new SFMReleaseReviewRuntime(Runnable::run)) {
            runtime.open(file, true);
            runtime.activateQuery(Optional.empty(), "HEAD");
            units = runtime.query("HEAD").reviewUnitIds();
            assertTrue(runtime.workQueueAsync(runtime.snapshot(), DEFER, Optional.empty()).join().saved());
            assertEquals(Optional.of(units.get(1)), runtime.document().orElseThrow().resumeState().currentUnitId());
            assertFalse(runtime.workQueueAsync(runtime.snapshot(), PREVIOUS, Optional.empty()).join().saved(),
                    "deferred work is not silently revisited by Previous");
            assertTrue(runtime.workQueueAsync(runtime.snapshot(), DEFER, Optional.empty()).join().saved());
            saved = runtime.document().orElseThrow().resumeState();
            assertTrue(saved.currentUnitId().isEmpty());
            assertEquals(units, saved.deferredUnitIds());
            assertFalse(runtime.workQueueAsync(runtime.snapshot(), NEXT, Optional.empty()).join().saved());
        }
        try (var runtime = new SFMReleaseReviewRuntime(Runnable::run)) {
            runtime.open(file, true);
            assertEquals(saved, runtime.document().orElseThrow().resumeState());
            var comments = runtime.document().orElseThrow().reviewSession().comments();
            assertTrue(runtime.workQueueAsync(runtime.snapshot(), RESUME, Optional.empty()).join().saved());
            assertEquals(Optional.of(units.get(0)), runtime.document().orElseThrow().resumeState().currentUnitId());
            assertEquals(List.of(units.get(1)), runtime.document().orElseThrow().resumeState().deferredUnitIds());
            assertEquals(comments, runtime.document().orElseThrow().reviewSession().comments(),
                    "queue navigation is never an approval/comment mutation");
        }
    }

    @Test
    void emptyFilteredMissingAndStaleTargetsRemainExplicit() throws Exception {
        Path file = fixtureCopy("missing");
        try (var runtime = new SFMReleaseReviewRuntime(Runnable::run)) {
            runtime.open(file, true);
            runtime.activateQuery(Optional.empty(), "HEAD");
            var before = runtime.snapshot();
            assertFalse(runtime.workQueueAsync(before, SELECT, Optional.of("unit:missing")).join().saved());
            var review = before.document().orElseThrow();
            String current = review.resumeState().currentUnitId().orElseThrow();
            var withoutCurrent = runtime.query("HEAD").reviewUnitIds().stream()
                    .filter(id -> !id.equals(current)).toList();
            assertThrows(SFMReleaseReviewWorkQueue.Unavailable.class,
                    () -> SFMReleaseReviewWorkQueue.current(review, withoutCurrent));
            assertThrows(SFMReleaseReviewWorkQueue.Unavailable.class,
                    () -> SFMReleaseReviewWorkQueue.transition(review, withoutCurrent, DEFER, Optional.empty()));
            runtime.activateQuery(Optional.empty(), "#empty-work-queue");
            assertFalse(runtime.workQueueAsync(runtime.snapshot(), NEXT, Optional.empty()).join().saved());
            assertFalse(runtime.workQueueAsync(before, NEXT, Optional.empty()).join().saved());
            runtime.open(file, false);
            var readonly = runtime.snapshot();
            assertFalse(runtime.workQueueAsync(readonly, DEFER, Optional.empty()).join().saved());
            assertFalse(runtime.workQueueAsync(before, NEXT, Optional.empty()).join().saved());
            assertEquals(readonly, runtime.snapshot());
        }
    }

    private Path fixtureCopy(String name) throws Exception {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(candidate)) return Files.copy(candidate, directory.resolve(name + ".sfm-review.json"));
        }
        throw new IllegalStateException("Canonical review fixture unavailable");
    }
}
