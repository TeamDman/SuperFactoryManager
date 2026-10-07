package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerFinderSessionTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://test/root");
    private static final SFMPath ALPHA = SFMPath.parse("registry://test/root/alpha");
    private static final SFMPath BETA = SFMPath.parse("registry://test/root/beta");

    @Test
    public void finderPublishesTypedStateAndWrapsWithoutChangingTheProjectionFilter() {
        SFMExplorerSession session = session();
        session.setFilterQuery("keep-visible-tree");

        long generation = session.beginFinderQuery("entry");
        SFMExplorerSession.Snapshot pending = session.snapshot();
        assertEquals(SFMExplorerSession.FinderStatus.PENDING, pending.finder().status());
        assertEquals(
                List.of(SFMExplorerSession.FinderDiagnosticCode.SEARCH_PENDING),
                pending.finder().diagnostics().stream().map(SFMExplorerSession.FinderDiagnostic::code).toList()
        );
        assertEquals("keep-visible-tree", pending.settings().filterQuery());

        assertTrue(session.publishFinderResults(
                generation,
                "entry",
                List.of(BETA, ALPHA, BETA),
                true,
                List.of()
        ));
        assertEquals(List.of(ALPHA, BETA), session.snapshot().finder().matches());
        assertEquals(SFMExplorerSession.FinderStatus.READY, session.snapshot().finder().status());

        assertTrue(session.advanceFinder(1));
        assertEquals(ALPHA, session.snapshot().finder().currentMatch().orElseThrow());
        assertTrue(session.advanceFinder(1));
        assertEquals(BETA, session.snapshot().finder().currentMatch().orElseThrow());
        assertTrue(session.advanceFinder(1));
        assertEquals(ALPHA, session.snapshot().finder().currentMatch().orElseThrow(), "next must wrap");
        assertTrue(session.advanceFinder(-1));
        assertEquals(BETA, session.snapshot().finder().currentMatch().orElseThrow(), "previous must wrap");
        assertEquals("keep-visible-tree", session.snapshot().settings().filterQuery());
    }

    @Test
    public void emptyAndStaleFinderPublicationsRemainExplicit() {
        SFMExplorerSession session = session();
        long staleGeneration = session.beginFinderQuery("stale");
        long currentGeneration = session.beginFinderQuery("missing");

        assertFalse(session.publishFinderResults(
                staleGeneration,
                "stale",
                List.of(ALPHA),
                true,
                List.of()
        ));
        assertEquals("missing", session.snapshot().finder().query());
        assertEquals(SFMExplorerSession.FinderStatus.PENDING, session.snapshot().finder().status());

        assertTrue(session.publishFinderResults(
                currentGeneration,
                "missing",
                List.of(),
                true,
                List.of()
        ));
        assertEquals(SFMExplorerSession.FinderStatus.EMPTY, session.snapshot().finder().status());
        assertEquals(
                List.of(SFMExplorerSession.FinderDiagnosticCode.NO_MATCHES),
                session.snapshot().finder().diagnostics().stream()
                        .map(SFMExplorerSession.FinderDiagnostic::code)
                        .toList()
        );
        assertFalse(session.advanceFinder(1));
        assertTrue(session.clearFinder());
        assertEquals(SFMExplorerSession.FinderStatus.CLEARED, session.snapshot().finder().status());
    }

    private static SFMExplorerSession session() {
        return new SFMExplorerSession(
                new SFMExplorerId("finder"),
                ROOT,
                new SFMSelectionRepository()
        );
    }

    @Test void optionsSurviveUnrelatedSettingsAndFindNeverChangesFilterOptions() {
        var session = session();
        var literal = ca.teamdman.sfm.client.search.SFMTextMatchOptions.defaults();
        assertEquals(literal, session.snapshot().settings().filterOptions());
        var filter = literal.withCase(true).withWholeWord(true);
        session.setFilterOptions(filter);
        session.setFilterQuery("needle");
        session.setView(SFMExplorerProjection.View.SMALL_ICONS);
        session.setSort(SFMExplorerProjection.Sort.EXTENSION);
        session.setGroup(SFMExplorerProjection.Group.NONE);
        session.setHoist(SFMExplorerProjection.Hoist.SHOW_ROOTS);
        session.setPathDisplay(SFMExplorerProjection.PathDisplay.ABSOLUTE_PATH);
        assertEquals(filter, session.snapshot().settings().filterOptions());
        var find = literal.toggleFuzzy();
        long old = session.beginFinderQuery("name", find);
        long current = session.beginFinderQuery("name", literal);
        assertFalse(session.publishFinderResults(old, "name", List.of(ALPHA), true, List.of()));
        assertTrue(session.publishFinderResultsInDisplayOrder(current, "name", List.of(BETA, ALPHA), true, List.of()));
        assertTrue(session.advanceFinder(1));
        assertEquals(BETA, session.snapshot().finder().currentMatch().orElseThrow());
        assertEquals(literal, session.snapshot().finder().options());
        session.clearFinder();
        assertEquals(literal, session.snapshot().finder().options());
        assertEquals(filter, session.snapshot().settings().filterOptions());
        assertEquals("needle", session.snapshot().settings().filterQuery());
    }

    @Test void incompleteEmptyAndClosedResultsCannotClaimAbsence() {
        var session = session();
        long generation = session.beginFinderQuery("unknown");
        session.publishFinderResults(generation, "unknown", List.of(), false, List.of("work limit"));
        assertEquals(SFMExplorerSession.FinderStatus.FAILED, session.snapshot().finder().status());
        assertTrue(session.snapshot().finder().diagnostics().stream()
                .noneMatch(d -> d.code() == SFMExplorerSession.FinderDiagnosticCode.NO_MATCHES));
        session.close();
        assertFalse(session.publishFinderResults(generation, "unknown", List.of(ALPHA), true, List.of()));
        assertFalse(session.failFinderQuery(generation, "unknown", "late failure"));
    }

    @Test void changedLocationRejectsTheOldDomainWithoutLosingSearchOptions() {
        var session = session();
        var options = ca.teamdman.sfm.client.search.SFMTextMatchOptions.defaults().withCase(true);
        long generation = session.beginFinderQuery("name", options);
        var next = SFMPath.parse("registry://another/root");
        session.replaceLocation(new ca.teamdman.sfm.client.explorer.SFMPathExpression.Literal(next), java.util.Set.of(next));
        assertFalse(session.publishFinderResults(generation, "name", List.of(ALPHA), true, List.of()));
        assertEquals(options, session.snapshot().finder().options());
        assertEquals("name", session.snapshot().finder().query());
        assertEquals(SFMExplorerSession.FinderStatus.FAILED, session.snapshot().finder().status());
    }
}
