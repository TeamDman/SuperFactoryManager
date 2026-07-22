package ca.teamdman.sfm.client.review.session;

import ca.teamdman.sfm.client.screen.review.SFMReviewLedger;
import ca.teamdman.sfm.client.screen.review.SFMSourceComparisonFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SFMReviewSessionV1Tests {
    @Test
    void canonicalFixtureRoundTripsDeterministicallyAndEvaluatesIdentically() throws Exception {
        String fixture = Files.readString(fixturePath(), StandardCharsets.UTF_8);
        var session = SFMReviewSessionV1Codec.parse(fixture);
        String canonical = SFMReviewSessionV1Codec.write(session);
        assertEquals(canonical, SFMReviewSessionV1Codec.write(SFMReviewSessionV1Codec.parse(canonical)));
        assertEquals(session, SFMReviewSessionV1Codec.parse(canonical));
        String canonicalSha256 = SFMReviewSessionV1Kernel.sha256(canonical.getBytes(StandardCharsets.UTF_8));
        System.out.println("SFM_REVIEW_SESSION_V1_CANONICAL_SHA256=" + canonicalSha256);
        assertEquals("a560e879a25c2d84f41ad1b322184921fe9241c46b0b6fe4f63387f4e9af2985", canonicalSha256);
        var evaluations = SFMReviewSessionV1Kernel.evaluateAll(session);
        assertEquals(List.of(
                SFMReviewSessionV1Kernel.Status.RESOLVED_EXACTLY,
                SFMReviewSessionV1Kernel.Status.RESOLVED_EXACTLY,
                SFMReviewSessionV1Kernel.Status.RESOLVED_EXACTLY
        ), evaluations.stream().map(SFMReviewSessionV1Kernel.Evaluation::status).toList());
        assertEquals(3, SFMReviewSessionV1Kernel.overlapPairCount(evaluations));
        assertTrue(SFMReviewSessionV1Kernel.isApprovalEffective(session, session.comments().get(0), evaluations.get(0)));
    }

    @Test
    void hashtagsAreDerivedNormalizedEscapedAndDeduplicated() {
        assertEquals(List.of("#approved", "#\"needs review\"", "#café"),
                SFMReviewSessionV1Kernel.derivedHashtags(
                        "#Approved #\"Needs Review\" #CAFÉ #cafe\u0301 ignored#suffix"));
        assertEquals(List.of("#\"escaped \"quote\"\""),
                SFMReviewSessionV1Kernel.derivedHashtags("#\"escaped \\\"quote\\\"\""));
    }

    @Test
    void serializedAuthoritativeTagsAndFutureSelectorsFailClosed() throws Exception {
        String fixture = Files.readString(fixturePath());
        String withTags = fixture.replace("\"text\": \"#approved", "\"tags\": [\"#approved\"],\n      \"text\": \"#approved");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> SFMReviewSessionV1Codec.parse(withTags))
                .getMessage().contains("forbidden authoritative tags"));
        String futureRule = fixture.replaceFirst("\"kind\": \"literal_utf8_range\"", "\"kind\": \"symbol_query\"");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> SFMReviewSessionV1Codec.parse(futureRule))
                .getMessage().contains("Unknown selection rule kind"));
    }

    @Test
    void setAlgebraNormalizesUnionIntersectionAndDifference() throws Exception {
        var session = SFMReviewSessionV1Codec.parse(Files.readString(fixturePath()));
        var broad = session.comments().get(0).selectionRule();
        var narrow = session.comments().get(1).selectionRule();
        assertRanges(sessionWithRule(session, new SFMReviewSessionV1.Union(List.of(broad, broad))), 20, 74);
        assertRanges(sessionWithRule(session, new SFMReviewSessionV1.Intersection(List.of(broad, narrow))), 60, 68);
        var difference = evaluateOnly(sessionWithRule(session, new SFMReviewSessionV1.Difference(broad, List.of(narrow))));
        assertEquals(List.of(
                new SFMReviewSessionV1Kernel.Range("sfm-1.19.2:after:Example.java", 20, 60),
                new SFMReviewSessionV1Kernel.Range("sfm-1.19.2:after:Example.java", 68, 74)
        ), difference.ranges());
    }

    @Test
    void utf8BoundaryDocumentAndSelectionWitnessesAreChecked() {
        String text = "aéz";
        var document = document("doc", text);
        String hash = SFMReviewSessionV1Kernel.sha256(text.getBytes(StandardCharsets.UTF_8));
        var invalid = literalSession(document, new SFMReviewSessionV1.LiteralUtf8Range("doc", 2, 3, hash, hash));
        assertEquals(SFMReviewSessionV1Kernel.Status.INVALID_RULE, evaluateOnly(invalid).status());
        assertEquals(3, SFMReviewSessionV1Kernel.utf8ByteToUtf16Index(text, 4));
        assertThrows(IllegalArgumentException.class, () -> SFMReviewSessionV1Kernel.utf8ByteToUtf16Index(text, 2));
    }

    @Test
    void changedUniqueAndAmbiguousWitnessesResolveConservatively() {
        String selected = "needle";
        String selectedHash = sha(selected);
        var original = new SFMReviewSessionV1.LiteralUtf8Range("doc", 0, selected.length(), sha(selected), selectedHash);
        assertEquals(SFMReviewSessionV1Kernel.Status.RESOLVED_WITH_RELOCATION,
                evaluateOnly(literalSession(document("doc", "xxneedle"), original)).status());
        assertEquals(SFMReviewSessionV1Kernel.Status.AMBIGUOUS,
                evaluateOnly(literalSession(document("doc", "needleneedle"), original)).status());
        assertEquals(SFMReviewSessionV1Kernel.Status.CONTENT_CHANGED,
                evaluateOnly(literalSession(document("doc", "changed"), original)).status());
        var missing = new SFMReviewSessionV1.LiteralUtf8Range("missing", 0, 0, sha(""), sha(""));
        assertEquals(SFMReviewSessionV1Kernel.Status.SCOPE_MISSING,
                evaluateOnly(literalSession(document("doc", ""), missing)).status());
    }

    @Test
    void appDataStoreRecoversLastValidSession(@TempDir Path directory) throws Exception {
        var session = SFMReviewSessionV1Codec.parse(Files.readString(fixturePath()));
        var store = new SFMReviewSessionV1Store(directory.resolve("session.json"));
        store.save(session);
        Files.writeString(store.path(), "{broken");
        var loaded = store.load();
        assertTrue(loaded.recoveredLastValid());
        assertEquals(session, loaded.session().orElseThrow());
        assertTrue(loaded.diagnostics().stream().anyMatch(message -> message.contains("Invalid active")));
    }

    @Test
    void legacyLedgerProjectsToOrdinaryCommentsWithoutBooleanFields() {
        var comparison = SFMSourceComparisonFixtures.reviewWalkthrough();
        var operation = comparison.operations().get(3);
        var decision = new SFMReviewLedger.Decision(SFMReviewLedger.ReviewState.REVIEWED,
                SFMReviewLedger.HumanDecision.APPROVED, operation.beforeHash(), "old-hash");
        var rule = new SFMReviewSessionV1.LiteralUtf8Range("doc", 0, 0, sha(""), sha(""));
        var comments = SFMLegacyReviewLedgerProjection.project(operation, decision, rule);
        assertEquals(2, comments.size());
        assertEquals(List.of("#reviewed", "#approved", "#stale"),
                SFMReviewSessionV1Kernel.derivedHashtags(comments.get(0).text()));
        assertEquals("legacy_ledger", comments.get(0).provenance().kind());
        assertEquals(List.of("#problem", "#audit-forbidden"),
                SFMReviewSessionV1Kernel.derivedHashtags(comments.get(1).text()));
    }

    private static void assertRanges(SFMReviewSessionV1 session, int start, int end) {
        assertEquals(List.of(new SFMReviewSessionV1Kernel.Range("sfm-1.19.2:after:Example.java", start, end)),
                evaluateOnly(session).ranges());
    }

    private static SFMReviewSessionV1Kernel.Evaluation evaluateOnly(SFMReviewSessionV1 session) {
        return SFMReviewSessionV1Kernel.evaluateAll(session).get(0);
    }

    private static SFMReviewSessionV1 sessionWithRule(SFMReviewSessionV1 session,
                                                       SFMReviewSessionV1.SelectionRule rule) {
        return new SFMReviewSessionV1(session.schema(), session.id(), session.title(), session.coordinateSystem(),
                session.revisionLanes(), List.of(new SFMReviewSessionV1.Comment("test", "#approved", session.comments().get(0).provenance(), rule)),
                session.styleRules(), session.completionPolicy());
    }

    private static SFMReviewSessionV1 literalSession(SFMReviewSessionV1.DocumentRevision document,
                                                      SFMReviewSessionV1.SelectionRule rule) {
        var snapshot = new SFMReviewSessionV1.Snapshot("snapshot", List.of(document));
        var lane = new SFMReviewSessionV1.RevisionLane("lane", new SFMReviewSessionV1.Repository("repo", "fixture"),
                null, new SFMReviewSessionV1.Snapshot("before", List.of()), snapshot);
        var comment = new SFMReviewSessionV1.Comment("comment", "#approved", new SFMReviewSessionV1.Provenance(
                "human", "test", "1", List.of()), rule);
        return new SFMReviewSessionV1(SFMReviewSessionV1.SCHEMA, "session", "Session",
                SFMReviewSessionV1.COORDINATE_SYSTEM, List.of(lane), List.of(comment), List.of(),
                new SFMReviewSessionV1.CompletionPolicy("changed_surface", "#approved", List.of("#problem")));
    }

    private static SFMReviewSessionV1.DocumentRevision document(String id, String text) {
        return new SFMReviewSessionV1.DocumentRevision(id, "src/Test.java", "utf-8", sha(text), text);
    }

    private static String sha(String text) {
        return SFMReviewSessionV1Kernel.sha256(text.getBytes(StandardCharsets.UTF_8));
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
