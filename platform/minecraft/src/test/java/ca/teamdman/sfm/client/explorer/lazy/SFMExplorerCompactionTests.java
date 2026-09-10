package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMExplorerCompactionTests {
    static final SFMPath ROOT = SFMPath.parse("file:///C:/project");
    static final SFMPath A = SFMPath.parse("file:///C:/project/ca");
    static final SFMPath B = SFMPath.parse("file:///C:/project/ca/teamdman");
    static final SFMPath C = SFMPath.parse("file:///C:/project/ca/teamdman/sfm");
    static final SFMPath FILE = SFMPath.parse("file:///C:/project/ca/teamdman/sfm/Main.java");
    static SFMExplorerEntry entry(SFMPath path, boolean container) {
        var keys = new HashMap<String, SFMExplorerEntry.SortKey>();
        String name = path.segments().get(path.segments().size() - 1);
        keys.put(SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available(name));
        keys.put(SFMExplorerEntry.SUBJECT_KIND, SFMExplorerEntry.SortKey.available(container ? "container" : "file"));
        return new SFMExplorerEntry(path, name, container, keys, List.of(name), List.of());
    }
    static void page(SFMChildRelationRepository relations, SFMPath parent, List<SFMPath> children, boolean complete) {
        var ticket = relations.beginRefresh(Set.of(parent), 1);
        relations.publish(ticket, List.of(new SFMChildPage(parent,
                children.stream().map(child -> new SFMChildEdge(parent, child)).toList(),
                complete ? Optional.empty() : Optional.of("next"), complete ? SFMChildPage.Completeness.COMPLETE
                : SFMChildPage.Completeness.PARTIAL, 1, List.of())));
    }
    static final class Fixture {
        final SFMChildRelationRepository relations = new SFMChildRelationRepository();
        final Map<SFMPath, SFMExplorerEntry> entries = new HashMap<>();
        final SFMExplorerSession session = new SFMExplorerSession(new SFMExplorerId("compact"), ROOT, new SFMSelectionRepository());
        Fixture() {
            for (var path : List.of(ROOT, A, B, C)) entries.put(path, entry(path, true));
            entries.put(FILE, entry(FILE, false));
            page(relations, ROOT, List.of(A), true);
            page(relations, A, List.of(B), true);
            page(relations, B, List.of(C), true);
            page(relations, C, List.of(FILE), true);
        }
        SFMExplorerProjection.Result project() { return SFMExplorerProjection.project(session.snapshot(), relations.snapshot(), entries); }
    }

    @Test void knownChainIsOneRowWithoutExpandingEachLinkAndFilesRemainSeparate() {
        var f = new Fixture();
        var row = f.project().rows().get(0);
        assertEquals("ca/teamdman/sfm", row.compactLabel());
        assertEquals(List.of(A, B, C), row.paths());
        assertEquals(C, row.path());
        assertTrue(row.contains(B));
        assertTrue(f.session.snapshot().expanded().isEmpty(), "projection never mutates expansion");
        f.session.expand(C);
        assertEquals(List.of(C, FILE), f.project().rows().stream().map(SFMExplorerProjection.Row::path).toList());
        assertEquals(1, f.project().rows().get(1).depth());
    }

    @Test void partialUnknownFailedAndUntypedParentsNeverCertifyFolding() {
        var f = new Fixture();
        page(f.relations, A, List.of(B), false);
        assertEquals(List.of(A), f.project().rows().get(0).paths());
        f.session.expand(A);
        assertEquals(List.of(B, C), f.project().rows().get(1).paths());
        f.entries.put(B, SFMExplorerEntry.simple(B, "teamdman", true, Optional.of("folder")));
        assertEquals(List.of(B), f.project().rows().get(1).paths(), "icon does not certify container kind");
        var unknown = new SFMChildRelationRepository();
        assertFalse(SFMExplorerCompaction.canJoin(A, B, SFMExplorerCompaction.Options.defaults(), Set.of(ROOT),
                unknown.snapshot(), Map.of(A, List.of(B)), f.entries));
    }

    @Test void filterDoesNotCertifyASingleChildAndRetainsExactMatchEvidence() {
        var f = new Fixture();
        var sibling = SFMPath.parse("file:///C:/project/ca/other");
        f.entries.put(sibling, entry(sibling, true));
        page(f.relations, A, List.of(B, sibling), true);
        f.session.setFilterQuery("sfm");
        var result = f.project();
        assertEquals(List.of(A), result.rows().get(0).paths());
        assertEquals(List.of(B, C), result.rows().get(1).paths());
        assertTrue(result.matchEvidence().get(C).self().matches());
        assertFalse(result.matchEvidence().get(B).self().matches());
        assertEquals(3, result.filter().visibleRowCount(), "logical matches/context remain auditable despite two rendered rows");
    }

    @Test void overridesAndAxesAreIndependentAndRootsStaySeparate() {
        var f = new Fixture();
        var second = new Fixture();
        var options = f.session.snapshot().settings().compaction().override(List.of(A), Optional.of(false));
        f.session.setSettings(f.session.snapshot().settings().withCompaction(options));
        f.session.expand(A);
        assertEquals(2, f.project().rows().size());
        assertEquals(1, second.project().rows().size());
        f.session.setFilterQuery("sfm");
        f.session.setView(SFMExplorerProjection.View.SMALL_ICONS);
        f.session.setSort(SFMExplorerProjection.Sort.NAME);
        f.session.setPathDisplay(SFMExplorerProjection.PathDisplay.RELATIVE_PATH);
        assertEquals(options, f.session.snapshot().settings().compaction());
        f.session.setSettings(f.session.snapshot().settings().withCompaction(options.override(List.of(A), Optional.empty())));
        assertEquals(1, f.project().rows().size());
        f.session.setFilterQuery("");
        f.session.setHoist(SFMExplorerProjection.Hoist.SHOW_ROOTS);
        assertEquals(List.of(ROOT), f.project().rows().get(0).paths());
        f.session.expand(ROOT);
        assertEquals(List.of(A, B, C), f.project().rows().get(1).paths());
        f.session.setGroup(SFMExplorerProjection.Group.NONE);
        assertTrue(f.project().rows().stream().allMatch(row -> row.segments().size() == 1));
    }

    @Test void differentAuthoritiesRevisionsAndCyclesAreNotFolded() {
        var f = new Fixture();
        var other = SFMPath.parse("registry://minecraft/item/chest");
        f.entries.put(other, entry(other, true));
        page(f.relations, A, List.of(other), true);
        assertEquals(List.of(A), f.project().rows().get(0).paths());
        page(f.relations, A, List.of(B), true);
        page(f.relations, B, List.of(A), true);
        f.session.expand(A);
        assertFalse(f.project().diagnostics().isEmpty());
        assertEquals(1, f.project().rows().size());
    }

    @Test void completeFilterPagesNeedSeparateActualTopologyAttestation() {
        var f = new Fixture();
        var page = new SFMChildRelationRepository.PageState(1, Optional.empty(), SFMChildPage.Completeness.COMPLETE,
                SFMChildRelationRepository.PageState.Materialization.MATERIALIZED, List.of(), 0);
        var filtered = new SFMChildRelationRepository.Snapshot(f.relations.snapshot().relation(), 1, Map.of(A, page));
        var children = Map.of(A, List.of(B));
        assertFalse(SFMExplorerCompaction.canJoin(A, B, SFMExplorerCompaction.Options.defaults(), Set.of(ROOT), filtered, children, f.entries));
        for (String count : List.of("2", "1")) {
            var original = f.entries.get(A); var keys = new HashMap<>(original.sortKeys());
            keys.put(SFMExplorerEntry.SUBJECT_COMPLETE_CHILD_COUNT, SFMExplorerEntry.SortKey.available(count));
            f.entries.put(A, new SFMExplorerEntry(A, original.label(), true, keys, original.searchTerms(), List.of()));
            assertEquals(count.equals("1"), SFMExplorerCompaction.canJoin(A, B, SFMExplorerCompaction.Options.defaults(), Set.of(ROOT), filtered, children, f.entries));
        }
    }
}
