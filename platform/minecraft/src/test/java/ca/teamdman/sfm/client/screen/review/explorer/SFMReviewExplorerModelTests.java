package ca.teamdman.sfm.client.screen.review.explorer;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewKernel;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1Codec;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
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
    void historicalEvidenceProjectsOneSnapshotInsteadOfAnInventedDiffPair() throws Exception {
        var base = releaseReviewFixture();
        String id = "historical-comment-document";
        String text = "retained bytes\n";
        String hash = SFMReviewSessionV1Kernel.sha256(text.getBytes(StandardCharsets.UTF_8));
        String laneId = "review-evidence:" + SFMReviewSessionV1Kernel.sha256(id.getBytes(StandardCharsets.UTF_8));
        var lanes = new java.util.ArrayList<>(base.reviewSession().revisionLanes());
        lanes.add(new SFMReviewSessionV1.RevisionLane(laneId,
                new SFMReviewSessionV1.Repository(SFMReleaseReviewV1.EVIDENCE_OWNER, "."),
                "Historical comment evidence",
                new SFMReviewSessionV1.Snapshot("evidence:empty", List.of()),
                new SFMReviewSessionV1.Snapshot("evidence:sha256:" + hash, List.of(
                        new SFMReviewSessionV1.DocumentRevision(id, "history.rs", "utf-8", hash, text)))));
        var session = base.reviewSession();
        var extendedSession = new SFMReviewSessionV2(session.schema(), session.id(), session.title(),
                session.coordinateSystem(), lanes, session.comments(), session.styleRules(), session.completionPolicy());
        var corpus = new java.util.ArrayList<>(base.corpusDocuments());
        corpus.add(new SFMReleaseReviewV1.CorpusDocument("historical-corpus", laneId,
                SFMReleaseReviewV1.SnapshotSide.AFTER, "history.rs", id, hash,
                SFMReleaseReviewV1.EVIDENCE_OWNER, "review-evidence://sha256/" + hash,
                SFMReleaseReviewV1.Materialization.COMPLETE));
        var review = new SFMReleaseReviewV1(SFMReleaseReviewV1.OBSERVATION_SCHEMA, extendedSession,
                base.repositoryBindings(), corpus, base.reviewUnits(), base.selectorBindings(),
                base.migrationReports(), base.namedQueries(), base.resumeState(), base.producerGenerations(),
                base.completionAttestations());
        var model = SFMReviewExplorerModel.releaseChanges(review);
        var file = model.root().children().stream().filter(node -> node.label().equals("history.rs"))
                .findFirst().orElseThrow();
        var lane = file.children().get(0);
        assertEquals("Historical comment evidence · not current approval", lane.label());
        assertEquals(1, lane.children().size());
        var source = lane.children().get(0);
        assertEquals(SFMReviewExplorerModel.Kind.REVISION, source.kind());
        assertEquals(Optional.of(id), source.leaf().documentRevisionId());
        assertEquals(text, source.leaf().text());
        assertEquals(base.reviewUnits(), review.reviewUnits(), "history must not add current approval surface");
    }

    @Test
    void historicalSnapshotKeepsExactSourceAndCommentsWithoutExposingRawLaneInLabel() throws Exception {
        var model = SFMReviewExplorerModel.releaseChanges(releaseReviewFixture());
        var original = model.root().children().stream().flatMap(file -> file.children().stream())
                .flatMap(lane -> lane.children().stream())
                .filter(node -> node.kind() == SFMReviewExplorerModel.Kind.REVISION
                        && !node.leaf().missing() && !node.children().isEmpty())
                .findFirst().orElseThrow();
        var source = original.leaf();
        var historical = SFMReviewExplorerModel.historicalSourceNode(source,
                java.util.Map.of(source.documentRevisionId().orElseThrow(), original.children()));
        assertSame(source, historical.leaf());
        assertEquals(source.id(), historical.id());
        assertEquals("Commented snapshot · " + source.path(), historical.label());
        assertEquals(original.children(), historical.children());
        assertFalse(historical.expanded());
    }

    @Test
    void releaseDocumentsExposeReadableCommentValuesOnlyForTheirExactRevision() throws Exception {
        var review = releaseReviewFixture();
        var model = SFMReviewExplorerModel.releaseChanges(review);
        int observed = 0;
        for (var file : model.root().children()) for (var lane : file.children()) {
            for (var source : lane.children()) {
                if (source.kind() != SFMReviewExplorerModel.Kind.REVISION || source.leaf().missing()) continue;
                String revision = source.leaf().documentRevisionId().orElseThrow();
                var expected = review.reviewSession().comments().stream().filter(comment -> {
                    var evaluation = ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel
                            .evaluateComment(review.reviewSession(), comment);
                    return evaluation.ranges().stream().anyMatch(range -> range.documentRevisionId().equals(revision));
                }).map(SFMReviewSessionV2.Comment::text).sorted().toList();
                assertEquals(expected, source.children().stream().map(node -> node.leaf().text()).sorted().toList());
                assertEquals(!expected.isEmpty(), source.expandable());
                assertFalse(source.expanded(), "comments must not automatically flood the source listing");
                observed += expected.size();
            }
        }
        assertTrue(observed > 0);
    }

    @Test
    void oversizedDiffSourceBecomesAnExplicitLeafWithoutRemovingItsSibling() {
        int limit = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceV1.DEFAULT_MAX_SOURCE_BYTES_PER_SIDE;
        var huge = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceV1.Source.fromCorpus(
                "large-revision", "large.txt", "text", "x".repeat(limit + 1));
        var small = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceV1.Source.fromCorpus(
                "small-revision", "small.java", "java", "class Small {}\n");
        for (var source : List.of(huge, small)) {
            var pair = new ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceV1.FilePair(
                    "pair-" + source.documentRevisionId(), "1.19.2", SFMReleaseReviewV1.ChangeOperation.ADDED,
                    List.of("unit"), Optional.empty(), Optional.of(source));
            for (var kind : ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceV1.SurfaceKind.values()) {
                var leaf = SFMReviewExplorerModel.boundedDiffLeaf("diff-" + source.documentRevisionId(), "Diff", source.path(), pair, kind);
                assertEquals(source.path(), leaf.path());
                assertFalse(leaf.missing());
                assertEquals(source == small, leaf.generatedSurface().isPresent());
                if (source == huge) {
                    assertTrue(leaf.text().contains("review.surface.source-oversized"));
                    assertTrue(leaf.text().contains("large-revision"));
                    assertTrue(leaf.text().contains(Integer.toString(limit)));
                }
            }
        }
    }

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
    void commentProjectionIsAnInspectableObjectWithAnOpenableValue() {
        var model = SFMReviewExplorerModel.comments();
        SFMReviewExplorerModel.Node comment = model.root().children().get(0);

        assertEquals(SFMReviewExplorerModel.Kind.COMMENT, comment.kind());
        assertFalse(comment.label().startsWith("diff-rename"), "durable ids are provenance, not row headlines");
        assertEquals(List.of("value", "selector", "matches (2)", "provenance"),
                comment.children().stream().map(node -> node.label().split(" · ")[0]).toList());
        SFMReviewExplorerModel.Node value = childWithPrefix(comment, "value · ");
        assertTrue(value.leaf() != null && !value.leaf().missing());
        assertEquals("#modified #renamed oldName → newName; audit() was added.", value.leaf().text());
        assertTrue(value.leaf().documentRevisionId().isEmpty(),
                "comment values open as immutable literal/read-only documents");
    }

    @Test
    void committedCommentKeepsSelectorIntentSeparateFromManyDerivedMatchesAndProvenance() {
        String beforeText = "class Example { int oldValue; }\n";
        String afterText = "class Example { int firstValue; void gap() {} int secondValue; }\n";
        SFMReviewSessionV1.DocumentRevision before = document("before-example", "src/Example.java", beforeText);
        SFMReviewSessionV1.DocumentRevision after = document("after-example", "src/Example.java", afterText);
        SFMReviewSessionV1.SelectionRule selector = new SFMReviewSessionV1.Union(List.of(
                literal(after, "firstValue"),
                literal(after, "secondValue")
        ));
        SFMReviewSessionV2.Comment persisted = new SFMReviewSessionV2.Comment(
                "opaque-comment-id",
                "#approved Both replacement fields are intentional.",
                new SFMReviewSessionV1.Provenance(
                        "human", "in-game-reviewer", "3", List.of("parent-comment")),
                new SFMReviewSessionV2.CommittedReviewTarget(selector)
        );

        SFMReviewExplorerModel.Node comment = SFMReviewExplorerModel.comments(
                session(List.of(before), List.of(after), List.of(persisted))).root().children().get(0);

        assertTrue(comment.label().startsWith("#approved Both replacement fields are intentional."));
        assertTrue(comment.label().contains("after · src/Example.java"));
        assertFalse(comment.label().contains("opaque-comment-id"),
                "the stable id belongs in provenance rather than consuming the primary label");

        SFMReviewExplorerModel.Node value = childWithPrefix(comment, "value · ");
        assertEquals(persisted.text(), value.leaf().text());

        SFMReviewExplorerModel.Node selectorNode = childWithPrefix(comment, "selector · committed");
        assertEquals(List.of("kind", "expression", "rule", "literal witnesses (2)", "semantic evidence"),
                selectorNode.children().stream().map(node -> node.label().split(" · ")[0]).toList());
        assertTrue(childWithPrefix(selectorNode, "expression · ").label().contains("union("));
        assertEquals(2, childWithPrefix(selectorNode, "rule · union").children().size());
        SFMReviewExplorerModel.Node literalWitnesses = childWithPrefix(selectorNode, "literal witnesses (2)");
        assertEquals(2, literalWitnesses.children().size());
        assertTrue(literalWitnesses.children().stream()
                .allMatch(node -> node.label().contains("after · src/Example.java")));
        assertEquals("semantic evidence · none", childWithPrefix(selectorNode, "semantic evidence").label());

        SFMReviewExplorerModel.Node matches = childWithPrefix(comment, "matches (2) · resolved exactly");
        assertEquals(2, matches.children().size());
        assertTrue(matches.children().get(0).label().contains("“firstValue”"));
        assertTrue(matches.children().get(1).label().contains("“secondValue”"));
        assertTrue(matches.children().stream().allMatch(node -> node.leaf() != null));
        assertTrue(matches.children().stream().allMatch(node -> node.leaf().targetRange().isPresent()));
        assertTrue(matches.children().stream().allMatch(node -> node.leaf().documentRevisionId().isPresent()),
                "production match leaves retain the pinned document identity and SHA-256");

        SFMReviewExplorerModel.Node provenance = childWithPrefix(comment, "provenance · human · in-game-reviewer");
        assertEquals("id · opaque-comment-id", childWithPrefix(provenance, "id · ").label());
        assertEquals("producer · in-game-reviewer", childWithPrefix(provenance, "producer · ").label());
        assertEquals("kind/schema · human@3", childWithPrefix(provenance, "kind/schema · ").label());
        SFMReviewExplorerModel.Node parents = childWithPrefix(provenance, "parents (1)");
        assertEquals(List.of("parent · parent-comment"),
                parents.children().stream().map(SFMReviewExplorerModel.Node::label).toList());
    }

    @Test
    void staleEvaluatorWitnessIsInspectableButIsNotCountedAsAMatch() {
        String text = "class Example { int current; }\n";
        SFMReviewSessionV1.DocumentRevision document = document("after-example", "src/Example.java", text);
        int start = text.indexOf("current");
        SFMReviewSessionV1.LiteralUtf8Range staleIntent = new SFMReviewSessionV1.LiteralUtf8Range(
                document.id(),
                start,
                start + "current".length(),
                "0".repeat(64),
                SFMReviewSessionV1Kernel.sha256("missing".getBytes(StandardCharsets.UTF_8))
        );
        SFMReviewSessionV2.Comment persisted = new SFMReviewSessionV2.Comment(
                "stale-comment",
                "#needs-change Revisit this stale selection.",
                new SFMReviewSessionV1.Provenance("human", "reviewer", "1", List.of()),
                new SFMReviewSessionV2.CommittedReviewTarget(staleIntent)
        );

        SFMReviewExplorerModel.Node comment = SFMReviewExplorerModel.comments(
                session(List.of(), List.of(document), List.of(persisted))).root().children().get(0);
        SFMReviewExplorerModel.Node selector = childWithPrefix(comment, "selector · committed");
        assertTrue(childWithPrefix(selector, "literal witnesses (1)").children().get(0).label()
                .contains("after · src/Example.java"), "the durable intent remains visible even when stale");

        SFMReviewExplorerModel.Node matches = childWithPrefix(comment, "matches (0) · content changed");
        assertEquals(1, matches.children().size());
        assertTrue(matches.children().get(0).label().startsWith("stale witness (not a match)"));
        assertTrue(matches.children().get(0).label().contains("“current”"));
        assertTrue(matches.children().get(0).leaf() != null,
                "the stale location remains openable for inspection without becoming a successful match");
    }

    @Test
    void missingSelectorScopeReportsZeroMatchesWithoutInventingASourceLeaf() {
        SFMReviewSessionV1.LiteralUtf8Range absent = new SFMReviewSessionV1.LiteralUtf8Range(
                "missing-document", 0, 1, "0".repeat(64), "1".repeat(64));
        SFMReviewSessionV2.Comment persisted = new SFMReviewSessionV2.Comment(
                "missing-comment",
                "#needs-change The selected document is no longer in this review.",
                new SFMReviewSessionV1.Provenance("human", "reviewer", "1", List.of()),
                new SFMReviewSessionV2.CommittedReviewTarget(absent)
        );

        SFMReviewExplorerModel.Node comment = SFMReviewExplorerModel.comments(
                session(List.of(), List.of(), List.of(persisted))).root().children().get(0);

        assertTrue(childWithPrefix(comment, "selector · committed").label().contains("missing-document[0,1)"));
        SFMReviewExplorerModel.Node matches = childWithPrefix(comment, "matches (0) · scope missing");
        assertEquals(List.of("no derived matches · scope missing"),
                matches.children().stream().map(SFMReviewExplorerModel.Node::label).toList());
        assertTrue(matches.children().stream().allMatch(node -> node.leaf() == null));
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
                        "release/status/stale-producer",
                        "release/approval-evidence"
                ), model.root().children().stream().map(SFMReviewExplorerModel.Node::id).toList(),
                "every completion witness category must remain present even when its count is zero");
        assertStatusCategory(model, "changed",
                List.of("unit:src/Cafe.java:value", "unit:src/Other.java:file"));
        assertStatusCategory(model, "approved-raw", List.of("unit:src/Cafe.java:value"));
        assertStatusCategory(model, "approved-effective", List.of());
        assertStatusCategory(model, "remaining", List.of("unit:src/Cafe.java:value", "unit:src/Other.java:file"));
        assertStatusCategory(model, "blocking", List.of());
        assertStatusCategory(model, "suspended", List.of());
        assertStatusCategory(model, "missing", List.of());
        assertStatusCategory(model, "deferred", List.of());
        assertStatusCategory(model, "unsupported", List.of("unit:src/Other.java:file"));
        assertStatusCategory(model, "stale-producer", List.of());
        var evidence = model.root().children().stream().filter(value -> value.id().equals("release/approval-evidence"))
                .findFirst().orElseThrow();
        assertFalse(evidence.children().isEmpty());
        assertTrue(evidence.label().contains("not a count of approved files"));
        var approval = evidence.children().get(0);
        assertTrue(approval.children().stream().anyMatch(value -> value.leaf() != null
                && value.leaf().text().contains("Refreshing never rewrites this comment")));
    }

    @Test
    void canonicalQueriesProjectExactStableUnitIdsIntoOrdinaryJumpLists() throws Exception {
        SFMReleaseReviewV1 review = releaseReviewFixture();

        assertQueryJumpList(review, "#approved intersect 1.19.2 HEAD",
                List.of("unit:src/Cafe.java:value"));
        assertQueryJumpList(review, "effective(#approved) intersect 1.19.2 HEAD",
                List.of());
        assertQueryJumpList(review, "remaining intersect 1.19.2 HEAD",
                List.of("unit:src/Cafe.java:value", "unit:src/Other.java:file"));
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
            assertEquals(List.of("before", "after", "text diff (inline)", "structured diff (inline)",
                            "text diff (split)", "structured diff (split)"),
                    lane.children().stream().map(child -> child.leaf().title().split(" · ")[0]).toList());
            assertEquals(List.of(
                            SFMReviewExplorerModel.Kind.REVISION,
                            SFMReviewExplorerModel.Kind.REVISION,
                            SFMReviewExplorerModel.Kind.DIFF,
                            SFMReviewExplorerModel.Kind.DIFF,
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

    @Test void releaseRevisionLabelsUsePinsRatherThanPersistedHeadAliases() throws Exception {
        var review = releaseReviewFixture();
        var binding = review.repositoryBindings().get(0);
        var model = SFMReviewExplorerModel.releaseChanges(review);
        assertFalse(model.root().label().contains("HEAD"));
        assertTrue(model.root().label().contains(binding.candidateCommit().substring(0, 12)));
        for (var file : model.root().children()) {
            for (var lane : file.children()) {
                var laneBinding = review.repositoryBindings().stream()
                        .filter(value -> lane.label().startsWith(value.laneId() + "  "))
                        .findFirst().orElseThrow();
                assertFalse(lane.label().contains("HEAD"));
                assertTrue(lane.label().contains(laneBinding.beforeCommit().substring(0, 12)));
                assertTrue(lane.label().contains(laneBinding.candidateCommit().substring(0, 12)));
            }
        }
    }

    @Test
    void releaseChangesCanProjectRepositorySegmentsWithoutChangingFileIdentity() throws Exception {
        SFMReleaseReviewV1 review = releaseReviewFixture();
        SFMReviewExplorerModel flat = SFMReviewExplorerModel.releaseChanges(
                review,
                SFMReviewExplorerModel.PathLayout.FLAT_PATHS
        );
        SFMReviewExplorerModel hierarchy = SFMReviewExplorerModel.releaseChanges(
                review,
                SFMReviewExplorerModel.PathLayout.HIERARCHY
        );

        assertEquals(List.of("src"), hierarchy.root().children().stream()
                .map(SFMReviewExplorerModel.Node::label).toList());
        SFMReviewExplorerModel.Node src = hierarchy.root().children().get(0);
        assertEquals(SFMReviewExplorerModel.Kind.DIRECTORY, src.kind());
        assertEquals(List.of("Cafe.java", "Missing.java", "Other.java", "Partial.java", "Unicode.java"),
                src.children().stream().map(SFMReviewExplorerModel.Node::label).toList());
        assertTrue(src.children().stream().allMatch(node -> node.kind() == SFMReviewExplorerModel.Kind.FILE));
        assertEquals(
                flat.root().children().stream().map(SFMReviewExplorerModel.Node::id).toList(),
                src.children().stream().map(SFMReviewExplorerModel.Node::id).toList(),
                "layout is presentation state and must not replace immutable review file identities"
        );
    }

    @Test
    void renamedNonJavaPairsStayUnifiedAndRequestAnExplicitStructuredFallbackSurface() throws Exception {
        SFMReviewExplorerModel model = SFMReviewExplorerModel.releaseChanges(renamedNonJavaFixture());
        SFMReviewExplorerModel.Node renamed = model.root().children().stream()
                .filter(file -> file.label().equals("src/Cafe.java → src/CafeRenamed.txt"))
                .findFirst().orElseThrow();
        assertEquals(1, renamed.children().size(), "rename must remain one lane/file pair rather than two path rows");
        List<SFMReviewExplorerModel.Node> leaves = renamed.children().get(0).children();
        assertEquals(6, leaves.size());
        assertTrue(leaves.get(4).leaf().generatedSurface().orElseThrow().split());
        assertTrue(leaves.get(5).leaf().generatedSurface().orElseThrow().split());
        assertFalse(leaves.get(2).leaf().generatedSurface().orElseThrow().split());
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
        SFMReviewExplorerModel.Node oldComment = model.root().children().get(1);
        assertTrue(oldComment.label().startsWith("#review Keep the old route decision"));
        assertTrue(oldComment.label().contains("candidate action"));
        assertTrue(oldComment.label().contains("candidate pinned unavailable"));
        assertFalse(oldComment.label().contains("old-comment"));
        assertEquals(List.of("value", "selector", "matches (0)", "provenance"),
                oldComment.children().stream().map(node -> node.label().split(" · ")[0]).toList());
        SFMReviewExplorerModel.Node selector = childWithPrefix(oldComment, "selector · candidate action");
        SFMReviewExplorerModel.Node targetNode = childWithKind(
                childWithPrefix(selector, "rule · immutable candidate trajectory target"),
                SFMReviewExplorerModel.Kind.CANDIDATE_TARGET);
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
        assertEquals("matches (0) · candidate pinned unavailable",
                childWithPrefix(oldComment, "matches (0)").label());
        assertSame(oldPlan, assertInstanceOf(
                SFMReviewExplorerModel.CandidateNavigation.class,
                oldComment.action().orElseThrow()).target());
        SFMReviewExplorerModel.CandidateNavigation newNavigation = assertInstanceOf(
                SFMReviewExplorerModel.CandidateNavigation.class,
                model.root().children().get(0).action().orElseThrow()
        );
        assertEquals("plan-new", newNavigation.target().trajectoryPlanRevisionId());
        assertEquals("plan-old", navigation.target().trajectoryPlanRevisionId(),
                "replanning must not retarget the retained old comment");
    }

    @Test
    void commentFeedPutsNewlyAppendedHumanWorkInTheFirstPage() {
        SFMReviewSessionV2 session = candidateSession(List.of(
                candidateComment("generated-1", "Generated first", candidateTarget(
                        "plan-1", "route-1", SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                        SFMReviewSessionV2.CandidateTargetKind.ACTION)),
                candidateComment("human:release-review:1", "First human comment", candidateTarget(
                        "plan-2", "route-2", SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                        SFMReviewSessionV2.CandidateTargetKind.ACTION)),
                candidateComment("human:release-review:2", "Newest human comment", candidateTarget(
                        "plan-3", "route-3", SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                        SFMReviewSessionV2.CandidateTargetKind.ACTION))
        ));

        List<String> labels = SFMReviewExplorerModel.comments(session).root().children().stream()
                .map(SFMReviewExplorerModel.Node::label)
                .toList();
        assertTrue(labels.get(0).contains("Newest human comment"));
        assertTrue(labels.get(1).contains("First human comment"));
        assertTrue(labels.get(2).contains("Generated first"));
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

        SFMReviewExplorerModel.Node comment = SFMReviewExplorerModel.comments(candidateSession(List.of(
                candidateComment("glyph-comment", "Inspect the projected glyphs", glyph)
        ))).root().children().get(0);
        SFMReviewExplorerModel.Node selector = childWithPrefix(comment, "selector · candidate document_region");
        SFMReviewExplorerModel.Node targetNode = childWithKind(
                childWithPrefix(selector, "rule · immutable candidate trajectory target"),
                SFMReviewExplorerModel.Kind.CANDIDATE_TARGET);

        assertTrue(targetNode.label().contains("document=document-a[4,9)"));
        assertTrue(childWithPrefix(selector, "literal witness · projected document").label()
                .contains("document-a [4..9)"));
        SFMReviewExplorerModel.CandidateNavigation navigation = assertInstanceOf(
                SFMReviewExplorerModel.CandidateNavigation.class,
                targetNode.action().orElseThrow()
        );
        assertEquals(selection, navigation.target().projectedDocumentSelection().orElseThrow());
    }

    private static SFMReviewExplorerModel.Node childWithPrefix(
            SFMReviewExplorerModel.Node parent,
            String prefix
    ) {
        return parent.children().stream()
                .filter(node -> node.label().startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No child of " + parent.label() + " starts with " + prefix));
    }

    private static SFMReviewExplorerModel.Node childWithKind(
            SFMReviewExplorerModel.Node parent,
            SFMReviewExplorerModel.Kind kind
    ) {
        return parent.children().stream()
                .filter(node -> node.kind() == kind)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No child of " + parent.label() + " has kind " + kind));
    }

    private static SFMReviewSessionV1.DocumentRevision document(String id, String path, String text) {
        return new SFMReviewSessionV1.DocumentRevision(
                id,
                path,
                "utf-8",
                SFMReviewSessionV1Kernel.sha256(text.getBytes(StandardCharsets.UTF_8)),
                text
        );
    }

    private static SFMReviewSessionV1.LiteralUtf8Range literal(
            SFMReviewSessionV1.DocumentRevision document,
            String selectedText
    ) {
        int utf16Start = document.text().indexOf(selectedText);
        if (utf16Start < 0) throw new AssertionError("Missing selected text " + selectedText);
        int startByte = document.text().substring(0, utf16Start).getBytes(StandardCharsets.UTF_8).length;
        byte[] selectedBytes = selectedText.getBytes(StandardCharsets.UTF_8);
        return new SFMReviewSessionV1.LiteralUtf8Range(
                document.id(),
                startByte,
                startByte + selectedBytes.length,
                document.sha256(),
                SFMReviewSessionV1Kernel.sha256(selectedBytes)
        );
    }

    private static SFMReviewSessionV2 session(
            List<SFMReviewSessionV1.DocumentRevision> before,
            List<SFMReviewSessionV1.DocumentRevision> after,
            List<SFMReviewSessionV2.Comment> comments
    ) {
        SFMReviewSessionV2 empty = SFMReviewSessionV2.empty("sfm:test/comment-object", "Comment object review");
        SFMReviewSessionV1.RevisionLane lane = new SFMReviewSessionV1.RevisionLane(
                "1.19.2",
                new SFMReviewSessionV1.Repository("sfm", "."),
                "1.19.2",
                new SFMReviewSessionV1.Snapshot("before", before),
                new SFMReviewSessionV1.Snapshot("after", after)
        );
        return new SFMReviewSessionV2(
                empty.schema(),
                empty.id(),
                empty.title(),
                empty.coordinateSystem(),
                List.of(lane),
                comments,
                empty.styleRules(),
                empty.completionPolicy()
        );
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
            List<SFMReviewExplorerModel.Node> sourceSides = unit.children().stream()
                    .filter(child -> child.kind() == SFMReviewExplorerModel.Kind.REVISION).toList();
            assertEquals(2, sourceSides.size(),
                    "every review-unit witness must retain before/after beside any exact remaining group");
            assertEquals(List.of(SFMReviewExplorerModel.Kind.REVISION, SFMReviewExplorerModel.Kind.REVISION),
                    sourceSides.stream().map(SFMReviewExplorerModel.Node::kind).toList());
            assertEquals(List.of("before", "after"), sourceSides.stream()
                    .map(child -> child.leaf().title().split(" · ")[0])
                    .toList());
            assertTrue(sourceSides.stream().allMatch(child -> child.leaf() != null));
            assertTrue(sourceSides.stream().allMatch(child -> child.id().endsWith("/" + expectedUnitId)),
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
