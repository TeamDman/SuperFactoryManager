package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMExplorerScrollAnchorTests {
    private static SFMExplorerProjection.Row row(String name, int depth) {
        var path = SFMPath.parse("file:///C:/" + name);
        var entry = SFMExplorerEntry.simple(path, name, true, Optional.empty());
        return new SFMExplorerProjection.Row(path, entry, depth, false, true, entry.sortKey("name"));
    }
    @Test void localSplitRetainsFirstSegmentPixelOffsetAndPanelToggleUsesTopNotSelection() {
        var a = row("a", 0); var b = row("b", 1); var c = row("c", 0); var d = row("d", 0);
        var merged = new SFMExplorerProjection.Row(b.path(), b.entry(), 0, false, true, a.activeSortKey(),
                a.filterRole(), a.rowKind(), List.of(a.entry(), b.entry()));
        var before = List.of(c, d, merged); var after = List.of(c, d, a, b);
        var local = SFMExplorerScrollAnchor.capture(before, 1, 1, Optional.of(b.path()));
        assertEquals(1, local.restore(after, 1));
        var global = SFMExplorerScrollAnchor.capture(after, 2, 1, Optional.empty());
        assertEquals(2, global.restore(before, 1));
        assertEquals(1, global.restore(before, 2), "resized grid maps logical identity, not old pixel count");
    }
    @Test void missingAnchorUsesAncestorThenDeterministicNeighbor() {
        var parent = row("parent", 0); var a = row("a", 1); var b = row("b", 1); var last = row("last", 0);
        var anchor = SFMExplorerScrollAnchor.capture(List.of(parent, a, b, last), 1, 1, Optional.empty());
        assertEquals(0, anchor.restore(List.of(parent, last), 1));
        assertEquals(0, anchor.restore(List.of(b, last), 1));
        assertEquals(0, anchor.restore(List.of(), 1));
    }
}
