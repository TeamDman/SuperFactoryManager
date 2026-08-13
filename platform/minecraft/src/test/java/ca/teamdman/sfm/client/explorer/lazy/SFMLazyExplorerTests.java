package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.SFMSelectionId;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMLazyExplorerTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    public void openingReadsOnlyRootMetadataAndExpansionReadsOneBoundedImmediatePage() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("root"));
        Path directory = Files.createDirectory(root.resolve("directory"));
        Files.writeString(directory.resolve("grandchild.txt"), "not eagerly observed");
        Files.writeString(root.resolve("a.txt"), "a");
        Files.writeString(root.resolve("b.txt"), "b");
        Files.writeString(root.resolve("c.txt"), "c");

        SFMExplorerIoCounter io = new SFMExplorerIoCounter();
        SFMFilesystemExplorerResolver filesystem = new SFMFilesystemExplorerResolver(
                List.of(root),
                Runnable::run,
                io,
                2
        );
        Fixture fixture = fixture(filesystem);
        SFMPath rootPath = SFMPath.fromNative(root);

        fixture.loader().openRoot(rootPath).join();
        SFMExplorerIoCounter.Snapshot opened = io.snapshot();
        assertEquals(1, opened.metadataReads());
        assertEquals(0, opened.directoryEnumerations());
        assertEquals(0, opened.entriesObserved());
        assertEquals(0, opened.eventsDropped());
        assertEquals(1, opened.latestSequence());
        assertEquals(1, opened.events().size());
        assertEquals(SFMExplorerIoCounter.OperationKind.METADATA_READ, opened.events().get(0).operation());
        assertEquals(Optional.of(rootPath.canonical()), opened.events().get(0).canonicalPath());
        assertTrue(fixture.relations().snapshot().relation().edges().isEmpty());

        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("lazy-files"),
                rootPath,
                fixture.selections()
        );
        session.expand(rootPath);
        SFMLazyExplorerLoader.LoadResult loaded = session
                .requestChildren(rootPath, fixture.loader(), 100)
                .completion()
                .join();

        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, loaded.disposition());
        assertEquals(2, loaded.resolverPage().orElseThrow().entries().size());
        assertTrue(loaded.resolverPage().orElseThrow().continuation().isPresent());
        assertTrue(loaded.resolverPage().orElseThrow().observedEntries() <= 3);
        SFMExplorerIoCounter.Snapshot expanded = io.snapshot();
        assertEquals(1, expanded.directoryEnumerations());
        assertEquals(3, expanded.metadataReads(), "root plus exactly one two-entry page");
        List<SFMExplorerIoCounter.Event> expansionEvents = expanded.eventsAfter(opened.latestSequence());
        assertEquals(
                List.of(rootPath.canonical()),
                expansionEvents.stream()
                        .filter(event -> event.operation()
                                == SFMExplorerIoCounter.OperationKind.DIRECTORY_ENUMERATION)
                        .flatMap(event -> event.canonicalPath().stream())
                        .toList(),
                "expansion must enumerate only the requested root"
        );
        assertTrue(expansionEvents.stream()
                        .filter(event -> event.operation() == SFMExplorerIoCounter.OperationKind.ENTRY_OBSERVED)
                        .flatMap(event -> event.canonicalPath().stream())
                        .map(SFMPath::parse)
                        .allMatch(path -> path.toNativePath().getParent().equals(root)),
                "observed entries must be immediate children of the requested root");
        assertTrue(expansionEvents.stream()
                        .noneMatch(event -> event.canonicalPath().orElse("")
                                .endsWith("/grandchild.txt")),
                "expanding a parent must not produce nested-descendant I/O events");
        assertEquals(
                expanded.events().stream().map(SFMExplorerIoCounter.Event::sequence).sorted().toList(),
                expanded.events().stream().map(SFMExplorerIoCounter.Event::sequence).toList(),
                "event snapshots must remain in deterministic sequence order"
        );
        assertTrue(
                fixture.relations().snapshot().relation().childrenOf(SFMPath.fromNative(directory)).isEmpty(),
                "expanding the root must not enumerate a child's descendants"
        );
        for (SFMPath child : fixture.relations().snapshot().relation().childrenOf(rootPath)) {
            assertEquals(root, child.toNativePath().getParent());
        }
    }

    @Test
    public void ioEvidenceRetainsASequenceOrderedBoundedTailWithoutLosingAggregates() {
        SFMExplorerIoCounter io = new SFMExplorerIoCounter(2);
        SFMPath first = SFMPath.parse("registry://minecraft/item/");
        SFMPath second = SFMPath.parse("registry://minecraft/item/stone");
        SFMPath third = SFMPath.parse("registry://minecraft/item/dirt");

        io.metadataRead(first);
        io.directoryEnumerated(second);
        io.containmentRejected(third);

        SFMExplorerIoCounter.Snapshot snapshot = io.snapshot();
        assertEquals(1, snapshot.metadataReads());
        assertEquals(1, snapshot.directoryEnumerations());
        assertEquals(1, snapshot.containmentRejections());
        assertEquals(2, snapshot.eventCapacity());
        assertEquals(1, snapshot.eventsDropped());
        assertEquals(3, snapshot.latestSequence());
        assertEquals(List.of(2L, 3L), snapshot.events().stream()
                .map(SFMExplorerIoCounter.Event::sequence)
                .toList());
        assertEquals(
                List.of(
                        SFMExplorerIoCounter.OperationKind.DIRECTORY_ENUMERATION,
                        SFMExplorerIoCounter.OperationKind.CONTAINMENT_REJECTION
                ),
                snapshot.events().stream().map(SFMExplorerIoCounter.Event::operation).toList()
        );
        assertEquals(
                List.of(second.canonical(), third.canonical()),
                snapshot.events().stream().flatMap(event -> event.canonicalPath().stream()).toList()
        );
        assertTrue(snapshot.events().stream().allMatch(event -> !event.workerThread().isBlank()));
    }

    @Test
    public void resolverCompletionPublishesOnlyThroughTheConfiguredOwnerExecutor() {
        SFMPath root = SFMPath.parse("registry://test/root");
        SFMPath child = SFMPath.parse("registry://test/root/child");
        SFMExplorerResolverRegistry registry = new SFMExplorerResolverRegistry();
        registry.register(registryResolver(root, child, Runnable::run));
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        ManualExecutor publicationExecutor = new ManualExecutor();
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                registry,
                relations,
                publicationExecutor
        );

        SFMLazyExplorerLoader.LoadHandle load = loader.refresh(root, 8);

        assertEquals(1, publicationExecutor.size());
        assertFalse(load.completion().isDone());
        assertTrue(relations.snapshot().relation().childrenOf(root).isEmpty());

        publicationExecutor.runNext();

        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, load.completion().join().disposition());
        assertEquals(Set.of(child), relations.snapshot().relation().childrenOf(root));
    }

    @Test
    public void synchronousResolverFailureStillReturnsTypedFailedRequestEvidence() {
        SFMPath root = SFMPath.parse("registry://test/root");
        SFMExplorerResolver resolver = new SFMExplorerResolver() {
            @Override
            public String scheme() {
                return "registry";
            }

            @Override
            public long generation() {
                return 0;
            }

            @Override
            public java.util.concurrent.CompletableFuture<SFMExplorerEntry> describe(
                    SFMPath path,
                    SFMExplorerCancellationToken cancellation
            ) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        SFMExplorerEntry.simple(path, "root", true, Optional.of("test"))
                );
            }

            @Override
            public java.util.concurrent.CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
                throw new IllegalStateException("injected synchronous resolver failure");
            }
        };
        Fixture fixture = fixture(resolver);

        SFMLazyExplorerLoader.LoadHandle handle = fixture.loader().refresh(root, 8);
        SFMLazyExplorerLoader.LoadResult result = handle.completion().join();

        assertTrue(handle.evidence().relationRequestId() > 0);
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.FAILED, result.disposition());
        assertEquals(handle.evidence(), result.evidence());
        assertTrue(result.diagnostic().orElseThrow().contains("injected synchronous resolver failure"));
        assertTrue(fixture.relations().snapshot().relation().childrenOf(root).isEmpty());
    }

    @Test
    public void publicationExecutorRejectionClosesTheRelationRequestAsFailed() {
        SFMPath root = SFMPath.parse("registry://test/root");
        SFMPath child = SFMPath.parse("registry://test/root/child");
        SFMExplorerResolverRegistry registry = new SFMExplorerResolverRegistry();
        registry.register(registryResolver(root, child, Runnable::run));
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                registry,
                relations,
                ignored -> {
                    throw new java.util.concurrent.RejectedExecutionException("injected owner shutdown");
                }
        );

        SFMLazyExplorerLoader.LoadResult result = loader.refresh(root, 8).completion().join();

        assertEquals(SFMLazyExplorerLoader.LoadDisposition.FAILED, result.disposition());
        SFMChildRelationRepository.PageState state = relations.snapshot().pageStates().get(root);
        assertEquals(
                SFMChildRelationRepository.PageState.Materialization.REFRESH_FAILED,
                state.materialization()
        );
        assertTrue(state.diagnostics().stream().anyMatch(message ->
                message.contains("publication executor rejected work")
        ));
        assertTrue(relations.snapshot().relation().childrenOf(root).isEmpty());
    }

    @Test
    public void collapseCloseAndGenerationChangesRejectObsoleteWork() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("root"));
        Files.writeString(root.resolve("a.txt"), "a");
        ManualExecutor executor = new ManualExecutor();
        SFMFilesystemExplorerResolver filesystem = new SFMFilesystemExplorerResolver(
                List.of(root),
                executor,
                new SFMExplorerIoCounter(),
                8
        );
        Fixture fixture = fixture(filesystem);
        SFMPath rootPath = SFMPath.fromNative(root);
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("cancellable"),
                rootPath,
                fixture.selections()
        );

        session.expand(rootPath);
        SFMLazyExplorerLoader.LoadHandle cancelled = session.requestChildren(rootPath, fixture.loader(), 8);
        assertEquals(1, executor.size());
        session.collapse(rootPath);
        assertEquals(
                SFMLazyExplorerLoader.LoadDisposition.CANCELLED,
                cancelled.completion().join().disposition()
        );
        executor.runNext();
        assertTrue(fixture.relations().snapshot().relation().edges().isEmpty());

        session.expand(rootPath);
        SFMLazyExplorerLoader.LoadHandle stale = session.requestChildren(rootPath, fixture.loader(), 8);
        filesystem.invalidate();
        executor.runNext();
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.STALE, stale.completion().join().disposition());
        assertTrue(fixture.relations().snapshot().relation().edges().isEmpty());

        SFMLazyExplorerLoader.LoadHandle closed = session.requestChildren(rootPath, fixture.loader(), 8);
        session.close();
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.CANCELLED, closed.completion().join().disposition());
        executor.runNext();
        assertTrue(session.snapshot().closed());
        assertTrue(fixture.relations().snapshot().relation().edges().isEmpty());
    }

    @Test
    public void newerRefreshMakesAnOlderCompletedPageStale() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("root"));
        Files.writeString(root.resolve("a.txt"), "a");
        ManualExecutor executor = new ManualExecutor();
        SFMFilesystemExplorerResolver filesystem = new SFMFilesystemExplorerResolver(
                List.of(root),
                executor,
                new SFMExplorerIoCounter(),
                8
        );
        Fixture fixture = fixture(filesystem);
        SFMPath rootPath = SFMPath.fromNative(root);

        SFMLazyExplorerLoader.LoadHandle old = fixture.loader().refresh(rootPath, 8);
        SFMLazyExplorerLoader.LoadHandle current = fixture.loader().refresh(rootPath, 8);
        executor.runNext();
        executor.runNext();

        assertEquals(SFMLazyExplorerLoader.LoadDisposition.STALE, old.completion().join().disposition());
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, current.completion().join().disposition());
        assertEquals(Set.of(SFMPath.fromNative(root.resolve("a.txt"))),
                     fixture.relations().snapshot().relation().childrenOf(rootPath));
    }

    @Test
    public void filesystemAuthorityRejectsPathsOutsideExplicitRoots() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("allowed"));
        Path outside = Files.writeString(temporaryDirectory.resolve("outside.txt"), "outside");
        SFMExplorerIoCounter io = new SFMExplorerIoCounter();
        SFMFilesystemExplorerResolver filesystem = new SFMFilesystemExplorerResolver(
                List.of(root),
                Runnable::run,
                io,
                8
        );

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> filesystem.describe(
                        SFMPath.fromNative(outside),
                        new SFMExplorerCancellationToken()
                ).join()
        );
        assertInstanceOf(SecurityException.class, failure.getCause());
        SFMExplorerIoCounter.Snapshot rejected = io.snapshot();
        assertEquals(1, rejected.containmentRejections());
        assertEquals(1, rejected.events().size());
        assertEquals(
                SFMExplorerIoCounter.OperationKind.CONTAINMENT_REJECTION,
                rejected.events().get(0).operation()
        );
        assertEquals(
                Optional.of(SFMPath.fromNative(outside).canonical()),
                rejected.events().get(0).canonicalPath()
        );
    }

    @Test
    public void grantingAFileRootIsClientThreadSafeAndInvalidatesCapturedRequests() throws IOException {
        Path first = Files.createDirectory(temporaryDirectory.resolve("first"));
        Path second = Files.createDirectory(temporaryDirectory.resolve("second"));
        SFMExplorerIoCounter io = new SFMExplorerIoCounter();
        SFMFilesystemExplorerResolver filesystem = new SFMFilesystemExplorerResolver(
                List.of(first),
                Runnable::run,
                io,
                8
        );
        long before = filesystem.generation();

        assertTrue(filesystem.authorizeRoot(second));
        assertFalse(filesystem.authorizeRoot(second));
        assertTrue(filesystem.generation() > before);
        assertEquals(0, io.snapshot().metadataReads());
        assertEquals(0, io.snapshot().directoryEnumerations());
        assertEquals(
                List.of(SFMPath.fromNative(first), SFMPath.fromNative(second)),
                filesystem.explicitRoots()
        );
    }

    @Test
    public void escapingSymlinkIsVisibleButNeverTraversable() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("allowed"));
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "secret");
        Path link = root.resolve("escape");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + unavailable.getMessage());
        }

        SFMExplorerIoCounter io = new SFMExplorerIoCounter();
        SFMFilesystemExplorerResolver filesystem = new SFMFilesystemExplorerResolver(
                List.of(root),
                Runnable::run,
                io,
                8
        );
        Fixture fixture = fixture(filesystem);
        SFMLazyExplorerLoader.LoadResult result = fixture.loader()
                .refresh(SFMPath.fromNative(root), 8)
                .completion()
                .join();

        SFMExplorerEntry linkEntry = result.resolverPage().orElseThrow().entries().stream()
                .filter(entry -> entry.path().equals(SFMPath.fromNative(link)))
                .findFirst()
                .orElseThrow();
        assertFalse(linkEntry.expandable());
        assertTrue(linkEntry.diagnostics().stream().anyMatch(message -> message.contains("symbolic link")));
        assertEquals(1, io.snapshot().containmentRejections());
    }

    @Test
    public void heterogeneousRootsUseEphemeralSelectionWhileNavigationLeavesPicksAlone() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("files"));
        SFMPath fileRoot = SFMPath.fromNative(root);
        SFMPath registryRoot = SFMPath.parse("registry://minecraft/item/");
        SFMPath item = SFMPath.parse("registry://minecraft/item/stone");
        SFMInMemoryRegistryExplorerResolver registry = registryResolver(registryRoot, item, Runnable::run);
        SFMFilesystemExplorerResolver filesystem = new SFMFilesystemExplorerResolver(
                List.of(root),
                Runnable::run,
                new SFMExplorerIoCounter(),
                8
        );
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(filesystem);
        resolvers.register(registry);
        SFMSelectionRepository selections = new SFMSelectionRepository();
        SFMSelectionId pickedId = new SFMSelectionId("picked");
        long pickedRevision = selections.create(
                pickedId,
                Optional.of("picked"),
                Set.of(item),
                "test",
                "create-picked"
        ).revision().id();
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(resolvers, relations);
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("mixed"),
                fileRoot,
                selections
        );

        loader.openRoot(fileRoot).join();
        assertTrue(session.addRoot(registryRoot));
        loader.openRoot(registryRoot).join();
        assertInstanceOf(SFMPathExpression.Members.class, session.snapshot().location());
        SFMSelectionId locationId = session.snapshot().ephemeralLocationSelection().orElseThrow();
        assertEquals(
                Set.of(fileRoot, registryRoot),
                selections.resolve(selections.livePath(locationId)).members()
        );
        assertEquals(List.of(fileRoot, registryRoot), session.snapshot().manualRootOrder());

        session.navigateTo(item);
        session.expand(registryRoot);
        session.setScrollOffset(7);
        session.showSelectionOverlay(pickedId);
        assertEquals(item, session.snapshot().navigationCursor().orElseThrow());
        assertEquals(Set.of(item), selections.resolve(selections.pinnedPath(pickedId, pickedRevision)).members());
        assertEquals(pickedRevision, selections.selection(pickedId).orElseThrow().headRevisionId());
        assertEquals(Set.of(pickedId), session.snapshot().overlaySelections());

        SFMLazyExplorerLoader.LoadResult registryPage = session
                .requestChildren(registryRoot, loader, 8)
                .completion()
                .join();
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, registryPage.disposition());
        assertEquals(Set.of(item), relations.snapshot().relation().childrenOf(registryRoot));
    }

    private static Fixture fixture(SFMExplorerResolver resolver) {
        SFMExplorerResolverRegistry registry = new SFMExplorerResolverRegistry();
        registry.register(resolver);
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        return new Fixture(
                new SFMSelectionRepository(),
                relations,
                new SFMLazyExplorerLoader(registry, relations)
        );
    }

    private static SFMInMemoryRegistryExplorerResolver registryResolver(
            SFMPath root,
            SFMPath child,
            Executor executor
    ) {
        SFMExplorerEntry rootEntry = SFMExplorerEntry.simple(
                root, "Items", true, Optional.of("registry")
        );
        SFMExplorerEntry childEntry = SFMExplorerEntry.simple(
                child, "Stone", false, Optional.of("minecraft:stone")
        );
        return new SFMInMemoryRegistryExplorerResolver(
                List.of(
                        new SFMInMemoryRegistryExplorerResolver.Node(rootEntry, List.of(child)),
                        new SFMInMemoryRegistryExplorerResolver.Node(childEntry, List.of())
                ),
                executor,
                8
        );
    }

    private record Fixture(
            SFMSelectionRepository selections,
            SFMChildRelationRepository relations,
            SFMLazyExplorerLoader loader
    ) {
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> work = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            work.add(Objects.requireNonNull(command, "command"));
        }

        private int size() {
            return work.size();
        }

        private void runNext() {
            work.removeFirst().run();
        }
    }
}
