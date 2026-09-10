package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.search.SFMTextMatcher;
import ca.teamdman.sfm.client.search.SFMTextMatchOptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SFMExplorerEntryMatchTests {
    @Test void metadataOnlyMatchDoesNotHighlightAnUnrelatedName() {
        var entry = entry("Widget.java", List.of("Widget.java", "needs-change"));
        var result = SFMExplorerEntryMatch.evaluate(entry, matcher("needs-change"));
        assertTrue(result.matches());
        assertTrue(result.complete());
        assertEquals(SFMExplorerEntryMatch.Role.SEARCH_TERM, result.fields().get(0).role());
        assertEquals(List.of(), result.labelFragments());
    }
    @Test void canonicalParentPathMatchRetainsItsProvenanceWithoutHighlightingTheBasename() {
        var entry = entry("Widget.java", List.of("Widget.java", "file:///D:/repo/source/Widget.java"));
        var result = SFMExplorerEntryMatch.evaluate(entry, matcher("source"));
        assertTrue(result.matches());
        assertEquals(SFMExplorerEntryMatch.Role.PATH, result.fields().get(0).role());
        assertTrue(result.labelFragments().isEmpty());
    }
    @Test void semanticFieldFragmentsTranslateIntoADecoratedLabelWithoutSearchingInheritedContext() {
        var entry = entry("before · Widget.java", List.of("Widget.java"));
        var result = SFMExplorerEntryMatch.evaluate(entry, matcher("Widget"));
        assertTrue(result.matches());
        assertEquals(List.of(new SFMTextMatcher.Fragment(9, 15)), result.labelFragments());
        assertFalse(SFMExplorerEntryMatch.evaluate(entry, matcher("before")).matches());
    }
    @Test void excessiveMetadataIsIncompleteRatherThanAnEmptySuccessfulSearch() {
        var entry = entry("Widget.java", List.of("Widget.java", "x".repeat(40000)));
        var result = SFMExplorerEntryMatch.evaluate(entry, matcher("absent"));
        assertFalse(result.matches());
        assertFalse(result.complete());
        assertFalse(result.diagnostics().isEmpty());
        var positive = SFMExplorerEntryMatch.evaluate(entry, matcher("Widget"));
        assertTrue(positive.matches());
        assertFalse(positive.complete());
    }
    private static SFMTextMatcher matcher(String query) { return SFMTextMatcher.compile(query, SFMTextMatchOptions.defaults()); }
    private static SFMExplorerEntry entry(String label, List<String> terms) {
        return new SFMExplorerEntry(SFMPath.parse("file:///D:/repo/source/Widget.java"), label, false,
                Map.of(SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available(label)), terms, List.of());
    }
}
