package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewEvaluatorTests {
    private static final String LANE = "1.19.2";
    private static final String PROVIDER = "fixture:java";
    private static final String GENERATION = "fixture-generation-1";
    private static final String BEFORE_TEXT = "class A { int oldName() { return 1; } }\n";
    private static final String AFTER_TEXT = "class A { int newName() { return 1; } }\n";
    private static final String MODIFIED_TEXT = "class A { int newName(int x) { return 2 + x; } }\n";

    @Test
    void indexIsCanonicalAcrossInputOrderAndIndexesEveryRequiredDimension() {
        Fixture fixture = fixture();
        var forward = index(fixture, fixture.evidence());
        ArrayList<SFMReleaseReviewEvaluator.PreparedSemanticCandidate> reversed =
                new ArrayList<>(fixture.evidence().semanticCandidates());
        Collections.reverse(reversed);
        var reverse = index(fixture, new SFMReleaseReviewEvaluator.PreparedEvidence(
                fixture.evidence().scopes(), reversed, fixture.evidence().diffCandidates()));

        var rule = semantic("method:stable", fixture.beforeBodyWitness());
        var left = forward.evaluate("selector:stable", rule, afterScope());
        var right = reverse.evaluate("selector:stable", rule, afterScope());
        assertEquals(left, right);
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.RELOCATED, left.result().status());
        assertEquals(List.of("before-a", "after-b", "after-c"),
                forward.documentIdsForLanguage("java"));
        assertEquals(List.of("before-a"), forward.documentIdsForPath("src/A.java"));
        assertEquals(List.of("after-b"), forward.documentIdsForContentHash(sha(AFTER_TEXT)));
        assertEquals(3, forward.documentCount());
    }

    @Test
    void literalAndTextRulesDistinguishExactRelocatedChangedAndDeleted() {
        Fixture fixture = fixture();
        var index = index(fixture, fixture.evidence());
        SFMReviewSessionV1.LiteralUtf8Range beforeReturn = literal(
                "before-a", BEFORE_TEXT, "return 1;");

        var exact = index.evaluate("literal:exact", beforeReturn,
                new SFMReleaseReviewEvaluator.Scope(List.of(LANE), List.of("src/A.java"),
                        List.of("java"), List.of(SFMReleaseReviewEvaluator.DiffSide.BEFORE)));
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.EXACT, exact.result().status());

        var relocated = index.evaluate("literal:relocated", beforeReturn,
                new SFMReleaseReviewEvaluator.Scope(List.of(LANE), List.of("src/B.java"),
                        List.of("java"), List.of(SFMReleaseReviewEvaluator.DiffSide.AFTER)));
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.RELOCATED, relocated.result().status());
        assertEquals("after-b", relocated.result().ranges().get(0).documentRevisionId());

        var text = index.evaluate("text", new SFMReleaseReviewEvaluator.TextRule(
                "return 2 + x;".getBytes(StandardCharsets.UTF_8)),
                new SFMReleaseReviewEvaluator.Scope(List.of(LANE), List.of("src/C.java"),
                        List.of("java"), List.of(SFMReleaseReviewEvaluator.DiffSide.AFTER)));
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.RELOCATED, text.result().status());

        var deleted = index.evaluate("literal:deleted", beforeReturn,
                new SFMReleaseReviewEvaluator.Scope(List.of(LANE), List.of("src/C.java"),
                        List.of("java"), List.of(SFMReleaseReviewEvaluator.DiffSide.AFTER)));
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.MISSING, deleted.result().status());
        assertFalse(deleted.result().diagnostics().isEmpty());
    }

    @Test
    void semanticCandidatesReportMovedModifiedAndOverloadAmbiguityWithoutFirstMatch() {
        Fixture fixture = fixture();
        var index = index(fixture, fixture.evidence());

        var moved = index.evaluate("semantic:moved",
                semantic("method:stable", fixture.beforeBodyWitness()), afterScope());
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.RELOCATED, moved.result().status());
        assertEquals(List.of(range("after-b", AFTER_TEXT, "return 1;")), moved.result().ranges());

        var modified = index.evaluate("semantic:modified",
                semantic("method:modified", fixture.beforeBodyWitness()), afterScope());
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.CONTENT_CHANGED, modified.result().status());
        assertTrue(modified.result().ranges().isEmpty());
        assertEquals(List.of(range("after-c", MODIFIED_TEXT, "return 2 + x;")),
                modified.result().candidates());

        var overload = index.evaluate("semantic:overload",
                semantic("method:overload", fixture.beforeBodyWitness()), afterScope());
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.AMBIGUOUS, overload.result().status());
        assertTrue(overload.result().ranges().isEmpty(), "ambiguous evidence must not choose a range");
        assertEquals(2, overload.result().candidates().size());
        assertTrue(overload.result().diagnostics().stream().anyMatch(value -> value.contains("retained")));
    }

    @Test
    void renamedMethodRetainsAnInspectableRelocatedBodyInsteadOfGuessingByTheNewName() {
        assertTrue(BEFORE_TEXT.contains("oldName()"));
        assertTrue(AFTER_TEXT.contains("newName()"));
        Fixture fixture = fixture();

        var renamed = index(fixture, fixture.evidence()).evaluate(
                "semantic:renamed-method",
                semantic("method:renamed:oldName()->newName()", fixture.beforeBodyWitness()),
                afterScope()
        );

        assertEquals(SFMReleaseReviewV1.EvaluationStatus.RELOCATED, renamed.result().status());
        assertEquals(List.of(range("after-b", AFTER_TEXT, "return 1;")), renamed.result().ranges());
        assertTrue(renamed.result().candidates().isEmpty());
        assertTrue(renamed.result().diagnostics().stream().anyMatch(value -> value.contains("relocated")));
    }

    @Test
    void preparedDiffRegionsAreExactRelocatedOrChangedUsingTheSameBoundedEvidence() {
        Fixture fixture = fixture();
        var index = index(fixture, fixture.evidence());
        var moved = index.evaluate("diff:moved", new SFMReleaseReviewEvaluator.DiffRegionRule(
                "hunk:return", SFMReleaseReviewEvaluator.DiffSide.AFTER,
                Optional.of(fixture.beforeBodyWitness())), afterScope());
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.RELOCATED, moved.result().status());

        var changed = index.evaluate("diff:changed", new SFMReleaseReviewEvaluator.DiffRegionRule(
                "hunk:modified", SFMReleaseReviewEvaluator.DiffSide.AFTER,
                Optional.of(fixture.beforeBodyWitness())), afterScope());
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.CONTENT_CHANGED, changed.result().status());
        assertEquals(1, changed.result().candidates().size());
    }

    @Test
    void parseGapAndMissingPreparedScopeAreVisibleScopeMissingStates() {
        Fixture fixture = fixture();
        var parseGap = new SFMReleaseReviewEvaluator.PreparedEvidence(
                List.of(scope("src/B.java", SFMReleaseReviewEvaluator.DiffSide.AFTER,
                        false, false, false, "parser gap")), List.of(), List.of());
        var result = index(fixture, parseGap).evaluate("semantic:parse-gap",
                semantic("method:stable", fixture.beforeBodyWitness()),
                new SFMReleaseReviewEvaluator.Scope(List.of(LANE), List.of("src/B.java"),
                        List.of("java"), List.of(SFMReleaseReviewEvaluator.DiffSide.AFTER)));
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.SCOPE_MISSING, result.result().status());
        assertTrue(result.result().diagnostics().stream().anyMatch(value -> value.contains("parser gap")));

        var absent = index(fixture, SFMReleaseReviewEvaluator.PreparedEvidence.empty())
                .evaluate("semantic:no-scope", semantic("method:stable", fixture.beforeBodyWitness()), afterScope());
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.SCOPE_MISSING, absent.result().status());
    }

    @Test
    void missingSemanticKeyIsTerminalAndIncrementalEvidenceChangesInvalidationKeys() {
        Fixture fixture = fixture();
        var first = index(fixture, fixture.evidence()).evaluate("semantic:missing",
                semantic("method:removed", fixture.beforeBodyWitness()), afterScope());
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.MISSING, first.result().status());

        var candidate = fixture.evidence().semanticCandidates().get(0);
        var changedCandidate = new SFMReleaseReviewEvaluator.PreparedSemanticCandidate(
                candidate.provider(), "fixture-generation-2", sha("changed-evidence"), candidate.laneId(),
                candidate.path(), candidate.language(), candidate.side(), candidate.kind(), candidate.semanticKey(),
                candidate.ranges(), candidate.contentSha256(), candidate.diagnostics());
        List<SFMReleaseReviewEvaluator.PreparedSemanticCandidate> changed = new ArrayList<>(
                fixture.evidence().semanticCandidates());
        changed.set(0, changedCandidate);
        var second = index(fixture, new SFMReleaseReviewEvaluator.PreparedEvidence(
                fixture.evidence().scopes(), changed, fixture.evidence().diffCandidates()))
                .evaluate("semantic:stable", semantic("method:stable", fixture.beforeBodyWitness()), afterScope());
        var baseline = index(fixture, fixture.evidence()).evaluate("semantic:stable",
                semantic("method:stable", fixture.beforeBodyWitness()), afterScope());
        assertNotEquals(baseline.result().invalidationKeys(), second.result().invalidationKeys());
    }

    @Test
    void unionIntersectionAndDifferenceObeySetLaws() {
        Fixture fixture = fixture();
        var index = index(fixture, fixture.evidence());
        var a = new SFMReleaseReviewEvaluator.LiteralRule(literal("after-b", AFTER_TEXT, "return 1;"));
        var b = new SFMReleaseReviewEvaluator.LiteralRule(literal("after-b", AFTER_TEXT, "1;"));
        var scope = new SFMReleaseReviewEvaluator.Scope(List.of(LANE), List.of("src/B.java"),
                List.of("java"), List.of(SFMReleaseReviewEvaluator.DiffSide.AFTER));

        var unionAB = index.evaluate("union-ab", new SFMReleaseReviewEvaluator.UnionRule(List.of(a, b)), scope);
        var unionBA = index.evaluate("union-ba", new SFMReleaseReviewEvaluator.UnionRule(List.of(b, a)), scope);
        assertEquals(unionAB.result().ranges(), unionBA.result().ranges(), "union is commutative");

        var idempotent = index.evaluate("union-aa", new SFMReleaseReviewEvaluator.UnionRule(List.of(a, a)), scope);
        var plain = index.evaluate("a", a, scope);
        assertEquals(plain.result().ranges(), idempotent.result().ranges(), "union is idempotent");

        var intersection = index.evaluate("intersection",
                new SFMReleaseReviewEvaluator.IntersectionRule(List.of(a, b)), scope);
        assertEquals(index.evaluate("b", b, scope).result().ranges(), intersection.result().ranges());

        var difference = index.evaluate("difference",
                new SFMReleaseReviewEvaluator.DifferenceRule(a, List.of(b)), scope);
        assertEquals("return ", slice(AFTER_TEXT, difference.result().ranges().get(0)));
    }

    @Test
    void workCountersAreDeterministicAndLimitsFailClosed() {
        Fixture fixture = fixture();
        var normal = index(fixture, fixture.evidence()).evaluate("text",
                new SFMReleaseReviewEvaluator.TextRule("return 1;".getBytes(StandardCharsets.UTF_8)), afterScope());
        assertTrue(normal.work().documentsVisited() > 0);
        assertTrue(normal.work().comparedBytes() > 0);
        assertTrue(normal.work().operations() > 0);
        assertEquals(normal.work(), index(fixture, fixture.evidence()).evaluate("text",
                new SFMReleaseReviewEvaluator.TextRule("return 1;".getBytes(StandardCharsets.UTF_8)), afterScope()).work());

        var bounded = SFMReleaseReviewEvaluator.Index.build(
                fixture.review(), fixture.evidence(), new SFMReleaseReviewEvaluator.Limits(10, 10, 5, 100));
        var exceeded = bounded.evaluate("text:bounded",
                new SFMReleaseReviewEvaluator.TextRule("return 1;".getBytes(StandardCharsets.UTF_8)), afterScope());
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.INVALID, exceeded.result().status());
        assertTrue(exceeded.result().diagnostics().stream().anyMatch(value -> value.contains("budget")));
        assertTrue(exceeded.work().comparedBytes() > 5);
    }

    @Test
    void fieldSelectorAtTwoRevisionsDoesNotTransferApprovalToChangedOrAmbiguousField() {
        // Prepared-symbol boundary proof, using real field text (not a method renamed as a field).
        String path = "src/DiskItem.java";
        String oldField = "private int capacity = 1;";
        String newField = "private int capacity = 2;";
        String oldText = "class DiskItem { " + oldField + " }\n";
        String newText = "class DiskItem { " + newField + " }\n";
        var before = document("field-before", path, oldText);
        var after = document("field-after", path, newText);
        var original = fixture().review();
        var lane = new SFMReviewSessionV1.RevisionLane(LANE,
                new SFMReviewSessionV1.Repository("sfm", "."), LANE,
                new SFMReviewSessionV1.Snapshot("field-commit-before", List.of(before)),
                new SFMReviewSessionV1.Snapshot("field-captured-after", List.of(after)));
        var session = new SFMReviewSessionV2(SFMReviewSessionV2.SCHEMA, "field-session", "Field revisions",
                SFMReviewSessionV1.COORDINATE_SYSTEM, List.of(lane), List.of(), List.of(),
                original.reviewSession().completionPolicy());
        var unit = new SFMReleaseReviewV1.ReviewUnit("field-unit", LANE,
                SFMReleaseReviewV1.ChangeOperation.MODIFIED, Optional.of(path), Optional.of(path),
                Optional.of(before.id()), Optional.of(after.id()),
                List.of(new SFMReleaseReviewV1.Utf8Range(0, oldText.length())),
                List.of(new SFMReleaseReviewV1.Utf8Range(0, newText.length())),
                "java", SFMReleaseReviewV1.SurfaceKind.FILE, Optional.empty(), Optional.empty(),
                "fixture-producer", GENERATION);
        var review = new SFMReleaseReviewV1(SFMReleaseReviewV1.SCHEMA, session,
                original.repositoryBindings(),
                List.of(corpus("field-before-corpus", before, SFMReleaseReviewV1.SnapshotSide.BEFORE),
                        corpus("field-after-corpus", after, SFMReleaseReviewV1.SnapshotSide.AFTER)),
                List.of(unit), List.of(), List.of(), List.of(), SFMReleaseReviewV1.ResumeState.empty(),
                original.producerGenerations(), List.of());
        String key = "ca.teamdman.sfm.common.item.DiskItem capacity";
        var beforeRange = range(before.id(), oldText, oldField);
        var afterRange = range(after.id(), newText, newField);
        var oldCandidate = new SFMReleaseReviewEvaluator.PreparedSemanticCandidate(PROVIDER, GENERATION,
                sha("field-old"), LANE, path, "java", SFMReleaseReviewEvaluator.DiffSide.BEFORE,
                SFMReleaseReviewV1.SelectorKind.DECLARATION, key, List.of(beforeRange), sha(oldField), List.of());
        var newCandidate = new SFMReleaseReviewEvaluator.PreparedSemanticCandidate(PROVIDER, GENERATION,
                sha("field-new"), LANE, path, "java", SFMReleaseReviewEvaluator.DiffSide.AFTER,
                SFMReleaseReviewV1.SelectorKind.DECLARATION, key, List.of(afterRange), sha(newField), List.of());
        var scopes = List.of(scope(path, SFMReleaseReviewEvaluator.DiffSide.BEFORE, true, true, true, ""),
                scope(path, SFMReleaseReviewEvaluator.DiffSide.AFTER, true, true, true, ""));
        var evidence = new SFMReleaseReviewEvaluator.PreparedEvidence(scopes,
                List.of(oldCandidate, newCandidate), List.of());
        var index = SFMReleaseReviewEvaluator.Index.build(review, evidence, SFMReleaseReviewEvaluator.Limits.defaults());
        var rule = new SFMReleaseReviewEvaluator.SemanticRule(SFMReleaseReviewV1.SelectorKind.DECLARATION, key,
                Optional.of(new SFMReleaseReviewEvaluator.Witness(List.of(beforeRange), sha(oldField))));
        var beforeScope = new SFMReleaseReviewEvaluator.Scope(List.of(LANE), List.of(path), List.of("java"),
                List.of(SFMReleaseReviewEvaluator.DiffSide.BEFORE));
        var exact = index.evaluate("field-pinned", rule, beforeScope).result();
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.EXACT, exact.status());
        assertEquals(List.of(beforeRange), exact.ranges());
        var changed = index.evaluate("field-new-revision", rule, afterScope()).result();
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.CONTENT_CHANGED, changed.status());
        assertTrue(changed.ranges().isEmpty());
        assertEquals(List.of(afterRange), changed.candidates());
        var ambiguous = index.evaluate("field-unscoped-revisions", rule, SFMReleaseReviewEvaluator.Scope.all()).result();
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.AMBIGUOUS, ambiguous.status());
        assertTrue(ambiguous.ranges().isEmpty());
        var missing = SFMReleaseReviewEvaluator.Index.build(review,
                new SFMReleaseReviewEvaluator.PreparedEvidence(scopes, List.of(oldCandidate), List.of()),
                SFMReleaseReviewEvaluator.Limits.defaults()).evaluate("field-deleted", rule, afterScope()).result();
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.MISSING, missing.status());
        assertTrue(missing.ranges().isEmpty());
    }

    private static SFMReleaseReviewEvaluator.SemanticRule semantic(
            String key, SFMReleaseReviewEvaluator.Witness witness) {
        return new SFMReleaseReviewEvaluator.SemanticRule(
                SFMReleaseReviewV1.SelectorKind.BODY, key, Optional.of(witness));
    }

    private static SFMReleaseReviewEvaluator.Index index(
            Fixture fixture, SFMReleaseReviewEvaluator.PreparedEvidence evidence) {
        return SFMReleaseReviewEvaluator.Index.build(
                fixture.review(), evidence, SFMReleaseReviewEvaluator.Limits.defaults());
    }

    private static SFMReleaseReviewEvaluator.Scope afterScope() {
        return new SFMReleaseReviewEvaluator.Scope(List.of(LANE), List.of(), List.of("java"),
                List.of(SFMReleaseReviewEvaluator.DiffSide.AFTER));
    }

    private static Fixture fixture() {
        var before = document("before-a", "src/A.java", BEFORE_TEXT);
        var after = document("after-b", "src/B.java", AFTER_TEXT);
        var modified = document("after-c", "src/C.java", MODIFIED_TEXT);
        var lane = new SFMReviewSessionV1.RevisionLane(
                LANE,
                new SFMReviewSessionV1.Repository("sfm", "."),
                LANE,
                new SFMReviewSessionV1.Snapshot("snapshot-before", List.of(before)),
                new SFMReviewSessionV1.Snapshot("snapshot-after", List.of(after, modified))
        );
        var session = new SFMReviewSessionV2(
                SFMReviewSessionV2.SCHEMA, "session", "Evaluator fixture",
                SFMReviewSessionV1.COORDINATE_SYSTEM, List.of(lane), List.of(), List.of(),
                new SFMReviewSessionV1.CompletionPolicy("changed_surface", "#approved", List.of("#problem"))
        );
        List<SFMReleaseReviewV1.CorpusDocument> corpus = List.of(
                corpus("corpus-before", before, SFMReleaseReviewV1.SnapshotSide.BEFORE),
                corpus("corpus-after-b", after, SFMReleaseReviewV1.SnapshotSide.AFTER),
                corpus("corpus-after-c", modified, SFMReleaseReviewV1.SnapshotSide.AFTER)
        );
        List<SFMReleaseReviewV1.ReviewUnit> units = List.of(
                unit("unit-a", Optional.of("src/A.java"), Optional.empty(), Optional.of("before-a"), Optional.empty()),
                unit("unit-b", Optional.empty(), Optional.of("src/B.java"), Optional.empty(), Optional.of("after-b")),
                unit("unit-c", Optional.empty(), Optional.of("src/C.java"), Optional.empty(), Optional.of("after-c"))
        );
        var review = new SFMReleaseReviewV1(
                SFMReleaseReviewV1.SCHEMA,
                session,
                List.of(new SFMReleaseReviewV1.RepositoryBinding(
                        LANE, "sfm", ".", "before", "1".repeat(40), "2".repeat(40),
                        "HEAD", "3".repeat(40), "4".repeat(40), List.of())),
                corpus,
                units,
                List.of(), List.of(), List.of(), SFMReleaseReviewV1.ResumeState.empty(),
                List.of(new SFMReleaseReviewV1.ProducerGeneration(
                        "fixture-producer", GENERATION, sha("input"), sha("output"))),
                List.of()
        );

        var beforeBody = range("before-a", BEFORE_TEXT, "return 1;");
        var afterBody = range("after-b", AFTER_TEXT, "return 1;");
        var modifiedBody = range("after-c", MODIFIED_TEXT, "return 2 + x;");
        var witness = new SFMReleaseReviewEvaluator.Witness(List.of(beforeBody), sha("return 1;"));
        List<SFMReleaseReviewEvaluator.PreparedScope> scopes = List.of(
                scope("src/A.java", SFMReleaseReviewEvaluator.DiffSide.BEFORE, true, true, true, ""),
                scope("src/B.java", SFMReleaseReviewEvaluator.DiffSide.AFTER, true, true, true, ""),
                scope("src/C.java", SFMReleaseReviewEvaluator.DiffSide.AFTER, true, true, true, "")
        );
        List<SFMReleaseReviewEvaluator.PreparedSemanticCandidate> semantic = List.of(
                candidate("stable", "method:stable", "src/B.java", SFMReleaseReviewEvaluator.DiffSide.AFTER,
                        List.of(afterBody), sha("return 1;")),
                candidate("renamed", "method:renamed:oldName()->newName()", "src/B.java",
                        SFMReleaseReviewEvaluator.DiffSide.AFTER, List.of(afterBody), sha("return 1;")),
                candidate("modified", "method:modified", "src/C.java", SFMReleaseReviewEvaluator.DiffSide.AFTER,
                        List.of(modifiedBody), sha("return 2 + x;")),
                candidate("overload-b", "method:overload", "src/B.java", SFMReleaseReviewEvaluator.DiffSide.AFTER,
                        List.of(afterBody), sha("return 1;")),
                candidate("overload-c", "method:overload", "src/C.java", SFMReleaseReviewEvaluator.DiffSide.AFTER,
                        List.of(modifiedBody), sha("return 2 + x;"))
        );
        List<SFMReleaseReviewEvaluator.PreparedDiffCandidate> diffs = List.of(
                diff("return", "hunk:return", "src/B.java", afterBody, sha("return 1;")),
                diff("modified", "hunk:modified", "src/C.java", modifiedBody, sha("return 2 + x;"))
        );
        return new Fixture(review, new SFMReleaseReviewEvaluator.PreparedEvidence(scopes, semantic, diffs), witness);
    }

    private static SFMReviewSessionV1.DocumentRevision document(String id, String path, String text) {
        return new SFMReviewSessionV1.DocumentRevision(id, path, "utf-8", sha(text), text);
    }

    private static SFMReleaseReviewV1.CorpusDocument corpus(
            String id, SFMReviewSessionV1.DocumentRevision document, SFMReleaseReviewV1.SnapshotSide side) {
        return new SFMReleaseReviewV1.CorpusDocument(
                id, LANE, side, document.path(), document.id(), document.sha256(),
                "fixture", "fixture://" + document.id(), SFMReleaseReviewV1.Materialization.COMPLETE);
    }

    private static SFMReleaseReviewV1.ReviewUnit unit(
            String id,
            Optional<String> beforePath,
            Optional<String> afterPath,
            Optional<String> beforeRevision,
            Optional<String> afterRevision
    ) {
        return new SFMReleaseReviewV1.ReviewUnit(
                id, LANE,
                beforePath.isPresent() && afterPath.isPresent() ? SFMReleaseReviewV1.ChangeOperation.MODIFIED
                        : beforePath.isPresent() ? SFMReleaseReviewV1.ChangeOperation.DELETED
                        : SFMReleaseReviewV1.ChangeOperation.ADDED,
                beforePath, afterPath, beforeRevision, afterRevision,
                beforeRevision.isPresent() ? List.of(new SFMReleaseReviewV1.Utf8Range(0, BEFORE_TEXT.length())) : List.of(),
                afterRevision.isPresent() ? List.of(new SFMReleaseReviewV1.Utf8Range(0, 1)) : List.of(),
                "java", SFMReleaseReviewV1.SurfaceKind.FILE, Optional.empty(), Optional.empty(),
                "fixture-producer", GENERATION
        );
    }

    private static SFMReleaseReviewEvaluator.PreparedScope scope(
            String path,
            SFMReleaseReviewEvaluator.DiffSide side,
            boolean syntax,
            boolean symbols,
            boolean diff,
            String diagnostic
    ) {
        return new SFMReleaseReviewEvaluator.PreparedScope(
                PROVIDER, GENERATION, sha("scope:" + path + ":" + side + ":" + syntax + ":" + symbols + ":" + diff),
                LANE, path, "java", side, syntax, symbols, diff,
                diagnostic.isEmpty() ? List.of() : List.of(diagnostic));
    }

    private static SFMReleaseReviewEvaluator.PreparedSemanticCandidate candidate(
            String id,
            String key,
            String path,
            SFMReleaseReviewEvaluator.DiffSide side,
            List<SFMReleaseReviewV1.AddressedRange> ranges,
            String contentHash
    ) {
        return new SFMReleaseReviewEvaluator.PreparedSemanticCandidate(
                PROVIDER, GENERATION, sha("semantic:" + id), LANE, path, "java", side,
                SFMReleaseReviewV1.SelectorKind.BODY, key, ranges, contentHash, List.of());
    }

    private static SFMReleaseReviewEvaluator.PreparedDiffCandidate diff(
            String id, String key, String path, SFMReleaseReviewV1.AddressedRange range, String contentHash) {
        return new SFMReleaseReviewEvaluator.PreparedDiffCandidate(
                PROVIDER, GENERATION, sha("diff:" + id), LANE, path, "java",
                SFMReleaseReviewEvaluator.DiffSide.AFTER, key, List.of(range), contentHash, List.of());
    }

    private static SFMReviewSessionV1.LiteralUtf8Range literal(String revision, String text, String selected) {
        int start = text.indexOf(selected);
        return new SFMReviewSessionV1.LiteralUtf8Range(
                revision, start, start + selected.getBytes(StandardCharsets.UTF_8).length,
                sha(text), sha(selected));
    }

    private static SFMReleaseReviewV1.AddressedRange range(String revision, String text, String selected) {
        int start = text.indexOf(selected);
        return new SFMReleaseReviewV1.AddressedRange(
                revision, start, start + selected.getBytes(StandardCharsets.UTF_8).length);
    }

    private static String slice(String text, SFMReleaseReviewV1.AddressedRange range) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new String(bytes, range.startByte(), range.endByte() - range.startByte(), StandardCharsets.UTF_8);
    }

    private static String sha(String value) {
        return SFMReviewSessionV1Kernel.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private record Fixture(
            SFMReleaseReviewV1 review,
            SFMReleaseReviewEvaluator.PreparedEvidence evidence,
            SFMReleaseReviewEvaluator.Witness beforeBodyWitness
    ) {
    }
}
