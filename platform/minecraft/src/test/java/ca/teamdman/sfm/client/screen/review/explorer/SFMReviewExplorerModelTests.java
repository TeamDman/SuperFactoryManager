package ca.teamdman.sfm.client.screen.review.explorer;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewKernel;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1Codec;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReviewExplorerModelTests {
    @Test
    void changesKeepBothRevisionLeavesForEveryFileAndLane() {
        var model = SFMReviewExplorerModel.changes("mod 4.34.0", "HEAD");

        var example = model.root().children().stream()
                .filter(node -> node.label().equals("src/Example.java"))
                .findFirst().orElseThrow();

        assertEquals(2, example.children().size());
        assertTrue(example.children().stream().allMatch(lane -> lane.children().size() == 2));
        assertEquals(List.of("before", "after"), example.children().get(0).children().stream()
                .map(node -> node.leaf().title().split(" · ")[0]).toList());
    }

    @Test
    void missingRevisionIsAnExplicitTombstoneLeaf() {
        var model = SFMReviewExplorerModel.changes("before", "after");
        var added = model.root().children().stream()
                .filter(node -> node.label().equals("src/Added.java"))
                .findFirst().orElseThrow();

        assertTrue(added.children().stream().allMatch(lane -> lane.children().get(0).leaf().missing()));
        assertFalse(added.children().stream().allMatch(lane -> lane.children().get(1).leaf().missing()));
    }

    @Test
    void commentProjectionIsCommentFileRegion() {
        var model = SFMReviewExplorerModel.comments();
        assertEquals(SFMReviewExplorerModel.Kind.COMMENT, model.root().children().get(0).kind());
        assertTrue(model.root().children().get(0).children().stream()
                .allMatch(node -> node.kind() == SFMReviewExplorerModel.Kind.REGION));
        assertTrue(model.root().children().get(0).children().stream()
                .allMatch(node -> node.leaf() != null && !node.leaf().missing()));
    }

    @Test
    void hashtagProjectionIsHashtagFileRegion() {
        var model = SFMReviewExplorerModel.hashtags();
        assertTrue(model.root().children().stream()
                .allMatch(node -> node.kind() == SFMReviewExplorerModel.Kind.HASHTAG));
        assertTrue(model.root().children().stream().flatMap(tag -> tag.children().stream())
                .allMatch(node -> node.kind() == SFMReviewExplorerModel.Kind.FILE));
    }

    @Test
    void navigationCollapsesBeforeSelectingParent() {
        var model = SFMReviewExplorerModel.changes("before", "after");
        model.select(0);
        model.expandSelection();
        model.selectNext();
        model.collapseSelectionOrSelectParent();
        model.collapseSelectionOrSelectParent();
        assertEquals(SFMReviewExplorerModel.Kind.ROOT, model.selected().kind());
    }

    @Test
    void immutableProjectionRefreshPreservesSelectionAndExpansion() {
        SFMReviewExplorerModel initial = SFMReviewExplorerModel.changes("before", "after");
        initial.selectNext();
        initial.expandSelection();
        initial.selectNext();
        String selectedId = initial.selected().id();
        AtomicReference<Object> revision = new AtomicReference<>(new Object());
        AtomicReference<SFMReviewExplorerModel> projection = new AtomicReference<>(initial);
        SFMReviewExplorerPanel panel = new SFMReviewExplorerPanel(
                "Live review", initial, revision::get, projection::get);

        SFMReviewExplorerModel replacement = SFMReviewExplorerModel.changes("before", "candidate");
        projection.set(replacement);
        revision.set(new Object());
        panel.tick();

        assertSame(replacement, panel.model());
        assertEquals(selectedId, panel.model().selected().id());
        assertTrue(panel.model().root().children().get(0).expanded());
        assertTrue(panel.model().root().expanded());
    }

    @Test
    void releaseStatusMakesEveryCompletionCountANavigableWitnessList() throws Exception {
        SFMReleaseReviewV1 review = releaseReviewFixture();
        SFMReleaseReviewKernel.CompletionReport report = SFMReleaseReviewKernel.completion(review);

        SFMReviewExplorerModel model = SFMReviewExplorerModel.releaseStatus(review);

        assertTrue(model.root().label().contains(report.status().name().toLowerCase(java.util.Locale.ROOT)));
        assertEquals(List.of(
                        "release/status/changed",
                        "release/status/approved-raw",
                        "release/status/approved-effective",
                        "release/status/remaining",
                        "release/status/blocking",
                        "release/status/suspended",
                        "release/status/missing",
                        "release/status/deferred",
                        "release/status/unsupported",
                        "release/status/stale-producer"
                ), model.root().children().stream().map(SFMReviewExplorerModel.Node::id).toList(),
                "every completion witness category must remain present even when its count is zero");
        assertStatusCategory(model, "changed",
                List.of("unit:src/Cafe.java:value", "unit:src/Other.java:file"));
        assertStatusCategory(model, "approved-raw", List.of("unit:src/Cafe.java:value"));
        assertStatusCategory(model, "approved-effective", List.of("unit:src/Cafe.java:value"));
        assertStatusCategory(model, "remaining", List.of("unit:src/Other.java:file"));
        assertStatusCategory(model, "blocking", List.of());
        assertStatusCategory(model, "suspended", List.of());
        assertStatusCategory(model, "missing", List.of());
        assertStatusCategory(model, "deferred", List.of());
        assertStatusCategory(model, "unsupported", List.of("unit:src/Other.java:file"));
        assertStatusCategory(model, "stale-producer", List.of());
    }

    @Test
    void canonicalQueriesProjectExactStableUnitIdsIntoOrdinaryJumpLists() throws Exception {
        SFMReleaseReviewV1 review = releaseReviewFixture();

        assertQueryJumpList(review, "#approved intersect 1.19.2 HEAD",
                List.of("unit:src/Cafe.java:value"));
        assertQueryJumpList(review, "effective(#approved) intersect 1.19.2 HEAD",
                List.of("unit:src/Cafe.java:value"));
        assertQueryJumpList(review, "remaining intersect 1.19.2 HEAD",
                List.of("unit:src/Other.java:file"));
        assertQueryJumpList(review, "blocking intersect 1.19.2 HEAD", List.of());
        assertQueryJumpList(review, "suspended intersect 1.19.2 HEAD", List.of());
    }

    @Test
    void releaseChangesProjectExactlyFourLazyLeavesPerFileLaneIncludingAddedTombstones() throws Exception {
        SFMReviewExplorerModel model = SFMReviewExplorerModel.releaseChanges(releaseReviewFixture());
        List<SFMReviewExplorerModel.Node> lanes = model.root().children().stream()
                .flatMap(file -> file.children().stream())
                .toList();
        assertFalse(lanes.isEmpty());
        assertEquals(List.of(
                        "src/Cafe.java", "src/Missing.java", "src/Other.java", "src/Partial.java", "src/Unicode.java"),
                model.root().children().stream().map(SFMReviewExplorerModel.Node::label).toList(),
                "the Changes projection must retain corpus-only pinned files outside the review-unit domain");
        for (SFMReviewExplorerModel.Node lane : lanes) {
            assertEquals(List.of("before", "after", "text diff", "structured diff"),
                    lane.children().stream().map(child -> child.leaf().title().split(" · ")[0]).toList());
            assertEquals(List.of(
                            SFMReviewExplorerModel.Kind.REVISION,
                            SFMReviewExplorerModel.Kind.REVISION,
                            SFMReviewExplorerModel.Kind.DIFF,
                            SFMReviewExplorerModel.Kind.DIFF),
                    lane.children().stream().map(SFMReviewExplorerModel.Node::kind).toList());
            assertEquals(
                    lane.children().get(2).leaf().generatedSurface().isPresent(),
                    lane.children().get(3).leaf().generatedSurface().isPresent(),
                    "text and structured diff availability must agree for one immutable file pair");
        }
        assertEquals(2, lanes.stream()
                .filter(lane -> lane.children().get(2).leaf().generatedSurface().isPresent())
                .count(), "only exact review-unit-backed pairs should launch generated diff work");
        SFMReviewExplorerModel.Node added = model.root().children().stream()
                .filter(file -> file.label().equals("src/Other.java"))
                .findFirst().orElseThrow();
        assertTrue(added.children().get(0).children().get(0).leaf().missing());
        assertFalse(added.children().get(0).children().get(1).leaf().missing());
        assertEquals(SFMReleaseReviewV1.ChangeOperation.ADDED,
                added.children().get(0).children().get(2).leaf().generatedSurface().orElseThrow()
                        .filePair().operation());
    }

    @Test
    void renamedNonJavaPairsStayUnifiedAndRequestAnExplicitStructuredFallbackSurface() throws Exception {
        SFMReviewExplorerModel model = SFMReviewExplorerModel.releaseChanges(renamedNonJavaFixture());
        SFMReviewExplorerModel.Node renamed = model.root().children().stream()
                .filter(file -> file.label().equals("src/Cafe.java → src/CafeRenamed.txt"))
                .findFirst().orElseThrow();
        assertEquals(1, renamed.children().size(), "rename must remain one lane/file pair rather than two path rows");
        List<SFMReviewExplorerModel.Node> leaves = renamed.children().get(0).children();
        assertEquals(4, leaves.size());
        var text = leaves.get(2).leaf().generatedSurface().orElseThrow();
        var structured = leaves.get(3).leaf().generatedSurface().orElseThrow();
        assertEquals(SFMReleaseReviewV1.ChangeOperation.RENAMED, text.filePair().operation());
        assertEquals("src/Cafe.java", text.filePair().before().orElseThrow().path());
        assertEquals("src/CafeRenamed.txt", text.filePair().after().orElseThrow().path());
        assertEquals("text", structured.filePair().after().orElseThrow().language());
        assertEquals(ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceV1.SurfaceKind.JAVA_STRUCTURED_DIFF,
                structured.surfaceKind(),
                "the Rust producer owns the explicit unsupported-language text fallback and diagnostics");
    }

    @Test
    void productionCandidateCommentsRenderWithoutCommittedRangesAndCarryExactNavigation() {
        SFMReviewSessionV2.CandidateTrajectoryTarget oldPlan = candidateTarget(
                "plan-old", "route-a", SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER,
                SFMReviewSessionV2.CandidateTargetKind.ACTION);
        SFMReviewSessionV2.CandidateTrajectoryTarget replanned = candidateTarget(
                "plan-new", "route-b", SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                SFMReviewSessionV2.CandidateTargetKind.STATE);
        SFMReviewSessionV2 session = candidateSession(List.of(
                candidateComment("old-comment", "#review Keep the old route decision", oldPlan),
                candidateComment("new-comment", "#review Inspect the replacement state", replanned)
        ));

        SFMReviewExplorerModel model = SFMReviewExplorerModel.comments(session);

        assertEquals(2, model.root().children().size());
        SFMReviewExplorerModel.Node oldComment = model.root().children().get(0);
        assertTrue(oldComment.label().contains("[candidate · external_barrier]"));
        assertEquals(1, oldComment.children().size(),
                "a range-free candidate comment still needs an explorer target row");
        SFMReviewExplorerModel.Node targetNode = oldComment.children().get(0);
        assertEquals(SFMReviewExplorerModel.Kind.CANDIDATE_TARGET, targetNode.kind());
        assertTrue(targetNode.label().contains("plan=plan-old"));
        assertTrue(targetNode.label().contains("route=route-a"));
        assertTrue(targetNode.label().contains("frame=1"));
        assertTrue(targetNode.label().contains("step=step-1"));
        assertTrue(targetNode.label().contains("action=action-1"));
        assertTrue(targetNode.label().contains("state=state-1"));
        assertTrue(targetNode.label().contains("status=external_barrier"));
        SFMReviewExplorerModel.CandidateNavigation navigation = assertInstanceOf(
                SFMReviewExplorerModel.CandidateNavigation.class,
                targetNode.action().orElseThrow()
        );
        assertEquals("old-comment", navigation.commentId());
        assertSame(oldPlan, navigation.target(),
                "navigation must retain the immutable old-plan target rather than resolve the latest plan");
        assertSame(oldPlan, assertInstanceOf(
                SFMReviewExplorerModel.CandidateNavigation.class,
                oldComment.action().orElseThrow()).target());
        SFMReviewExplorerModel.CandidateNavigation newNavigation = assertInstanceOf(
                SFMReviewExplorerModel.CandidateNavigation.class,
                model.root().children().get(1).action().orElseThrow()
        );
        assertEquals("plan-new", newNavigation.target().trajectoryPlanRevisionId());
        assertEquals("plan-old", navigation.target().trajectoryPlanRevisionId(),
                "replanning must not retarget the retained old comment");
    }

    @Test
    void hashtagProjectionKeepsRangeFreeCandidateTargets() {
        SFMReviewSessionV2.CandidateTrajectoryTarget target = candidateTarget(
                "plan-tagged", "route-tagged", SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER,
                SFMReviewSessionV2.CandidateTargetKind.ACTION);

        SFMReviewExplorerModel model = SFMReviewExplorerModel.hashtags(candidateSession(List.of(
                candidateComment("candidate-tagged", "#review Explain the route barrier", target)
        )));

        SFMReviewExplorerModel.Node hashtag = model.root().children().stream()
                .filter(node -> node.label().equals("#review"))
                .findFirst().orElseThrow();
        SFMReviewExplorerModel.Node candidate = hashtag.children().get(0);
        assertEquals(SFMReviewExplorerModel.Kind.CANDIDATE_TARGET, candidate.kind());
        assertSame(target, assertInstanceOf(
                SFMReviewExplorerModel.CandidateNavigation.class,
                candidate.action().orElseThrow()).target());
    }

    @Test
    void candidateGlyphLabelAndPayloadRetainTheExactProjectedUtf8Witness() {
        SFMReviewSessionV2.ProjectedDocumentSelection selection = new SFMReviewSessionV2.ProjectedDocumentSelection(
                "document-a",
                "document-state-7",
                "0".repeat(64),
                4,
                9,
                "1".repeat(64)
        );
        SFMReviewSessionV2.CandidateTrajectoryTarget glyph = new SFMReviewSessionV2.CandidateTrajectoryTarget(
                "sfm:test/machine",
                4,
                "plan-glyph",
                "route-glyph",
                1,
                Optional.of("step-glyph"),
                "state-glyph",
                Optional.of("state-hash-glyph"),
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION,
                Optional.of("action-glyph"),
                Optional.of(selection),
                Optional.of("evaluator-1"),
                List.of()
        );

        SFMReviewExplorerModel.Node targetNode = SFMReviewExplorerModel.comments(candidateSession(List.of(
                candidateComment("glyph-comment", "Inspect the projected glyphs", glyph)
        ))).root().children().get(0).children().get(0);

        assertTrue(targetNode.label().contains("document=document-a[4,9)"));
        SFMReviewExplorerModel.CandidateNavigation navigation = assertInstanceOf(
                SFMReviewExplorerModel.CandidateNavigation.class,
                targetNode.action().orElseThrow()
        );
        assertEquals(selection, navigation.target().projectedDocumentSelection().orElseThrow());
    }

    private static SFMReviewSessionV2 candidateSession(List<SFMReviewSessionV2.Comment> comments) {
        SFMReviewSessionV2 empty = SFMReviewSessionV2.empty("sfm:test/explorer-candidates", "Candidate review");
        return new SFMReviewSessionV2(
                empty.schema(), empty.id(), empty.title(), empty.coordinateSystem(),
                empty.revisionLanes(), comments, empty.styleRules(), empty.completionPolicy()
        );
    }

    private static SFMReviewSessionV2.Comment candidateComment(
            String id,
            String text,
            SFMReviewSessionV2.CandidateTrajectoryTarget target
    ) {
        return new SFMReviewSessionV2.Comment(
                id,
                text,
                new SFMReviewSessionV1.Provenance("human", "explorer-test", "1", List.of()),
                target
        );
    }

    private static SFMReviewSessionV2.CandidateTrajectoryTarget candidateTarget(
            String plan,
            String route,
            SFMHistoryGraphContract.ProjectionStatus status,
            SFMReviewSessionV2.CandidateTargetKind kind
    ) {
        return new SFMReviewSessionV2.CandidateTrajectoryTarget(
                "sfm:test/machine",
                4,
                plan,
                route,
                1,
                Optional.of("step-1"),
                "state-1",
                Optional.of("state-hash-1"),
                status,
                kind,
                kind == SFMReviewSessionV2.CandidateTargetKind.ACTION
                        ? Optional.of("action-1")
                        : Optional.empty(),
                Optional.empty(),
                Optional.of("evaluator-1"),
                List.of()
        );
    }

    private static void assertStatusCategory(
            SFMReviewExplorerModel model,
            String id,
            List<String> expectedUnitIds
    ) {
        SFMReviewExplorerModel.Node category = model.root().children().stream()
                .filter(node -> node.id().equals("release/status/" + id))
                .findFirst()
                .orElseThrow();
        assertEquals(SFMReviewExplorerModel.Kind.STATUS_CATEGORY, category.kind());
        assertTrue(category.label().endsWith(" · " + expectedUnitIds.size()));
        assertEquals(expectedUnitIds,
                category.children().stream()
                        .map(node -> node.id().substring("release/unit/".length()))
                        .toList());
        assertNavigableReviewUnits(category.children(), expectedUnitIds);
    }

    private static void assertQueryJumpList(
            SFMReleaseReviewV1 review,
            String expression,
            List<String> expectedUnitIds
    ) {
        SFMReviewExplorerModel model = SFMReviewExplorerModel.releaseQuery(review, expression);
        assertEquals(expectedUnitIds.stream().map(id -> "release/unit/" + id).toList(),
                model.root().children().stream().map(SFMReviewExplorerModel.Node::id).toList());
        assertNavigableReviewUnits(model.root().children(), expectedUnitIds);
    }

    private static void assertNavigableReviewUnits(
            List<SFMReviewExplorerModel.Node> rows,
            List<String> expectedUnitIds
    ) {
        assertEquals(expectedUnitIds.size(), rows.size());
        for (int index = 0; index < rows.size(); index++) {
            SFMReviewExplorerModel.Node unit = rows.get(index);
            String expectedUnitId = expectedUnitIds.get(index);
            assertEquals("release/unit/" + expectedUnitId, unit.id());
            assertEquals(SFMReviewExplorerModel.Kind.REVIEW_UNIT, unit.kind());
            assertEquals(2, unit.children().size(),
                    "every review-unit witness must expose explicit before and after destinations");
            assertEquals(List.of(SFMReviewExplorerModel.Kind.REVISION, SFMReviewExplorerModel.Kind.REVISION),
                    unit.children().stream().map(SFMReviewExplorerModel.Node::kind).toList());
            assertEquals(List.of("before", "after"), unit.children().stream()
                    .map(child -> child.leaf().title().split(" · ")[0])
                    .toList());
            assertTrue(unit.children().stream().allMatch(child -> child.leaf() != null));
            assertTrue(unit.children().stream().allMatch(child -> child.id().endsWith("/" + expectedUnitId)),
                    "status and query leaves must retain the stable review-unit id in their jump identity");
        }
    }

    private static SFMReleaseReviewV1 releaseReviewFixture() throws Exception {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(candidate)) {
                return SFMReleaseReviewV1Codec.parse(Files.readString(candidate).replace("\r\n", "\n"));
            }
        }
        throw new IllegalStateException("Unable to locate canonical release-review fixture");
    }

    private static SFMReleaseReviewV1 renamedNonJavaFixture() throws Exception {
        SFMReleaseReviewV1 source = releaseReviewFixture();
        String revisionId = "1.19.2:after:src/Cafe.java";
        String renamedPath = "src/CafeRenamed.txt";
        List<SFMReviewSessionV1.RevisionLane> lanes = source.reviewSession().revisionLanes().stream()
                .map(lane -> new SFMReviewSessionV1.RevisionLane(
                        lane.id(), lane.repository(), lane.versionLabel(), lane.before(),
                        new SFMReviewSessionV1.Snapshot(
                                lane.after().id(),
                                lane.after().documents().stream().map(document -> document.id().equals(revisionId)
                                        ? new SFMReviewSessionV1.DocumentRevision(
                                                document.id(), renamedPath, document.encoding(), document.sha256(),
                                                document.text())
                                        : document).toList())))
                .toList();
        SFMReviewSessionV2 session = new SFMReviewSessionV2(
                source.reviewSession().schema(), source.reviewSession().id(), source.reviewSession().title(),
                source.reviewSession().coordinateSystem(), lanes, source.reviewSession().comments(),
                source.reviewSession().styleRules(), source.reviewSession().completionPolicy());
        List<SFMReleaseReviewV1.CorpusDocument> corpus = source.corpusDocuments().stream()
                .map(document -> document.documentRevisionId().equals(revisionId)
                        ? new SFMReleaseReviewV1.CorpusDocument(
                                document.id(), document.laneId(), document.snapshotSide(), renamedPath,
                                document.documentRevisionId(), document.sha256(), document.sourceOwner(),
                                document.sourceLocator(), document.materialization())
                        : document)
                .toList();
        List<SFMReleaseReviewV1.ReviewUnit> units = source.reviewUnits().stream()
                .map(unit -> unit.afterDocumentRevisionId().filter(revisionId::equals).isPresent()
                        ? new SFMReleaseReviewV1.ReviewUnit(
                                unit.id(), unit.laneId(), SFMReleaseReviewV1.ChangeOperation.RENAMED,
                                unit.pathBefore(), Optional.of(renamedPath), unit.beforeDocumentRevisionId(),
                                unit.afterDocumentRevisionId(), unit.beforeRanges(), unit.afterRanges(), "text",
                                unit.surfaceKind(), unit.semanticKey(), unit.limitation(), unit.producerId(),
                                unit.producerGeneration())
                        : unit)
                .toList();
        return new SFMReleaseReviewV1(
                source.schema(), session, source.repositoryBindings(), corpus, units, source.selectorBindings(),
                source.migrationReports(), source.namedQueries(), source.resumeState(), source.producerGenerations(),
                source.completionAttestations());
    }
}
