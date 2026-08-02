package ca.teamdman.sfm.client.screen.file_explorer;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentDispatcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SFMFileExplorerWorkspaceTests {
    @Test
    void firstOpenSplitsOnceAndSecondOpenReusesSameViewerAndId() {
        SFMFileExplorerSource source = new SFMFileExplorerSource() {
            @Override public String displayName() { return "test"; }
            @Override public SFMFileExplorerSnapshot snapshot() { return SFMFileExplorerSnapshot.ready(List.of()); }
            @Override public SFMFileReadResult readText(String logicalPath) {
                return SFMFileReadResult.ready("content:" + logicalPath);
            }
        };
        SFMFileExplorerWorkspace.Controller controller = new SFMFileExplorerWorkspace.Controller();
        SFMFileExplorerPanel explorer = new SFMFileExplorerPanel(source, controller::open);
        controller.attachExplorer(explorer);
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.single(explorer);
        var explorerId = layout.focusedPanel();
        explorer.opened(null, new SFMScreenPanelBounds(0, 0, 100, 40),
                new SFMWorkspacePanelContext(explorerId,
                        (id, intent) -> SFMWorkspacePanelIntentDispatcher.apply(layout, id, intent).result()));
        SFMFileExplorerEntry first = SFMFileExplorerEntry.file("first.txt", "first.txt");
        SFMFileExplorerEntry second = SFMFileExplorerEntry.file("second.txt", "second.txt");

        controller.open(new SFMFileExplorerModel.OpenIntent("test", first));
        SFMReadOnlyTextPanel viewer = controller.viewer();
        assertNotNull(viewer);
        assertEquals(2, layout.panels().size());
        var viewerId = layout.focusedPanel();
        Map<?, ?> firstBounds = layout.bounds(new SFMScreenPanelBounds(0, 0, 102, 40), 2);
        assertEquals(2, firstBounds.size());

        controller.open(new SFMFileExplorerModel.OpenIntent("test", second));
        assertSame(viewer, controller.viewer());
        assertEquals(viewerId, layout.focusedPanel());
        assertEquals(2, layout.panels().size());
        assertEquals("second.txt", viewer.path());
        assertEquals("content:second.txt", viewer.text());
    }

    @Test
    void viewerReplacementResetsScrollWithoutLifecycleReplacement() {
        SFMReadOnlyTextPanel viewer = new SFMReadOnlyTextPanel(() -> {}, paths -> {});
        viewer.show("test", SFMFileExplorerEntry.file("one.txt", "one.txt"), "one\ntwo\nthree");
        viewer.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0, 0);
        assertEquals(1, viewer.firstLine());
        viewer.show("test", SFMFileExplorerEntry.file("two.txt", "two.txt"), "replacement");
        assertEquals(0, viewer.firstLine());
        assertEquals("two.txt", viewer.path());
        assertEquals("replacement", viewer.text());
    }

}
