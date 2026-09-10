package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.*;
import ca.teamdman.sfm.client.explorer.lazy.*;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class SFMExplorerPanelFindFilterTimingTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://test/search/");
    private static final SFMPath ALPHA = SFMPath.parse("registry://test/search/alpha");
    private static final SFMPath BETA = SFMPath.parse("registry://test/search/beta");

    @Test
    void completedFindCannotConsumeFilterBeforeItsRequestStarts() throws Exception {
        var resolver = new GatedFilterResolver();
        var registry = new SFMExplorerResolverRegistry();
        registry.register(resolver);
        var loader = new SFMLazyExplorerLoader(registry, new SFMChildRelationRepository());
        loader.openRoot(ROOT).join();
        loader.refresh(ROOT, 32).completion().join();
        var session = new SFMExplorerSession(new SFMExplorerId("filter-timing"), ROOT, new SFMSelectionRepository());
        var panel = new SFMExplorerPanel(session, loader, ignored -> {}, () -> {}, () -> {},
                SFMExplorerPresentationRegistry.minecraftDefaults(), ignored -> {});
        panel.resized(null, new SFMScreenPanelBounds(0, 0, 320, 180));
        panel.setFindMaterializedOnly(true);
        panel.focusSearch("find");
        for (char ch : "alpha".toCharArray()) panel.charTyped(ch, 0);
        panel.focusSearch("filter");
        for (char ch : "alpha".toCharArray()) panel.charTyped(ch, 0);
        // This panel fixture records no global command dispatcher; apply the emitted
        // filter setting as that action does, without pre-requesting its async domain.
        session.setFilterQuery("alpha");
        // Deliberately let the background Find finish before the first UI tick.
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (session.snapshot().finder().status() == SFMExplorerSession.FinderStatus.PENDING
                && System.nanoTime() < deadline) Thread.sleep(5);
        assertNotEquals(SFMExplorerSession.FinderStatus.PENDING, session.snapshot().finder().status());
        assertNull(resolver.request, "Filter is requested by the next panel tick");
        panel.tick();
        assertNotNull(resolver.request);
        assertFalse(panel.narration().getString().contains("1 matches"),
                "Find must wait rather than consume the old materialized filter projection");
        resolver.publish();
        deadline = System.nanoTime() + 2_000_000_000L;
        do {
            panel.tick();
            if (panel.narration().getString().contains("1 matches")) break;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        assertTrue(panel.narration().getString().contains("1 matches"),
                panel.narration().getString() + " finder=" + session.snapshot().finder());
        assertFalse(panel.narration().getString().contains("INCOMPLETE"), panel.narration().getString());
        panel.selectSearchMatches(true);
        assertEquals(Set.of(ALPHA), session.selectedPaths());
    }

    private static final class GatedFilterResolver implements SFMExplorerFilterDomainResolver {
        private FilterDomainRequest request;
        private final CompletableFuture<FilterDomain> completion = new CompletableFuture<>();
        private static SFMExplorerEntry entry(SFMPath path) {
            return SFMExplorerEntry.simple(path, path.equals(ROOT) ? "Root" : path.equals(ALPHA) ? "alpha" : "beta",
                    path.equals(ROOT), Optional.empty());
        }
        @Override public String scheme() { return "registry"; }
        @Override public long generation() { return 1; }
        @Override public CompletableFuture<SFMExplorerEntry> describe(SFMPath path, SFMExplorerCancellationToken token) {
            return CompletableFuture.completedFuture(entry(path));
        }
        @Override public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
            var children = request.parent().equals(ROOT) ? List.of(entry(ALPHA), entry(BETA)) : List.<SFMExplorerEntry>of();
            return CompletableFuture.completedFuture(new ChildPage(request.parent(), children, Optional.empty(), 1, List.of(), children.size()));
        }
        @Override public CompletableFuture<FilterDomain> resolveFilterDomain(FilterDomainRequest request) {
            this.request = request;
            return completion;
        }
        void publish() {
            completion.complete(new FilterDomain(ROOT, request.query(), List.of(entry(ROOT), entry(ALPHA), entry(BETA)),
                    List.of(new SFMChildPage(ROOT, List.of(new SFMChildEdge(ROOT, ALPHA), new SFMChildEdge(ROOT, BETA)),
                            Optional.empty(), SFMChildPage.Completeness.COMPLETE, 1, List.of())),
                    Set.of(ALPHA), 1, true, 1, List.of(), request.options()));
        }
    }
}
