package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SFMReviewEvidenceTableTests {
    @Test void observationsDoNotPersistUntilSelectedAndEqualBytesDeduplicateAcrossPaths() {
        var first = observed("first", "src/A.java", "a😀\r\n", Optional.empty());
        var second = observed("second", "src/B.java", first.text(), Optional.empty());
        var unrelated = observed("other", "src/C.java", "do not embed", Optional.empty());
        var observation = Map.of("first", first, "second", second, "other", unrelated);
        assertTrue(SFMReviewEvidenceTable.EMPTY.contents().isEmpty());
        var captured = SFMReviewEvidenceTable.EMPTY.capture(proposal(first, 0, 7), observation);
        assertEquals(1, captured.documents().size());
        assertEquals(first.text(), captured.contents().get(0).text());
        var again = captured.capture(proposal(second, 0, 7), observation);
        assertEquals(2, again.documents().size());
        assertEquals(1, again.contents().size());
        assertSame(captured.contents().get(0), again.contents().get(0), "equal bytes reuse validated content");
        assertSame(again, again.capture(proposal(first, 0, 7), observation));
        assertEquals(1, captured.documents().size(), "capture cannot mutate the previous authority");
    }

    @Test void gitEvidenceStoresImmutableReferenceRatherThanSourceBody() {
        var source = observed("git-doc", "A.java", "class A {}\n", Optional.of(
                new SFMReviewEvidenceTable.GitReference("sfm", "a".repeat(40), "b".repeat(40))));
        var captured = SFMReviewEvidenceTable.EMPTY.capture(proposal(source, 0, 5), Map.of("git-doc", source));
        assertTrue(captured.contents().isEmpty());
        assertEquals(source.document(), captured.documents().get(0));
        assertSame(captured, captured.capture(proposal(source, 0, 5), Map.of("git-doc", source)));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMReviewEvidenceTable.GitReference("sfm", "HEAD", "b".repeat(40)));
    }

    @Test void changedOrMissingDisplayedEvidenceFailsRatherThanRecapturing() {
        var old = observed("old", "A.java", "old\r\n", Optional.empty());
        var changed = observed("old", "A.java", "new\n", Optional.empty());
        var request = proposal(old, 0, 3);
        assertThrows(IllegalArgumentException.class,
                () -> SFMReviewEvidenceTable.EMPTY.capture(request, Map.of("old", changed)));
        assertThrows(IllegalArgumentException.class,
                () -> SFMReviewEvidenceTable.EMPTY.capture(request, Map.of()));
        var captured = SFMReviewEvidenceTable.EMPTY.capture(request, Map.of("old", old));
        assertEquals("old\r\n", captured.contents().get(0).text());
        assertThrows(IllegalArgumentException.class,
                () -> captured.capture(proposal(changed, 0, 3), Map.of("old", changed)));
    }

    @Test void equalContentDoesNotConflateSuccessorIdentityAndInvalidRangesStillFailOnWarmCapture() {
        var first = observed("first", "A.java", "a😀\r\n", Optional.empty());
        var successor = observed("successor", "A.java", first.text(), Optional.empty());
        var observation = Map.of("first", first, "successor", successor);
        var captured = SFMReviewEvidenceTable.EMPTY.capture(proposal(first, 0, 7), observation);
        var next = captured.capture(proposal(successor, 0, 7), observation);
        assertNotSame(captured, next);
        assertEquals(2, next.documents().size());
        assertSame(captured.contents().get(0), next.contents().get(0));
        assertThrows(IllegalArgumentException.class,
                () -> next.capture(proposal(first, 2, 4), observation));
        assertEquals(1, captured.documents().size());
    }

    @Test void invalidUtf8BoundariesAndContentAreRejected() {
        var source = observed("doc", "A.java", "a😀\r\n", Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> SFMReviewEvidenceTable.EMPTY.capture(proposal(source, 2, 4), Map.of("doc", source)));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMReviewEvidenceTable.Content("0".repeat(64), "actual"));
        assertThrows(IllegalArgumentException.class,
                () -> observed("bad", "A.java", "\uD800", Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMReviewEvidenceTable(List.of(), List.of(source.document())));
    }

    private static SFMReviewEvidenceTable.Observed observed(String id, String path, String text,
                                                            Optional<SFMReviewEvidenceTable.GitReference> git) {
        return new SFMReviewEvidenceTable.Observed(
                new SFMReviewEvidenceTable.Document(id, path, hash(text.getBytes(StandardCharsets.UTF_8)), git), text);
    }

    private static SFMReleaseReviewV1.SelectorProposal proposal(SFMReviewEvidenceTable.Observed source,
                                                               int start, int end) {
        var bytes = source.text().getBytes(StandardCharsets.UTF_8);
        var selection = new SFMReleaseReviewV1.PinnedSelection("selection", "explicit", 0, List.of(
                new SFMReleaseReviewV1.PinnedSelectionRange(SFMReleaseReviewV1.SelectionDirection.FORWARD,
                        source.document().revisionId(), source.document().sha256(), start, end)));
        var rule = new SFMReviewSessionV1.LiteralUtf8Range(source.document().revisionId(), start, end,
                source.document().sha256(), hash(java.util.Arrays.copyOfRange(bytes, start, end)));
        return new SFMReleaseReviewV1.SelectorProposal("proposal", SFMReleaseReviewV1.SelectorKind.LITERAL,
                rule, selection, Optional.empty(), Optional.empty(), List.of(),
                SFMReleaseReviewV1.ProposalConfidence.EXACT, "0".repeat(64), "snapshot", List.of());
    }

    private static String hash(byte[] bytes) { return SFMReleaseReviewKernel.sha256(bytes); }
}
