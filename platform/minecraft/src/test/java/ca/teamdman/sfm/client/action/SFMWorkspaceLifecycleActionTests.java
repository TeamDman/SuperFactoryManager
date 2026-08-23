package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelCloseState;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelEntryAffordanceLayout;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMHeadlessWorkspaceTestSupport;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMWorkspaceLifecycleActionTests {
    @AfterEach
    void clearSessions() {
        SFMPanelEntryInteractionSessionService.clearForTests();
        SFMWorkspacePaneCloseSessionService.clearForTests();
    }

    @Test
    void exactEntrySessionFocusesCapturedHiddenEntryAndIsOneShot() throws Exception {
        LifecyclePanel first = panel("first");
        LifecyclePanel second = panel("second");
        LifecyclePanel neighbor = panel("neighbor");
        SFMWorkspaceLayout layout = stackedBeside(first, second, neighbor);
        SFMScreenMultiplexer workspace = SFMHeadlessWorkspaceTestSupport.create(layout);
        SFMWorkspacePanelId firstId = idFor(layout, first);
        SFMWorkspacePanelId secondId = idFor(layout, second);
        assertTrue(workspace.focusPanel(firstId));
        SFMPanelEntryAffordanceLayout.HitRegion hit = hitFor(workspace, firstId, secondId);
        SFMPanelEntryInteractionSessionService.Session session =
                SFMPanelEntryInteractionSessionService.create(workspace, hit).orElseThrow();
        var tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(Map.entry(
                SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_FOCUS,
                new SFMPanelEntryAction(SFMPanelEntryAction.Operation.FOCUS)
        )));
        SFMClientActionSource source = source(workspace, firstId);
        String command = "sfm action invoke " + SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_FOCUS
                + " " + session.commandArgument();

        assertEquals(1, tree.execute(command, source));
        assertEquals(secondId, workspace.focusedPanelId());
        assertThrows(CommandSyntaxException.class, () -> tree.execute(command, source));
    }

    @Test
    void replacementAtCapturedIdCannotReceiveDelayedEntryAction() {
        LifecyclePanel first = panel("first");
        LifecyclePanel hidden = panel("hidden");
        LifecyclePanel neighbor = panel("neighbor");
        SFMWorkspaceLayout layout = stackedBeside(first, hidden, neighbor);
        SFMScreenMultiplexer workspace = SFMHeadlessWorkspaceTestSupport.create(layout);
        SFMWorkspacePanelId firstId = idFor(layout, first);
        SFMPanelEntryInteractionSessionService.Session session =
                SFMPanelEntryInteractionSessionService.create(
                        workspace,
                        hitFor(workspace, firstId, firstId)
                ).orElseThrow();
        var tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(Map.entry(
                SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_CLOSE,
                new SFMPanelEntryAction(SFMPanelEntryAction.Operation.CLOSE)
        )));
        SFMClientActionSource source = source(workspace, firstId);

        assertTrue(workspace.rotateVisibleContent(1));

        assertThrows(CommandSyntaxException.class, () -> tree.execute(
                "sfm action invoke " + SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_CLOSE
                        + " " + session.commandArgument(),
                source
        ));
        assertEquals(3, workspace.panels().size());
        assertEquals(0, first.closeCount());
        assertEquals(0, hidden.closeCount());
        assertEquals(0, neighbor.closeCount());
    }

    @Test
    void rightClickChoiceSurfaceContainsExactEntryActionsAndItsPaneCloseAction() {
        LifecyclePanel first = panel("first");
        LifecyclePanel second = panel("second");
        LifecyclePanel neighbor = panel("neighbor");
        SFMWorkspaceLayout layout = stackedBeside(first, second, neighbor);
        SFMScreenMultiplexer workspace = SFMHeadlessWorkspaceTestSupport.create(layout);
        SFMWorkspacePanelId firstId = idFor(layout, first);
        SFMWorkspacePanelId secondId = idFor(layout, second);
        SFMPanelEntryInteractionSessionService.Session session =
                SFMPanelEntryInteractionSessionService.create(
                        workspace,
                        hitFor(workspace, firstId, secondId)
                ).orElseThrow();

        List<SFMActionChoice> choices = SFMWorkspaceLifecycleActionIds.panelEntryChoices(session);

        assertEquals(List.of(
                SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_FOCUS,
                SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_CLOSE,
                SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_MOVE_LEFT,
                SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_MOVE_RIGHT,
                SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_MOVE_ABOVE,
                SFMWorkspaceLifecycleActionIds.PANEL_ENTRY_MOVE_BELOW,
                SFMWorkspaceLifecycleActionIds.PANE_CLOSE
        ), choices.stream().map(SFMActionChoice::actionId).toList());
        assertTrue(choices.subList(0, 6).stream()
                .allMatch(choice -> choice.command().endsWith(session.commandArgument())));
        assertEquals("sfm action invoke sfm:pane/close", choices.get(6).command());
        assertTrue(choices.stream().allMatch(choice -> choice.displayText().contains(session.stableId())));
    }

    @Test
    void panePreflightReportsExactCountsAndCancellationIsNoOp() throws Exception {
        LifecyclePanel dirty = new LifecyclePanel("dirty", new SFMPanelCloseState(true, false));
        LifecyclePanel readOnly = new LifecyclePanel("read-only", SFMPanelCloseState.cleanReadOnly());
        LifecyclePanel neighbor = panel("neighbor");
        SFMWorkspaceLayout layout = stackedBeside(dirty, readOnly, neighbor);
        SFMScreenMultiplexer workspace = SFMHeadlessWorkspaceTestSupport.create(layout);
        SFMWorkspacePanelId dirtyId = idFor(layout, dirty);
        workspace.setPanelReopenRecipe(dirtyId, recipeFor(dirty));
        AtomicReference<String> title = new AtomicReference<>();
        AtomicReference<List<SFMActionChoice>> choices = new AtomicReference<>();
        AtomicReference<Runnable> closeListener = new AtomicReference<>();
        SFMClosePaneAction preflight = new SFMClosePaneAction(
                SFMClosePaneAction.Phase.PREFLIGHT,
                (context, heading, offered, onClose) -> {
                    title.set(heading.getString());
                    choices.set(offered);
                    closeListener.set(onClose);
                }
        );
        SFMClosePaneAction confirm = new SFMClosePaneAction(SFMClosePaneAction.Phase.CONFIRM);
        var tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(SFMWorkspaceLifecycleActionIds.PANE_CLOSE, preflight),
                Map.entry(SFMWorkspaceLifecycleActionIds.PANE_CLOSE_CONFIRM, confirm)
        ));
        SFMClientActionSource source = source(workspace, dirtyId);

        assertEquals(1, tree.execute(
                "sfm action invoke " + SFMWorkspaceLifecycleActionIds.PANE_CLOSE,
                source
        ));
        assertEquals(3, workspace.panels().size());
        assertTrue(title.get().contains(
                "total=2, dirty=1, read-only=1, recoverable=1, non-recoverable=1"));
        assertEquals(2, choices.get().size());
        assertEquals(SFMWorkspaceLifecycleActionIds.PANE_CLOSE_CONFIRM, choices.get().get(0).actionId());
        assertEquals(new ResourceLocation("sfm", "palette/close"), choices.get().get(1).actionId());
        String canceledConfirmation = choices.get().get(0).command();

        closeListener.get().run();

        assertThrows(CommandSyntaxException.class, () -> tree.execute(canceledConfirmation, source));
        assertEquals(3, workspace.panels().size());
        assertEquals(0, dirty.closeCount());
        assertEquals(0, readOnly.closeCount());

        assertEquals(1, tree.execute(
                "sfm action invoke " + SFMWorkspaceLifecycleActionIds.PANE_CLOSE,
                source
        ));
        assertEquals(1, tree.execute(choices.get().get(0).command(), source));
        assertEquals(1, workspace.panels().size());
        assertSame(neighbor, workspace.panels().get(0));
        assertEquals(1, dirty.closeCount());
        assertEquals(1, readOnly.closeCount());
    }

    @Test
    void cleanRecoverableSingleEntryPaneClosesWithoutConfirmation() throws Exception {
        LifecyclePanel target = panel("target");
        LifecyclePanel neighbor = panel("neighbor");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(target, neighbor);
        SFMScreenMultiplexer workspace = SFMHeadlessWorkspaceTestSupport.create(layout);
        SFMWorkspacePanelId targetId = idFor(layout, target);
        workspace.setPanelReopenRecipe(targetId, recipeFor(target));
        AtomicInteger presented = new AtomicInteger();
        SFMClosePaneAction action = new SFMClosePaneAction(
                SFMClosePaneAction.Phase.PREFLIGHT,
                (context, title, choices, closeListener) -> presented.incrementAndGet()
        );
        var tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(Map.entry(
                SFMWorkspaceLifecycleActionIds.PANE_CLOSE,
                action
        )));

        assertEquals(1, tree.execute(
                "sfm action invoke " + SFMWorkspaceLifecycleActionIds.PANE_CLOSE,
                source(workspace, targetId)
        ));
        assertEquals(0, presented.get());
        assertEquals(1, target.closeCount());
        assertEquals(1, workspace.panels().size());
        assertSame(neighbor, workspace.panels().get(0));
    }

    private static LifecyclePanel panel(String name) {
        return new LifecyclePanel(name, SFMPanelCloseState.cleanEditable());
    }

    private static SFMWorkspaceLayout stackedBeside(
            SFMScreenPanel first,
            SFMScreenPanel second,
            SFMScreenPanel neighbor
    ) {
        return SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.stack(
                        0,
                        SFMWorkspaceLayout.panel(first),
                        SFMWorkspaceLayout.panel(second)
                ),
                SFMWorkspaceLayout.panel(neighbor)
        ));
    }

    private static SFMWorkspacePanelId idFor(SFMWorkspaceLayout layout, SFMScreenPanel panel) {
        return layout.panels().stream()
                .filter(entry -> entry.panel() == panel)
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static SFMPanelEntryAffordanceLayout.HitRegion hitFor(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId paneEntry,
            SFMWorkspacePanelId target
    ) {
        return SFMPanelEntryAffordanceLayout.layout(
                        new SFMScreenPanelBounds(0, 0, 100, 100),
                        workspace.panelStackId(paneEntry).orElseThrow(),
                        workspace.panelSlotEntries(paneEntry),
                        workspace.focusedPanelId()
                ).stream()
                .filter(hit -> hit.entryId().equals(target))
                .findFirst()
                .orElseThrow();
    }

    private static SFMClientActionSource source(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId panelId
    ) {
        return new SFMClientActionSource(new SFMClientActionContext(
                workspace,
                () -> true,
                panelId
        ));
    }

    private static SFMPanelReopenRecipe recipeFor(LifecyclePanel panel) {
        return new SFMPanelReopenRecipe() {
            @Override
            public ResourceLocation sceneTypeId() {
                return new ResourceLocation("sfm", "test/lifecycle-action");
            }

            @Override
            public SFMScreenPanel reopen() {
                return new LifecyclePanel(panel.name(), panel.closeState());
            }
        };
    }

    private static final class LifecyclePanel implements SFMScreenPanel {
        private final String name;
        private final SFMPanelCloseState closeState;
        private final AtomicInteger closeCount = new AtomicInteger();

        private LifecyclePanel(String name, SFMPanelCloseState closeState) {
            this.name = name;
            this.closeState = closeState;
        }

        private String name() {
            return name;
        }

        private int closeCount() {
            return closeCount.get();
        }

        @Override
        public Component title() {
            return Component.literal(name);
        }

        @Override
        public SFMPanelCloseState closeState() {
            return closeState;
        }

        @Override
        public void closed() {
            closeCount.incrementAndGet();
        }

        @Override
        public void render(
                PoseStack poseStack,
                Minecraft minecraft,
                SFMScreenPanelBounds bounds,
                int mouseX,
                int mouseY,
                float partialTick,
                boolean focused
        ) {
        }
    }
}
