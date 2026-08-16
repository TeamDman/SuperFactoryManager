package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMExplorerDocumentRevealCoordinator;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMRevealInExplorerActionTests {
    private static final SFMPath ROOT = SFMPath.parse("file:///D:/repo/");
    private static final SFMPath PATH = SFMPath.parse("file:///D:/repo/src/SFM.java");

    @Test
    void canonicalRegisteredCommandCapturesExactDocumentProvenanceWithoutUsingTitle() throws Exception {
        DocumentPanel document = new DocumentPanel("definitely-not-a-path", addressedSnapshot());
        SFMScreenMultiplexer workspace = headlessWorkspace(SFMWorkspaceLayout.single(document));
        AtomicReference<SFMRevealInExplorerAction.Target> captured = new AtomicReference<>();
        SFMRevealInExplorerAction action = new SFMRevealInExplorerAction(target -> {
            captured.set(target);
            return CompletableFuture.completedFuture(new SFMExplorerDocumentRevealCoordinator.Outcome(
                    new SFMExplorerId("explorer-1"),
                    new SFMWorkspacePanelId(2),
                    ROOT,
                    PATH,
                    false
            ));
        });
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(SFMRevealInExplorerAction.ID, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(workspace, () -> true));
        String command = "sfm action invoke sfm:explorer/reveal";
        String fuzzyQuery = "sfm action invoke reveal";

        assertEquals("sfm:explorer/reveal", SFMRevealInExplorerAction.ID.toString());
        assertTrue(tree.getPaletteSuggestions(fuzzyQuery, tree.parse(fuzzyQuery, source)).join()
                .getList().stream()
                .anyMatch(suggestion -> suggestion.getText().equals(SFMRevealInExplorerAction.ID.toString())));
        assertTrue(SFMClientActionExecutor.isExecutable(tree.parse(command, source)));
        assertEquals(1, tree.execute(command, source));
        assertEquals(PATH, captured.get().document().path());
        assertEquals(ROOT, captured.get().document().authorizedRoot());
        assertEquals(workspace.focusedPanelId(), captured.get().sourcePanelId());
    }

    @Test
    void actionIsUnavailableForLiteralOrStaleDocumentContexts() throws Exception {
        SFMRevealInExplorerAction action = new SFMRevealInExplorerAction(target ->
                CompletableFuture.failedFuture(new AssertionError("must not execute")));
        DocumentPanel literal = new DocumentPanel("D:/repo/src/SFM.java", SFMTextDocumentSnapshot.literal("class X {}"));
        SFMScreenMultiplexer workspace = headlessWorkspace(SFMWorkspaceLayout.single(literal));

        var noPath = action.requirement().resolve(SFMClientActionContext.create(workspace, () -> true));
        var stale = action.requirement().resolve(SFMClientActionContext.create(workspace, () -> false));

        assertFalse(noPath.isAvailable());
        assertTrue(noPath.unavailableReason().getString().contains("no resolver-issued path"));
        assertFalse(stale.isAvailable());
    }

    private static SFMTextDocumentSnapshot addressedSnapshot() {
        return new SFMTextDocumentSnapshot(
                SFMTextDocumentSnapshot.State.READY,
                "class SFM {}",
                SFMTextDocumentSnapshot.MutationCapability.READ_ONLY,
                Optional.of(PATH),
                Optional.of(ROOT),
                Optional.of("sha256:test"),
                OptionalLong.of(12),
                Optional.empty(),
                Optional.of(SFMResolverTextResult.LineEndingKind.NONE),
                Optional.empty(),
                List.of()
        );
    }

    private record DocumentPanel(String name, SFMTextDocumentSnapshot snapshot)
            implements SFMScreenPanel, SFMTextDocumentPanelState {
        @Override public Component title() {
            return Component.literal(name);
        }

        @Override public void render(
                PoseStack poseStack,
                Minecraft minecraft,
                SFMScreenPanelBounds bounds,
                int mouseX,
                int mouseY,
                float partialTick,
                boolean focused
        ) {
        }

        @Override public boolean isReadOnly() {
            return snapshot.readOnly();
        }

        @Override public Optional<SFMTextDocumentSnapshot> documentSnapshot() {
            return Optional.of(snapshot);
        }
    }

    private static SFMScreenMultiplexer headlessWorkspace(SFMWorkspaceLayout layout) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        SFMScreenMultiplexer workspace = (SFMScreenMultiplexer) unsafe.allocateInstance(SFMScreenMultiplexer.class);
        Field layoutField = SFMScreenMultiplexer.class.getDeclaredField("layout");
        layoutField.setAccessible(true);
        layoutField.set(workspace, layout);
        Field widthField = Screen.class.getDeclaredField("width");
        widthField.setAccessible(true);
        widthField.setInt(workspace, 320);
        Field heightField = Screen.class.getDeclaredField("height");
        heightField.setAccessible(true);
        heightField.setInt(workspace, 180);
        return workspace;
    }
}
