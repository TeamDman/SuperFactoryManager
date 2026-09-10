package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SFMExplorerEntryActivationTests {
    private static SFMExplorerEntry entry(boolean expandable, boolean primaryOpen) {
        var path = new SFMPath(SFMPath.Kind.CONTRIBUTED, "review-tree", "test",
                List.of("after"), Optional.empty(), false);
        return new SFMExplorerEntry(path, "after", expandable, Map.of(
                SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available("after"),
                SFMExplorerEntry.PRIMARY_ACTION_OPEN, SFMExplorerEntry.SortKey.available(Boolean.toString(primaryOpen))
        ), List.of());
    }

    @Test void sourceWithCommentChildrenStillOpensOnActivation() {
        var source = entry(true, true);
        assertTrue(source.expandable(), "Chevron must still expose comments");
        assertTrue(source.opensOnActivate(), "Enter/double-click must open source");
    }

    @Test void ordinaryContainersExpandAndLeavesOpen() {
        assertFalse(entry(true, false).opensOnActivate());
        assertTrue(entry(false, false).opensOnActivate());
    }
}
