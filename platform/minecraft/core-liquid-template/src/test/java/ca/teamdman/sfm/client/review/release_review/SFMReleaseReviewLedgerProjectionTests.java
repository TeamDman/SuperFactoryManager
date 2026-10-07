package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SFMReleaseReviewLedgerProjectionTests {
    private static final String TEXT = "class Example { }\r\n";
    private static final String HASH = SFMReleaseReviewKernel.sha256(TEXT.getBytes(StandardCharsets.UTF_8));
    private static final SFMReviewSessionV1.LiteralUtf8Range RULE =
            new SFMReviewSessionV1.LiteralUtf8Range("doc", 0, TEXT.length(), HASH, HASH);

    @Test void browsingGeneratedMarkersDoesNotPersistSourceOrComments() {
        var ledger = ledger();
        var observation = observation(ledger, List.of(comment("derived", "generated")), List.of());
        var result = SFMReleaseReviewLedgerProjection.project(ledger, observation, observation, Map.of());
        assertEquals(ledger, result);
        assertFalse(SFMReleaseReviewLedgerV3Codec.write(result).contains(TEXT));
    }

    @Test void commentCapturesOnceAndUnchangedSaveNeedsNoSourceRead() {
        var ledger = ledger();
        var derived = comment("derived", "generated");
        var observation = observation(ledger, List.of(derived), List.of());
        var human = comment("human", "human");
        var edited = observation(ledger, List.of(derived, human), List.of(binding("human")));
        var saved = SFMReleaseReviewLedgerProjection.project(ledger, observation, edited, sources());
        assertEquals(List.of(human), saved.state().reviewSession().comments());
        assertEquals(TEXT, saved.evidence().contents().get(0).text());
        assertTrue(saved.state().reviewSession().revisionLanes().isEmpty());
        assertEquals(saved, SFMReleaseReviewLedgerProjection.project(saved, edited, edited, Map.of()));
        var second = observation(saved, List.of(derived, human, comment("second", "human")),
                List.of(binding("human"), binding("second")));
        var savedTwice = SFMReleaseReviewLedgerProjection.project(saved, edited, second, sources());
        assertEquals(2, savedTwice.state().reviewSession().comments().size());
        assertEquals(1, savedTwice.evidence().contents().size());
        assertEquals(1, savedTwice.evidence().documents().size());
        assertSame(saved.evidence(), savedTwice.evidence(), "another comment reuses exact evidence, not comment state");
        assertEquals(1, saved.state().reviewSession().comments().size());
        assertTrue(ledger.evidence().contents().isEmpty());
    }

    @Test void failedCaptureAndDerivedMarkerEditsCannotProduceNewAuthority() {
        var ledger = ledger();
        var original = observation(ledger, List.of(comment("derived", "generated")), List.of());
        var edited = observation(ledger, List.of(comment("human", "human")), List.of(binding("human")));
        assertThrows(IllegalArgumentException.class,
                () -> SFMReleaseReviewLedgerProjection.project(ledger, original, edited, Map.of()));
        var spoof = observation(ledger, List.of(comment("derived", "human")), List.of(binding("derived")));
        assertThrows(IllegalArgumentException.class,
                () -> SFMReleaseReviewLedgerProjection.project(ledger, original, spoof, sources()));
        String changedText = "changed on disk";
        String changedHash = SFMReleaseReviewKernel.sha256(changedText.getBytes(StandardCharsets.UTF_8));
        var changed = new SFMReviewEvidenceTable.Observed(new SFMReviewEvidenceTable.Document(
                "doc", "Example.java", changedHash, Optional.empty()), changedText);
        assertThrows(IllegalArgumentException.class,
                () -> SFMReleaseReviewLedgerProjection.project(ledger, original, edited, Map.of("doc", changed)));
        assertTrue(ledger.state().reviewSession().comments().isEmpty());
        assertTrue(ledger.evidence().contents().isEmpty());
    }

    private static SFMReleaseReviewLedgerV3 ledger() {
        return SFMReleaseReviewLedgerV3.create("test", "Test", List.of(
                new SFMReleaseReviewLedgerV3.TargetLane("main", "sfm", ".", "a".repeat(40),
                        Optional.empty(), List.of("."), List.of("review.json"), true)));
    }

    private static Map<String, SFMReviewEvidenceTable.Observed> sources() {
        return Map.of("doc", new SFMReviewEvidenceTable.Observed(
                new SFMReviewEvidenceTable.Document("doc", "Example.java", HASH, Optional.empty()), TEXT));
    }

    private static SFMReviewSessionV2.Comment comment(String id, String kind) {
        return new SFMReviewSessionV2.Comment(id, "#approved",
                new SFMReviewSessionV1.Provenance(kind, "test", "1", List.of()),
                new SFMReviewSessionV2.CommittedReviewTarget(RULE));
    }

    private static SFMReleaseReviewV1.CommentSelectorBinding binding(String id) {
        var selection = new SFMReleaseReviewV1.PinnedSelection("selected", "explicit", 0, List.of(
                new SFMReleaseReviewV1.PinnedSelectionRange(SFMReleaseReviewV1.SelectionDirection.FORWARD,
                        "doc", HASH, 0, TEXT.length())));
        var proposal = new SFMReleaseReviewV1.SelectorProposal("proposal", SFMReleaseReviewV1.SelectorKind.LITERAL,
                RULE, selection, Optional.empty(), Optional.empty(), List.of(),
                SFMReleaseReviewV1.ProposalConfidence.EXACT, "0".repeat(64), "snapshot", List.of());
        return new SFMReleaseReviewV1.CommentSelectorBinding(id, selection, proposal);
    }

    private static SFMReleaseReviewV1 observation(SFMReleaseReviewLedgerV3 ledger,
                                                List<SFMReviewSessionV2.Comment> comments,
                                                List<SFMReleaseReviewV1.CommentSelectorBinding> bindings) {
        var base = ledger.state();
        var session = base.reviewSession();
        var lane = new SFMReviewSessionV1.RevisionLane("main", new SFMReviewSessionV1.Repository("sfm", "."),
                "main", new SFMReviewSessionV1.Snapshot("before", List.of()),
                new SFMReviewSessionV1.Snapshot("after", List.of(new SFMReviewSessionV1.DocumentRevision(
                        "doc", "Example.java", "utf8", HASH, TEXT))));
        var resolved = new SFMReviewSessionV2(session.schema(), session.id(), session.title(),
                session.coordinateSystem(), List.of(lane), comments, session.styleRules(), session.completionPolicy());
        return new SFMReleaseReviewV1(base.schema(), resolved, List.of(), List.of(), List.of(), bindings,
                List.of(), List.of(), base.resumeState(), List.of(), List.of());
    }
}
