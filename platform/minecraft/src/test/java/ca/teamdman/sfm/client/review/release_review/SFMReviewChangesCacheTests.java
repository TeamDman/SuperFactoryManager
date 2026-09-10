package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewChangesCacheTests {
    @Test void optionalReleaseScaleProjectionMeasurements() throws Exception {
        var configured = System.getenv("SFM_TEST_REVIEW_PROJECTION_FIXTURE");
        org.junit.jupiter.api.Assumptions.assumeTrue(configured != null && !configured.isBlank(),
                "Set SFM_TEST_REVIEW_PROJECTION_FIXTURE for read-only release-scale measurements");
        var file = Path.of(configured);
        var bytes = Files.readAllBytes(file);
        var review = SFMReleaseReviewV1Codec.parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        var cache = new SFMReviewExplorerModel.ChangesCache();
        var layout = SFMReviewExplorerModel.PathLayout.HIERARCHY;
        cache.project(review, layout);
        var expected = describe(SFMReviewExplorerModel.releaseChanges(review, layout).root());
        // Alternate order to reduce one-sided JIT/GC bias. These are observations,
        // never a percentage-based performance gate on a shared workstation.
        for (int i = 0; i < 4; i++) {
            long cachedNanos;
            long uncachedNanos;
            if (i % 2 == 0) {
                long start = System.nanoTime();
                var model = cache.project(review, layout);
                cachedNanos = System.nanoTime() - start;
                assertEquals(expected, describe(model.root()));
                start = System.nanoTime();
                model = SFMReviewExplorerModel.releaseChanges(review, layout);
                uncachedNanos = System.nanoTime() - start;
                assertEquals(expected, describe(model.root()));
            } else {
                long start = System.nanoTime();
                var model = SFMReviewExplorerModel.releaseChanges(review, layout);
                uncachedNanos = System.nanoTime() - start;
                assertEquals(expected, describe(model.root()));
                start = System.nanoTime();
                model = cache.project(review, layout);
                cachedNanos = System.nanoTime() - start;
                assertEquals(expected, describe(model.root()));
            }
            ca.teamdman.sfm.SFM.LOGGER.info("SFM_REVIEW_PROJECTION_MEASUREMENT iteration={} bytes={} documents={} comments={} nodes={} cached_us={} uncached_us={} source_builds={} presentation_evaluations={}",
                    i, bytes.length, review.corpusDocuments().size(), review.reviewSession().comments().size(),
                    expected.size(), cachedNanos / 1000, uncachedNanos / 1000,
                    cache.sourceBuilds(), cache.commentEvaluationBuilds());
        }
        assertEquals(1, cache.sourceBuilds());
        assertEquals(review.reviewSession().comments().size(), cache.commentEvaluationBuilds());
        assertArrayEquals(bytes, Files.readAllBytes(file), "Measurement must not modify the review");
    }

    @Test void commentsRefreshWithoutRebuildingSourceOrSharingPanelState() throws Exception {
        var review = fixture();
        assertFalse(review.reviewSession().comments().isEmpty());
        var empty = with(review, List.of(), review.reviewUnits());
        var cache = new SFMReviewExplorerModel.ChangesCache();
        var first = cache.project(empty, SFMReviewExplorerModel.PathLayout.HIERARCHY);
        var second = cache.project(review, SFMReviewExplorerModel.PathLayout.HIERARCHY);
        assertEquals(1, cache.sourceBuilds());
        var expected = SFMReviewExplorerModel.releaseChanges(review, SFMReviewExplorerModel.PathLayout.HIERARCHY);
        assertEquals(describe(expected.root()), describe(second.root()));
        assertTrue(nodes(first.root()).stream().noneMatch(n -> n.kind() == SFMReviewExplorerModel.Kind.COMMENT));
        assertTrue(nodes(second.root()).stream().anyMatch(n -> n.kind() == SFMReviewExplorerModel.Kind.COMMENT));
        var firstLeaf = nodes(first.root()).stream().filter(n -> n.leaf() != null).findFirst().orElseThrow();
        var secondLeaf = nodes(second.root()).stream().filter(n -> n.id().equals(firstLeaf.id())).findFirst().orElseThrow();
        assertSame(firstLeaf.leaf(), secondLeaf.leaf());
        assertNotSame(firstLeaf, secondLeaf);
        first.root().setExpanded(false);
        assertTrue(second.root().expanded());
        assertTrue(cache.project(review, SFMReviewExplorerModel.PathLayout.HIERARCHY).root().expanded());
        assertEquals(review.reviewSession().comments().size(), cache.commentEvaluationBuilds());
        var removed = cache.project(empty, SFMReviewExplorerModel.PathLayout.HIERARCHY);
        assertTrue(nodes(removed.root()).stream().noneMatch(n -> n.kind() == SFMReviewExplorerModel.Kind.COMMENT));
        assertEquals(1, cache.sourceBuilds());
    }

    @Test void oneNewCommentEvaluatesOnlyThatCommentForChangesPresentation() throws Exception {
        var review = fixture();
        var cache = new SFMReviewExplorerModel.ChangesCache();
        cache.project(review, SFMReviewExplorerModel.PathLayout.HIERARCHY);
        var comments = new ArrayList<>(review.reviewSession().comments());
        var prior = comments.get(0);
        comments.add(new SFMReviewSessionV2.Comment("new-cache-comment", "new cached comment",
                prior.provenance(), prior.target()));
        var updated = with(review, comments, review.reviewUnits());
        var actual = cache.project(updated, SFMReviewExplorerModel.PathLayout.HIERARCHY);
        assertEquals(comments.size(), cache.commentEvaluationBuilds());
        assertEquals(1, cache.sourceBuilds());
        assertEquals(describe(SFMReviewExplorerModel.releaseChanges(updated,
                SFMReviewExplorerModel.PathLayout.HIERARCHY).root()), describe(actual.root()));
    }

    @Test void changedSourceAndLayoutInvalidateRecipes() throws Exception {
        var review = fixture();
        assertFalse(review.reviewUnits().isEmpty());
        var cache = new SFMReviewExplorerModel.ChangesCache();
        cache.project(review, SFMReviewExplorerModel.PathLayout.HIERARCHY);
        var changed = with(review, review.reviewSession().comments(), List.of());
        var updated = cache.project(changed, SFMReviewExplorerModel.PathLayout.HIERARCHY);
        assertEquals(2, cache.sourceBuilds());
        assertEquals(describe(SFMReviewExplorerModel.releaseChanges(changed,
                SFMReviewExplorerModel.PathLayout.HIERARCHY).root()), describe(updated.root()));
        cache.project(changed, SFMReviewExplorerModel.PathLayout.FLAT_PATHS);
        assertEquals(3, cache.sourceBuilds());
    }

    @Test void warmSourceCannotBypassNewStateValidation() throws Exception {
        var review = fixture();
        var cache = new SFMReviewExplorerModel.ChangesCache();
        cache.project(review, SFMReviewExplorerModel.PathLayout.HIERARCHY);
        var empty = with(review, List.of(), review.reviewUnits());
        var invalid = new SFMReleaseReviewV1(empty.schema(), empty.reviewSession(), empty.repositoryBindings(),
                empty.corpusDocuments(), empty.reviewUnits(), review.selectorBindings(), List.of(),
                empty.namedQueries(), empty.resumeState(), empty.producerGenerations(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> cache.project(invalid, SFMReviewExplorerModel.PathLayout.HIERARCHY));
        assertEquals(1, cache.sourceBuilds());
    }

    @Test void changedBytesInvalidateSourceRecipesWithoutMutatingPreviousProjection() throws Exception {
        var original = fixture();
        var review = with(original, List.of(), original.reviewUnits());
        var cache = new SFMReviewExplorerModel.ChangesCache();
        var before = cache.project(review, SFMReviewExplorerModel.PathLayout.HIERARCHY);
        var beforeDescription = describe(before.root());
        var tree = SFMReleaseReviewV1Codec.writeTree(review);
        var document = tree.getAsJsonObject("review_session").getAsJsonArray("revision_lanes")
                .get(0).getAsJsonObject().getAsJsonObject("after").getAsJsonArray("documents")
                .get(0).getAsJsonObject();
        var id = document.get("id").getAsString();
        var text = document.get("text").getAsString().replace("return 2;", "return 314;");
        var hash = ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel.sha256(
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        document.addProperty("text", text);
        document.addProperty("sha256", hash);
        for (var entry : tree.getAsJsonArray("corpus_documents")) {
            var corpus = entry.getAsJsonObject();
            if (id.equals(corpus.get("document_revision_id").getAsString())) corpus.addProperty("sha256", hash);
        }
        var changed = SFMReleaseReviewV1Codec.parse(tree.toString());
        var after = cache.project(changed, SFMReviewExplorerModel.PathLayout.HIERARCHY);
        assertEquals(2, cache.sourceBuilds());
        assertNotEquals(beforeDescription, describe(after.root()));
        assertEquals(beforeDescription, describe(before.root()));
        assertEquals(describe(SFMReviewExplorerModel.releaseChanges(changed,
                SFMReviewExplorerModel.PathLayout.HIERARCHY).root()), describe(after.root()));
    }

    private static SFMReleaseReviewV1 with(SFMReleaseReviewV1 r, List<SFMReviewSessionV2.Comment> comments,
                                          List<SFMReleaseReviewV1.ReviewUnit> units) {
        var s = r.reviewSession();
        var session = new SFMReviewSessionV2(s.schema(), s.id(), s.title(), s.coordinateSystem(),
                s.revisionLanes(), comments, s.styleRules(), s.completionPolicy());
        return new SFMReleaseReviewV1(r.schema(), session, r.repositoryBindings(), r.corpusDocuments(), units,
                r.selectorBindings().stream().filter(b -> comments.stream().anyMatch(c -> c.id().equals(b.commentId()))).toList(),
                List.of(), r.namedQueries(), SFMReleaseReviewV1.ResumeState.empty(),
                r.producerGenerations(), List.of());
    }

    private static List<SFMReviewExplorerModel.Node> nodes(SFMReviewExplorerModel.Node root) {
        var nodes = new ArrayList<SFMReviewExplorerModel.Node>();
        nodes.add(root);
        root.children().forEach(child -> nodes.addAll(nodes(child)));
        return nodes;
    }
    private static List<String> describe(SFMReviewExplorerModel.Node root) {
        return nodes(root).stream().map(n -> n.id() + "|" + n.label() + "|" + n.kind() + "|" + n.leaf()).toList();
    }
    private static SFMReleaseReviewV1 fixture() throws Exception {
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            var file = p.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(file)) return SFMReleaseReviewV1Codec.parse(Files.readString(file));
        }
        throw new IllegalStateException("Missing release-review fixture");
    }
}
