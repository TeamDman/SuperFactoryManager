package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMChildEdge;
import ca.teamdman.sfm.client.explorer.SFMChildPage;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRevision;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMExplorerRowInspectionTests {
    private static final SFMPath ROOT = SFMPath.parse("review://candidate/changes/");
    private static final SFMPath ROW = SFMPath.parse("review://candidate/changes/SFM.java");
    private static final SFMPath CHILD_ONE = SFMPath.parse("review://candidate/changes/SFM.java/after");
    private static final SFMPath CHILD_TWO = SFMPath.parse("review://candidate/changes/SFM.java/diff");

    @Test
    void filteredQueryPagesRemainInspectableWithSyntheticRequestZero() {
        var entry = SFMExplorerEntry.simple(ROW, "SFM.java", true, Optional.empty());
        var row = new SFMExplorerProjection.Row(ROW, entry, 1, false, true,
                entry.sortKey(SFMExplorerEntry.SORT_NAME), SFMExplorerProjection.FilterRole.MATCH,
                SFMExplorerProjection.RowKind.ENTRY);
        var page = new SFMChildRelationRepository.PageState(1, Optional.empty(),
                SFMChildPage.Completeness.COMPLETE,
                SFMChildRelationRepository.PageState.Materialization.MATERIALIZED, List.of(), 0);
        var relations = new SFMChildRelationRepository.Snapshot(
                new SFMChildRelationRevision(1, Set.of(new SFMChildEdge(ROW, CHILD_ONE))), 1, Map.of(ROW, page));
        var details = SFMExplorerRowInspection.capture(snapshot(SFMExplorerProjection.Settings.defaults()),
                row, relations, List.of(), true, "body").detailsPayload();
        assertTrue(details.contains("page-last-request-id: 0"));
        assertTrue(details.contains("page-completeness: complete"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                new SFMExplorerRowInspection.PageEvidence(1, Optional.empty(), SFMChildPage.Completeness.COMPLETE,
                        SFMChildRelationRepository.PageState.Materialization.MATERIALIZED, -1, List.of()));
    }

    @Test
    void payloadDeterministicallyCoversRowSettingsPageLoadingSelectionAndFocusEvidence() {
        SFMExplorerRowInspection inspection = inspection();

        String expected = String.join("\n",
                "schema: sfm.explorer-row-details/1",
                "explorer-id: \"release review\"",
                "row-address: \"" + ROW.canonical() + "\"",
                "row-label: \"SFM \\\"Main\\\"\\n.java\"",
                "row-kind: entry",
                "filter-role: match",
                "row-depth: 2",
                "row-root: false",
                "row-expandable: true",
                "row-expanded: true",
                "row-loading: false",
                "row-selected: true",
                "explorer-focused: true",
                "keyboard-focus: \"body\"",
                "query: \".java\"",
                "filter.match-mode: fuzzy",
                "filter.match-case: false",
                "filter.whole-word: false",
                "filter.dot-all: false",
                "view: small-icons",
                "sort: extension",
                "group: none",
                "hoist: show-roots",
                "path-display: absolute-path",
                "compact.enabled: true",
                "compact.override-count: 0",
                "compact.segment-count: 1",
                "compact.segment[0].path: \"" + ROW.canonical() + "\"",
                "compact.segment[0].override: default",
                "relation-revision: 12",
                "relation-status-generation: 13",
                "published-child-count: 2",
                "page-available: true",
                "page-resolver-generation: 7",
                "page-materialization: materialized",
                "page-completeness: partial",
                "page-continuation: \"next page\"",
                "page-last-request-id: 41",
                "page-diagnostic-count: 1",
                "page-diagnostic[0]: \"page warning\"",
                "loading-active-request-count: 2",
                "loading-request[0].id: 51",
                "loading-request[0].mode: replace",
                "loading-request[0].parent: \"" + ROOT.canonical() + "\"",
                "loading-request[0].resolver-scheme: \"review\"",
                "loading-request[0].resolver-generation: 7",
                "loading-request[0].continuation: none",
                "loading-request[0].page-size: 32",
                "loading-request[0].targets-row: false",
                "loading-request[1].id: 52",
                "loading-request[1].mode: append",
                "loading-request[1].parent: \"" + ROW.canonical() + "\"",
                "loading-request[1].resolver-scheme: \"review\"",
                "loading-request[1].resolver-generation: 7",
                "loading-request[1].continuation: \"next page\"",
                "loading-request[1].page-size: 64",
                "loading-request[1].targets-row: true",
                "entry-diagnostic-count: 1",
                "entry-diagnostic[0]: \"resolver warning\""
        );

        assertEquals(expected, inspection.detailsPayload());
        assertEquals(expected, inspection.detailsPayload(), "repeated serialization must not drift");
    }

    @Test
    void unavailablePageAndLoadingEvidenceAreExplicitRatherThanGuessed() {
        SFMExplorerEntry entry = SFMExplorerEntry.simple(ROW, "SFM.java", false, Optional.empty());
        SFMExplorerProjection.Row row = new SFMExplorerProjection.Row(
                ROW,
                entry,
                0,
                false,
                false,
                entry.sortKey(SFMExplorerEntry.SORT_NAME),
                SFMExplorerProjection.FilterRole.NONE,
                SFMExplorerProjection.RowKind.LOADING
        );
        SFMExplorerRowInspection inspection = SFMExplorerRowInspection.capture(
                snapshot(SFMExplorerProjection.Settings.defaults()),
                row,
                new SFMChildRelationRepository().snapshot(),
                List.of(),
                false,
                "filter"
        );

        assertTrue(inspection.detailsPayload().contains("row-loading: true"));
        assertTrue(inspection.detailsPayload().contains("page-available: false"));
        assertTrue(inspection.detailsPayload().contains("loading-active-request-count: 0"));
        assertTrue(inspection.detailsPayload().contains("explorer-focused: false"));
    }

    static SFMExplorerRowInspection inspection() {
        String label = "SFM \"Main\"\n.java";
        SFMExplorerEntry simple = SFMExplorerEntry.simple(ROW, label, true, Optional.of("minecraft:cocoa_beans"));
        SFMExplorerEntry entry = new SFMExplorerEntry(
                ROW,
                label,
                true,
                simple.sortKeys(),
                simple.searchTerms(),
                List.of("resolver warning")
        );
        SFMExplorerProjection.Settings settings = new SFMExplorerProjection.Settings(
                SFMExplorerProjection.View.SMALL_ICONS,
                SFMExplorerProjection.Sort.EXTENSION,
                SFMExplorerProjection.Group.NONE,
                SFMExplorerProjection.Hoist.SHOW_ROOTS,
                SFMExplorerProjection.PathDisplay.ABSOLUTE_PATH,
                ".java"
        );
        SFMExplorerProjection.Row row = new SFMExplorerProjection.Row(
                ROW,
                entry,
                2,
                false,
                true,
                entry.sortKey(SFMExplorerEntry.SORT_EXTENSION),
                SFMExplorerProjection.FilterRole.MATCH,
                SFMExplorerProjection.RowKind.ENTRY
        );
        SFMChildRelationRepository.PageState page = new SFMChildRelationRepository.PageState(
                7,
                Optional.of("next page"),
                SFMChildPage.Completeness.PARTIAL,
                SFMChildRelationRepository.PageState.Materialization.MATERIALIZED,
                List.of("page warning"),
                41
        );
        SFMChildRelationRepository.Snapshot relations = new SFMChildRelationRepository.Snapshot(
                new SFMChildRelationRevision(12, Set.of(
                        new SFMChildEdge(ROW, CHILD_ONE),
                        new SFMChildEdge(ROW, CHILD_TWO)
                )),
                13,
                Map.of(ROW, page)
        );
        List<SFMExplorerSession.RequestObservation> activeRequests = List.of(
                activeRequest(52, SFMChildRelationRepository.RequestMode.APPEND, ROW, Optional.of("next page"), 64),
                activeRequest(51, SFMChildRelationRepository.RequestMode.REPLACE, ROOT, Optional.empty(), 32)
        );
        return SFMExplorerRowInspection.capture(
                snapshot(settings),
                row,
                relations,
                activeRequests,
                true,
                "body"
        );
    }

    private static SFMExplorerSession.Snapshot snapshot(SFMExplorerProjection.Settings settings) {
        return new SFMExplorerSession.Snapshot(
                new SFMExplorerId("release review"),
                9,
                new SFMPathExpression.Literal(ROOT),
                Set.of(ROOT),
                List.of(ROOT),
                Set.of(ROW),
                Optional.of(ROW),
                4,
                settings,
                Set.of(),
                Optional.empty(),
                false
        );
    }

    private static SFMExplorerSession.RequestObservation activeRequest(
            long id,
            SFMChildRelationRepository.RequestMode mode,
            SFMPath parent,
            Optional<String> continuation,
            int pageSize
    ) {
        return new SFMExplorerSession.RequestObservation(
                new SFMLazyExplorerLoader.RequestEvidence(
                        id,
                        mode,
                        parent,
                        "review",
                        7,
                        continuation,
                        pageSize
                ),
                100,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }
}
