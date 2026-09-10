package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMChildPage;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerFilterDomainResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerIoCounter;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMFilesystemExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryRegistryExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerPanelSessionPaginationTests {
    private static final SFMScreenPanelBounds BOUNDS = new SFMScreenPanelBounds(0, 0, 320, 120);

    @TempDir
    Path temporaryDirectory;

    @Test
    public void approachingA128EntryBoundaryLoadsTheLaterPageWithoutReadingDescendants() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("root"));
        Path directory = Files.createDirectory(root.resolve("directory"));
        Path grandchild = Files.writeString(directory.resolve("must-not-be-prefetched.txt"), "nested");
        for (int index = 0; index < 140; index++) {
            Files.writeString(root.resolve("file-%03d.txt".formatted(index)), Integer.toString(index));
        }

        SFMExplorerIoCounter io = new SFMExplorerIoCounter();
        QueuedExecutor executor = new QueuedExecutor();
        SFMFilesystemExplorerResolver resolver = new SFMFilesystemExplorerResolver(
                List.of(root),
                executor,
                io,
                128
        );
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(resolvers, relations);
        SFMPath rootPath = SFMPath.fromNative(root);
        SFMPath directoryPath = SFMPath.fromNative(directory);
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("paged-files"),
                rootPath,
                new SFMSelectionRepository()
        );

        var rootDescription = loader.openRoot(rootPath);
        assertEquals(0, io.snapshot().metadataReads());
        executor.runNext();
        rootDescription.join();
        session.expand(rootPath);
        SFMLazyExplorerLoader.LoadHandle firstPage = session.requestChildren(rootPath, loader, 128);
        executor.runNext();
        firstPage.completion().join();
        assertEquals(128, relations.snapshot().relation().childrenOf(rootPath).size());
        assertTrue(relations.snapshot().pageStates().get(rootPath).continuation().isPresent());
        assertEquals(1, io.snapshot().directoryEnumerations());
        assertTrue(relations.snapshot().relation().childrenOf(directoryPath).isEmpty());
        assertTrue(loader.entry(SFMPath.fromNative(grandchild)).isEmpty());

        SFMExplorerPanelModel model = new SFMExplorerPanelModel(session, loader, ignored -> {}, 128);
        model.state(BOUNDS);
        assertEquals(1, io.snapshot().directoryEnumerations(), "opening the first viewport must not page eagerly");

        session.setScrollOffset(Integer.MAX_VALUE);
        model.state(BOUNDS);

        assertEquals(1, executor.size());
        assertEquals(1, session.activeRequestCount());
        assertEquals(128, relations.snapshot().relation().childrenOf(rootPath).size());
        model.state(BOUNDS);
        assertEquals(1, executor.size(), "repeated viewport observations must not restart the append");
        assertEquals(1, session.activeRequestCount());
        assertEquals(128, relations.snapshot().relation().childrenOf(rootPath).size());
        SFMExplorerPanelModel.State pending = model.state(BOUNDS);
        assertTrue(pending.projection().rows().stream().anyMatch(row ->
                        row.loading() && row.entry().label().equals("Loading children...")),
                "the in-flight append must occupy its future inline child location");

        executor.runNext();

        assertEquals(141, relations.snapshot().relation().childrenOf(rootPath).size());
        assertTrue(relations.snapshot().pageStates().get(rootPath).continuation().isEmpty());
        assertEquals(2, io.snapshot().directoryEnumerations(), "only the root's second page is enumerated");
        assertTrue(relations.snapshot().relation().childrenOf(directoryPath).isEmpty());
        assertTrue(loader.entry(SFMPath.fromNative(grandchild)).isEmpty());
        assertEquals(0, session.activeRequestCount());
        assertTrue(session.recentRequestEvidence().stream().anyMatch(observation ->
                observation.evidence().mode() == SFMChildRelationRepository.RequestMode.APPEND));

        model.state(BOUNDS);
        assertEquals(2, io.snapshot().directoryEnumerations(), "a complete page must not be requested again");
        assertTrue(model.state(BOUNDS).projection().rows().stream().noneMatch(row -> row.loading()),
                "published children atomically replace the inline loading row");
    }

    @Test
    public void keyboardAndWheelNavigationAreObservedByEveryPanelThroughTheSession() {
        SFMPath root = SFMPath.parse("registry://minecraft/item/");
        ArrayList<SFMPath> children = new ArrayList<>();
        ArrayList<SFMInMemoryRegistryExplorerResolver.Node> nodes = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            SFMPath child = SFMPath.parse("registry://minecraft/item/test/item-%03d".formatted(index));
            children.add(child);
            nodes.add(new SFMInMemoryRegistryExplorerResolver.Node(
                    SFMExplorerEntry.simple(child, "Item " + index, false, Optional.of("test:item-" + index)),
                    List.of()
            ));
        }
        nodes.add(new SFMInMemoryRegistryExplorerResolver.Node(
                SFMExplorerEntry.simple(root, "Items", true, Optional.of("minecraft:chest")),
                children
        ));
        SFMInMemoryRegistryExplorerResolver resolver = new SFMInMemoryRegistryExplorerResolver(
                nodes,
                Runnable::run,
                64
        );
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(resolvers, relations);
        loader.openRoot(root).join();
        loader.refresh(root, 64).completion().join();
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("shared-navigation"),
                root,
                new SFMSelectionRepository()
        );
        ArrayList<String> semanticActions = new ArrayList<>();
        SFMExplorerPanel firstPanel = new SFMExplorerPanel(session, loader, semanticActions::add);
        firstPanel.resized(null, BOUNDS);

        SFMExplorerPanelModel.State initial = firstPanel.model().state(BOUNDS);
        assertEquals(children.get(0), session.snapshot().navigationCursor().orElseThrow());
        assertEquals(initial.selectedPath(), session.snapshot().navigationCursor());

        assertTrue(firstPanel.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0));
        assertEquals(children.get(1), session.snapshot().navigationCursor().orElseThrow());

        SFMExplorerPanelViewport.Rect body = firstPanel.model().state(BOUNDS).viewport().layout().body();
        assertTrue(firstPanel.mouseScrolled(body.x() + 1, body.y() + 1, -1));
        assertEquals(1, session.snapshot().scrollOffset());

        SFMExplorerPanelModel.State beforeEnd = firstPanel.model().state(BOUNDS);
        SFMPath expectedLast = beforeEnd.projection().rows()
                .get(beforeEnd.projection().rows().size() - 1)
                .path();
        assertTrue(firstPanel.keyPressed(GLFW.GLFW_KEY_END, 0, 0));
        assertEquals(expectedLast, session.snapshot().navigationCursor().orElseThrow());
        assertTrue(session.snapshot().scrollOffset() > 1);

        SFMExplorerPanel secondPanel = new SFMExplorerPanel(session, loader, semanticActions::add);
        secondPanel.resized(null, BOUNDS);
        SFMExplorerPanelModel.State shared = secondPanel.model().state(BOUNDS);
        assertEquals(session.snapshot().navigationCursor(), shared.selectedPath());
        assertEquals(session.snapshot().scrollOffset(), shared.viewport().scrollRow());
        assertTrue(semanticActions.isEmpty(), "visual navigation must not emit semantic actions");
    }

    @Test
    public void replacementFilterKeepsLastPublishedRowsUntilAtomicPublicationAndClearRestoresOrdinaryTree() {
        SFMPath root = SFMPath.parse("registry://test/root");
        SFMPath alpha = SFMPath.parse("registry://test/root/alpha.java");
        SFMPath beta = SFMPath.parse("registry://test/root/beta.java");
        SFMExplorerEntry rootEntry = SFMExplorerEntry.simple(root, "root", true, Optional.of("test"));
        SFMExplorerEntry alphaEntry = SFMExplorerEntry.simple(alpha, "Alpha.java", false, Optional.of("paper"));
        SFMExplorerEntry betaEntry = SFMExplorerEntry.simple(beta, "Beta.java", false, Optional.of("paper"));
        java.util.concurrent.CompletableFuture<SFMExplorerFilterDomainResolver.FilterDomain> pendingBeta =
                new java.util.concurrent.CompletableFuture<>();
        SFMExplorerFilterDomainResolver resolver = new SFMExplorerFilterDomainResolver() {
            @Override
            public String scheme() {
                return "registry";
            }

            @Override
            public long generation() {
                return 3;
            }

            @Override
            public java.util.concurrent.CompletableFuture<SFMExplorerEntry> describe(
                    SFMPath path,
                    SFMExplorerCancellationToken cancellation
            ) {
                return java.util.concurrent.CompletableFuture.completedFuture(rootEntry);
            }

            @Override
            public java.util.concurrent.CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
                return java.util.concurrent.CompletableFuture.completedFuture(new ChildPage(
                        root,
                        List.of(alphaEntry, betaEntry),
                        Optional.empty(),
                        generation(),
                        List.of(),
                        2
                ));
            }

            @Override
            public java.util.concurrent.CompletableFuture<FilterDomain> resolveFilterDomain(
                    FilterDomainRequest request
            ) {
                if (request.query().equals("beta")) return pendingBeta;
                return java.util.concurrent.CompletableFuture.completedFuture(domain(request.query(), alphaEntry));
            }

            private FilterDomain domain(String query, SFMExplorerEntry match) {
                return new FilterDomain(
                        root,
                        query,
                        List.of(rootEntry, match),
                        List.of(
                                new SFMChildPage(
                                        root,
                                        List.of(new ca.teamdman.sfm.client.explorer.SFMChildEdge(root, match.path())),
                                        Optional.empty(),
                                        SFMChildPage.Completeness.COMPLETE,
                                        generation(),
                                        List.of()
                                ),
                                new SFMChildPage(
                                        match.path(),
                                        List.of(),
                                        Optional.empty(),
                                        SFMChildPage.Completeness.COMPLETE,
                                        generation(),
                                        List.of()
                                )
                        ),
                        Set.of(match.path()),
                        1,
                        true,
                        generation(),
                        List.of()
                );
            }
        };
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers, new SFMChildRelationRepository(), Runnable::run
        );
        loader.openRoot(root).join();
        loader.refresh(root, 8).completion().join();
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("retained-filter"), root, new SFMSelectionRepository()
        );
        session.setFilterOptions(ca.teamdman.sfm.client.search.SFMTextMatchOptions.legacyFuzzy());
        session.expand(root);
        SFMExplorerPanelModel model = new SFMExplorerPanelModel(session, loader, ignored -> { });

        session.setFilterQuery("alpha");
        loader.ensureFilterDomain(root, "alpha").orElseThrow().join();
        assertEquals(List.of(alpha), model.state(BOUNDS).projection().rows().stream()
                .map(row -> row.path()).toList());

        session.setFilterQuery("beta");
        java.util.concurrent.CompletableFuture<SFMLazyExplorerLoader.LoadDisposition> replacement =
                loader.ensureFilterDomain(root, "beta").orElseThrow();
        assertEquals("beta", model.state(BOUNDS).session().settings().filterQuery());
        assertEquals(List.of(alpha), model.state(BOUNDS).projection().rows().stream()
                        .map(row -> row.path()).toList(),
                "the last coherent filtered page remains visible while its replacement is pending");

        pendingBeta.complete(new SFMExplorerFilterDomainResolver.FilterDomain(
                root,
                "beta",
                List.of(rootEntry, betaEntry),
                List.of(
                        new SFMChildPage(
                                root,
                                List.of(new ca.teamdman.sfm.client.explorer.SFMChildEdge(root, beta)),
                                Optional.empty(),
                                SFMChildPage.Completeness.COMPLETE,
                                resolver.generation(),
                                List.of()
                        ),
                        new SFMChildPage(
                                beta,
                                List.of(),
                                Optional.empty(),
                                SFMChildPage.Completeness.COMPLETE,
                                resolver.generation(),
                                List.of()
                        )
                ),
                Set.of(beta),
                1,
                true,
                resolver.generation(),
                List.of()
        ));
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, replacement.join());
        assertEquals(List.of(beta), model.state(BOUNDS).projection().rows().stream()
                .map(row -> row.path()).toList());

        session.setFilterQuery("");
        SFMExplorerPanelModel.State cleared = model.state(BOUNDS);
        assertTrue(!cleared.projection().filter().active());
        assertEquals(List.of(alpha, beta), cleared.projection().rows().stream()
                .map(row -> row.path()).toList());
    }

    private static final class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            pending.addLast(Objects.requireNonNull(command, "command"));
        }

        private int size() {
            return pending.size();
        }

        private void runNext() {
            pending.removeFirst().run();
        }
    }
}
