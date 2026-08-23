package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerPathReveal;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPresentationRegistry;
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewExplorerRuntimeTests {
    @TempDir Path temporaryDirectory;

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
                    Optional.empty()
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
            assertEquals(List.of("before", "after"), descendants.entries().stream()
                    .map(entry -> entry.label().split(" · ")[0])
                    .toList());
            assertTrue(descendants.entries().stream().noneMatch(entry -> entry.expandable()));
            assertFalse(resolver.documentTarget(descendants.entries().get(0).path()).isEmpty());
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
                    .filter(entry -> resolver.documentTarget(entry.path()).isPresent())
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
