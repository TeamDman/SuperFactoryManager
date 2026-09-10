package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SFMExplorerFindNavigationTests {
    private final SFMPath a = SFMPath.parse("file:///review/a");
    private final SFMPath b = SFMPath.parse("file:///review/b");
    private final SFMPath c = SFMPath.parse("file:///review/c");
    private final SFMPath d = SFMPath.parse("file:///review/d");
    private final List<SFMPath> all = List.of(a, b, c, d);

    @Test void capturesTopVisibleUnlessASelectedRowIsVisible() {
        for (var selected : List.of(Set.<SFMPath>of(), Set.of(a), Set.of(d))) {
            var nav = new SFMExplorerFindNavigation();
            nav.capture(List.of(b, c), selected);
            nav.publish(nav.beginQuery(), all, Set.copyOf(all), true);
            assertEquals(Optional.of(b), nav.preview());
            assertTrue(nav.takeSelection().isEmpty());
        }
        var nav = new SFMExplorerFindNavigation();
        nav.capture(List.of(b, c), Set.of(c, b, a));
        nav.publish(nav.beginQuery(), all, Set.copyOf(all), true);
        assertEquals(Optional.of(c), nav.preview(), "topmost visible selected b, not arbitrary set order");
        nav.move(1, true);
        assertEquals(Optional.of(c), nav.takeSelection(), "first Enter commits preview without skipping it");
    }

    @Test void reverseWrapAndNoFurtherMatchHaveDistinctEffects() {
        var nav = new SFMExplorerFindNavigation();
        nav.capture(List.of(b, c), Set.of(b));
        nav.publish(nav.beginQuery(), all, Set.of(a, c), true);
        nav.move(-1, true);
        assertEquals(Optional.of(a), nav.takeSelection());
        assertEquals(SFMExplorerFindNavigation.Outcome.BOUNDARY, nav.move(-1, false));
        assertTrue(nav.takeSelection().isEmpty());
        nav.move(-1, true);
        assertEquals(Optional.of(c), nav.takeSelection());
        nav.move(1, true);
        assertEquals(Optional.of(a), nav.takeSelection());
    }

    @Test void queryChangesRetainAnchorAndRejectOldOrNavigatedCompletions() {
        var nav = new SFMExplorerFindNavigation();
        nav.capture(List.of(b, c), Set.of(b));
        var old = nav.beginQuery();
        nav.move(1, true);
        var current = nav.beginQuery();
        assertFalse(nav.publish(old, all, Set.of(d), true));
        assertTrue(nav.publish(current, all, Set.of(a, c), true));
        assertEquals(Optional.of(c), nav.preview());
        assertTrue(nav.takeSelection().isEmpty(), "old queued Enter must not commit new query");
        current = nav.beginQuery();
        nav.move(1, true);
        nav.userNavigated();
        assertFalse(nav.publish(current, all, Set.of(d), true));
        assertTrue(nav.takeSelection().isEmpty());
        assertEquals(SFMExplorerFindNavigation.Outcome.CANCELLED, nav.move(1, true));
    }

    @Test void onePendingMoveIsBoundToItsGenerationAndNoMatchesNeverChangesSelection() {
        var nav = new SFMExplorerFindNavigation();
        nav.capture(List.of(b, c), Set.of(b));
        var token = nav.beginQuery();
        nav.move(1, true);
        nav.move(1, true);
        nav.publish(token, all, Set.of(c, d), true);
        assertEquals(Optional.of(c), nav.takeSelection());
        assertTrue(nav.takeSelection().isEmpty());
        nav.publish(nav.beginQuery(), all, Set.of(), false);
        assertEquals(SFMExplorerFindNavigation.Outcome.INCOMPLETE, nav.move(1, true));
        assertTrue(nav.takeSelection().isEmpty());
        nav.publish(nav.beginQuery(), all, Set.of(), true);
        assertEquals(SFMExplorerFindNavigation.Outcome.NO_MATCHES, nav.move(-1, true));
    }

    @Test void missingAnchorUsesVisibleSurvivorAndAbsentForeignMatchesAreIgnored() {
        var nav = new SFMExplorerFindNavigation();
        nav.capture(List.of(b, c), Set.of(b));
        nav.publish(nav.beginQuery(), List.of(a, c, d), Set.of(b, c, d), true);
        assertEquals(Optional.of(c), nav.preview());
        nav.clear();
        assertTrue(nav.preview().isEmpty());
    }

    @Test void additiveMatchSelectionSkipsMembershipAndRefusesPartialEvidence() {
        var nav = new SFMExplorerFindNavigation();
        nav.capture(List.of(b,c), Set.of(b));
        nav.publish(nav.beginQuery(), all, Set.copyOf(all), false);
        assertFalse(nav.complete());
        assertEquals(SFMExplorerFindNavigation.Outcome.INCOMPLETE, nav.addNext(Set.of(b)));
        assertTrue(nav.takeSelection().isEmpty());
        nav.publish(nav.beginQuery(), all, Set.copyOf(all), true);
        assertEquals(all, nav.orderedMatches());
        assertEquals(SFMExplorerFindNavigation.Outcome.SELECTED, nav.addNext(Set.of(b,c)));
        assertEquals(Optional.of(d), nav.takeSelection());
        nav.addNext(Set.of(b,c,d));
        assertEquals(Optional.of(a), nav.takeSelection(), "wrap to first unselected");
        assertEquals(SFMExplorerFindNavigation.Outcome.NO_MATCHES, nav.addNext(Set.copyOf(all)));
        assertTrue(nav.takeSelection().isEmpty());
        nav.userNavigated();
        assertFalse(nav.complete());
    }
}
