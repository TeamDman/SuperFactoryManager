package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextSelectionProjection;
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewCommentDraftServiceTests {
    private final SFMReleaseReviewRuntime runtime = SFMReleaseReviewRuntime.get();
    private final SFMReleaseReviewCommentDraftService service =
            new SFMReleaseReviewCommentDraftService(runtime);

    @AfterEach
    void closeRuntime() {
        runtime.discardAndClose();
        service.clearForTests();
    }

    @Test
    void frozenDraftAppliesAtomicallyAndSurvivesCloseReopen(@TempDir Path directory) throws Exception {
        Path file = copyFixture(directory);
        runtime.open(file, true);
        int commentsBefore = runtime.document().orElseThrow().reviewSession().comments().size();
        SFMReleaseReviewEditorCapture.Capture capture = firstCapture(runtime.document().orElseThrow());
        SFMReleaseReviewV1.SelectorProposal proposal = literalProposal(capture);
        var draft = service.create(capture, proposal);

        String comment = "#approved Frozen pointer-selection persistence test.";
        var result = service.apply(draft.id(), comment);

        assertTrue(result.mutation().saved(), result.mutation().failure().orElse(""));
        assertTrue(service.draftsForTests().isEmpty(), "successful one-shot draft must be consumed");
        assertEquals(commentsBefore + 1, runtime.document().orElseThrow().reviewSession().comments().size());
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains(comment));

        runtime.discardAndClose();
        runtime.open(file, false);
        assertEquals(commentsBefore + 1, runtime.document().orElseThrow().reviewSession().comments().size());
        assertEquals(comment, service.recentTemplates().get(0));
    }

    @Test
    void readOnlyReopenMustExplicitlyRebindTheSamePinnedCorpus(@TempDir Path directory) throws Exception {
        Path file = copyFixture(directory);
        runtime.open(file, false);
        SFMReleaseReviewEditorCapture.Capture capture = firstCapture(runtime.document().orElseThrow());
        var draft = service.create(capture, literalProposal(capture));
        assertFalse(runtime.snapshot().writable());

        runtime.open(file, true);
        assertThrows(IllegalStateException.class, () -> service.requireCurrent(draft.id()),
                "replacing the lease must stale the choice until its explicit writable transition");
        var rebound = service.rebindAfterWritableOpen(draft.id());

        assertTrue(runtime.snapshot().writable());
        assertEquals(runtime.snapshot().openEpoch(), rebound.reviewOpenEpoch());
        assertEquals(draft.corpusFingerprint(), rebound.corpusFingerprint());
        assertEquals(draft.proposal(), service.requireCurrent(draft.id()).proposal());
    }

    @Test
    void draftRegistryIsBoundedAndCancellationIsOneShot(@TempDir Path directory) throws Exception {
        Path file = copyFixture(directory);
        runtime.open(file, true);
        SFMReleaseReviewEditorCapture.Capture capture = firstCapture(runtime.document().orElseThrow());
        SFMReleaseReviewV1.SelectorProposal proposal = literalProposal(capture);
        ArrayList<String> ids = new ArrayList<>();
        for (int index = 0; index < SFMReleaseReviewCommentDraftService.MAXIMUM_DRAFTS + 3; index++) {
            ids.add(service.create(capture, proposal).id());
        }

        assertEquals(SFMReleaseReviewCommentDraftService.MAXIMUM_DRAFTS, service.draftsForTests().size());
        assertThrows(IllegalArgumentException.class, () -> service.require(ids.get(0)));
        String newest = ids.get(ids.size() - 1);
        assertTrue(service.cancel(newest));
        assertFalse(service.cancel(newest));
    }

    private static SFMReleaseReviewV1.SelectorProposal literalProposal(
            SFMReleaseReviewEditorCapture.Capture capture
    ) {
        return capture.proposals().proposals().stream()
                .filter(value -> value.kind() == SFMReleaseReviewV1.SelectorKind.LITERAL)
                .findFirst().orElseThrow();
    }

    private static SFMReleaseReviewEditorCapture.Capture firstCapture(SFMReleaseReviewV1 review) {
        SFMReleaseReviewCorpus.DocumentView document = review.corpusDocuments().stream()
                .filter(value -> value.materialization() == SFMReleaseReviewV1.Materialization.COMPLETE)
                .map(value -> SFMReleaseReviewCorpus.from(review).documentRevision(value.documentRevisionId()))
                .flatMap(Optional::stream)
                .filter(value -> value.materializedDocument().map(materialized -> !materialized.text().isEmpty())
                        .orElse(false))
                .findFirst().orElseThrow();
        String text = document.materializedDocument().orElseThrow().text();
        int end = Math.min(text.getBytes(StandardCharsets.UTF_8).length, 8);
        return editorCapture(review, document.binding().documentRevisionId(), 0, end);
    }

    private static SFMReleaseReviewEditorCapture.Capture editorCapture(
            SFMReleaseReviewV1 review,
            String revisionId,
            int startByte,
            int endByte
    ) {
        SFMReleaseReviewCorpus.DocumentView corpus = SFMReleaseReviewCorpus.from(review)
                .documentRevision(revisionId).orElseThrow();
        String text = corpus.materializedDocument().orElseThrow().text();
        SFMPath root = new SFMPath(
                SFMPath.Kind.CONTRIBUTED, "review", "document", List.of(revisionId), Optional.empty(), true);
        ArrayList<String> segments = new ArrayList<>();
        segments.add(revisionId);
        for (String segment : corpus.binding().path().split("/")) {
            if (!segment.isBlank()) segments.add(segment);
        }
        SFMPath addressed = new SFMPath(
                SFMPath.Kind.CONTRIBUTED, "review", "document", segments, Optional.empty(), false);
        SFMTextDocumentSnapshot baseline = SFMTextDocumentSnapshot.pinned(
                addressed, root, text, corpus.binding().sha256(), Optional.empty(), Optional.empty());
        var start = SFMTextDocumentRange.positionAtByteOffset(text, startByte);
        var end = SFMTextDocumentRange.positionAtByteOffset(text, endByte);
        SFMTextDocumentSelection exact = new SFMTextDocumentSelection("primary", start, end, true);
        SFMContextDocumentProjection projection = SFMContextDocumentProjection.capture(
                "sfm:text_editor_v3", baseline, text, false, true, List.of(),
                List.of(new SFMContextSelectionProjection(
                        "primary", List.of(exact.orderedRange()), true, List.of(exact))));
        return SFMReleaseReviewEditorCapture.capture(
                new SFMClientActionContext(null, () -> true, null),
                new SFMContextSnapshot(1, 1, 1, Optional.empty(), List.of()),
                projection,
                review
        );
    }

    private static Path copyFixture(Path directory) throws Exception {
        Path copy = directory.resolve("comment-loop.sfm-review.json");
        Files.copy(fixturePath(), copy);
        return copy;
    }

    private static Path fixturePath() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate canonical release-review fixture");
    }
}
