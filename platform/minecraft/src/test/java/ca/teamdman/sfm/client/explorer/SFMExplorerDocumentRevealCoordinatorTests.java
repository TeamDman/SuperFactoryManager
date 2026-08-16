package ca.teamdman.sfm.client.explorer;

import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMExplorerDocumentRevealCoordinatorTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://minecraft/item/");
    private static final SFMPath NARROW_ROOT = SFMPath.parse("registry://minecraft/item/minecraft");
    private static final SFMPath TARGET = SFMPath.parse("registry://minecraft/item/minecraft/stick");
    private static final SFMPath OTHER_ROOT = SFMPath.parse("registry://minecraft/block/");

    @Test
    void visibleMostRecentlyFocusedCompatibleExplorerWinsWithoutOpeningOrRemovingPanels() {
        AtomicInteger hiddenCalls = new AtomicInteger();
        AtomicInteger visibleOlderCalls = new AtomicInteger();
        AtomicInteger visibleRecentCalls = new AtomicInteger();
        FakeHost host = new FakeHost(List.of(
                target("hidden", 1, Set.of(NARROW_ROOT), false, 99, hiddenCalls),
                target("visible-older", 2, Set.of(NARROW_ROOT), true, 3, visibleOlderCalls),
                target("visible-recent", 3, Set.of(ROOT), true, 8, visibleRecentCalls),
                target("incompatible", 4, Set.of(OTHER_ROOT), true, 100, new AtomicInteger())
        ));
        int panelCountBefore = host.panels.size();

        var outcome = new SFMExplorerDocumentRevealCoordinator()
                .reveal(new SFMExplorerDocumentRevealCoordinator.Document(TARGET, ROOT), host)
                .toCompletableFuture().join();

        assertEquals("visible-recent", outcome.explorerId().value());
        assertEquals(ROOT, outcome.containingRoot());
        assertFalse(outcome.openedNew());
        assertEquals(0, host.openCalls);
        assertEquals(panelCountBefore, host.panels.size(), "reveal must preserve unrelated panels");
        assertEquals(new SFMWorkspacePanelId(3), host.focused);
        assertEquals(0, hiddenCalls.get());
        assertEquals(0, visibleOlderCalls.get());
        assertEquals(1, visibleRecentCalls.get());
    }

    @Test
    void noCompatibleExplorerAppendsOneAtExactAuthorityAndUsesIt() {
        FakeHost host = new FakeHost(List.of(
                target("unrelated", 7, Set.of(OTHER_ROOT), true, 1, new AtomicInteger())
        ));
        List<SFMExplorerDocumentRevealCoordinator.ExplorerTarget> before = List.copyOf(host.panels);

        var outcome = new SFMExplorerDocumentRevealCoordinator()
                .reveal(new SFMExplorerDocumentRevealCoordinator.Document(TARGET, ROOT), host)
                .toCompletableFuture().join();

        assertTrue(outcome.openedNew());
        assertEquals(ROOT, host.openedRoot.get());
        assertEquals(1, host.openCalls);
        assertEquals(2, host.panels.size());
        assertSame(before.get(0), host.panels.get(0), "existing panel identity must be preserved");
        assertEquals(outcome.panelId(), host.focused);
    }

    @Test
    void staleChosenPanelFailsWithoutFallingBackOrOpeningAnotherExplorer() {
        AtomicInteger revealCalls = new AtomicInteger();
        FakeHost host = new FakeHost(List.of(
                target("stale", 4, Set.of(ROOT), true, 2, revealCalls)
        ));
        host.focusSucceeds = false;

        var failure = org.junit.jupiter.api.Assertions.assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> new SFMExplorerDocumentRevealCoordinator()
                        .reveal(new SFMExplorerDocumentRevealCoordinator.Document(TARGET, ROOT), host)
                        .toCompletableFuture().join()
        );

        assertTrue(failure.getCause().getMessage().contains("no longer available"));
        assertEquals(0, revealCalls.get());
        assertEquals(0, host.openCalls);
    }

    private static SFMExplorerDocumentRevealCoordinator.ExplorerTarget target(
            String id,
            long panelId,
            Set<SFMPath> roots,
            boolean visible,
            long recency,
            AtomicInteger calls
    ) {
        return new SFMExplorerDocumentRevealCoordinator.ExplorerTarget(
                new SFMExplorerId(id),
                new SFMWorkspacePanelId(panelId),
                roots,
                visible,
                recency,
                (root, path) -> {
                    calls.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }
        );
    }

    private static final class FakeHost implements SFMExplorerDocumentRevealCoordinator.Host {
        private final ArrayList<SFMExplorerDocumentRevealCoordinator.ExplorerTarget> panels;
        private final AtomicReference<SFMPath> openedRoot = new AtomicReference<>();
        private int openCalls;
        private boolean focusSucceeds = true;
        private SFMWorkspacePanelId focused;

        private FakeHost(List<SFMExplorerDocumentRevealCoordinator.ExplorerTarget> panels) {
            this.panels = new ArrayList<>(panels);
        }

        @Override
        public List<SFMExplorerDocumentRevealCoordinator.ExplorerTarget> existingTargets() {
            return List.copyOf(panels);
        }

        @Override
        public SFMExplorerDocumentRevealCoordinator.ExplorerTarget openNew(SFMPath authorizedRoot) {
            openCalls++;
            openedRoot.set(authorizedRoot);
            var opened = target("opened", 99, Set.of(authorizedRoot), true, 999, new AtomicInteger());
            panels.add(opened);
            return opened;
        }

        @Override
        public boolean focus(SFMWorkspacePanelId panelId) {
            if (!focusSucceeds) return false;
            focused = panelId;
            return true;
        }
    }
}
