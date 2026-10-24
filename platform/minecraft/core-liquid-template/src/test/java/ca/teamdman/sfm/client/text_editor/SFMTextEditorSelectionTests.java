package ca.teamdman.sfm.client.text_editor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SFMTextEditorSelectionTests {
    @Test
    void thirdPartyRoundTripsStageAndPersistStableIds() {
        var registry = new ArrayList<>(List.of("sfm:v1", "sfm:v2", "addon:editor"));
        var configured = new AtomicReference<>("sfm:v1");
        for (String id : List.of("addon:editor", "sfm:v2", "sfm:v1", "addon:editor")) {
            var selection = new SFMTextEditorSelection(configured.get(), () -> registry);
            assertTrue(selection.select(id));
            assertEquals(selection.configuredId(), configured.get(), "Choosing is staged until Apply");
            assertTrue(selection.apply(configured::set));
            assertEquals(id, configured.get());
        }
    }

    @Test
    void cancellationAndRefreshDoNotWriteOrRepairThePreference() {
        for (String stored : List.of("missing:editor", "not a valid id", "sfm:v1")) {
            var configured = new AtomicReference<>(stored);
            var selection = new SFMTextEditorSelection(configured.get(), () -> List.of("sfm:v1", "addon:editor"));
            assertEquals(stored.equals("sfm:v1"), selection.configuredAvailable());
            assertTrue(selection.select("addon:editor"));
            selection.refresh();
            assertEquals(stored, configured.get());
            assertEquals(stored, selection.configuredId());
        }
    }

    @Test
    void unavailableChoiceCannotBeCommittedAndLateRegistrationRecoversIt() {
        var registry = new ArrayList<>(List.of("sfm:v1"));
        var selection = new SFMTextEditorSelection("addon:editor", () -> registry);
        assertFalse(selection.canApply());
        assertFalse(selection.apply(id -> fail("Unavailable preference must not be saved")));
        registry.add("addon:editor");
        assertTrue(selection.refresh());
        assertTrue(selection.configuredAvailable());
        assertTrue(selection.canApply());
        registry.remove("addon:editor");
        assertFalse(selection.apply(id -> fail("Revalidate on Apply, not just on the last UI tick")));
        registry.add("addon:editor");
        assertTrue(selection.apply(id -> assertEquals("addon:editor", id)));
    }

    @Test
    void longListsAreSortedDeduplicatedSearchedAndPagedWithoutLosingSelection() {
        var registry = new ArrayList<String>();
        for (int i = 999; i >= 0; i--) registry.add("addon:editor_" + String.format(java.util.Locale.ROOT, "%03d", i));
        registry.add("addon:editor_999");
        var selection = new SFMTextEditorSelection("addon:editor_999", () -> registry);
        selection.setPageSize(7);
        assertEquals(143, selection.pageCount());
        assertEquals("addon:editor_000", selection.visibleIds().get(0));
        selection.setPage(Integer.MAX_VALUE);
        assertEquals(6, selection.visibleIds().size());
        assertEquals("addon:editor_999", selection.visibleIds().get(5));
        selection.setQuery(" EDITOR_99 ");
        assertEquals(2, selection.pageCount());
        assertEquals("addon:editor_990", selection.visibleIds().get(0));
        selection.setQuery("no match");
        assertTrue(selection.visibleIds().isEmpty());
        assertEquals(1, selection.pageCount());
        assertTrue(selection.canApply(), "Filtering must not erase a staged choice");
        assertEquals("addon:editor_999", selection.selectedId());
        selection.setPageSize(0);
        selection.setQuery("");
        selection.setPage(999);
        registry.clear();
        selection.refresh();
        assertEquals(0, selection.page());
        assertTrue(selection.visibleIds().isEmpty());
        assertFalse(selection.canApply());
    }

    @Test
    void failedPersistenceKeepsTheSessionAvailableForRetry() {
        var selection = new SFMTextEditorSelection("sfm:v1", () -> List.of("sfm:v1", "addon:editor"));
        selection.select("addon:editor");
        assertThrows(IllegalStateException.class, () -> selection.apply(id -> { throw new IllegalStateException("disk full"); }));
        assertEquals("addon:editor", selection.selectedId());
        assertTrue(selection.apply(id -> assertEquals("addon:editor", id)));
    }
    @Test
    void translatedNamesAreSearchableButDuplicateLabelsNeverBecomeIdentities() {
        var selection = new SFMTextEditorSelection("sfm:v1",
                () -> List.of("sfm:v1", "first:editor", "second:editor"),
                id -> id.equals("sfm:v1") ? "V1" : "Same Editor Name");
        selection.setPageSize(10);
        selection.setQuery("same EDITOR name");
        assertEquals(List.of("first:editor", "second:editor"), selection.visibleIds());
        assertTrue(selection.select("second:editor"));
        assertTrue(selection.apply(id -> assertEquals("second:editor", id)));
        selection.setQuery("first:");
        assertEquals(List.of("first:editor"), selection.visibleIds());
        assertEquals("second:editor", selection.selectedId());
    }

    @Test
    void lateInsertionBeforeDisplayedRowCannotChangeItsSelectedIdentity() {
        var ids = new ArrayList<>(List.of("addon:b", "addon:d", "addon:f"));
        var selection = new SFMTextEditorSelection("addon:f", () -> ids);
        selection.setPageSize(1);
        selection.setPage(1);
        String displayedId = selection.visibleIds().get(0);
        assertEquals("addon:d", displayedId);
        ids.add("addon:a");
        assertTrue(selection.select(displayedId));
        assertEquals("addon:d", selection.selectedId());
        ids.remove("addon:d");
        assertFalse(selection.apply(id -> fail("Removed staged ID must not be replaced by its row neighbour")));
        assertEquals("addon:d", selection.selectedId());
        ids.add("addon:d");
        assertTrue(selection.apply(id -> assertEquals("addon:d", id)));
    }

    @Test
    void searchResizeAndLateRegistrationClampPagesWithoutErasingStagedChoice() {
        var ids = new ArrayList<>(List.of("addon:a", "addon:b", "addon:c", "addon:d", "addon:e"));
        var selection = new SFMTextEditorSelection("addon:a", () -> ids);
        selection.setPageSize(2);
        selection.setPage(2);
        selection.select("addon:e");
        selection.setPageSize(4);
        assertEquals(1, selection.page());
        assertEquals(List.of("addon:e"), selection.visibleIds());
        selection.setQuery("NEW:");
        assertEquals(0, selection.page());
        assertTrue(selection.visibleIds().isEmpty());
        ids.add("new:editor");
        selection.refresh();
        assertEquals(List.of("new:editor"), selection.visibleIds());
        assertEquals("addon:e", selection.selectedId());
        assertTrue(selection.apply(id -> assertEquals("addon:e", id)));
        selection.setQuery("");
        assertEquals(0, selection.page());
        assertEquals(2, selection.pageCount());
    }

}
