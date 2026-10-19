package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.*;
import ca.teamdman.sfm.client.explorer.lazy.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMExplorerNavigationChoicesTests {
    private static SFMPath fixturePath(String relative) {
        return SFMPath.fromNative(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "sfm-navigation-fixture")
                .resolve(relative).toAbsolutePath().normalize());
    }

{% if features.workspace_panel_actions %}
    @Test void nativeRootOffersDistinctReplaceAddAndRefresh() {
        var root=fixturePath("fixture/child");
        var session=new SFMExplorerSession(new SFMExplorerId("navigation-test"),root,new SFMSelectionRepository());
        var commands=SFMExplorerNavigationChoices.roots(session.snapshot()).stream().map(c->c.command()).toList();
        assertEquals(3,commands.size());
        assertTrue(commands.stream().anyMatch(c->c.equals("sfm action invoke sfm:explorer/refresh")));
        assertTrue(commands.stream().anyMatch(c->c.contains("sfm:explorer/root/parent/set " + root.canonical() + " --expected-revision ")));
        assertTrue(commands.stream().anyMatch(c->c.contains("sfm:explorer/root/add ") && c.endsWith(" " + fixturePath("fixture").canonical())));
        var directory=new SFMExplorerEntry(fixturePath("fixture/child/nested"),"nested",true,
                Map.of(SFMExplorerEntry.SUBJECT_KIND,SFMExplorerEntry.SortKey.available("container"),
                        SFMExplorerEntry.SORT_NAME,SFMExplorerEntry.SortKey.available("nested")),List.of("nested"),List.of());
        assertTrue(SFMExplorerNavigationChoices.row(session.snapshot(),directory).stream()
                .anyMatch(c->c.command().contains("sfm:explorer/root/add ") && c.command().endsWith("/nested")));
    }
{% endif %}
{% if features.workspace_panel_actions %}
    @Test void mountedFileCanReplaceOrJoinTheCurrentRootSetWithoutPretendingToBeADirectory() {
        var root=fixturePath("fixture");
        var mountedPath=fixturePath("fixture/review.sfm-review.json");
        var session=new SFMExplorerSession(new SFMExplorerId("mounted-navigation-test"),root,new SFMSelectionRepository());
        var mounted=new SFMExplorerEntry(mountedPath,"review.sfm-review.json",true,
                Map.of(SFMExplorerEntry.SUBJECT_KIND,SFMExplorerEntry.SortKey.available("file"),
                        SFMExplorerEntry.MOUNT_PROVIDER,SFMExplorerEntry.SortKey.available("sfm:release-review"),
                        SFMExplorerEntry.SORT_NAME,SFMExplorerEntry.SortKey.available("review.sfm-review.json")),
                List.of("review.sfm-review.json"),List.of());

        var choices=SFMExplorerNavigationChoices.row(session.snapshot(),mounted);
        assertTrue(choices.stream().anyMatch(c->c.command().contains("sfm:explorer/location/set ")
                && c.command().contains(mountedPath.canonical())));
        assertTrue(choices.stream().anyMatch(c->c.command().contains("sfm:explorer/root/add ")
                && c.command().endsWith(mountedPath.canonical())));
    }
{% endif %}
    @Test void volumeAndContributedAuthoritiesDoNotInventParents() {
        assertTrue(SFMExplorerNavigationChoices.parent(SFMPath.fromNative(java.nio.file.Path.of("").toAbsolutePath().getRoot())).isEmpty());
        assertTrue(SFMExplorerNavigationChoices.parent(SFMPath.parse("registry://minecraft/item/")).isEmpty());
        assertTrue(SFMExplorerNavigationChoices.parent(SFMPath.parse("review://session/document")).isEmpty());
    }
}
