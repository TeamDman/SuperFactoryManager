package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentKernelDataSource;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SFMReleaseReviewGeneratedMarkersTests {
    @Test
    void removingNeutralGeneratedMarkerPreservesChangedAndRemainingCoverage() throws Exception {
        var original = fixture();
        var session = original.reviewSession();
        var comments = new java.util.ArrayList<>(session.comments());
        comments.add(comment("generated:coverage-parity", "#release-change", "generated",
                "sfm.release-review.working-tree-materializer/1", comments.get(0).target()));
        var markedSession = new SFMReviewSessionV2(session.schema(), session.id(), session.title(),
                session.coordinateSystem(), session.revisionLanes(), comments,
                session.styleRules(), session.completionPolicy());
        var marked = new SFMReleaseReviewV1(original.schema(), markedSession,
                original.repositoryBindings(), original.corpusDocuments(), original.reviewUnits(),
                original.selectorBindings(), original.migrationReports(), original.namedQueries(),
                original.resumeState(), original.producerGenerations(), original.completionAttestations());
        var before = SFMReleaseReviewKernel.completion(marked);
        var after = SFMReleaseReviewKernel.completion(original);
        assertTrue(after.changedDomain() > 0, "Coverage comparison must not use an empty domain");
        assertEquals(before.changedDomain(), after.changedDomain());
        assertEquals(before.remaining(), after.remaining());
        assertEquals(before.approvedEffective(), after.approvedEffective());
        assertEquals(SFMReleaseReviewKernel.query(marked, "#approved intersect 1.19.2 HEAD"),
                SFMReleaseReviewKernel.query(original, "#approved intersect 1.19.2 HEAD"));
        assertEquals(marked.reviewUnits(), original.reviewUnits());
        assertEquals(marked.corpusDocuments(), original.corpusDocuments());
    }

    @Test
    void producerProvenanceNotHashtagsDeterminesLegacyMarkerVisibility() throws Exception {
        var original = fixture().reviewSession();
        var target = original.comments().get(0).target();
        var producer = "sfm.release-review.working-tree-materializer/1";
        var marker = comment("marker", "Changed source", "generated", producer, target);
        var human = comment("human", "#release-change I wrote this", "human", producer, target);
        var other = comment("other", "#release-change a separate annotation", "generated", "other-tool", target);
        assertTrue(SFMReleaseReviewGeneratedMarkers.isChangeMarker(marker));
        assertFalse(SFMReleaseReviewGeneratedMarkers.isChangeMarker(human));
        assertFalse(SFMReleaseReviewGeneratedMarkers.isChangeMarker(other));
        var session = new SFMReviewSessionV2(original.schema(), original.id(), original.title(),
                original.coordinateSystem(), original.revisionLanes(), List.of(marker, human, other),
                original.styleRules(), original.completionPolicy());
        var adapter = new SFMReviewCommentKernelDataSource(session);
        assertEquals(List.of("human", "other"), adapter.refresh().comments().stream().map(c -> c.id()).toList());
        assertEquals(2, adapter.refresh().migrations().size());
        assertTrue(SFMReviewExplorerModel.commentObject(session, "marker").isEmpty());
        assertTrue(SFMReviewExplorerModel.commentObject(session, "human").isPresent());
        assertSame(session, adapter.session(), "presentation must not rewrite historical evidence");
        assertEquals(3, adapter.session().comments().size());
    }

    private static SFMReviewSessionV2.Comment comment(String id, String text, String kind, String producer,
                                                      SFMReviewSessionV2.CommentTarget target) {
        return new SFMReviewSessionV2.Comment(id, text,
                new SFMReviewSessionV1.Provenance(kind, producer, "1", List.of()), target);
    }

    private static SFMReleaseReviewV1 fixture() throws Exception {
        for (Path cursor = Path.of("").toAbsolutePath(); cursor != null; cursor = cursor.getParent()) {
            Path file = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(file)) return SFMReleaseReviewV1Codec.parse(Files.readString(file));
        }
        throw new IllegalStateException("Missing release review fixture");
    }
}
