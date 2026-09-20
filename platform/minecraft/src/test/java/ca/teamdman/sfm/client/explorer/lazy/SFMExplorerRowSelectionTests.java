package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ca.teamdman.sfm.client.explorer.lazy.SFMExplorerRowSelection.Gesture.*;

class SFMExplorerRowSelectionTests {
    final SFMPath root = SFMPath.parse("file:///test/");
    final SFMPath a = SFMPath.parse("file:///test/a");
    final SFMPath b = SFMPath.parse("file:///test/b");
    final SFMPath c = SFMPath.parse("file:///test/c");
    final SFMPath d = SFMPath.parse("file:///test/d");
    final List<SFMPath> order = List.of(a, b, c, d);

    @Test void replaceToggleAndRangesKeepAnExplicitAnchor() {
        var value = SFMExplorerRowSelection.apply(Set.of(d), Optional.empty(), b, order, REPLACE);
        assertEquals(Set.of(b), value.members());
        value = SFMExplorerRowSelection.apply(value.members(), value.anchor(), d, order, TOGGLE);
        assertEquals(Set.of(b, d), value.members());
        assertEquals(Optional.of(b), value.anchor());
        value = SFMExplorerRowSelection.apply(value.members(), value.anchor(), c, order, RANGE);
        assertEquals(Set.of(b, c), value.members());
        value = SFMExplorerRowSelection.apply(value.members(), value.anchor(), a, order, RANGE);
        assertEquals(Set.of(a, b), value.members());
    }

    @Test void ctrlShiftAddsOrRemovesWholeRangeNotIndividualInversion() {
        var value = SFMExplorerRowSelection.apply(Set.of(a, c), Optional.of(b), d, order, TOGGLE_RANGE);
        assertEquals(Set.of(a, b, c, d), value.members());
        value = SFMExplorerRowSelection.apply(value.members(), value.anchor(), c, order, TOGGLE_RANGE);
        assertEquals(Set.of(a, d), value.members());
        assertEquals(Optional.of(b), value.anchor());
    }

    @Test void hiddenAnchorDoesNotSelectUnloadedRowsAndCursorDoesNotChangeMembership() {
        var value = SFMExplorerRowSelection.apply(Set.of(a, d), Optional.of(a), c, List.of(b, c), TOGGLE_RANGE);
        assertEquals(Set.of(a, c, d), value.members());
        assertEquals(Optional.of(c), value.anchor());
        assertEquals(value, SFMExplorerRowSelection.apply(value.members(), value.anchor(), b, order, CURSOR));
        assertThrows(IllegalArgumentException.class,
                () -> SFMExplorerRowSelection.apply(Set.of(), Optional.of(b), a, List.of(b, c), RANGE));
    }

    @Test void sessionUsesSharedLedgerWithOneRevisionPerReplacementAndIsolation() {
        var repository = new SFMSelectionRepository();
        var first = new SFMExplorerSession(new SFMExplorerId("first"), root, repository);
        var second = new SFMExplorerSession(new SFMExplorerId("second"), root, repository);
        first.selectRow(b, order, REPLACE);
        var initial = first.snapshot().rowSelection().orElseThrow();
        first.selectRow(d, order, RANGE);
        var range = first.snapshot().rowSelection().orElseThrow();
        assertEquals(initial.selectionId(), range.selectionId());
        assertEquals(List.of(initial.id()), range.parentRevisionIds());
        assertEquals(SFMSelectionRevision.OperationKind.REPLACE, range.operation().kind());
        assertEquals(Set.of(b,c,d), range.members());
        assertEquals(2, repository.history(initial.selectionId()).size());
        assertTrue(second.snapshot().selectedPaths().isEmpty());
        first.setFilterQuery("nothing");
        first.collapseWithoutCancelling(root);
        assertEquals(Set.of(b,c,d), first.snapshot().selectedPaths());
        repository.undo(initial.selectionId(), "test", "undo");
        assertEquals(Set.of(b), first.snapshot().selectedPaths(), "head is read from the shared repository");
        first.navigateTo(a);
        assertEquals(Set.of(a), first.selectedPaths());
        assertEquals(3, repository.history(initial.selectionId()).size(), "old branch was not deleted");
    }

    @Test void replacementIsIdempotentAndArchivesRoundTripNewOperation() {
        var repository = new SFMSelectionRepository();
        var id = repository.create(Optional.empty(), Set.of(a), "test", "create").selection().id();
        var result = repository.replace(id, Set.of(b,c), "test", "replace");
        assertTrue(result.changed());
        assertTrue(repository.replace(id, Set.of(c,b), "test", "replace").replayed());
        assertFalse(repository.replace(id, Set.of(c,b), "test", "same").changed());
        assertThrows(IllegalArgumentException.class, () -> repository.replace(id, Set.of(d), "test", "replace"));
        var restored = new SFMSelectionRepository();
        restored.restoreArchive(SFMSelectionArchiveJsonCodec.read(SFMSelectionArchiveJsonCodec.write(repository.exportArchive())));
        assertEquals(repository.exportArchive(), restored.exportArchive());
    }

    @Test void transactionRestoresMembershipPrimaryAndAnchorTogether() {
        var repository = new SFMSelectionRepository();
        var session = new SFMExplorerSession(new SFMExplorerId("first"), root, repository);
        session.selectRow(b, order, REPLACE);
        var selectionBefore = repository.transactionSnapshot();
        var before = session.transactionSnapshot();
        session.selectRow(d, order, TOGGLE_RANGE);
        repository.restoreTransactionSnapshot(selectionBefore);
        session.restoreTransactionSnapshot(before);
        assertEquals(Set.of(b), session.selectedPaths());
        assertEquals(Optional.of(b), session.snapshot().navigationCursor());
        assertEquals(Optional.of(b), session.snapshot().rangeAnchor());
    }
}
