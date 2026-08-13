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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMSelectionRepositoryTests {
    private static final SFMPath A = SFMPath.parse("file:///C:/a.txt");
    private static final SFMPath B = SFMPath.parse("file:///C:/b.txt");
    private static final SFMPath C = SFMPath.parse("registry://minecraft/item/stone");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC);

    @Test
    public void namesAreIndependentAndAddRemoveAppendImmutableRevisions() {
        SFMSelectionRepository repository = new SFMSelectionRepository(CLOCK);
        SFMSelectionRepository.MutationResult first = repository.create(
                Optional.of("first"), Set.of(A), "test", "create-first"
        );
        SFMSelectionRepository.MutationResult second = repository.create(
                Optional.of("second"), Set.of(C), "test", "create-second"
        );
        SFMSelectionId firstId = first.selection().id();
        SFMPath pinnedFirst = repository.pinnedPath(firstId, first.revision().id());

        SFMSelectionRepository.MutationResult added = repository.add(
                firstId, Set.of(B), "test", "add-b"
        );
        repository.remove(firstId, Set.of(A), "test", "remove-a");

        assertEquals(Set.of(A), repository.resolve(pinnedFirst).members());
        assertEquals(Set.of(B), repository.resolve(SFMPath.parse("selection://first")).members());
        assertEquals(Set.of(B), members(repository, firstId));
        assertEquals(Set.of(C), members(repository, second.selection().id()));
        assertNotEquals(first.revision().id(), added.revision().id());
    }

    @Test
    public void setAlgebraDerivesNamedSelectionsAndPinnedPathsStayImmutable() {
        SFMSelectionRepository repository = new SFMSelectionRepository(CLOCK);
        SFMSelectionRepository.MutationResult left = repository.create(
                Optional.of("left"), Set.of(A, B), "test", "left"
        );
        SFMSelectionRepository.MutationResult right = repository.create(
                Optional.of("right"), Set.of(B, C), "test", "right"
        );
        SFMPath pinnedLeft = repository.pinnedPath(left.selection().id(), left.revision().id());
        repository.add(left.selection().id(), Set.of(C), "test", "left-later");

        SFMSelectionRepository.MutationResult union = repository.union(
                new SFMSelectionId("union"),
                Optional.of("union"),
                List.of(left.selection().id(), right.selection().id()),
                "test",
                "derive-union"
        );
        SFMSelectionRepository.MutationResult intersection = repository.intersection(
                new SFMSelectionId("intersection"),
                Optional.of("intersection"),
                List.of(left.selection().id(), right.selection().id()),
                "test",
                "derive-intersection"
        );
        SFMSelectionRepository.MutationResult difference = repository.difference(
                new SFMSelectionId("difference"),
                Optional.of("difference"),
                left.selection().id(),
                List.of(right.selection().id()),
                "test",
                "derive-difference"
        );

        assertEquals(Set.of(A, B), repository.resolve(pinnedLeft).members());
        assertEquals(Set.of(A, B, C), members(repository, union.selection().id()));
        assertEquals(Set.of(B, C), members(repository, intersection.selection().id()));
        assertEquals(Set.of(A), members(repository, difference.selection().id()));
    }

    @Test
    public void undoRedoMoveHeadsWithoutDeletingRevisions() {
        SFMSelectionRepository repository = new SFMSelectionRepository(CLOCK);
        SFMSelectionRepository.MutationResult created = repository.create(
                Optional.of("history"), Set.of(A), "test", "create"
        );
        SFMSelectionId id = created.selection().id();
        SFMSelectionRepository.MutationResult changed = repository.add(
                id, Set.of(B), "test", "add"
        );
        SFMPath changedRevision = repository.pinnedPath(id, changed.revision().id());

        SFMSelectionRepository.MutationResult undone = repository.undo(id, "test", "undo");
        assertEquals(SFMSelectionHeadEvent.Kind.UNDO, undone.headEvent().orElseThrow().kind());
        assertEquals(Set.of(A), members(repository, id));
        assertEquals(Set.of(A, B), repository.resolve(changedRevision).members());
        SFMSelectionRepository.MutationResult redone = repository.redo(id, "test", "redo");
        assertEquals(SFMSelectionHeadEvent.Kind.REDO, redone.headEvent().orElseThrow().kind());
        assertEquals(Set.of(A, B), members(repository, id));
        assertTrue(repository.revision(created.revision().id()).isPresent());
    }

    @Test
    public void requestsReplayButConflictingReuseFailsClosed() {
        SFMSelectionRepository repository = new SFMSelectionRepository(CLOCK);
        SFMSelectionRepository.MutationResult created = repository.create(
                Optional.of("once"), Set.of(A), "agent", "request-1"
        );
        SFMSelectionRepository.MutationResult replay = repository.create(
                Optional.of("once"), Set.of(A), "agent", "request-1"
        );

        assertFalse(created.replayed());
        assertTrue(replay.replayed());
        assertEquals(created.selection(), replay.selection());
        assertEquals(created.revision(), replay.revision());
        assertEquals(1, repository.stateSnapshot().selections().size());
        assertThrows(IllegalArgumentException.class, () -> repository.create(
                Optional.of("different"), Set.of(B), "agent", "request-1"
        ));
        SFMSelectionRepository.MutationResult anotherActor = repository.create(
                Optional.of("different"), Set.of(B), "another-agent", "request-1"
        );
        assertFalse(anotherActor.replayed());
    }

    @Test
    public void mutationAfterUndoClearsRedoWithoutDeletingOldBranch() {
        SFMSelectionRepository repository = new SFMSelectionRepository(CLOCK);
        SFMSelectionRepository.MutationResult created = repository.create(
                Optional.of("branch"), Set.of(A), "test", "create"
        );
        SFMSelectionId id = created.selection().id();
        SFMSelectionRepository.MutationResult oldBranch = repository.add(
                id, Set.of(B), "test", "old-branch"
        );
        SFMPath oldPinned = repository.pinnedPath(id, oldBranch.revision().id());
        repository.undo(id, "test", "undo");
        repository.add(id, Set.of(C), "test", "new-branch");

        assertFalse(repository.redo(id, "test", "redo-after-branch").changed());
        assertEquals(Set.of(A, C), members(repository, id));
        assertEquals(Set.of(A, B), repository.resolve(oldPinned).members());
    }

    @Test
    public void selectionNamePathsResolveThroughChildrenExpressions() {
        SFMSelectionRepository repository = new SFMSelectionRepository(CLOCK);
        SFMSelectionRepository.MutationResult created = repository.create(
                new SFMSelectionId("selection-id"),
                Optional.of("friendly-name"),
                Set.of(A),
                "test",
                "named-path"
        );
        SFMPathExpressionResolution live = SFMPathExpressionResolver.resolve(
                new SFMPathExpression.Children(
                        new SFMPathExpression.Literal(SFMPath.parse("selection://friendly-name"))
                ),
                repository,
                new SFMChildRelationRepository()
        );
        SFMPathExpressionResolution pinned = SFMPathExpressionResolver.resolve(
                new SFMPathExpression.Children(new SFMPathExpression.Literal(SFMPath.parse(
                        "selection://friendly-name@revision-" + created.revision().id()
                ))),
                repository,
                new SFMChildRelationRepository()
        );

        assertEquals(Set.of(A), live.paths());
        assertEquals(Set.of(A), pinned.paths());
        assertEquals(SFMPathExpressionResolution.Completeness.COMPLETE, live.completeness());
        assertEquals(SFMPathExpressionResolution.Completeness.COMPLETE, pinned.completeness());
    }

    private static Set<SFMPath> members(SFMSelectionRepository repository, SFMSelectionId id) {
        return repository.resolve(repository.livePath(id)).members();
    }
}
