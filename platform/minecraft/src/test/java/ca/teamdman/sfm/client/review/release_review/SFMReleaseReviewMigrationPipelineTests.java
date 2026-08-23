package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewMigrationPipelineTests {
    private static final String LANE = "1.19.2";
    private static final String PROVIDER = "fixture-java";
    private static final String GENERATION = "fixture-generation-1";
    private static final String BEFORE = "class A { int value() { return 1; } }\n";
    private static final String AFTER_B = "class B { int renamed() { return 1; } }\n";
    private static final String AFTER_C = "class C { int changed() { return 2; } }\n";
    private static final String BEFORE_ID = "before:A.java";
    private static final String AFTER_B_ID = "after:B.java";
    private static final String AFTER_C_ID = "after:C.java";

    @Test
    void productionPipelineBuildsEveryTerminalMigrationStateWithoutChoosingAmbiguity() {
        Fixture fixture = fixture();
        SFMReleaseReviewMigrationPipeline.Result first = SFMReleaseReviewMigrationPipeline.rebuild(
                fixture.review(), fixture.evidence(), SFMReleaseReviewEvaluator.Limits.defaults(),
                SFMReleaseReviewMigrationPipeline.Transition.beforeToAfter());
        SFMReleaseReviewKernel.validate(first.document());

        Map<String, SFMReleaseReviewV1.MigrationReport> reports = reportsBySelector(first.document());
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.EXACT,
                reports.get("proposal:stable").sourceEvaluation().status());
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.RELOCATED,
                reports.get("proposal:stable").candidateEvaluation().status());
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.CONTENT_CHANGED,
                reports.get("proposal:modified").candidateEvaluation().status());
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.AMBIGUOUS,
                reports.get("proposal:ambiguous").candidateEvaluation().status());
        assertEquals(2, reports.get("proposal:ambiguous").newCandidates().size());
        assertTrue(reports.get("proposal:ambiguous").candidateEvaluation().ranges().isEmpty(),
                "ambiguous evidence must never select the first candidate");
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.MISSING,
                reports.get("proposal:missing").candidateEvaluation().status());
        assertTrue(first.work().operations() > 0);
        assertTrue(first.work().candidatesVisited() > 0);

        List<String> producedCommentIds = first.document().reviewSession().comments().stream()
                .map(SFMReviewSessionV2.Comment::id).sorted().toList();
        assertEquals(producedCommentIds, first.commentEvaluations().stream()
                        .map(SFMReleaseReviewMigrationPipeline.CommentEvaluation::commentId).sorted().toList(),
                "every comment in this produced corpus must retain an inspectable terminal evaluation");
        java.util.Set<SFMReleaseReviewV1.EvaluationStatus> terminalStates = java.util.Set.of(
                SFMReleaseReviewV1.EvaluationStatus.EXACT,
                SFMReleaseReviewV1.EvaluationStatus.RELOCATED,
                SFMReleaseReviewV1.EvaluationStatus.CONTENT_CHANGED,
                SFMReleaseReviewV1.EvaluationStatus.AMBIGUOUS,
                SFMReleaseReviewV1.EvaluationStatus.MISSING,
                SFMReleaseReviewV1.EvaluationStatus.INVALID,
                SFMReleaseReviewV1.EvaluationStatus.SCOPE_MISSING
        );
        first.commentEvaluations().forEach(evaluation -> {
            assertTrue(terminalStates.contains(evaluation.sourceEvaluation().status()));
            assertTrue(terminalStates.contains(evaluation.candidateEvaluation().status()));
            assertTrue(first.document().migrationReports().stream().anyMatch(report ->
                    report.id().equals(evaluation.migrationReportId())
                            && report.sourceSelectorId().equals(evaluation.selectorId())));
        });

        SFMReleaseReviewMigrationPipeline.MigrationExplanation explanation = first.explanations().stream()
                .filter(value -> value.selector().id().equals("proposal:stable"))
                .findFirst().orElseThrow();
        assertEquals(reports.get("proposal:stable").oldWitnesses(), explanation.oldWitnesses());
        assertEquals(reports.get("proposal:stable").newCandidates(), explanation.newWitnesses());
        assertTrue(explanation.displayLines().contains("selector-kind=body"));
        assertTrue(explanation.displayLines().contains("semantic-provider=" + PROVIDER));
        assertTrue(explanation.displayLines().contains("semantic-key=method:stable"));
        assertTrue(explanation.displayLines().stream().anyMatch(value ->
                value.equals("provenance[fixture-case]=stable")));
        assertTrue(explanation.displayLines().stream().anyMatch(value -> value.startsWith("literal-witness[0]=")));
        assertTrue(explanation.displayLines().stream().anyMatch(value -> value.startsWith("old-witness[0]=")));
        assertTrue(explanation.displayLines().stream().anyMatch(value -> value.startsWith("new-witness[0]=")));
        assertTrue(explanation.displayLines().stream().anyMatch(value -> value.startsWith("source-provenance=")));
        assertTrue(explanation.displayLines().stream().anyMatch(value -> value.startsWith("candidate-provenance=")));

        ArrayList<SFMReleaseReviewEvaluator.PreparedSemanticCandidate> reversed =
                new ArrayList<>(fixture.evidence().semanticCandidates());
        java.util.Collections.reverse(reversed);
        SFMReleaseReviewV1 second = SFMReleaseReviewMigrationPipeline.rebuild(
                fixture.review(),
                new SFMReleaseReviewEvaluator.PreparedEvidence(
                        fixture.evidence().scopes(), reversed, fixture.evidence().diffCandidates()),
                SFMReleaseReviewEvaluator.Limits.defaults(),
                SFMReleaseReviewMigrationPipeline.Transition.beforeToAfter()).document();
        assertEquals(first.document().migrationReports(), second.migrationReports(),
                "prepared evidence input order must not alter durable reports");
    }

    @Test
    void humanDecisionSurvivesOnlyWhileTheEvaluatedEvidenceIsIdentical() {
        Fixture fixture = fixture();
        SFMReleaseReviewV1 evaluated = SFMReleaseReviewMigrationPipeline.rebuild(
                fixture.review(), fixture.evidence(), SFMReleaseReviewEvaluator.Limits.defaults(),
                SFMReleaseReviewMigrationPipeline.Transition.beforeToAfter()).document();
        SFMReleaseReviewV1.MigrationReport stable = reportsBySelector(evaluated).get("proposal:stable");
        SFMReleaseReviewV1 decided = withDecision(evaluated, stable);

        SFMReleaseReviewMigrationPipeline.Result unchanged = SFMReleaseReviewMigrationPipeline.rebuild(
                decided, fixture.evidence(), SFMReleaseReviewEvaluator.Limits.defaults(),
                SFMReleaseReviewMigrationPipeline.Transition.beforeToAfter());
        assertEquals(SFMReleaseReviewV1.MigrationDecision.DEFERRED,
                reportsBySelector(unchanged.document()).get("proposal:stable").decision());

        List<SFMReleaseReviewEvaluator.PreparedSemanticCandidate> changedCandidates =
                fixture.evidence().semanticCandidates().stream().map(candidate -> {
                    if (!candidate.semanticKey().equals("method:stable")
                            || candidate.side() != SFMReleaseReviewEvaluator.DiffSide.AFTER) return candidate;
                    return new SFMReleaseReviewEvaluator.PreparedSemanticCandidate(
                            candidate.provider(), "fixture-generation-2", sha("changed-stable-evidence"),
                            candidate.laneId(), candidate.path(), candidate.language(), candidate.side(),
                            candidate.kind(), candidate.semanticKey(), candidate.ranges(),
                            candidate.contentSha256(), candidate.diagnostics());
                }).toList();
        SFMReleaseReviewMigrationPipeline.Result changed = SFMReleaseReviewMigrationPipeline.rebuild(
                decided,
                new SFMReleaseReviewEvaluator.PreparedEvidence(
                        fixture.evidence().scopes(), changedCandidates, fixture.evidence().diffCandidates()),
                SFMReleaseReviewEvaluator.Limits.defaults(),
                SFMReleaseReviewMigrationPipeline.Transition.beforeToAfter());
        assertEquals(SFMReleaseReviewV1.MigrationDecision.UNRESOLVED,
                reportsBySelector(changed.document()).get("proposal:stable").decision());
        assertTrue(changed.diagnostics().stream().anyMatch(value ->
                value.startsWith("review.migration-decision-invalidated:")));
    }

    private static SFMReleaseReviewV1 withDecision(
            SFMReleaseReviewV1 review,
            SFMReleaseReviewV1.MigrationReport chosen
    ) {
        String decisionCommentId = "human:migration-decision";
        ArrayList<SFMReviewSessionV2.Comment> comments = new ArrayList<>(review.reviewSession().comments());
        comments.add(new SFMReviewSessionV2.Comment(
                decisionCommentId,
                "#migration-decision #deferred Preserve only while evidence is unchanged.",
                new SFMReviewSessionV1.Provenance("human", "fixture-reviewer", "1", List.of()),
                review.reviewSession().comments().get(0).target()
        ));
        SFMReviewSessionV2 session = new SFMReviewSessionV2(
                review.reviewSession().schema(), review.reviewSession().id(), review.reviewSession().title(),
                review.reviewSession().coordinateSystem(), review.reviewSession().revisionLanes(), comments,
                review.reviewSession().styleRules(), review.reviewSession().completionPolicy());
        List<SFMReleaseReviewV1.MigrationReport> reports = review.migrationReports().stream().map(value ->
                value.id().equals(chosen.id())
                        ? new SFMReleaseReviewV1.MigrationReport(
                                value.id(), value.sourceSelectorId(), value.sourceEvaluation(),
                                value.candidateEvaluation(), value.oldWitnesses(), value.newCandidates(),
                                SFMReleaseReviewV1.MigrationDecision.DEFERRED, Optional.of(decisionCommentId))
                        : value).toList();
        return new SFMReleaseReviewV1(
                review.schema(), session, review.repositoryBindings(), review.corpusDocuments(),
                review.reviewUnits(), review.selectorBindings(), reports, review.namedQueries(),
                review.resumeState(), review.producerGenerations(), review.completionAttestations());
    }

    private static Map<String, SFMReleaseReviewV1.MigrationReport> reportsBySelector(SFMReleaseReviewV1 review) {
        HashMap<String, SFMReleaseReviewV1.MigrationReport> answer = new HashMap<>();
        review.migrationReports().forEach(value -> answer.put(value.sourceSelectorId(), value));
        return answer;
    }

    private static Fixture fixture() {
        SFMReviewSessionV1.DocumentRevision before = document(BEFORE_ID, "src/A.java", BEFORE);
        SFMReviewSessionV1.DocumentRevision afterB = document(AFTER_B_ID, "src/B.java", AFTER_B);
        SFMReviewSessionV1.DocumentRevision afterC = document(AFTER_C_ID, "src/C.java", AFTER_C);
        SFMReviewSessionV1.LiteralUtf8Range literal = literal(before, "return 1;");
        SFMReleaseReviewV1.PinnedSelection pinned = new SFMReleaseReviewV1.PinnedSelection(
                "selection:before", "selection://fixture/source", 0,
                List.of(new SFMReleaseReviewV1.PinnedSelectionRange(
                        SFMReleaseReviewV1.SelectionDirection.FORWARD,
                        before.id(), before.sha256(), literal.startByte(), literal.endByte())));

        List<String> keys = List.of("stable", "modified", "ambiguous", "missing");
        ArrayList<SFMReviewSessionV2.Comment> comments = new ArrayList<>();
        ArrayList<SFMReleaseReviewV1.CommentSelectorBinding> bindings = new ArrayList<>();
        for (String key : keys) {
            String commentId = "human:" + key;
            SFMReleaseReviewV1.SelectorProposal proposal = new SFMReleaseReviewV1.SelectorProposal(
                    "proposal:" + key,
                    SFMReleaseReviewV1.SelectorKind.BODY,
                    literal,
                    pinned,
                    Optional.of(PROVIDER),
                    Optional.of("method:" + key),
                    List.of(new SFMReleaseReviewV1.Evidence("fixture-case", key)),
                    SFMReleaseReviewV1.ProposalConfidence.EXACT,
                    sha("proposal:" + key),
                    "snapshot:before",
                    List.of()
            );
            comments.add(new SFMReviewSessionV2.Comment(
                    commentId, "#approved Fixture " + key,
                    new SFMReviewSessionV1.Provenance("human", "fixture-reviewer", "1", List.of()),
                    new SFMReviewSessionV2.CommittedReviewTarget(literal)));
            bindings.add(new SFMReleaseReviewV1.CommentSelectorBinding(commentId, pinned, proposal));
        }
        SFMReviewSessionV1.RevisionLane lane = new SFMReviewSessionV1.RevisionLane(
                LANE,
                new SFMReviewSessionV1.Repository("sfm", "."),
                LANE,
                new SFMReviewSessionV1.Snapshot("snapshot:before", List.of(before)),
                new SFMReviewSessionV1.Snapshot("snapshot:after", List.of(afterB, afterC))
        );
        SFMReviewSessionV2 session = new SFMReviewSessionV2(
                SFMReviewSessionV2.SCHEMA, "session:migration-pipeline", "Migration pipeline fixture",
                SFMReviewSessionV1.COORDINATE_SYSTEM, List.of(lane), comments, List.of(),
                new SFMReviewSessionV1.CompletionPolicy("changed_surface", "#approved", List.of("#problem")));
        List<SFMReleaseReviewV1.CorpusDocument> corpus = List.of(
                corpus(before, SFMReleaseReviewV1.SnapshotSide.BEFORE),
                corpus(afterB, SFMReleaseReviewV1.SnapshotSide.AFTER),
                corpus(afterC, SFMReleaseReviewV1.SnapshotSide.AFTER));
        List<SFMReleaseReviewV1.ReviewUnit> units = List.of(
                new SFMReleaseReviewV1.ReviewUnit(
                        "unit:A-to-B", LANE, SFMReleaseReviewV1.ChangeOperation.RENAMED,
                        Optional.of(before.path()), Optional.of(afterB.path()),
                        Optional.of(before.id()), Optional.of(afterB.id()),
                        List.of(new SFMReleaseReviewV1.Utf8Range(0, utf8(BEFORE).length)),
                        List.of(new SFMReleaseReviewV1.Utf8Range(0, utf8(AFTER_B).length)),
                        "java", SFMReleaseReviewV1.SurfaceKind.BODY, Optional.of("method:stable"), Optional.empty(),
                        "fixture-producer", GENERATION),
                new SFMReleaseReviewV1.ReviewUnit(
                        "unit:C", LANE, SFMReleaseReviewV1.ChangeOperation.ADDED,
                        Optional.empty(), Optional.of(afterC.path()), Optional.empty(), Optional.of(afterC.id()),
                        List.of(), List.of(new SFMReleaseReviewV1.Utf8Range(0, utf8(AFTER_C).length)),
                        "java", SFMReleaseReviewV1.SurfaceKind.FILE, Optional.empty(), Optional.empty(),
                        "fixture-producer", GENERATION));
        SFMReleaseReviewV1 review = new SFMReleaseReviewV1(
                SFMReleaseReviewV1.SCHEMA, session,
                List.of(new SFMReleaseReviewV1.RepositoryBinding(
                        LANE, "sfm", ".", "before", "1".repeat(40), "2".repeat(40),
                        "HEAD", "3".repeat(40), "4".repeat(40), List.of())),
                corpus, units, bindings, List.of(), List.of(), SFMReleaseReviewV1.ResumeState.empty(),
                List.of(new SFMReleaseReviewV1.ProducerGeneration(
                        "fixture-producer", GENERATION, sha("producer-input"), sha("producer-output"))),
                List.of());

        SFMReleaseReviewV1.AddressedRange beforeRange = range(before, "return 1;");
        SFMReleaseReviewV1.AddressedRange afterBRange = range(afterB, "return 1;");
        SFMReleaseReviewV1.AddressedRange afterCRange = range(afterC, "return 2;");
        ArrayList<SFMReleaseReviewEvaluator.PreparedSemanticCandidate> candidates = new ArrayList<>();
        for (String key : keys) {
            candidates.add(candidate("source-" + key, "method:" + key, before.path(),
                    SFMReleaseReviewEvaluator.DiffSide.BEFORE, beforeRange, sha("return 1;")));
        }
        candidates.add(candidate("after-stable", "method:stable", afterB.path(),
                SFMReleaseReviewEvaluator.DiffSide.AFTER, afterBRange, sha("return 1;")));
        candidates.add(candidate("after-modified", "method:modified", afterC.path(),
                SFMReleaseReviewEvaluator.DiffSide.AFTER, afterCRange, sha("return 2;")));
        candidates.add(candidate("after-ambiguous-b", "method:ambiguous", afterB.path(),
                SFMReleaseReviewEvaluator.DiffSide.AFTER, afterBRange, sha("return 1;")));
        candidates.add(candidate("after-ambiguous-c", "method:ambiguous", afterC.path(),
                SFMReleaseReviewEvaluator.DiffSide.AFTER, afterCRange, sha("return 2;")));
        SFMReleaseReviewEvaluator.PreparedEvidence evidence = new SFMReleaseReviewEvaluator.PreparedEvidence(
                List.of(
                        scope(before.path(), SFMReleaseReviewEvaluator.DiffSide.BEFORE),
                        scope(afterB.path(), SFMReleaseReviewEvaluator.DiffSide.AFTER),
                        scope(afterC.path(), SFMReleaseReviewEvaluator.DiffSide.AFTER)),
                candidates,
                List.of());
        return new Fixture(review, evidence);
    }

    private static SFMReviewSessionV1.DocumentRevision document(String id, String path, String text) {
        return new SFMReviewSessionV1.DocumentRevision(id, path, "utf-8", sha(text), text);
    }

    private static SFMReleaseReviewV1.CorpusDocument corpus(
            SFMReviewSessionV1.DocumentRevision document,
            SFMReleaseReviewV1.SnapshotSide side
    ) {
        return new SFMReleaseReviewV1.CorpusDocument(
                "corpus:" + document.id(), LANE, side, document.path(), document.id(), document.sha256(),
                "fixture", "fixture://" + document.id(), SFMReleaseReviewV1.Materialization.COMPLETE);
    }

    private static SFMReviewSessionV1.LiteralUtf8Range literal(
            SFMReviewSessionV1.DocumentRevision document,
            String selected
    ) {
        int start = document.text().indexOf(selected);
        return new SFMReviewSessionV1.LiteralUtf8Range(
                document.id(), start, start + utf8(selected).length, document.sha256(), sha(selected));
    }

    private static SFMReleaseReviewV1.AddressedRange range(
            SFMReviewSessionV1.DocumentRevision document,
            String selected
    ) {
        int start = document.text().indexOf(selected);
        return new SFMReleaseReviewV1.AddressedRange(document.id(), start, start + utf8(selected).length);
    }

    private static SFMReleaseReviewEvaluator.PreparedSemanticCandidate candidate(
            String id,
            String key,
            String path,
            SFMReleaseReviewEvaluator.DiffSide side,
            SFMReleaseReviewV1.AddressedRange range,
            String contentHash
    ) {
        return new SFMReleaseReviewEvaluator.PreparedSemanticCandidate(
                PROVIDER, GENERATION, sha("candidate:" + id), LANE, path, "java", side,
                SFMReleaseReviewV1.SelectorKind.BODY, key, List.of(range), contentHash, List.of());
    }

    private static SFMReleaseReviewEvaluator.PreparedScope scope(
            String path,
            SFMReleaseReviewEvaluator.DiffSide side
    ) {
        return new SFMReleaseReviewEvaluator.PreparedScope(
                PROVIDER, GENERATION, sha("scope:" + path + ":" + side),
                LANE, path, "java", side, true, true, false, List.of());
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha(String value) {
        return SFMReviewSessionV1Kernel.sha256(utf8(value));
    }

    private record Fixture(
            SFMReleaseReviewV1 review,
            SFMReleaseReviewEvaluator.PreparedEvidence evidence
    ) {
    }
}
