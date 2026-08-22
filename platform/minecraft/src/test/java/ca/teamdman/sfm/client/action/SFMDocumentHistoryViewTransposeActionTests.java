package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.history.canvas.SFMHistoryCanvasLayout;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistorySelector;
import ca.teamdman.sfm.client.screen.history.document.SFMDocumentHistoryPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMDocumentHistoryViewTransposeActionTests {
    @Test
    void registeredActionTransposesTheCapturedCanvasPanel() throws Exception {
        SFMDocumentHistoryPanel panel = new SFMDocumentHistoryPanel(SFMDocumentHistorySelector.focused());
        SFMScreenMultiplexer workspace = headlessWorkspace(SFMWorkspaceLayout.single(panel));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(workspace, () -> true));
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(new ResourceLocation("sfm", "document/history/view/transpose"),
                        new SFMDocumentHistoryViewTransposeAction())
        ));

        assertEquals(PanelActionSupport.closePaletteAfter(1), tree.execute(
                "sfm action invoke sfm:document/history/view/transpose", source));
        assertEquals(SFMHistoryCanvasLayout.Orientation.LEFT_RIGHT, panel.orientation());
    }

    private static SFMScreenMultiplexer headlessWorkspace(SFMWorkspaceLayout layout) throws Exception {
        SFMScreenMultiplexer workspace = allocateWithoutConstructor(SFMScreenMultiplexer.class);
        Field layoutField = SFMScreenMultiplexer.class.getDeclaredField("layout");
        layoutField.setAccessible(true);
        layoutField.set(workspace, layout);
        return workspace;
    }

    private static <T> T allocateWithoutConstructor(Class<T> type) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return type.cast(((Unsafe) unsafeField.get(null)).allocateInstance(type));
    }
}
