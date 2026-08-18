package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMSpatialCoverageRunActionTests {
    private static final String PREFIX =
            "sfm action invoke sfm:spatial/coverage/run ";
    private static final String CANONICAL = PREFIX
            + "document focused sfm:strict_java_navigation "
            + "sfm:auto_1_through_8 0 100000 auto";

    @Test
    void canonicalCommandDelegatesToInjectedHandlerWithCapturedTarget() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        TestRig rig = rig(handler);

        assertEquals(37, rig.tree().execute(CANONICAL, rig.source()));
        assertEquals(1, handler.requests.size());
        assertEquals(new SFMSpatialCoverageRunAction.Request(
                "document",
                "focused",
                "sfm:strict_java_navigation",
                "sfm:auto_1_through_8",
                0,
                100000,
                "auto"
        ), handler.requests.get(0));
        assertEquals(rig.workspace(), handler.target.workspace());
        assertEquals(rig.workspace().focusedPanelId(), handler.target.panelId());
    }

    @Test
    void incompleteAndInvalidFormsNeverReachHandler() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        TestRig rig = rig(handler);

        for (String command : List.of(
                PREFIX,
                PREFIX + "document focused sfm:strict_java_navigation",
                PREFIX + "pane focused sfm:strict_java_navigation sfm:auto_1_through_8 0 1 auto",
                PREFIX + "document all sfm:strict_java_navigation sfm:auto_1_through_8 0 1 auto",
                PREFIX + "document focused strict_java_navigation sfm:auto_1_through_8 0 1 auto",
                PREFIX + "document focused sfm:strict_java_navigation auto_1_through_8 0 1 auto",
                PREFIX + "document focused sfm:strict_java_navigation sfm:auto_1_through_8 -1 1 auto",
                PREFIX + "document focused sfm:strict_java_navigation sfm:auto_1_through_8 0 0 auto"
        )) {
            assertThrows(CommandSyntaxException.class,
                    () -> rig.tree().execute(command, rig.source()), command);
        }

        assertTrue(handler.requests.isEmpty());
    }

    @Test
    void frozenChoicesAndAutoDestinationAreSuggested() throws Exception {
        TestRig rig = rig(new RecordingHandler());

        assertSuggestionsContain(rig, PREFIX, "document", "workspace");
        assertSuggestionsContain(rig, PREFIX + "document ", "focused");
        assertSuggestionsContain(
                rig,
                PREFIX + "document focused ",
                "sfm:classification",
                "sfm:strict_java_navigation",
                "sfm:real_gesture",
                "sfm:branch_boundary",
                "sfm:reciprocity"
        );
        assertSuggestionsContain(
                rig,
                PREFIX + "document focused sfm:strict_java_navigation ",
                "sfm:auto_1_through_8"
        );
        assertSuggestionsContain(
                rig,
                PREFIX + "document focused sfm:strict_java_navigation "
                        + "sfm:auto_1_through_8 0 100000 ",
                "auto"
        );
    }

    private static void assertSuggestionsContain(TestRig rig, String command, String... expected)
            throws ExecutionException, InterruptedException {
        List<String> actual = rig.tree().getCompletionSuggestions(
                        rig.tree().parse(command, rig.source()))
                .get()
                .getList()
                .stream()
                .map(suggestion -> suggestion.getText())
                .toList();
        assertTrue(actual.containsAll(List.of(expected)), () -> "Expected "
                + List.of(expected) + " in " + actual + " for `" + command + "`");
    }

    private static TestRig rig(RecordingHandler handler) throws Exception {
        SFMScreenPanel panel = new SFMScreenPanel() {
            @Override
            public Component title() {
                return Component.literal("Spatial coverage fixture");
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
        };
        SFMScreenMultiplexer workspace = headlessWorkspace(SFMWorkspaceLayout.single(panel));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(workspace, () -> true));
        SFMSpatialCoverageRunAction action = new SFMSpatialCoverageRunAction(handler);
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(
                List.of(Map.entry(
                        new ResourceLocation("sfm", "spatial/coverage/run"),
                        action
                ))
        );
        return new TestRig(workspace, source, tree);
    }

    private static SFMScreenMultiplexer headlessWorkspace(SFMWorkspaceLayout layout) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        SFMScreenMultiplexer workspace =
                (SFMScreenMultiplexer) unsafe.allocateInstance(SFMScreenMultiplexer.class);
        Field layoutField = SFMScreenMultiplexer.class.getDeclaredField("layout");
        layoutField.setAccessible(true);
        layoutField.set(workspace, layout);
        return workspace;
    }

    private static final class RecordingHandler implements SFMSpatialCoverageRunAction.Handler {
        private final java.util.ArrayList<SFMSpatialCoverageRunAction.Request> requests =
                new java.util.ArrayList<>();
        private PanelActionSupport.CapturedPanel target;

        @Override
        public int run(
                PanelActionSupport.CapturedPanel target,
                SFMSpatialCoverageRunAction.Request request,
                java.util.function.Consumer<Component> feedback
        ) {
            this.target = target;
            requests.add(request);
            return 37;
        }
    }

    private record TestRig(
            SFMScreenMultiplexer workspace,
            SFMClientActionSource source,
            SFMClientActionCommandTree tree
    ) {
    }
}
