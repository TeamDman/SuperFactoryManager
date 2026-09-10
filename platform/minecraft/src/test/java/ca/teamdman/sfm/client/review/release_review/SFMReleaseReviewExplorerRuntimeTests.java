package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerPathReveal;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPresentationRegistry;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewExplorerRuntimeTests {
    @TempDir Path temporaryDirectory;

    @Test
    void newCommentReusesSourceRevealIndexButReopenDoesNot() throws Exception {
        Path file = temporaryDirectory.resolve("incremental-review.json");
        Files.copy(fixture(), file);
        try (var runtime = new SFMReleaseReviewRuntime()) {
            runtime.open(file, true);
            var resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            var root = resolver.prepareLens(file, SFMReleaseReviewExplorerScreenType.Projection.CHANGES, Optional.empty());
            var oldSourceRows = descendants(resolver, root).stream()
                    .map(row -> row.path())
                    .filter(path -> resolver.documentTarget(path).isPresent()).toList();
            assertFalse(oldSourceRows.isEmpty());
            assertEquals(1, resolver.changesIndexBuilds());
            runtime.mutate(r -> {
                var binding = r.selectorBindings().get(0);
                var prior = r.reviewSession().comments().stream()
                        .filter(c -> c.id().equals(binding.commentId())).findFirst().orElseThrow();
                var comments = new java.util.ArrayList<>(r.reviewSession().comments());
                comments.add(new ca.teamdman.sfm.client.review.session.SFMReviewSessionV2.Comment(
                        "human:incremental-test", "incremental-new-comment", prior.provenance(), prior.target()));
                var bindings = new java.util.ArrayList<>(r.selectorBindings());
                var p = binding.selectedProposal();
                var proposal = new SFMReleaseReviewV1.SelectorProposal("proposal:incremental-test", p.kind(),
                        p.selectionRule(), p.literalWitness(), p.semanticProvider(), p.semanticKey(),
                        p.semanticProvenance(), p.confidence(), p.projectionFingerprint(), p.sourceSnapshotId(), p.diagnostics());
                bindings.add(new SFMReleaseReviewV1.CommentSelectorBinding("human:incremental-test",
                        binding.capturedSelection(), proposal));
                var s = r.reviewSession();
                var session = new ca.teamdman.sfm.client.review.session.SFMReviewSessionV2(s.schema(), s.id(), s.title(),
                        s.coordinateSystem(), s.revisionLanes(), comments, s.styleRules(), s.completionPolicy());
                return new SFMReleaseReviewV1(r.schema(), session, r.repositoryBindings(), r.corpusDocuments(),
                        r.reviewUnits(), bindings, r.migrationReports(), r.namedQueries(), r.resumeState(),
                        r.producerGenerations(), r.completionAttestations());
            });
            for (SFMPath path : oldSourceRows) {
                assertTrue(resolver.documentTarget(path).isPresent(),
                        () -> "Source/diff row must open after comment save without re-expanding: " + path);
                SFMPath oppositeExpandability = new SFMPath(path.kind(), path.scheme(), path.authority(),
                        path.segments(), path.revision(), !path.trailingSlash());
                assertTrue(resolver.documentTarget(oppositeExpandability).isPresent(),
                        "Source identity must survive becoming expandable after its first comment");
            }
            var commentRow = descendants(resolver, root).stream()
                    .filter(row -> row.label().contains("incremental-new-comment")).findFirst().orElseThrow();
            var actions = resolver.contextChoices(commentRow.path());
            assertTrue(actions.stream().anyMatch(choice -> choice.command().equals(
                    "sfm action invoke sfm:review/comment/details/open \"human:incremental-test\" text "
                            + runtime.snapshot().openEpoch())), () -> "Actual choices: " + actions);
            assertTrue(actions.stream().anyMatch(choice -> choice.displayText().equals("Show comment targets in Explorer")));
            assertEquals(1, resolver.changesIndexBuilds(), "comment-only update must reuse immutable source index");
            runtime.open(file, true);
            var reopened = resolver.prepareLens(file, SFMReleaseReviewExplorerScreenType.Projection.CHANGES, Optional.empty());
            descendants(resolver, reopened);
            assertNotEquals(root, reopened);
            assertEquals(2, resolver.changesIndexBuilds(), "different open epoch must not reuse old row addresses");
        }
    }

    @Test
    void savedWorkCursorResolvesItsExactAfterRangeWithoutWritingOrUsingExplorerHighlight() throws Exception {
        Path file = temporaryDirectory.resolve("work-cursor.sfm-review.json");
        Files.copy(fixture(), file);
        try (var runtime = new SFMReleaseReviewRuntime()) {
            runtime.open(file, true);
            runtime.activateQuery(Optional.empty(), "HEAD");
            String bytes = Files.readString(file);
            var before = runtime.snapshot();
            var resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            var root = resolver.prepareLens(file, SFMReleaseReviewExplorerScreenType.Projection.QUERY, Optional.empty());
            var current = resolver.currentWorkTargetAsync(root).get();
            assertEquals(before.document().orElseThrow().resumeState().currentUnitId().orElseThrow(), current.unitId());
            var units = children(resolver, root);
            assertTrue(units.stream().anyMatch(entry -> entry.path().equals(current.unitPath())
                    && entry.label().startsWith("[Saved cursor]")));
            children(resolver, current.unitPath());
            var target = resolver.documentTarget(current.documentPath().orElseThrow()).orElseThrow();
            var unit = before.document().orElseThrow().reviewUnits().stream()
                    .filter(value -> value.id().equals(current.unitId())).findFirst().orElseThrow();
            assertEquals(unit.afterDocumentRevisionId(), target.leaf().documentRevisionId());
            assertEquals(unit.afterRanges().stream().findFirst(), target.leaf().targetRange());
            assertEquals(before, runtime.snapshot());
            assertEquals(bytes, Files.readString(file));
            var other = resolver.prepareLens(file, SFMReleaseReviewExplorerScreenType.Projection.QUERY,
                    Optional.of("#approved"));
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> resolver.currentWorkTargetAsync(other).get());
            assertEquals(bytes, Files.readString(file));
        }
    }

    @Test
    void inFlightSavedCursorLookupRejectsSameFileReopenEpoch() throws Exception {
        Path file = temporaryDirectory.resolve("stale-work-cursor.sfm-review.json");
        Files.copy(fixture(), file);
        try (var runtime = new SFMReleaseReviewRuntime()) {
            runtime.open(file, true);
            runtime.activateQuery(Optional.empty(), "HEAD");
            var resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            var root = resolver.prepareLens(file, SFMReleaseReviewExplorerScreenType.Projection.QUERY, Optional.empty());
            ArrayDeque<Runnable> worker = new ArrayDeque<>();
            var executor = SFMReleaseReviewExplorerRuntime.class.getDeclaredField("commentNavigationExecutor");
            executor.setAccessible(true);
            executor.set(resolver, (java.util.concurrent.Executor) worker::add);
            var pending = resolver.currentWorkTargetAsync(root);
            assertFalse(pending.isDone());
            runtime.open(file, true);
            worker.remove().run();
            assertTrue(pending.isCompletedExceptionally());
        }
    }

    @Test
    void explicitSameFileReopenRebindsTheCapturedLensButRejectsOtherEpochsAndFiles() throws Exception {
        Path path = temporaryDirectory.resolve("reopen.sfm-review.json");
        Path other = temporaryDirectory.resolve("other.sfm-review.json");
        Files.copy(fixture(), path);
        Files.copy(fixture(), other);
        var runtime = new SFMReleaseReviewRuntime();
        runtime.open(path, false);
        try {
            var resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            var root = resolver.prepareLens(path, SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                    Optional.empty(), SFMReviewExplorerModel.PathLayout.FLAT_PATHS);
            var captured = resolver.lensDescriptor(Set.of(root)).orElseThrow();
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> resolver.prepareReopenedLens(captured, runtime.snapshot().openEpoch()));
            runtime.openAsync(path, true).get();
            long reopened = runtime.snapshot().openEpoch();
            var rebound = resolver.prepareReopenedLens(captured, reopened);
            assertNotEquals(root, rebound);
            var actual = resolver.lensDescriptor(Set.of(rebound)).orElseThrow();
            assertEquals(captured.changesPathLayout(), actual.changesPathLayout());
            assertEquals(captured.projection(), actual.projection());
            assertEquals(reopened, actual.reviewOpenEpoch());
            assertFalse(children(resolver, rebound).isEmpty());
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> resolver.prepareReopenedLens(captured, reopened - 1));
            runtime.openAsync(other, false).get();
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> resolver.prepareReopenedLens(captured, runtime.snapshot().openEpoch()));
        } finally {
            runtime.discardAndClose();
        }
    }

    @Test
    void lensDescriptorRequiresOneExactProjectionRoot() throws Exception {
        Path reviewPath = temporaryDirectory.resolve("lens-descriptor.sfm-review.json");
        Files.copy(fixture(), reviewPath);
        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        runtime.open(reviewPath, false);
        try {
            SFMReleaseReviewExplorerRuntime resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            SFMPath changes = resolver.prepareLens(
                    reviewPath,
                    SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                    Optional.empty()
            );
            SFMPath comments = resolver.prepareLens(
                    reviewPath,
                    SFMReleaseReviewExplorerScreenType.Projection.COMMENTS,
                    Optional.empty()
            );

            var descriptor = resolver.lensDescriptor(java.util.Set.of(changes)).orElseThrow();
            assertEquals(SFMReleaseReviewExplorerScreenType.Projection.CHANGES, descriptor.projection());
            assertEquals(reviewPath.toAbsolutePath().normalize(), descriptor.reviewPath());
            assertTrue(resolver.lensDescriptor(java.util.Set.of(changes, comments)).isEmpty(),
                    "a mixed projection must not be guessed from ambient roots");
        } finally {
            runtime.discardAndClose();
        }
    }

    @Test
    void switchingLensRetainsExplorerAddressIdentityAndPresentationSettings() throws Exception {
        Path reviewPath = temporaryDirectory.resolve("lens-switch.sfm-review.json");
        Files.copy(fixture(), reviewPath);
        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        runtime.open(reviewPath, false);
        try {
            SFMReleaseReviewExplorerRuntime resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            SFMPath changes = resolver.prepareLens(
                    reviewPath,
                    SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                    Optional.empty()
            );
            SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
            resolvers.register(resolver);
            SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                    resolvers,
                    new SFMChildRelationRepository(),
                    Runnable::run
            );
            SFMPath displayPath = SFMPath.fromNative(reviewPath);
            SFMExplorerSession session = new SFMExplorerSession(
                    new SFMExplorerId("review-lens-switch"),
                    new SFMPathExpression.Literal(displayPath),
                    java.util.Set.of(changes),
                    new SFMSelectionRepository()
            );
            session.setView(SFMExplorerProjection.View.SMALL_ICONS);
            session.setFilterQuery("approved");
            SFMExplorerPanel panel = new SFMExplorerPanel(
                    session,
                    loader,
                    ignored -> { },
                    () -> { },
                    () -> { },
                    SFMExplorerPresentationRegistry.minecraftDefaults()
            );
            loader.openRoot(changes).join();
            SFMExplorerId identity = session.snapshot().id();
            SFMPathExpression location = session.snapshot().location();
            SFMExplorerProjection.Settings settings = session.snapshot().settings();

            var next = resolver.switchLens(
                    panel,
                    SFMReleaseReviewExplorerScreenType.Projection.COMMENTS,
                    Optional.empty()
            ).toCompletableFuture().join();

            assertEquals(SFMReleaseReviewExplorerScreenType.Projection.COMMENTS, next.projection());
            assertEquals(identity, session.snapshot().id());
            assertEquals(location, session.snapshot().location());
            assertEquals(settings, session.snapshot().settings());
            assertEquals(java.util.Set.of(next.root()), session.snapshot().roots());
            assertTrue(session.snapshot().expanded().contains(next.root()));
        } finally {
            runtime.discardAndClose();
        }
    }

    @Test
    void switchingChangedPathLayoutRetainsExplorerAndReviewIdentity() throws Exception {
        Path reviewPath = temporaryDirectory.resolve("layout-switch.sfm-review.json");
        Files.copy(fixture(), reviewPath);
        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        runtime.open(reviewPath, false);
        try {
            SFMReleaseReviewExplorerRuntime resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            SFMPath hierarchyRoot = resolver.prepareLens(
                    reviewPath,
                    SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                    Optional.empty()
            );
            assertEquals(
                    SFMReviewExplorerModel.PathLayout.HIERARCHY,
                    resolver.lensDescriptor(Set.of(hierarchyRoot)).orElseThrow().changesPathLayout()
            );
            SFMExplorerEntry src = children(resolver, hierarchyRoot).get(0);
            assertEquals("src", src.label());
            assertEquals("minecraft:chest",
                    src.sortKey(SFMExplorerEntry.SORT_ICON).value().orElseThrow());
            assertEquals("minecraft:barrel",
                    src.sortKey(SFMExplorerEntry.PRESENTATION_ICON_FALLBACK).value().orElseThrow(),
                    "review hierarchy directories must remain visibly container-like on the title screen");
            assertEquals("directory",
                    src.sortKey(SFMExplorerEntry.PRESENTATION_ICON_LABEL).value().orElseThrow());
            assertTrue(resolver.contextChoices(src.path()).stream().anyMatch(choice -> choice.command().equals(
                    "sfm action invoke sfm:review/changes/layout/set flat-paths"
            )), "every Changes row must expose the alternate layout through its ordinary context surface");

            SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
            resolvers.register(resolver);
            SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                    resolvers,
                    new SFMChildRelationRepository(),
                    Runnable::run
            );
            SFMPath displayPath = SFMPath.fromNative(reviewPath);
            SFMExplorerSession session = new SFMExplorerSession(
                    new SFMExplorerId("review-layout-switch"),
                    new SFMPathExpression.Literal(displayPath),
                    Set.of(hierarchyRoot),
                    new SFMSelectionRepository()
            );
            SFMExplorerPanel panel = new SFMExplorerPanel(
                    session,
                    loader,
                    ignored -> { },
                    () -> { },
                    () -> { },
                    SFMExplorerPresentationRegistry.minecraftDefaults()
            );
            loader.openRoot(hierarchyRoot).join();
            SFMExplorerId explorerId = session.snapshot().id();
            SFMPathExpression location = session.snapshot().location();

            var flat = resolver.switchChangesPathLayout(
                    panel,
                    SFMReviewExplorerModel.PathLayout.FLAT_PATHS
            ).toCompletableFuture().join();

            assertEquals(explorerId, session.snapshot().id());
            assertEquals(location, session.snapshot().location());
            assertEquals(SFMReviewExplorerModel.PathLayout.FLAT_PATHS, flat.changesPathLayout());
            assertEquals(Set.of(flat.root()), session.snapshot().roots());
            assertTrue(children(resolver, flat.root()).get(0).label().contains("/"),
                    "flat mode must restore complete repository paths as rows");
        } finally {
            runtime.discardAndClose();
        }
    }

    @Test
    void reopeningTheSameDurableReviewGetsAFreshEphemeralExplorerIdentity() throws Exception {
        Path reviewPath = temporaryDirectory.resolve("reopened-review.sfm-review.json");
        Files.copy(fixture(), reviewPath);
        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        SFMReleaseReviewExplorerRuntime resolver = new SFMReleaseReviewExplorerRuntime(runtime);

        runtime.open(reviewPath, false);
        SFMPath firstRoot = resolver.prepareLens(
                reviewPath,
                SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                Optional.empty()
        );
        long firstOpenEpoch = runtime.snapshot().openEpoch();
        runtime.discardAndClose();

        runtime.open(reviewPath, false);
        try {
            SFMPath reopenedRoot = resolver.prepareLens(
                    reviewPath,
                    SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                    Optional.empty()
            );
            assertNotEquals(firstOpenEpoch, runtime.snapshot().openEpoch());
            assertNotEquals(firstRoot, reopenedRoot,
                    "a reopened review must not reuse generic Explorer child relations from its prior lease");
        } finally {
            runtime.discardAndClose();
        }
    }

    @Test
    void changesProjectionPagesGenericRowsAndPreservesExplicitLaneMultiplicity() throws Exception {
        Path reviewPath = temporaryDirectory.resolve("candidate.sfm-review.json");
        Files.copy(fixture(), reviewPath);
        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        runtime.open(reviewPath, false);
        try {
            SFMReleaseReviewExplorerRuntime resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            SFMPath root = resolver.prepareLens(
                    reviewPath,
                    SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                    Optional.empty(),
                    SFMReviewExplorerModel.PathLayout.FLAT_PATHS
            );
            assertTrue(resolver.describe(root, new SFMExplorerCancellationToken()).join().expandable());

            SFMExplorerResolver.ChildPage firstPage = resolver.resolveChildren(new SFMExplorerResolver.ChildRequest(
                    root,
                    Optional.empty(),
                    1,
                    resolver.generation(),
                    new SFMExplorerCancellationToken()
            )).join();
            assertEquals(1, firstPage.entries().size());
            assertTrue(firstPage.observedEntries() >= firstPage.entries().size());
            if (firstPage.observedEntries() > 1) assertTrue(firstPage.continuation().isPresent());
            assertEquals(
                    new SFMReleaseReviewExplorerRuntime.MaterializationEvidence(2, 2, 0),
                    resolver.materializationEvidence(root),
                    "a one-entry page must not prebuild every review row"
            );

            SFMPath file = firstPage.entries().get(0).path();
            assertTrue(firstPage.entries().get(0).expandable());
            assertEquals(
                    "minecraft:cocoa_beans",
                    firstPage.entries().get(0).sortKey(SFMExplorerEntry.SORT_ICON).value().orElseThrow(),
                    "release-review file rows must share the central Java file icon mapping"
            );
            SFMExplorerResolver.ChildPage descendants = resolver.resolveChildren(new SFMExplorerResolver.ChildRequest(
                    file,
                    Optional.empty(),
                    16,
                    resolver.generation(),
                    new SFMExplorerCancellationToken()
            )).join();
            if (descendants.entries().stream().allMatch(entry -> entry.label().contains("→"))) {
                assertTrue(runtime.document().orElseThrow().repositoryBindings().size() > 1,
                        "a redundant lane level is allowed only when the review genuinely has multiple lanes");
                SFMPath lane = descendants.entries().get(0).path();
                descendants = resolver.resolveChildren(new SFMExplorerResolver.ChildRequest(
                        lane,
                        Optional.empty(),
                        16,
                        resolver.generation(),
                        new SFMExplorerCancellationToken()
                )).join();
            }
            assertEquals(List.of("before", "after", "text diff (inline)", "structured diff (inline)",
                    "text diff (split)", "structured diff (split)"), descendants.entries().stream()
                    .map(entry -> entry.label().split(" · ")[0])
                    .toList());
            assertEquals(
                    List.of("00-before", "01-after", "02-text-diff", "03-structured-diff", "04-text-diff-split", "05-structured-diff-split"),
                    descendants.entries().stream()
                            .map(entry -> entry.sortKey(SFMExplorerEntry.SORT_NAME).value().orElseThrow())
                            .toList(),
                    "the generic name sort must retain review chronology instead of alphabetizing after before"
            );
            assertTrue(descendants.entries().stream()
                    .filter(entry -> entry.label().startsWith("before ·") || entry.label().startsWith("after ·"))
                    .allMatch(entry -> entry.sortKey(SFMExplorerEntry.SORT_ICON).value()
                            .filter("minecraft:cocoa_beans"::equals)
                            .isPresent()),
                    "release-review source revisions must share the central Java file icon mapping");
            assertTrue(descendants.entries().stream().filter(entry -> entry.expandable()).allMatch(entry ->
                    entry.label().startsWith("before ·") || entry.label().startsWith("after ·")),
                    "only source documents may expose comments beneath the four presentation rows");
            assertTrue(descendants.entries().stream().anyMatch(entry -> entry.expandable()),
                    "the fixture's commented source is expandable without losing its openable document");
            assertTrue(descendants.entries().stream()
                    .anyMatch(entry -> resolver.documentTarget(entry.path()).isPresent()),
                    "the four-leaf projection must retain at least one openable document beside any tombstone");

            SFMTextDocumentSnapshot javaRevision = descendants(resolver, root).stream()
                    .filter(entry -> entry.label().startsWith("before ·")
                            || entry.label().startsWith("after ·"))
                    .map(entry -> resolver.documentTarget(entry.path()))
                    .flatMap(Optional::stream)
                    .filter(target -> target.leaf().path().endsWith(".java"))
                    .map(target -> target.source().load(new SFMExplorerCancellationToken()).join())
                    .filter(SFMTextDocumentSnapshot::ready)
                    .findFirst()
                    .orElseThrow();
            assertEquals(SFMTextDocumentLanguage.java(), javaRevision.language());
            assertEquals(Optional.of("java"), javaRevision.language().remoteWorkerLanguage());
        } finally {
            runtime.discardAndClose();
        }
    }

    @Test
    void changesFilterDomainContainsEveryFileAndCompleteAncestorRelation() throws Exception {
        Path reviewPath = temporaryDirectory.resolve("complete-filter-domain.sfm-review.json");
        Files.copy(fixture(), reviewPath);
        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        runtime.open(reviewPath, false);
        try {
            SFMReleaseReviewExplorerRuntime resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            SFMPath root = resolver.prepareLens(
                    reviewPath,
                    SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                    Optional.empty()
            );
            List<SFMExplorerEntry> completeRows = descendants(resolver, root);
            long expectedMatches = completeRows.stream()
                    .filter(entry -> entry.searchTerms().stream().anyMatch(term ->
                            ca.teamdman.sfm.client.search.SFMFuzzyScorer.matches("java", term)))
                    .count();
            var domain = resolver.resolveFilterDomain(
                    new ca.teamdman.sfm.client.explorer.lazy.SFMExplorerFilterDomainResolver.FilterDomainRequest(
                            root,
                            "java",
                            ca.teamdman.sfm.client.explorer.lazy.SFMExplorerFilterDomainResolver.DEFAULT_MAXIMUM_FILTER_MATCHES,
                            resolver.generation(),
                            new SFMExplorerCancellationToken()
                    )
            ).join();

            assertEquals(root, domain.root());
            assertEquals(expectedMatches, domain.totalMatchCount(),
                    "complete filtering must search every row, independent of lazy page boundaries");
            assertEquals(expectedMatches, domain.matchedPaths().size(),
                    "an unbounded fixture query must publish every direct match");
            assertTrue(domain.entries().stream().anyMatch(entry -> entry.label().contains("Cafe.java")));
            assertTrue(domain.entries().stream().anyMatch(entry -> entry.label().contains("Unicode.java")));
            assertTrue(domain.childPages().stream().allMatch(page ->
                    page.completeness() == ca.teamdman.sfm.client.explorer.SFMChildPage.Completeness.COMPLETE
                            && page.continuation().isEmpty()));
            assertTrue(domain.childPages().stream().anyMatch(page -> page.parent().equals(root)));
            assertEquals(domain.entries().size(), domain.entries().stream()
                    .map(SFMExplorerEntry::path)
                    .distinct()
                    .count());

            SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
            resolvers.register(resolver);
            SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                    resolvers,
                    new SFMChildRelationRepository(),
                    Runnable::run
            );
            loader.openRoot(root).join();
            SFMExplorerSession session = new SFMExplorerSession(
                    new SFMExplorerId("complete-review-filter"),
                    root,
                    new SFMSelectionRepository()
            );
            session.setFilterOptions(ca.teamdman.sfm.client.search.SFMTextMatchOptions.legacyFuzzy());
            session.setFilterQuery("java");
            SFMExplorerPanel panel = new SFMExplorerPanel(session, loader, ignored -> { });

            panel.tick();
            loader.ensureFilterDomain(root, "java").orElseThrow().join();

            SFMLazyExplorerLoader.FilterProjection javaDomain = loader.filterProjection(
                    Set.of(root), "java", Set.of()
            ).orElseThrow();

            SFMExplorerProjection.Result filtered = SFMExplorerProjection.project(
                    session.snapshot(),
                    javaDomain.relations(),
                    javaDomain.entries()
            );
            assertFalse(filtered.filter().incompleteMaterialization());
            assertTrue(filtered.filter().matchCount() > 1,
                    "the common fuzzy filter must see Java rows beyond the first lazy page");
            assertTrue(filtered.rows().stream().anyMatch(row -> row.entry().label().contains("Cafe.java")));
            assertTrue(filtered.rows().stream().anyMatch(row -> row.entry().label().contains("Unicode.java")));
            assertTrue(filtered.rows().stream().noneMatch(row ->
                            row.entry().label().startsWith("before ·")
                                    || row.entry().label().startsWith("after ·")
                                    || row.entry().label().startsWith("text diff (")
                                    || row.entry().label().startsWith("structured diff (")),
                    "a file-extension query must not expand every matching file into inherited source/diff leaves");

            session.setFilterQuery("structured");
            loader.ensureFilterDomain(root, "structured").orElseThrow().join();
            SFMLazyExplorerLoader.FilterProjection structuredDomain = loader.filterProjection(
                    Set.of(root), "structured", Set.of()
            ).orElseThrow();
            SFMExplorerProjection.Result structured = SFMExplorerProjection.project(
                    session.snapshot(),
                    structuredDomain.relations(),
                    structuredDomain.entries()
            );
            assertTrue(structured.rows().stream().anyMatch(row ->
                    row.entry().label().startsWith("structured diff (inline) ·")));
        } finally {
            runtime.discardAndClose();
        }
    }

    @Test void completeDomainUsesTheSameTypedPredicateAsTheProjection() throws Exception {
        Path file = temporaryDirectory.resolve("typed-match.sfm-review.json");
        Files.copy(fixture(), file);
        String original = Files.readString(file);
        try (var runtime = new SFMReleaseReviewRuntime()) {
            runtime.open(file, false);
            var resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            var root = resolver.prepareLens(file, SFMReleaseReviewExplorerScreenType.Projection.CHANGES, Optional.empty());
            var rows = descendants(resolver, root);
            var literal = ca.teamdman.sfm.client.search.SFMTextMatchOptions.defaults();
            var fuzzy = literal.toggleFuzzy();
            for (var options : List.of(literal, fuzzy, literal.withCase(true), literal.withWholeWord(true), literal.toggleRegex())) {
                for (String query : List.of("Cafe.java", "cafe.java", "Cfe.jva", "UncommittedNotInSnapshot.java")) {
                    var matcher = ca.teamdman.sfm.client.search.SFMTextMatcher.compile(query, options);
                    var expected = rows.stream().filter(entry ->
                            ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntryMatch.evaluate(entry, matcher).matches())
                            .map(SFMExplorerEntry::path).collect(java.util.stream.Collectors.toSet());
                    var domain = resolver.resolveFilterDomain(new ca.teamdman.sfm.client.explorer.lazy.SFMExplorerFilterDomainResolver.FilterDomainRequest(
                            root, query, 4096, resolver.generation(), new SFMExplorerCancellationToken(), options)).join();
                    assertEquals(options, domain.options());
                    assertTrue(domain.complete());
                    assertEquals(expected, domain.matchedPaths(), query + " " + options);
                    assertTrue(domain.matchEvidence().keySet().containsAll(domain.matchedPaths()));
                }
            }
            assertEquals(original, Files.readString(file));
        }
    }

    @Test
    void sourceCommentDetailsResolveToTheExactSharedCommentsLensObjects() throws Exception {
        Path reviewPath = temporaryDirectory.resolve("comment-details.sfm-review.json");
        Files.copy(fixture(), reviewPath);
        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        runtime.open(reviewPath, false);
        try {
            SFMReleaseReviewExplorerRuntime resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            SFMPath root = resolver.prepareLens(
                    reviewPath,
                    SFMReleaseReviewExplorerScreenType.Projection.COMMENTS,
                    Optional.empty()
            );
            String commentId = runtime.document().orElseThrow().reviewSession().comments().get(0).id();
            SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
            resolvers.register(resolver);
            SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                    resolvers, new SFMChildRelationRepository(), Runnable::run
            );
            SFMExplorerSession session = new SFMExplorerSession(
                    new SFMExplorerId("comment-details"), root, new SFMSelectionRepository()
            );
            loader.openRoot(root).join();

            for (String section : List.of("value", "selector", "matches", "provenance")) {
                SFMPath sectionPath = resolver.commentNodePathAsync(root, commentId, section).join();
                SFMExplorerPathReveal.reveal(
                        session, loader, root, sectionPath, 128, () -> { }
                ).toCompletableFuture().join();

                assertEquals(sectionPath, session.snapshot().navigationCursor().orElseThrow());
                assertTrue(loader.entry(sectionPath).orElseThrow().label().startsWith(section),
                        "source decoration details must navigate the same object used by the Comments lens");
            }
        } finally {
            runtime.discardAndClose();
        }
    }

    @Test
    void migrationRowsContributeTheExistingDecisionActionsToGenericExplorerMenus() throws Exception {
        Path reviewPath = temporaryDirectory.resolve("migration-actions.sfm-review.json");
        SFMReleaseReviewV1 source = SFMReleaseReviewV1Codec.parse(Files.readString(fixture()));
        SFMReleaseReviewV1.CommentSelectorBinding binding = source.selectorBindings().get(0);
        String selectorId = binding.selectedProposal().id();
        SFMReleaseReviewV1.EvaluationResult unavailable = new SFMReleaseReviewV1.EvaluationResult(
                selectorId,
                SFMReleaseReviewV1.EvaluationStatus.MISSING,
                List.of(),
                List.of(),
                List.of(),
                List.of("Synthetic unresolved migration for generic Explorer menu coverage.")
        );
        SFMReleaseReviewV1.MigrationReport migration = new SFMReleaseReviewV1.MigrationReport(
                "migration:generic-explorer-test",
                selectorId,
                unavailable,
                unavailable,
                binding.capturedSelection().ranges(),
                List.of(),
                SFMReleaseReviewV1.MigrationDecision.UNRESOLVED,
                Optional.empty()
        );
        SFMReleaseReviewV1 withMigration = new SFMReleaseReviewV1(
                source.schema(),
                source.reviewSession(),
                source.repositoryBindings(),
                source.corpusDocuments(),
                source.reviewUnits(),
                source.selectorBindings(),
                List.of(migration),
                source.namedQueries(),
                source.resumeState(),
                source.producerGenerations(),
                source.completionAttestations()
        );
        Files.writeString(reviewPath, SFMReleaseReviewV1Codec.write(withMigration));
        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        runtime.open(reviewPath, true);
        try {
            SFMReleaseReviewExplorerRuntime resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            SFMPath root = resolver.prepareLens(
                    reviewPath,
                    SFMReleaseReviewExplorerScreenType.Projection.MIGRATIONS,
                    Optional.empty()
            );
            ArrayDeque<SFMPath> pending = new ArrayDeque<>();
            pending.add(root);
            SFMPath actionable = null;
            while (!pending.isEmpty() && actionable == null) {
                SFMPath parent = pending.removeFirst();
                for (var entry : children(resolver, parent)) {
                    if (!resolver.contextChoices(entry.path()).isEmpty()) {
                        actionable = entry.path();
                        break;
                    }
                    if (entry.expandable()) pending.addLast(entry.path());
                }
            }
            assertFalse(actionable == null, "fixture must expose an unresolved migration row");
            var choices = resolver.contextChoices(actionable);
            assertTrue(choices.size() >= 3);
            assertTrue(choices.stream().allMatch(choice -> choice.command().startsWith(
                    "sfm action invoke sfm:review/session/migration/decide ")));
            assertTrue(choices.stream().anyMatch(choice -> choice.displayText().contains("Defer migration")));
        } finally {
            runtime.discardAndClose();
        }
    }

    @Test
    void immutableBeforeAndAfterDocumentsMapBackToTheirExactGenericExplorerRows() throws Exception {
        Path reviewPath = temporaryDirectory.resolve("reveal-targets.sfm-review.json");
        Files.copy(fixture(), reviewPath);
        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        runtime.open(reviewPath, false);
        try {
            SFMReleaseReviewExplorerRuntime resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            SFMPath root = resolver.prepareLens(
                    reviewPath,
                    SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                    Optional.empty()
            );
            List<SFMPath> sourceRows = descendants(resolver, root).stream()
                    .filter(entry -> resolver.documentTarget(entry.path())
                            .map(target -> target.leaf().documentRevisionId().isPresent())
                            .orElse(false))
                    .map(entry -> entry.path())
                    .limit(2)
                    .toList();
            assertEquals(2, sourceRows.size(), "fixture must expose a before/after source pair");

            SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
            resolvers.register(resolver);
            SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                    resolvers,
                    new SFMChildRelationRepository(),
                    Runnable::run
            );
            SFMExplorerSession session = new SFMExplorerSession(
                    new SFMExplorerId("review-reveal-test"),
                    root,
                    new SFMSelectionRepository()
            );
            loader.openRoot(root).join();

            for (SFMPath sourceRow : sourceRows) {
                SFMTextDocumentSnapshot snapshot = resolver.documentTarget(sourceRow).orElseThrow()
                        .source().load(new SFMExplorerCancellationToken()).join();
                assertEquals(
                        List.of(new SFMReleaseReviewExplorerRuntime.RevealTarget(root, sourceRow)),
                        resolver.revealTargets(java.util.Set.of(root), snapshot)
                );
                SFMExplorerPathReveal.reveal(
                        session,
                        loader,
                        root,
                        sourceRow,
                        128,
                        () -> { }
                ).toCompletableFuture().join();
                assertEquals(
                        sourceRow,
                        session.snapshot().navigationCursor().orElse(null),
                        "each reveal should publish the exact contributed row"
                );
                assertTrue(resolver.revealTargets(
                        java.util.Set.of(root),
                        snapshot.withSavedText(snapshot.text() + "stale")
                ).isEmpty(), "a changed hash must not reveal a stale review row");
            }
        } finally {
            runtime.discardAndClose();
        }
    }

    @Test
    void diffLeavesRemainLazyUntilOpenedAndThenRegisterTheirExactSourceMap() throws Exception {
        Path reviewPath = temporaryDirectory.resolve("lazy-surfaces.sfm-review.json");
        Files.copy(fixture(), reviewPath);
        SFMReleaseReviewRuntime reviewRuntime = new SFMReleaseReviewRuntime();
        reviewRuntime.open(reviewPath, false);
        AtomicInteger invocations = new AtomicInteger();
        SFMReleaseReviewSurfaceRuntime surfaces = new SFMReleaseReviewSurfaceRuntime(
                new SFMReleaseReviewSurfaceRuntime.Configuration(
                        "fixture-worker", Duration.ofSeconds(2), Duration.ofMillis(100),
                        16 * 1024 * 1024, 64 * 1024),
                Executors.newSingleThreadExecutor(action -> {
                    Thread thread = new Thread(action, "sfm-review-explorer-surface-test");
                    thread.setDaemon(true);
                    return thread;
                }),
                (configuration, json, cancellation) -> {
                    invocations.incrementAndGet();
                    var request = SFMReleaseReviewSurfaceJsonCodec.decodeRequest(json);
                    return new SFMReleaseReviewSurfaceRuntime.ProcessResult(
                            0,
                            SFMReleaseReviewSurfaceJsonCodec.encodeSurface(
                                    SFMReleaseReviewSurfaceJsonCodecTests.afterSurface(request)),
                            "");
                },
                true
        );
        try {
            SFMReleaseReviewExplorerRuntime resolver = new SFMReleaseReviewExplorerRuntime(reviewRuntime, surfaces);
            SFMPath root = resolver.prepareLens(
                    reviewPath, SFMReleaseReviewExplorerScreenType.Projection.CHANGES, Optional.empty());
            var allRows = descendants(resolver, root);
            assertEquals(0, invocations.get(), "describing and expanding the tree must not launch the producer");
            SFMPath textDiff = allRows.stream()
                    .filter(entry -> entry.label().startsWith("text diff (inline) ·"))
                    .map(entry -> entry.path())
                    .findFirst().orElseThrow();
            var source = resolver.documentTarget(textDiff).orElseThrow().source();
            assertEquals(0, invocations.get(), "creating a generated source must remain lazy");

            SFMTextDocumentSnapshot snapshot = source.load(new SFMExplorerCancellationToken()).join();

            assertEquals(1, invocations.get());
            assertTrue(snapshot.ready());
            assertTrue(snapshot.readOnly());
            assertEquals(SFMTextDocumentLanguage.diff(), snapshot.language());
            assertFalse(snapshot.language().usesLocalSfmlHighlighting());
            assertTrue(resolver.generatedSurface(snapshot).isPresent());
            int bytes = snapshot.text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            assertFalse(resolver.projectGeneratedSelection(
                    snapshot, new SFMReleaseReviewSurfaceV1.Utf8Range(0, bytes)).isEmpty());
            source.load(new SFMExplorerCancellationToken()).join();
            assertEquals(1, invocations.get(), "reopening immutable generated content must use the cache");
        } finally {
            surfaces.close();
            reviewRuntime.discardAndClose();
        }
    }

    private static List<ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry> descendants(
            SFMReleaseReviewExplorerRuntime resolver,
            SFMPath root
    ) {
        java.util.ArrayList<ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry> answer =
                new java.util.ArrayList<>();
        ArrayDeque<SFMPath> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            for (var entry : children(resolver, pending.removeFirst())) {
                answer.add(entry);
                if (entry.expandable()) pending.addLast(entry.path());
            }
        }
        return List.copyOf(answer);
    }

    private static List<ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry> children(
            SFMReleaseReviewExplorerRuntime resolver,
            SFMPath parent
    ) {
        java.util.ArrayList<ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry> answer =
                new java.util.ArrayList<>();
        Optional<String> continuation = Optional.empty();
        do {
            SFMExplorerResolver.ChildPage page = resolver.resolveChildren(new SFMExplorerResolver.ChildRequest(
                    parent,
                    continuation,
                    128,
                    resolver.generation(),
                    new SFMExplorerCancellationToken()
            )).join();
            answer.addAll(page.entries());
            continuation = page.continuation();
        } while (continuation.isPresent());
        return List.copyOf(answer);
    }

    private static Path fixture() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate canonical release-review fixture");
    }
}
