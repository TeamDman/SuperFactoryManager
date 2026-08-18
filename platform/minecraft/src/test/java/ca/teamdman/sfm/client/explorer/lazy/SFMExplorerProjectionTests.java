package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildEdge;
import ca.teamdman.sfm.client.explorer.SFMChildPage;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerProjectionTests {
    private static final SFMPath FILE_ROOT = SFMPath.parse("file:///C:/project");
    private static final SFMPath REGISTRY_ROOT = SFMPath.parse("registry://minecraft/item/");
    private static final SFMPath ALPHA = SFMPath.parse("file:///C:/project/alpha.txt");
    private static final SFMPath README = SFMPath.parse("file:///C:/project/README");
    private static final SFMPath ZETA = SFMPath.parse("file:///C:/project/zeta.java");
    private static final SFMPath DIRECTORY = SFMPath.parse("file:///C:/project/directory");
    private static final SFMPath NESTED = SFMPath.parse("file:///C:/project/directory/nested.md");
    private static final SFMPath FRESH = SFMPath.parse("file:///C:/project/directory/fresh-result.txt");

    @Test
    public void unmaterializedAutoHoistKeepsTheSingleRootExpandableUntilItsPagePublishes() {
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        Map<SFMPath, SFMExplorerEntry> entries = entries(
                entry(FILE_ROOT, "project", true, Optional.of("folder")),
                entry(ALPHA, "alpha.txt", false, Optional.of("text"))
        );
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("initial-root"),
                FILE_ROOT,
                new SFMSelectionRepository()
        );

        SFMExplorerProjection.Result before = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );
        assertEquals(List.of(FILE_ROOT), paths(before));
        assertTrue(before.rows().get(0).root());

        publish(relations, FILE_ROOT, List.of(ALPHA), 1);
        SFMExplorerProjection.Result after = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );
        assertEquals(List.of(ALPHA), paths(after));
        assertFalse(after.rows().get(0).root());
    }

    @Test
    public void autoHoistsOneRootAndShowsTwoHeterogeneousRootsInManualOrder() {
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        publish(relations, FILE_ROOT, List.of(ALPHA, ZETA), 1);
        Map<SFMPath, SFMExplorerEntry> entries = entries(
                entry(FILE_ROOT, "project", true, Optional.of("folder")),
                entry(ALPHA, "alpha.txt", false, Optional.of("text")),
                entry(ZETA, "zeta.java", false, Optional.of("code")),
                entry(REGISTRY_ROOT, "Items", true, Optional.of("registry"))
        );
        SFMSelectionRepository selections = new SFMSelectionRepository();
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("hoist"),
                FILE_ROOT,
                selections
        );

        SFMExplorerProjection.Result oneRoot = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );
        assertEquals(List.of(ALPHA, ZETA), paths(oneRoot));
        assertTrue(oneRoot.rows().stream().noneMatch(SFMExplorerProjection.Row::root));

        session.setHoist(SFMExplorerProjection.Hoist.SHOW_ROOTS);
        SFMExplorerProjection.Result explicitRoot = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );
        assertEquals(List.of(FILE_ROOT), paths(explicitRoot));
        assertTrue(explicitRoot.rows().get(0).root());

        session.setHoist(SFMExplorerProjection.Hoist.AUTO);
        session.addRoot(REGISTRY_ROOT);
        session.setManualRootOrder(List.of(REGISTRY_ROOT, FILE_ROOT));
        SFMExplorerProjection.Result twoRoots = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );
        assertEquals(List.of(REGISTRY_ROOT, FILE_ROOT), paths(twoRoots));
        assertTrue(twoRoots.rows().stream().allMatch(SFMExplorerProjection.Row::root));
        assertEquals(Set.of(FILE_ROOT, REGISTRY_ROOT), session.snapshot().roots());
        assertEquals(List.of(REGISTRY_ROOT, FILE_ROOT), session.snapshot().manualRootOrder());
    }

    @Test
    public void nameExtensionAndIconSortsAreDeterministicAndReportUnavailableKeys() {
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        publish(relations, FILE_ROOT, List.of(ZETA, README, ALPHA), 1);
        Map<SFMPath, SFMExplorerEntry> entries = entries(
                entry(FILE_ROOT, "project", true, Optional.of("folder")),
                entry(ALPHA, "alpha.txt", false, Optional.of("text")),
                entry(README, "README", false, Optional.empty()),
                entry(ZETA, "zeta.java", false, Optional.of("code"))
        );
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("sorting"),
                FILE_ROOT,
                new SFMSelectionRepository()
        );

        assertEquals(
                List.of(ALPHA, README, ZETA),
                paths(SFMExplorerProjection.project(session.snapshot(), relations.snapshot(), entries))
        );

        session.setSort(SFMExplorerProjection.Sort.EXTENSION);
        SFMExplorerProjection.Result extension = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );
        assertEquals(List.of(ZETA, ALPHA, README), paths(extension));
        assertFalse(extension.rows().get(2).activeSortKey().available());
        assertTrue(extension.diagnostics().stream().anyMatch(message ->
                message.equals(README.canonical() + ": path has no extension")
        ));

        session.setSort(SFMExplorerProjection.Sort.ICON);
        session.setView(SFMExplorerProjection.View.SMALL_ICONS);
        SFMExplorerProjection.Result icon = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );
        assertEquals(List.of(ZETA, ALPHA, README), paths(icon));
        assertEquals(SFMExplorerProjection.View.SMALL_ICONS, icon.settings().view());
        assertFalse(icon.rows().get(2).activeSortKey().available());
        assertTrue(icon.rows().get(2).activeSortKey().unavailableReason().orElseThrow()
                           .contains("icon key"));
    }

    @Test
    public void hierarchyUsesPublishedRelationsWhileNoneFlattensMaterializedDescendants() {
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        publish(relations, FILE_ROOT, List.of(ZETA, DIRECTORY), 1);
        publish(relations, DIRECTORY, List.of(NESTED), 1);
        Map<SFMPath, SFMExplorerEntry> entries = entries(
                entry(FILE_ROOT, "project", true, Optional.of("folder")),
                entry(DIRECTORY, "directory", true, Optional.of("folder")),
                entry(NESTED, "nested.md", false, Optional.of("text")),
                entry(ZETA, "zeta.java", false, Optional.of("code"))
        );
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("grouping"),
                FILE_ROOT,
                new SFMSelectionRepository()
        );
        session.expand(DIRECTORY);

        SFMExplorerProjection.Result hierarchy = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );
        assertEquals(List.of(DIRECTORY, NESTED, ZETA), paths(hierarchy));
        assertEquals(List.of(0, 1, 0), hierarchy.rows().stream().map(SFMExplorerProjection.Row::depth).toList());

        session.setGroup(SFMExplorerProjection.Group.NONE);
        SFMExplorerProjection.Result flat = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );
        assertEquals(List.of(DIRECTORY, NESTED, ZETA), paths(flat));
        assertTrue(flat.rows().stream().allMatch(row -> row.depth() == 0));

        SFMChildRelationRepository.Snapshot before = relations.snapshot();
        session.navigateTo(NESTED);
        session.setScrollOffset(12);
        SFMExplorerProjection.project(session.snapshot(), relations.snapshot(), entries);
        assertEquals(before, relations.snapshot(), "projection and navigation are read-only over semantic relations");
    }

    @Test
    public void fuzzyFilterRanksOnlyPublishedMaterializationAndIncludesNewlyPublishedRows() {
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        publish(relations, FILE_ROOT, List.of(ZETA, DIRECTORY), 1);
        publish(relations, DIRECTORY, List.of(NESTED), 1);
        Map<SFMPath, SFMExplorerEntry> entries = entries(
                entry(FILE_ROOT, "project", true, Optional.of("folder")),
                entry(DIRECTORY, "directory", true, Optional.of("folder")),
                entry(NESTED, "nested.md", false, Optional.of("text")),
                entry(FRESH, "fresh-result.txt", false, Optional.of("text")),
                entry(ZETA, "zeta.java", false, Optional.of("code"))
        );
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("filter"), FILE_ROOT, new SFMSelectionRepository()
        );
        assertFalse(session.snapshot().expanded().contains(DIRECTORY));

        session.setFilterQuery("nstd");
        SFMExplorerProjection.Result typo = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );
        assertEquals(
                List.of(DIRECTORY, NESTED),
                paths(typo),
                "filtering force-reveals the materialized ancestry of a published descendant"
        );
        assertTrue(typo.rows().get(0).filterContextAncestor());
        assertTrue(typo.rows().get(0).expanded(), "the filtered projection, not session state, opens context");
        assertTrue(typo.rows().get(1).filterMatch());
        assertEquals(List.of(0, 1), typo.rows().stream().map(SFMExplorerProjection.Row::depth).toList());
        assertEquals("nstd", typo.filter().query());
        assertEquals(3, typo.filter().candidateCount());
        assertEquals(1, typo.filter().matchCount());
        assertEquals(2, typo.filter().visibleRowCount());
        assertEquals(1, typo.filter().contextAncestorCount());
        assertFalse(typo.filter().incompleteMaterialization());
        assertFalse(session.snapshot().expanded().contains(DIRECTORY), "filtering must not mutate expansion");

        publish(relations, DIRECTORY, List.of(NESTED, FRESH), 2);
        session.setFilterQuery("frslt");
        SFMExplorerProjection.Result arriving = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );
        assertEquals(
                List.of(DIRECTORY, FRESH),
                paths(arriving),
                "newly published materialization joins the next hierarchy projection"
        );
        assertEquals(4, arriving.filter().candidateCount());
        assertEquals(1, arriving.filter().matchCount());
        assertEquals(2, arriving.filter().visibleRowCount());
        assertEquals(1, arriving.filter().contextAncestorCount());
    }

    @Test
    public void filteredHierarchyMergesSharedAncestryAndMarksMatchingAncestorsHonestly() {
        SFMPath source = SFMPath.parse("registry://test/source");
        SFMPath java = SFMPath.parse("registry://test/source/java");
        SFMPath packagePath = SFMPath.parse("registry://test/source/java/package");
        SFMPath exact = SFMPath.parse("registry://test/source/java/package/qzxv-needle-7391.java");
        SFMPath sibling = SFMPath.parse("registry://test/source/java/package/qzxv-other-needle-7391-test.java");
        SFMPath unrelated = SFMPath.parse("registry://test/source/resources");
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        publish(relations, source, List.of(java, unrelated), 1);
        publish(relations, java, List.of(packagePath), 1);
        publish(relations, packagePath, List.of(exact, sibling), 1);
        Map<SFMPath, SFMExplorerEntry> entries = entries(
                entry(source, "source", true, Optional.of("folder")),
                entry(java, "java", true, Optional.of("folder")),
                entry(packagePath, "package", true, Optional.of("folder")),
                entry(exact, "qzxv-needle-7391.java", false, Optional.of("code")),
                entry(sibling, "qzxv-other-needle-7391-test.java", false, Optional.of("code")),
                entry(unrelated, "resources", true, Optional.of("folder"))
        );
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("filter-shared-ancestry"), source, new SFMSelectionRepository()
        );
        session.setFilterQuery("qzxv-needle-7391");

        SFMExplorerProjection.Result result = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );

        assertEquals(List.of(java, packagePath, exact, sibling), paths(result),
                "siblings remain deterministically ordered by fuzzy score then canonical path");
        assertEquals(List.of(0, 1, 2, 2), result.rows().stream()
                .map(SFMExplorerProjection.Row::depth).toList());
        assertEquals(List.of(
                SFMExplorerProjection.FilterRole.CONTEXT_ANCESTOR,
                SFMExplorerProjection.FilterRole.CONTEXT_ANCESTOR,
                SFMExplorerProjection.FilterRole.MATCH,
                SFMExplorerProjection.FilterRole.MATCH
        ), result.rows().stream().map(SFMExplorerProjection.Row::filterRole).toList());
        assertEquals(2, result.filter().matchCount());
        assertEquals(4, result.filter().visibleRowCount());
        assertEquals(2, result.filter().contextAncestorCount());
        assertEquals(5, result.filter().candidateCount());
        assertEquals(Set.of(), session.snapshot().expanded(), "forced ancestry must remain projection-only");

        session.setFilterQuery("package");
        SFMExplorerProjection.Result matchingAncestor = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );
        assertEquals(List.of(java, packagePath, sibling, exact), paths(matchingAncestor));
        assertTrue(matchingAncestor.rows().get(1).filterMatch(),
                "an ancestor that independently matches is a match, not context");
        assertEquals(
                matchingAncestor.rows().stream().filter(SFMExplorerProjection.Row::filterMatch).count(),
                matchingAncestor.filter().matchCount()
        );
        assertEquals(
                matchingAncestor.rows().stream().filter(SFMExplorerProjection.Row::filterContextAncestor).count(),
                matchingAncestor.filter().contextAncestorCount()
        );
    }

    @Test
    public void filteredSubtreesUseBestDescendantScoreAcrossMultipleRoots() {
        SFMPath rootA = SFMPath.parse("registry://test/a");
        SFMPath rootB = SFMPath.parse("registry://test/b");
        SFMPath fuzzy = SFMPath.parse("registry://test/a/nxxeexxdxxlxxe");
        SFMPath exact = SFMPath.parse("registry://test/b/needle");
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        publish(relations, rootA, List.of(fuzzy), 1);
        publish(relations, rootB, List.of(exact), 1);
        Map<SFMPath, SFMExplorerEntry> entries = entries(
                entry(rootA, "root-a", true, Optional.of("folder")),
                entry(rootB, "root-b", true, Optional.of("folder")),
                entry(fuzzy, "nxxeexxdxxlxxe", false, Optional.of("text")),
                entry(exact, "needle", false, Optional.of("text"))
        );
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("filter-multiple-roots"), rootA, new SFMSelectionRepository()
        );
        session.addRoot(rootB);
        session.setManualRootOrder(List.of(rootA, rootB));
        session.setFilterQuery("needle");

        SFMExplorerProjection.Result result = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );

        assertEquals(List.of(rootB, exact, rootA, fuzzy), paths(result),
                "exact descendant score outranks manual root order; canonical paths break ties");
        assertEquals(List.of(0, 1, 0, 1), result.rows().stream()
                .map(SFMExplorerProjection.Row::depth).toList());
        assertEquals(2, result.filter().matchCount());
        assertEquals(2, result.filter().contextAncestorCount());
    }

    @Test
    public void flatFilteredProjectionComposesWithViewPathAxesAndSuppressesCycles() {
        SFMPath leaf = SFMPath.parse("file:///C:/project/directory/qzxv-target-7391.java");
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        publish(relations, FILE_ROOT, List.of(DIRECTORY), 1);
        publish(relations, DIRECTORY, List.of(FILE_ROOT, leaf), 1);
        Map<SFMPath, SFMExplorerEntry> entries = entries(
                entry(FILE_ROOT, "project", true, Optional.of("folder")),
                entry(DIRECTORY, "directory", true, Optional.of("folder")),
                entry(leaf, "qzxv-target-7391.java", false, Optional.of("code"))
        );
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("flat-filter-cycle"), FILE_ROOT, new SFMSelectionRepository()
        );
        session.setGroup(SFMExplorerProjection.Group.NONE);
        session.setView(SFMExplorerProjection.View.SMALL_ICONS);
        session.setPathDisplay(SFMExplorerProjection.PathDisplay.ABSOLUTE_PATH);
        session.setFilterQuery("qzxv-target-7391");

        SFMExplorerProjection.Result result = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );

        assertEquals(List.of(leaf), paths(result));
        assertEquals(0, result.rows().get(0).depth());
        assertTrue(result.rows().get(0).filterMatch());
        assertEquals(0, result.filter().contextAncestorCount());
        assertEquals(SFMExplorerProjection.View.SMALL_ICONS, result.settings().view());
        assertEquals(SFMExplorerProjection.PathDisplay.ABSOLUTE_PATH, result.settings().pathDisplay());
        assertTrue(result.diagnostics().stream().anyMatch(message -> message.contains("cycle suppressed")));
    }

    @Test
    public void fuzzyFilterReportsPartialLazyScopeInsteadOfClaimingExhaustiveSearch() {
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        SFMChildRelationRepository.RefreshTicket ticket = relations.beginRefresh(Set.of(FILE_ROOT), 1);
        relations.publish(ticket, List.of(new SFMChildPage(
                FILE_ROOT,
                List.of(new SFMChildEdge(FILE_ROOT, ALPHA)),
                Optional.of("next-page"),
                SFMChildPage.Completeness.PARTIAL,
                1,
                List.of()
        )));
        Map<SFMPath, SFMExplorerEntry> entries = entries(
                entry(FILE_ROOT, "project", true, Optional.of("folder")),
                entry(ALPHA, "alpha.txt", false, Optional.of("text"))
        );
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("partial-filter"), FILE_ROOT, new SFMSelectionRepository()
        );
        session.setFilterQuery("alpha");

        SFMExplorerProjection.Result result = SFMExplorerProjection.project(
                session.snapshot(), relations.snapshot(), entries
        );

        assertEquals(List.of(ALPHA), paths(result));
        assertTrue(result.filter().incompleteMaterialization());
        assertEquals(1, result.filter().candidateCount());
    }

    private static void publish(
            SFMChildRelationRepository relations,
            SFMPath parent,
            List<SFMPath> children,
            long generation
    ) {
        SFMChildRelationRepository.RefreshTicket ticket = relations.beginRefresh(Set.of(parent), generation);
        relations.publish(ticket, List.of(new SFMChildPage(
                parent,
                children.stream().map(child -> new SFMChildEdge(parent, child)).toList(),
                Optional.empty(),
                SFMChildPage.Completeness.COMPLETE,
                generation,
                List.of()
        )));
    }

    private static SFMExplorerEntry entry(
            SFMPath path,
            String label,
            boolean expandable,
            Optional<String> icon
    ) {
        return SFMExplorerEntry.simple(path, label, expandable, icon);
    }

    private static Map<SFMPath, SFMExplorerEntry> entries(SFMExplorerEntry... entries) {
        TreeMap<SFMPath, SFMExplorerEntry> answer = new TreeMap<>();
        for (SFMExplorerEntry entry : entries) answer.put(entry.path(), entry);
        return Map.copyOf(answer);
    }

    private static List<SFMPath> paths(SFMExplorerProjection.Result result) {
        return result.rows().stream().map(SFMExplorerProjection.Row::path).toList();
    }
}
