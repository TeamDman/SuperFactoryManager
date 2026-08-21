package ca.teamdman.sfm.client.history;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionId;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMSelectionHistoryGraphProjectionTests {
    @Test
    void projectionUsesTheRepositoryTreeWithoutASecondRedoTopology() {
        SFMSelectionRepository repository = new SFMSelectionRepository(
                Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC)
        );
        var created = repository.create(
                Optional.of("branch"),
                Set.of(SFMPath.parse("file:///C:/a.txt")),
                "fixture",
                "create"
        );
        SFMSelectionId id = created.selection().id();
        var old = repository.add(id, Set.of(SFMPath.parse("file:///C:/b.txt")),
                "fixture", "old");
        repository.undo(id, "fixture", "undo");
        var next = repository.add(id, Set.of(SFMPath.parse("file:///C:/c.txt")),
                "fixture", "new");
        repository.nameHead(id, "old-route", old.revision().id(), "fixture", "name-old");
        repository.checkout(id, created.revision().id(), "fixture", "checkout-root");

        SFMSelectionHistoryActions.Result enumerate = SFMSelectionHistoryActions.apply(
                repository,
                new SFMSelectionHistoryActions.Request(
                        SFMSelectionHistoryActions.Operation.ENUMERATE,
                        id,
                        Optional.empty(),
                        Optional.empty(),
                        "fixture",
                        "enumerate"
                )
        );
        SFMSelectionHistoryActions.Result redo = SFMSelectionHistoryActions.apply(
                repository,
                new SFMSelectionHistoryActions.Request(
                        SFMSelectionHistoryActions.Operation.REDO,
                        id,
                        Optional.empty(),
                        Optional.empty(),
                        "fixture",
                        "ambiguous-redo"
                )
        );

        SFMHistoryGraphContract.Graph graph = SFMSelectionHistoryGraphProjection.project(
                repository.exportArchive(), id
        );

        String root = "selection:" + id.value() + ":revision:" + created.revision().id();
        assertEquals(List.of(
                        "selection:" + id.value() + ":revision:" + old.revision().id(),
                        "selection:" + id.value() + ":revision:" + next.revision().id()
                ),
                graph.edges().stream()
                        .filter(edge -> edge.parentStateRevisionId().equals(root))
                        .map(SFMHistoryGraphContract.BranchEdge::childStateRevisionId)
                        .sorted()
                        .toList());
        assertTrue(graph.edges().stream().allMatch(SFMHistoryGraphContract.BranchEdge::committed));
        assertTrue(graph.retentionPins().stream().anyMatch(pin ->
                pin.kind() == SFMHistoryGraphContract.RetentionKind.NAMED_BRANCH
                        && pin.targetId().endsWith(":" + old.revision().id())));
        assertEquals(repository.history(id).size(), graph.states().size());
        assertEquals(3, enumerate.history().size());
        assertEquals(SFMSelectionRepository.HeadNavigationStatus.AMBIGUOUS,
                redo.mutation().orElseThrow().headNavigationStatus());
        assertEquals("sfm:history/selection/redo/child",
                SFMSelectionHistoryActions.Operation.REDO_CHILD.actionId());
    }
}
