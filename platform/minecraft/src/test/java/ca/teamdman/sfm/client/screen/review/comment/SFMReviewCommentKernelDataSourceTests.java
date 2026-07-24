package ca.teamdman.sfm.client.screen.review.comment;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Codec;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReviewCommentKernelDataSourceTests {
    @Test
    void uiCommandsMutateAndPersistTheAuthoritativeKernelSession(@TempDir Path directory) throws Exception {
        SFMReviewSessionV1 fixture = SFMReviewSessionV1Codec.parse(Files.readString(fixturePath()));
        SFMReviewSessionV1Store store = new SFMReviewSessionV1Store(directory.resolve("session.json"));
        SFMReviewCommentKernelDataSource source = new SFMReviewCommentKernelDataSource(fixture, store, List.of());

        SFMReviewCommentDataSource.SessionView initial = source.refresh();
        assertEquals(List.of(SFMReviewCommentDataSource.Side.BEFORE, SFMReviewCommentDataSource.Side.AFTER),
                initial.documents().stream().map(SFMReviewCommentDataSource.DocumentView::side).toList());
        assertEquals(3, initial.comments().size());
        assertTrue(initial.migrations().stream().allMatch(migration ->
                migration.status() == SFMReviewCommentDataSource.EvaluationStatus.RESOLVED_EXACTLY));

        String id = source.createLiteralComment("#\"Needs Review\" Cross-side selection.", List.of(
                new SFMReviewCommentDataSource.RangeView(initial.documents().get(0).id(), 0, 5),
                new SFMReviewCommentDataSource.RangeView(initial.documents().get(1).id(), 0, 5)
        ));
        SFMReviewSessionV1.Comment created = source.session().comments().stream()
                .filter(comment -> comment.id().equals(id)).findFirst().orElseThrow();
        assertInstanceOf(SFMReviewSessionV1.Union.class, created.selectionRule());
        assertEquals(List.of("#\"needs review\""), SFMReviewSessionV1Kernel.derivedHashtags(created.text()));

        source.editComment(id, "#approved Reviewed in game.");
        source.archiveComment(id);
        source.updateStyleColour("problem-underline", SFMReviewCommentDataSource.StyleChannel.BACKGROUND,
                0xFFAA11CC);

        SFMReviewCommentDataSource.CommentView archived = source.refresh().comments().stream()
                .filter(comment -> comment.id().equals(id)).findFirst().orElseThrow();
        assertTrue(archived.archived());
        assertEquals(Integer.valueOf(0xFFAA11CC), source.refresh().styleRules().stream()
                .filter(style -> style.id().equals("problem-underline")).findFirst().orElseThrow().background());
        assertEquals(source.session(), store.load().session().orElseThrow());
    }

    private static Path fixturePath() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/review-comment-session-v1.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate canonical review-comment fixture");
    }
}
