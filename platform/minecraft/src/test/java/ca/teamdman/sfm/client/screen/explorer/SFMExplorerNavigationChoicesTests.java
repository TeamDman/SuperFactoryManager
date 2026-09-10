package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.*;
import ca.teamdman.sfm.client.explorer.lazy.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMExplorerNavigationChoicesTests {
    @Test void nativeRootOffersDistinctReplaceAddAndRefresh() {
        var root=SFMPath.parse("file:///C:/fixture/child");
        var session=new SFMExplorerSession(new SFMExplorerId("navigation-test"),root,new SFMSelectionRepository());
        var commands=SFMExplorerNavigationChoices.roots(session.snapshot()).stream().map(c->c.command()).toList();
        assertEquals(3,commands.size());
        assertTrue(commands.stream().anyMatch(c->c.equals("sfm action invoke sfm:explorer/refresh")));
        assertTrue(commands.stream().anyMatch(c->c.contains("sfm:explorer/root/parent/set file:///C:/fixture/child --expected-revision ")));
        assertTrue(commands.stream().anyMatch(c->c.contains("sfm:explorer/root/add ") && c.endsWith(" file:///C:/fixture")));
        var directory=new SFMExplorerEntry(SFMPath.parse("file:///C:/fixture/child/nested"),"nested",true,
                Map.of(SFMExplorerEntry.SUBJECT_KIND,SFMExplorerEntry.SortKey.available("container"),
                        SFMExplorerEntry.SORT_NAME,SFMExplorerEntry.SortKey.available("nested")),List.of("nested"),List.of());
        assertTrue(SFMExplorerNavigationChoices.row(session.snapshot(),directory).stream()
                .anyMatch(c->c.command().contains("sfm:explorer/root/add ") && c.command().endsWith("/nested")));
    }
    @Test void volumeAndContributedAuthoritiesDoNotInventParents() {
        assertTrue(SFMExplorerNavigationChoices.parent(SFMPath.parse("file:///C:/")).isEmpty());
        assertTrue(SFMExplorerNavigationChoices.parent(SFMPath.parse("registry://minecraft/item/")).isEmpty());
        assertTrue(SFMExplorerNavigationChoices.parent(SFMPath.parse("review://session/document")).isEmpty());
    }
}
