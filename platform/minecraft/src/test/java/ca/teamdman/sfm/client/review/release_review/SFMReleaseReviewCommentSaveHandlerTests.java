package ca.teamdman.sfm.client.review.release_review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

import static ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewCommentDraftServiceTests.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMReleaseReviewCommentSaveHandlerTests {
    @Test
    void presetDuplicateAndCancellationShareOneOperationAndKeepFailedDrafts(@TempDir Path directory) throws Exception {
        Path file = copyFixture(directory);
        var worker = new ArrayDeque<Runnable>();
        try (var runtime = new SFMReleaseReviewRuntime(worker::add)) {
            runtime.open(file, true);
            var service = new SFMReleaseReviewCommentDraftService(runtime);
            var capture = firstCapture(runtime.document().orElseThrow());
            var draft = service.create(capture, literalProposal(capture));
            int before = runtime.document().orElseThrow().reviewSession().comments().size();
            var first = service.applyAsync(draft.id(), "#needs-change");
            assertSame(first, service.applyAsync(draft.id(), "#needs-change"));
            assertTrue(service.applyAsync(draft.id(), "#approved").isCompletedExceptionally());
            assertEquals(1, worker.size());
            assertEquals(SFMReleaseReviewCommentDraftService.Cancellation.REQUESTED_BEFORE_COMMIT,
                    service.cancelChoice(draft.id()));
            worker.remove().run();
            assertFalse(first.join().mutation().saved());
            assertEquals(draft, service.requireCurrent(draft.id()));
            assertEquals(before, runtime.document().orElseThrow().reviewSession().comments().size());
            var retry = service.applyAsync(draft.id(), "#needs-change");
            worker.remove().run();
            assertTrue(retry.join().mutation().saved());
            assertEquals(before + 1, runtime.document().orElseThrow().reviewSession().comments().size());
            assertEquals(SFMReleaseReviewCommentDraftService.Cancellation.ABSENT, service.cancelChoice(draft.id()));
        }
    }

    @Test
    void subsequentSavesUpdateTheSameCommentAndPreserveWhitespace(@TempDir Path directory) throws Exception {
        Path file = copyFixture(directory);
        var worker = new ArrayDeque<Runnable>();
        try (var runtime = new SFMReleaseReviewRuntime(worker::add)) {
            runtime.open(file, true);
            var service = new SFMReleaseReviewCommentDraftService(runtime);
            var capture = firstCapture(runtime.document().orElseThrow());
            var draft = service.create(capture, literalProposal(capture));
            int before = runtime.document().orElseThrow().reviewSession().comments().size();
            var refreshes = new AtomicInteger();
            var handler = new SFMReleaseReviewCommentSaveHandler(runtime, service, draft.id(), refreshes::incrementAndGet);
            assertTrue(handler.asynchronous());
            assertFalse(handler.save("do not run synchronously").saved());
            String firstText = "  #approved\nA freeform note.\n";
            var first = handler.saveAsync(firstText);
            assertSame(first, handler.saveAsync(firstText));
            assertFalse(first.isDone());
            assertEquals(before, runtime.document().orElseThrow().reviewSession().comments().size());
            worker.remove().run();
            assertTrue(first.join().saved());
            var comment = runtime.document().orElseThrow().reviewSession().comments().get(before);
            assertEquals(firstText, comment.text());
            assertTrue(service.draftsForTests().isEmpty());
            var second = handler.saveAsync("Updated note\n\n");
            worker.remove().run();
            assertTrue(second.join().saved(), second.join().diagnostic().toString());
            var updated = runtime.document().orElseThrow().reviewSession().comments().get(before);
            assertEquals(comment.id(), updated.id());
            assertEquals(comment.target(), updated.target());
            assertEquals(comment.provenance(), updated.provenance());
            assertEquals(before + 1, runtime.document().orElseThrow().reviewSession().comments().size());
            assertTrue(handler.saveAsync("Updated note\n\n").join().saved());
            assertTrue(worker.isEmpty(), "Done on the saved version must not append a duplicate");
            assertEquals(2, refreshes.get());
            runtime.discardAndClose();
            runtime.open(file, false);
            assertEquals("Updated note\n\n", runtime.document().orElseThrow().reviewSession().comments().get(before).text());
        }
    }

    @Test
    void cancellationAndExternalFileFailureRetainTheOriginalDraftForRetry(@TempDir Path directory) throws Exception {
        Path file = copyFixture(directory);
        byte[] original = Files.readAllBytes(file);
        var worker = new ArrayDeque<Runnable>();
        try (var runtime = new SFMReleaseReviewRuntime(worker::add)) {
            runtime.open(file, true);
            var service = new SFMReleaseReviewCommentDraftService(runtime);
            var capture = firstCapture(runtime.document().orElseThrow());
            var draft = service.create(capture, literalProposal(capture));
            var handler = new SFMReleaseReviewCommentSaveHandler(runtime, service, draft.id(), () -> {});
            var cancelled = handler.saveAsync("Keep this draft");
            assertTrue(handler.cancelPendingSave());
            worker.remove().run();
            assertFalse(cancelled.join().saved());
            assertArrayEquals(original, Files.readAllBytes(file));
            assertEquals(draft, service.requireCurrent(draft.id()));
            var failed = handler.saveAsync("Keep this draft");
            Files.writeString(file, "external replacement");
            worker.remove().run();
            assertFalse(failed.join().saved());
            assertEquals("external replacement", Files.readString(file));
            assertEquals(draft, service.requireCurrent(draft.id()));
            Files.write(file, original);
            var retry = handler.saveAsync("Keep this draft");
            worker.remove().run();
            assertTrue(retry.join().saved());
            assertFalse(handler.cancelPendingSave());
        }
    }

    @Test
    void replacedReviewAndConflictingCommentEditCannotBeOverwritten(@TempDir Path directory) throws Exception {
        Path file = copyFixture(directory);
        var worker = new ArrayDeque<Runnable>();
        try (var runtime = new SFMReleaseReviewRuntime(worker::add)) {
            runtime.open(file, true);
            var service = new SFMReleaseReviewCommentDraftService(runtime);
            var capture = firstCapture(runtime.document().orElseThrow());
            var draft = service.create(capture, literalProposal(capture));
            int before = runtime.document().orElseThrow().reviewSession().comments().size();
            var handler = new SFMReleaseReviewCommentSaveHandler(runtime, service, draft.id(), () -> {});
            var first = handler.saveAsync("Original");
            worker.remove().run();
            assertTrue(first.join().saved());
            String id = runtime.document().orElseThrow().reviewSession().comments().get(before).id();
            var anotherEditor = runtime.updateCommentAsync(file, runtime.snapshot().openEpoch(), id, "Original", "Other editor");
            worker.remove().run();
            assertTrue(anotherEditor.join().saved());
            var conflict = handler.saveAsync("My later draft");
            worker.remove().run();
            assertFalse(conflict.join().saved());
            assertEquals("Other editor", runtime.document().orElseThrow().reviewSession().comments().get(before).text());
            runtime.discardAndClose();
            runtime.open(file, true);
            assertFalse(handler.saveAsync("Must not save to a new epoch").join().saved());
            assertTrue(worker.isEmpty());
        }
    }

    @Test
    void failedRefreshAfterDurableSaveDoesNotReportAFalseFailure(@TempDir Path directory) throws Exception {
        Path file = copyFixture(directory);
        var worker = new ArrayDeque<Runnable>();
        try (var runtime = new SFMReleaseReviewRuntime(worker::add)) {
            runtime.open(file, true);
            var service = new SFMReleaseReviewCommentDraftService(runtime);
            var capture = firstCapture(runtime.document().orElseThrow());
            var draft = service.create(capture, literalProposal(capture));
            var handler = new SFMReleaseReviewCommentSaveHandler(runtime, service, draft.id(), () -> {
                throw new IllegalStateException("originating screen disappeared");
            });
            var saving = handler.saveAsync("Durably saved despite refresh failure");
            worker.remove().run();
            assertTrue(saving.join().saved());
            assertTrue(service.draftsForTests().isEmpty());
            assertTrue(Files.readString(file).contains("Durably saved despite refresh failure"));
        }
    }
}
