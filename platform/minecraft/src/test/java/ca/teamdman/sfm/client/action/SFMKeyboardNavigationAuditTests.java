package ca.teamdman.sfm.client.action;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMKeyboardNavigationAuditTests {
    @Test
    void rawHandlerInventoryIsDeterministicAndEveryEntryHasADisposition() {
        List<SFMKeyboardNavigationInventory.Entry> entries = SFMKeyboardNavigationInventory.entries();
        assertFalse(entries.isEmpty());
        assertEquals(entries.stream().map(SFMKeyboardNavigationInventory.Entry::line).toList(),
                SFMKeyboardNavigationInventory.lines());
        assertTrue(entries.stream().allMatch(entry -> entry.reason() != null && !entry.reason().isBlank()));
        assertEquals(entries.stream().map(SFMKeyboardNavigationInventory.Entry::line).sorted().toList(),
                SFMKeyboardNavigationInventory.lines());
    }

    @Test
    void inventoryRecordsBothSemanticAndParameterizedBoundaries() {
        assertTrue(SFMKeyboardNavigationInventory.entries().stream()
                .anyMatch(entry -> entry.disposition() == SFMKeyboardNavigationInventory.Disposition.SEMANTIC_ACTION));
        assertTrue(SFMKeyboardNavigationInventory.entries().stream()
                .anyMatch(entry -> entry.disposition() == SFMKeyboardNavigationInventory.Disposition.PARAMETERIZED_INPUT));
    }
}
