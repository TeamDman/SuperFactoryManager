package ca.teamdman.sfm.client.explorer;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMPathExpressionResolverTests {
    private static final SFMPath ROOT = SFMPath.parse("file:///C:/root");
    private static final SFMPath CHILD = SFMPath.parse("file:///C:/root/child.txt");
    private static final SFMPath OTHER_ROOT = SFMPath.parse("file:///C:/other");
    private static final SFMPath OTHER_CHILD = SFMPath.parse("file:///C:/other/other.txt");
    private static final SFMPath SELECTED = SFMPath.parse("registry://minecraft/item/minecraft/stick");

    @Test
    public void membersAndChildRangesUseOneCapturedGenerationEach() {
        SFMSelectionRepository selections = selections();
        SFMSelectionRepository.MutationResult selection = selections.create(
                Optional.of("review"),
                Set.of(SELECTED),
                "test",
                "create-review"
        );
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        SFMChildRelationRepository.RefreshTicket refresh = relations.beginRefresh(Set.of(ROOT), 7);
        relations.publish(refresh, List.of(complete(ROOT, CHILD, 7)));

        SFMPathExpression expression = SFMPathExpression.parse(
                "union(members(name(review)),children(file:///C:/root))"
        );
        SFMPathExpressionResolution resolved = SFMPathExpressionResolver.resolve(
                expression,
                selections,
                relations
        );

        assertTrue(resolved.complete());
        assertEquals(Set.of(SELECTED, CHILD), resolved.paths());
        assertEquals(selections.stateSnapshot().generation(), resolved.selectionGeneration());
        assertEquals(relations.snapshot().relation().id(), resolved.relationRevisionId());
        assertEquals(
                java.util.Map.of(selection.selection().id(), selection.revision().id()),
                resolved.capturedSelectionHeads()
        );
    }

    @Test
    public void selectionChildrenDistinguishLiveAndPinnedMeaning() {
        SFMSelectionRepository selections = selections();
        SFMSelectionRepository.MutationResult created = selections.create(
                new SFMSelectionId("history"),
                Optional.empty(),
                Set.of(ROOT),
                "test",
                "create-history"
        );
        SFMPath pinned = selections.pinnedPath(created.selection().id(), created.revision().id());
        selections.add(created.selection().id(), Set.of(OTHER_ROOT), "test", "add-other");

        SFMPathExpressionResolution live = SFMPathExpressionResolver.resolve(
                SFMPathExpression.parse("children(selection://history)"),
                selections,
                new SFMChildRelationRepository()
        );
        SFMPathExpressionResolution historical = SFMPathExpressionResolver.resolve(
                SFMPathExpression.parse("children(" + pinned.canonical() + ")"),
                selections,
                new SFMChildRelationRepository()
        );

        assertEquals(Set.of(ROOT, OTHER_ROOT), live.paths());
        assertEquals(Set.of(ROOT), historical.paths());
        assertTrue(live.complete());
        assertTrue(historical.complete());
    }

    @Test
    public void unmaterializedAndPagedChildrenAreExplicitlyPartial() {
        SFMSelectionRepository selections = selections();
        SFMChildRelationRepository relations = new SFMChildRelationRepository();

        SFMPathExpressionResolution absent = SFMPathExpressionResolver.resolve(
                SFMPathExpression.parse("children(file:///C:/root)"),
                selections,
                relations
        );
        assertEquals(SFMPathExpressionResolution.Completeness.PARTIAL, absent.completeness());
        assertTrue(absent.paths().isEmpty());
        assertTrue(hasDiagnostic(absent, "relation.children-unmaterialized"));

        SFMChildRelationRepository.RefreshTicket refresh = relations.beginRefresh(Set.of(ROOT), 8);
        relations.publish(refresh, List.of(new SFMChildPage(
                ROOT,
                List.of(new SFMChildEdge(ROOT, CHILD)),
                Optional.of("page-2"),
                SFMChildPage.Completeness.PARTIAL,
                8,
                List.of()
        )));
        SFMPathExpressionResolution paged = SFMPathExpressionResolver.resolve(
                SFMPathExpression.parse("children(file:///C:/root)"),
                selections,
                relations
        );
        assertEquals(SFMPathExpressionResolution.Completeness.PARTIAL, paged.completeness());
        assertEquals(Set.of(CHILD), paged.paths());
        assertTrue(hasDiagnostic(paged, "relation.children-partial"));
    }

    @Test
    public void childRelationRetainsParentOwnershipDuringSetEvaluation() {
        SFMSelectionRepository selections = selections();
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        SFMChildRelationRepository.RefreshTicket refresh = relations.beginRefresh(
                Set.of(ROOT, OTHER_ROOT),
                4
        );
        relations.publish(refresh, List.of(
                complete(ROOT, CHILD, 4),
                complete(OTHER_ROOT, OTHER_CHILD, 4)
        ));

        SFMPathExpressionResolution onlyRoot = SFMPathExpressionResolver.resolve(
                SFMPathExpression.parse(
                        "difference(children(union(file:///C:/root,file:///C:/other)),"
                                + "children(file:///C:/other))"
                ),
                selections,
                relations
        );
        assertEquals(Set.of(CHILD), onlyRoot.paths());
        assertTrue(onlyRoot.complete());
        assertFalse(onlyRoot.paths().contains(OTHER_CHILD));
    }

    private static boolean hasDiagnostic(SFMPathExpressionResolution resolution, String code) {
        return resolution.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals(code));
    }

    private static SFMChildPage complete(
            SFMPath parent,
            SFMPath child,
            long resolverGeneration
    ) {
        return new SFMChildPage(
                parent,
                List.of(new SFMChildEdge(parent, child)),
                Optional.empty(),
                SFMChildPage.Completeness.COMPLETE,
                resolverGeneration,
                List.of()
        );
    }

    private static SFMSelectionRepository selections() {
        return new SFMSelectionRepository(Clock.fixed(
                Instant.parse("2026-08-12T00:00:00Z"),
                ZoneOffset.UTC
        ));
    }
}
