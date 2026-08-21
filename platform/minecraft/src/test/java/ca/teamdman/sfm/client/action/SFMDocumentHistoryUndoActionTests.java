package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.history.SFMDocumentHistoryTarget;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMDocumentHistoryUndoActionTests {
    @Test
    void canonicalActionTargetsTheCapturedTemporalDocumentPanel() throws Exception {
        RecordingPanel panel = new RecordingPanel();
        SFMScreenMultiplexer workspace = headlessWorkspace(SFMWorkspaceLayout.single(panel));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(workspace, () -> true));
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(new ResourceLocation("sfm", "document/history/undo"),
                        new SFMDocumentHistoryUndoAction())
        ));

        assertEquals(
                PanelActionSupport.closePaletteAfter(1),
                tree.execute("sfm action invoke sfm:document/history/undo", source)
        );
        assertEquals(1, panel.undoCalls);
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

    private static final class RecordingPanel implements SFMScreenPanel, SFMDocumentHistoryTarget {
        private int undoCalls;

        @Override
        public Component title() {
            return Component.literal("Temporal document fixture");
        }

        @Override
        public void render(
                PoseStack poseStack,
                Minecraft minecraft,
                ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds bounds,
                int mouseX,
                int mouseY,
                float partialTick,
                boolean focused
        ) {
        }

        @Override
        public SFMHistoryGraphRuntime.OperationResult undoDocumentHistory() {
            undoCalls++;
            return SFMHistoryGraphRuntime.OperationResult.applied("undone");
        }
    }
}
