package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.*;
import ca.teamdman.sfm.client.search.*;
import ca.teamdman.sfm.client.theme.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMExplorerMatchHighlightsTests {
    @Test void compactRepeatedNamesHighlightOnlyTheOwningExactSegment() {
        var first = SFMExplorerEntry.simple(SFMPath.parse("file:///C:/same"), "same", true, Optional.empty());
        var second = SFMExplorerEntry.simple(SFMPath.parse("file:///C:/same/same"), "same", true, Optional.empty());
        var row = new SFMExplorerProjection.Row(second.path(), second, 0, false, false, first.sortKey("name"),
                SFMExplorerProjection.FilterRole.MATCH, SFMExplorerProjection.RowKind.ENTRY, List.of(first, second));
        var match = SFMExplorerEntryMatch.evaluate(first, SFMTextMatcher.compile("same", SFMTextMatchOptions.defaults()));
        assertEquals(List.of(new SFMTextMatcher.Fragment(10, 14)), SFMExplorerMatchHighlights.project(
                "[context] same/same", row, path -> path.equals(first.path()) ? match : null));
    }
    @Test void allFourMembershipsAreDisjointAndSupplementaryGlyphIsNotSplit() {
        var runs = SFMExplorerMatchHighlights.runs("a😀bc", List.of(new SFMTextMatcher.Fragment(1, 4)),
                List.of(new SFMTextMatcher.Fragment(3, 5)));
        assertEquals(List.of(
                new SFMExplorerMatchHighlights.Run(0, 1, SFMExplorerMatchHighlights.Membership.NONE),
                new SFMExplorerMatchHighlights.Run(1, 3, SFMExplorerMatchHighlights.Membership.FIND),
                new SFMExplorerMatchHighlights.Run(3, 4, SFMExplorerMatchHighlights.Membership.BOTH),
                new SFMExplorerMatchHighlights.Run(4, 5, SFMExplorerMatchHighlights.Membership.FILTER)), runs);
        assertEquals(1, SFMExplorerMatchHighlights.runs("a😀bc", List.of(), List.of()).size());
    }

    @Test void contextPrefixIsNotHighlightedAndMetadataDoesNotInventFilenameMatches() {
        var entry = SFMExplorerEntry.simple(SFMPath.parse("file:///test/Alpha.java"), "Alpha.java", false, Optional.empty());
        var match = SFMExplorerEntryMatch.evaluate(entry, SFMTextMatcher.compile("Alpha", SFMTextMatchOptions.defaults()));
        assertEquals(List.of(new SFMTextMatcher.Fragment(10, 15)),
                SFMExplorerMatchHighlights.project("[context] Alpha.java", match));
        var pathOnly = SFMExplorerEntryMatch.evaluate(entry, SFMTextMatcher.compile("test", SFMTextMatchOptions.defaults()));
        assertTrue(pathOnly.matches());
        assertTrue(SFMExplorerMatchHighlights.project("Alpha.java", pathOnly).isEmpty());
    }

    @Test void explicitIntersectionIsItsOwnThemeColourAndForegroundHasContrastOnLightAndDark() {
        var theme = SFMClientTheme.defaults();
        var colours = new EnumMap<SFMColourRole, Integer>(theme.colours());
        colours.put(SFMColourRole.SEARCH_INTERSECTION, 0xFF123456);
        var custom = new SFMClientTheme(colours, theme.sfmlSyntax(), theme.fileIcons(), theme.actionIcons());
        assertEquals(0xFF123456, SFMExplorerMatchHighlights.background(SFMExplorerMatchHighlights.Membership.BOTH, custom, 0xFFEEEEEE));
        for (int under : new int[] {0xFF101010, 0xFFF0F0F0, 0xFF264F78}) {
            for (var kind : List.of(SFMExplorerMatchHighlights.Membership.FIND, SFMExplorerMatchHighlights.Membership.FILTER,
                    SFMExplorerMatchHighlights.Membership.BOTH)) {
                int background = SFMExplorerMatchHighlights.background(kind, custom, under);
                int foreground = SFMExplorerMatchHighlights.foreground(background);
                assertTrue(contrast(background, foreground) >= 4.5);
            }
        }
        assertNotEquals(theme.colour(SFMColourRole.SEARCH_FIND), theme.colour(SFMColourRole.SEARCH_FILTER));
    }

    @Test void narrowAndTinySearchBarsDoNotOverlapOrPlaceButtonsOutsideTheirRow() {
        for (int width : new int[] {0, 1, 20, 79, 80, 209, 210, 800}) {
            var row = new SFMExplorerPanelViewport.Rect(50, 60, width, 18);
            var layout = SFMExplorerSearchBar.layout(row);
            int next = layout.input().x() + layout.input().width();
            for (var button : layout.buttons()) {
                assertEquals(next, button.bounds().x());
                next += button.bounds().width();
            }
            assertEquals(row.x() + row.width(), next);
            if (width >= 80 && width < 210) assertEquals("menu", layout.buttons().get(0).option());
        }
    }

    private static double contrast(int a, int b) {
        double one = luminance(a), two = luminance(b);
        return (Math.max(one, two) + .05) / (Math.min(one, two) + .05);
    }
    private static double luminance(int rgb) {
        double total = 0;
        int[] shifts = {16, 8, 0};
        double[] weights = {.2126, .7152, .0722};
        for (int i = 0; i < 3; i++) {
            double value = ((rgb >>> shifts[i]) & 255) / 255.0;
            total += weights[i] * (value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4));
        }
        return total;
    }
}
