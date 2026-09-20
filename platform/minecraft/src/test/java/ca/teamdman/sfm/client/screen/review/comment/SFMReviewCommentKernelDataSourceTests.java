package ca.teamdman.sfm.client.screen.review.comment;

import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Codec;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionStore;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Codec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReviewCommentKernelDataSourceTests {
    @Test
    void uiCommandsMutateAndPersistTheAuthoritativeKernelSession(@TempDir Path directory) throws Exception {
        SFMReviewSessionV1 fixture = SFMReviewSessionV1Codec.parse(Files.readString(fixturePath()));
        SFMReviewSessionStore store = new SFMReviewSessionStore(directory.resolve("session.json"));
        SFMReviewCommentKernelDataSource source = new SFMReviewCommentKernelDataSource(
                SFMReviewSessionV2Codec.migrate(fixture), store, List.of());

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
        SFMReviewSessionV2.Comment created = source.session().comments().stream()
                .filter(comment -> comment.id().equals(id)).findFirst().orElseThrow();
        SFMReviewSessionV2.CommittedReviewTarget createdTarget = assertInstanceOf(
                SFMReviewSessionV2.CommittedReviewTarget.class, created.target());
        assertInstanceOf(SFMReviewSessionV1.Union.class, createdTarget.selectionRule());
        assertEquals(List.of("#\"needs review\""), SFMReviewSessionV1Kernel.derivedHashtags(created.text()));

        source.editComment(id, "#approved Reviewed in game.");
        source.archiveComment(id);
        assertEquals(createdTarget, source.session().comments().stream()
                .filter(comment -> comment.id().equals(id)).findFirst().orElseThrow().target(),
                "ordinary comment edits and archival must retain the exact V2 target");
        source.updateStyleColour("problem-underline", SFMReviewCommentDataSource.StyleChannel.BACKGROUND,
                0xFFAA11CC);

        SFMReviewCommentDataSource.CommentView archived = source.refresh().comments().stream()
                .filter(comment -> comment.id().equals(id)).findFirst().orElseThrow();
        assertTrue(archived.archived());
        assertEquals(Integer.valueOf(0xFFAA11CC), source.refresh().styleRules().stream()
                .filter(style -> style.id().equals("problem-underline")).findFirst().orElseThrow().background());
        assertEquals(source.session(), store.load().session().orElseThrow());
        assertTrue(Files.readString(store.path()).contains("\"schema\": \"sfm.review-session/2\""));
    }

    @Test
    void textualGutterMarkersAreNotParsedAsArgbColours() {
        SFMReviewSessionV2 empty = SFMReviewSessionV2.empty("sfm:test/text-gutter", "Text gutter marker");
        SFMReviewSessionV2 session = new SFMReviewSessionV2(
                empty.schema(),
                empty.id(),
                empty.title(),
                empty.coordinateSystem(),
                empty.revisionLanes(),
                empty.comments(),
                List.of(new SFMReviewSessionV1.StyleRule(
                        "release-change", List.of("#release-change"), 0,
                        null, null, null, "R", true
                )),
                empty.completionPolicy()
        );

        SFMReviewCommentDataSource.StyleRuleView style =
                new SFMReviewCommentKernelDataSource(session).refresh().styleRules().get(0);

        assertEquals("R", style.gutterMarker());
    }

    @Test
    void v1ConstructionMigratesConvenientlyToCommittedV2Comments() throws Exception {
        SFMReviewSessionV1 fixture = SFMReviewSessionV1Codec.parse(Files.readString(fixturePath()));

        SFMReviewCommentKernelDataSource source = new SFMReviewCommentKernelDataSource(fixture);

        assertEquals(SFMReviewSessionV2.SCHEMA, source.session().schema());
        assertTrue(source.session().comments().stream().allMatch(comment ->
                comment.target() instanceof SFMReviewSessionV2.CommittedReviewTarget));
        assertTrue(source.refresh().comments().stream().noneMatch(SFMReviewCommentDataSource.CommentView::candidate));
        assertTrue(source.refresh().comments().stream().allMatch(comment ->
                comment.targetLabel().startsWith("committed ")));
    }

    @Test
    void candidateCommentsExposeExactLabelsAndNeverProjectCommittedRanges() {
        SFMReviewSessionV2 empty = SFMReviewSessionV2.empty("sfm:test/ui-candidate", "Candidate UI");
        SFMReviewSessionV2.CandidateTrajectoryTarget target = new SFMReviewSessionV2.CandidateTrajectoryTarget(
                "sfm:test/machine",
                7,
                "plan-2",
                "route-a",
                1,
                Optional.of("step-1"),
                "state-1",
                Optional.of("state-hash"),
                SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER,
                SFMReviewSessionV2.CandidateTargetKind.ACTION,
                Optional.of("action-intent"),
                Optional.empty(),
                Optional.of("evaluator-1"),
                List.of(new SFMCandidateHistoryContract.EvaluatorEvidence("witness", "barrier"))
        );
        SFMReviewSessionV2 session = new SFMReviewSessionV2(
                empty.schema(), empty.id(), empty.title(), empty.coordinateSystem(), empty.revisionLanes(),
                List.of(new SFMReviewSessionV2.Comment(
                        "candidate-1",
                        "Inspect the future action",
                        new SFMReviewSessionV1.Provenance("human", "test", "1", List.of()),
                        target
                )),
                empty.styleRules(), empty.completionPolicy()
        );

        SFMReviewCommentDataSource.CommentView view =
                new SFMReviewCommentKernelDataSource(session).refresh().comments().get(0);

        assertTrue(view.candidate());
        assertEquals(SFMReviewCommentDataSource.EvaluationStatus.CANDIDATE_PINNED_UNAVAILABLE,
                view.evaluationStatus());
        assertTrue(view.ranges().isEmpty(), "candidate identities must not masquerade as committed ranges");
        assertEquals(
                "candidate action · machine=sfm:test/machine@7 · plan=plan-2 · route=route-a"
                        + " · position=1 · step=step-1 · action=action-intent · state=state-1"
                        + " · state-hash=state-hash · status=external_barrier",
                view.targetLabel());
        assertFalse(view.targetLabel().contains("document="));
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
