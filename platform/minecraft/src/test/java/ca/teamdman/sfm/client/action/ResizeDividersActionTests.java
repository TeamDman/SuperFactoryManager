package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMTestScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResizeDividersActionTests {
    @Test
    void registeredGrammarSuggestsExplicitIdsAndExecutesTheSameConstrainedIntent() throws Exception {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(
                new SFMTestScreenPanel("left"), new SFMTestScreenPanel("right"));
        SFMScreenMultiplexer workspace = headlessWorkspace(layout, 202, 80);
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(
                        new ResourceLocation("sfm", "panel/resize/dividers"),
                        new ResizeDividersAction())));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(workspace, () -> true));
        String dividerId = workspace.dividerDescriptions().get(0).id().toString();
        String prefix = "sfm action invoke sfm:panel/resize/dividers 20 0 ";

        List<String> suggestions = tree.getCompletionSuggestions(tree.parse(prefix, source)).join()
                .getList().stream().map(suggestion -> suggestion.getText()).toList();
        assertTrue(suggestions.contains(dividerId));
        assertEquals(1, tree.execute(prefix + dividerId, source));

        var leftId = layout.visiblePanels().get(0).id();
        var rightId = layout.visiblePanels().get(1).id();
        assertEquals(120, workspace.panelBounds(leftId).width());
        assertEquals(80, workspace.panelBounds(rightId).width());
    }

    @Test
    void semanticIntentSupportsAnExplicitMultiDividerSelectorWithoutPointerCoordinates() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.vertical(
                SFMWorkspaceLayout.horizontal(
                        SFMWorkspaceLayout.panel(new SFMTestScreenPanel("top-left")),
                        SFMWorkspaceLayout.panel(new SFMTestScreenPanel("top-right"))),
                SFMWorkspaceLayout.horizontal(
                        SFMWorkspaceLayout.panel(new SFMTestScreenPanel("bottom-left")),
                        SFMWorkspaceLayout.panel(new SFMTestScreenPanel("bottom-right")))));
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(0, 0, 302, 202);
        var columnIds = layout.dividers(viewport, 2, 3, 20).stream()
                .filter(divider -> divider.axis()
                        == ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceAxis.HORIZONTAL)
                .map(divider -> divider.id())
                .toList();
        String selector = String.join(",", columnIds.stream().map(Object::toString).toList());

        var result = layout.resizeDividers(
                new ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceResizeDividersIntent(
                        ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceResizeDividersIntent
                                .parseSelector(selector),
                        15,
                        0),
                viewport,
                2,
                3,
                20);

        assertTrue(result.changed());
        assertEquals(2, result.appliedDeltas().size());
        assertTrue(result.appliedDeltas().values().stream().allMatch(delta -> delta == 15));
    }

    private static SFMScreenMultiplexer headlessWorkspace(
            SFMWorkspaceLayout layout,
            int width,
            int height
    ) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        SFMScreenMultiplexer workspace =
                (SFMScreenMultiplexer) unsafe.allocateInstance(SFMScreenMultiplexer.class);
        Field layoutField = SFMScreenMultiplexer.class.getDeclaredField("layout");
        layoutField.setAccessible(true);
        layoutField.set(workspace, layout);
        Field widthField = Screen.class.getDeclaredField("width");
        widthField.setAccessible(true);
        widthField.setInt(workspace, width);
        Field heightField = Screen.class.getDeclaredField("height");
        heightField.setAccessible(true);
        heightField.setInt(workspace, height);
        return workspace;
    }
}
