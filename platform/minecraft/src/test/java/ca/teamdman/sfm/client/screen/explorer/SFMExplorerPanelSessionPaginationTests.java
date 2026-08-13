package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
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
