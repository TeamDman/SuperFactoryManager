package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.action.SFMExplorerCompactAction.Kind;
import ca.teamdman.sfm.client.explorer.*;
import ca.teamdman.sfm.client.explorer.lazy.*;
import ca.teamdman.sfm.client.screen.workspace.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SFMExplorerCompactPanelTests {
    static final SFMPath ROOT = SFMPath.parse("registry://test/compact/");
    static final SFMPath A = SFMPath.parse("registry://test/compact/ca/");
    static final SFMPath B = SFMPath.parse("registry://test/compact/ca/teamdman/");
    static final SFMPath C = SFMPath.parse("registry://test/compact/ca/teamdman/sfm/");
    static final SFMPath FILE = SFMPath.parse("registry://test/compact/ca/teamdman/sfm/main.java");
    static final SFMScreenPanelBounds BOUNDS = new SFMScreenPanelBounds(0, 0, 600, 400);
    record Fixture(SFMExplorerSession session, SFMExplorerPanel panel, AtomicReference<String> clipboard) { }
    static Fixture fixture() {
        var nodes = new ArrayList<SFMInMemoryRegistryExplorerResolver.Node>();
        var paths = List.of(ROOT, A, B, C, FILE);
        for (int i = 0; i < paths.size(); i++) {
            var path = paths.get(i); String label = path.segments().get(path.segments().size() - 1);
            var entry = new SFMExplorerEntry(path, label, i < 4, Map.of(
                    SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available(label),
                    SFMExplorerEntry.SUBJECT_KIND, SFMExplorerEntry.SortKey.available(i < 4 ? "container" : "file")), List.of(label), List.of());
            nodes.add(new SFMInMemoryRegistryExplorerResolver.Node(entry, i < 4 ? List.of(paths.get(i + 1)) : List.of()));
        }
        var registry = new SFMExplorerResolverRegistry();
        registry.register(new SFMInMemoryRegistryExplorerResolver(nodes, Runnable::run, 128));
        var loader = new SFMLazyExplorerLoader(registry, new SFMChildRelationRepository());
        loader.openRoot(ROOT).join();
        for (var path : List.of(ROOT, A, B, C)) loader.refresh(path, 128).completion().join();
        var session = new SFMExplorerSession(new SFMExplorerId("compact-panel"), ROOT, new SFMSelectionRepository());
        var clipboard = new AtomicReference<String>();
        var panel = new SFMExplorerPanel(session, loader, ignored -> {}, () -> {}, () -> {},
                SFMExplorerPresentationRegistry.minecraftDefaults(), clipboard::set);
        panel.resized(null, BOUNDS);
        return new Fixture(session, panel, clipboard);
    }
    @Test void localUnmergeAndResetPreserveHiddenDisjointSelectionsAndOtherPanels() {
        var one = fixture(); var two = fixture();
        one.session.selectMembers(Set.of(A, FILE), Optional.of(A));
        var before = one.session.snapshot().rowSelection();
        assertEquals(List.of(A, B, C), one.panel.model().state(BOUNDS).selectedRow().orElseThrow().paths());
        one.panel.compactPaths(Kind.UNMERGE, A.canonical());
        assertEquals(3, one.panel.model().state(BOUNDS).projection().rows().size());
        assertEquals(before, one.session.snapshot().rowSelection());
        assertTrue(two.session.snapshot().settings().compaction().overrides().isEmpty());
        assertEquals(1, two.panel.model().state(BOUNDS).projection().rows().size());
        one.panel.compactPaths(Kind.RESET, A.canonical());
        assertEquals(1, one.panel.model().state(BOUNDS).projection().rows().size());
        assertEquals(before, one.session.snapshot().rowSelection());
        one.panel.resized(null, new SFMScreenPanelBounds(0, 0, 300, 240));
        assertEquals(before, one.session.snapshot().rowSelection());
    }
    @Test void clipboardPresetAndTypedReopenPreservePreferencesWithoutSharedMutation() {
        var one = fixture();
        var catalog = new SFMPanelReopenCatalog();
        catalog.register(one.panel, new SFMPanelReopenRecipe() {
            public ResourceLocation sceneTypeId() { return new ResourceLocation("sfm", "explorer"); }
            public SFMScreenPanel reopen() { return fixture().panel; }
        });
        one.panel.compactPaths(Kind.UNMERGE, A.canonical());
        one.panel.compactPaths(Kind.COPY, "");
        var reopened = (SFMExplorerPanel) catalog.reopen(one.panel).orElseThrow().panel();
        assertEquals(one.session.snapshot().settings().compaction(), reopened.sessionSnapshot().settings().compaction());
        reopened.compactPaths(Kind.APPLY, one.clipboard.get());
        reopened.compactPaths(Kind.RESET, A.canonical());
        assertFalse(one.session.snapshot().settings().compaction().overrides().isEmpty());
        assertTrue(reopened.sessionSnapshot().settings().compaction().overrides().isEmpty());
        var before = reopened.sessionSnapshot().settings();
        assertThrows(IllegalArgumentException.class, () -> reopened.compactPaths(Kind.APPLY, "{}"));
        assertEquals(before, reopened.sessionSnapshot().settings());
    }

    @Test void compactRowContextRetainsTerminalIdentityAndOffersExecutableLocalActions() {
        var one = fixture();
        var row = one.panel.model().state(BOUNDS).projection().rows().get(0);
        assertEquals("ca/teamdman/sfm", row.compactLabel());
        var inspection = SFMExplorerRowInspection.capture(one.session.snapshot(), row,
                new SFMChildRelationRepository().snapshot(), List.of(), true, "body");
        var context = new ca.teamdman.sfm.client.action.SFMClientActionContext(null, () -> true, null);
        var request = new SFMExplorerContextActionProvider.Request(context, one.session.snapshot().id(),
                row.path(), row.entry(), inspection);
        assertEquals(C, request.path());
        assertFalse(SFMExplorerContextActionRegistry.minecraftDefaults().resolve(request).isEmpty());
        var choices = one.panel.compactChoices(row);
        assertTrue(choices.stream().anyMatch(choice -> choice.command().equals(
                "sfm action invoke sfm:explorer/compact/unmerge " + A.canonical())));
        assertTrue(choices.stream().anyMatch(choice -> choice.continuation()
                && choice.actionId().equals(Kind.APPLY.id())));
        assertThrows(IllegalArgumentException.class, () -> new SFMExplorerContextActionProvider.Request(
                context, one.session.snapshot().id(), A, row.entry(), inspection));
    }
}
