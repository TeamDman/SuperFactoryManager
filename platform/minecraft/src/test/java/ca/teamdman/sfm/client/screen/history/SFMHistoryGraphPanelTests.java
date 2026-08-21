package ca.teamdman.sfm.client.screen.history;

import ca.teamdman.sfm.client.action.SFMTrajectoryMachineAction;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMHistoryGraphTestFixture;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelWidget;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelWidgetHost;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMHistoryGraphPanelTests {
    @Test
    void panelLateSubscriptionCoalescesToLatestPushAndKeepsStableControls() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        SFMHistoryGraphTestFixture.MutableController controller =
                new SFMHistoryGraphTestFixture.MutableController("episode-a");
        runtime.register(controller);
        List<String> actions = new ArrayList<>();
        SFMHistoryGraphPanel panel = new SFMHistoryGraphPanel(
                runtime,
                SFMEntitySelector.parseCanonical(SFMEntitySelector.Domain.EPISODE, "focused"),
                actions::add
        );
        panel.opened(null, new SFMScreenPanelBounds(0, 0, 640, 360),
                SFMWorkspacePanelContext.unhosted(new SFMWorkspacePanelId(7)));

        controller.advanceOutsideAction();
        runtime.publish(controller.machineId());
        controller.advanceOutsideAction();
        runtime.publish(controller.machineId());
        assertEquals(-1, panel.catalogRevision(), "updates are coalesced until the client tick");
        long expectedRevision = runtime.snapshotEvent().revision();
        awaitPanel(panel, () -> panel.catalogRevision() == expectedRevision
                && panel.model().selected().isPresent());

        assertEquals(expectedRevision, panel.catalogRevision());
        assertEquals(Optional.of("episode-a"), panel.displayedMachineId());
        assertTrue(panel.model().selected().orElseThrow().instructionPointer());
        SFMPanelWidgetHost host = panel.widgetHost().orElseThrow();
        List<SFMPanelWidget> controls = host.children();
        int actionCount = SFMTrajectoryMachineAction.Kind.values().length;
        assertEquals(actionCount, controls.size());
        assertEquals(actionCount,
                new HashSet<>(controls.stream().map(SFMPanelWidget::elementId).toList()).size());
        controls.forEach(control -> assertTrue(control.actionDraft().orElseThrow()
                .contains("id(episode-a)")));

        panel.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        String firstNarration = panel.narration().getString();
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0));
        assertNotEquals(firstNarration, panel.narration().getString());
        assertTrue(panel.narration().getString().contains(
                panel.model().selected().orElseThrow().narration()));

        assertTrue(host.focus(controls.get(0).elementId()));
        assertTrue(host.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(controls.get(0).actionDraft().orElseThrow(), actions.get(0));
        panel.closed();
    }

    @Test
    void pinnedPanelWaitsForItsEpisodeInsteadOfLeakingToTheActiveOne() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        runtime.register(new SFMHistoryGraphTestFixture.MutableController("episode-a"));
        SFMHistoryGraphPanel panel = new SFMHistoryGraphPanel(
                runtime,
                SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, "episode-b"),
                ignored -> { }
        );
        panel.opened(null, new SFMScreenPanelBounds(0, 0, 320, 240),
                SFMWorkspacePanelContext.unhosted(new SFMWorkspacePanelId(8)));
        long initialRevision = runtime.snapshotEvent().revision();
        awaitPanel(panel, () -> panel.catalogRevision() == initialRevision);

        assertTrue(panel.displayedMachineId().isEmpty());
        runtime.register(new SFMHistoryGraphTestFixture.MutableController("episode-b"));
        long episodeBRevision = runtime.snapshotEvent().revision();
        awaitPanel(panel, () -> panel.catalogRevision() == episodeBRevision
                && panel.displayedMachineId().equals(Optional.of("episode-b"))
                && panel.model().selected().isPresent());
        assertEquals(Optional.of("episode-b"), panel.displayedMachineId());
        panel.closed();
    }

    @Test
    void reopeningTheSamePanelAcceptsAnEqualRevisionBaseline() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        runtime.register(new SFMHistoryGraphTestFixture.MutableController("episode-a"));
        SFMHistoryGraphPanel panel = new SFMHistoryGraphPanel(
                runtime,
                SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, "episode-a"),
                ignored -> { }
        );
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 320, 240);
        SFMWorkspacePanelContext context =
                SFMWorkspacePanelContext.unhosted(new SFMWorkspacePanelId(10));

        panel.opened(null, bounds, context);
        long revision = runtime.snapshotEvent().revision();
        awaitPanel(panel, () -> panel.catalogRevision() == revision
                && panel.model().selected().isPresent());
        panel.closed();
        assertEquals(-1, panel.catalogRevision());

        panel.opened(null, bounds, context);
        awaitPanel(panel, () -> panel.catalogRevision() == revision
                && panel.model().selected().isPresent());
        assertEquals(Optional.of("episode-a"), panel.displayedMachineId());
        panel.closed();
    }

    @Test
    void callbackHandedOffBeforeCloseCannotRepopulateTheClosedPanel() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        runtime.register(new SFMHistoryGraphTestFixture.MutableController("episode-a"));
        Queue<Runnable> handedOffCallbacks = new ConcurrentLinkedQueue<>();
        SFMHistoryGraphPanel panel = new SFMHistoryGraphPanel(
                runtime,
                SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, "episode-a"),
                ignored -> { },
                handedOffCallbacks::add
        );
        panel.opened(null, new SFMScreenPanelBounds(0, 0, 320, 240),
                SFMWorkspacePanelContext.unhosted(new SFMWorkspacePanelId(11)));
        awaitCondition(() -> !handedOffCallbacks.isEmpty());

        panel.closed();
        Runnable callback;
        while ((callback = handedOffCallbacks.poll()) != null) callback.run();
        panel.tick();

        assertEquals(-1, panel.catalogRevision());
        assertTrue(panel.displayedMachineId().isEmpty());
        assertTrue(panel.model().rows().isEmpty());
    }

    @Test
    void panelTickStartsButNeverWaitsForDeferredProjection() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        SFMHistoryGraphRuntime.MachineSnapshot contracts =
                SFMHistoryGraphTestFixture.snapshot("episode-a", 0);
        var expectedPresentation = contracts.presentation();
        Queue<Runnable> projectionTasks = new ConcurrentLinkedQueue<>();
        AtomicInteger projections = new AtomicInteger();
        SFMHistoryGraphRuntime.MachineSnapshot deferred =
                SFMHistoryGraphTestFixture.snapshotWithProjection(
                        contracts,
                        projectionTasks::add,
                        () -> {
                            projections.incrementAndGet();
                            return expectedPresentation;
                        }
                );
        runtime.register(new SFMHistoryGraphRuntime.Controller() {
            @Override
            public String machineId() {
                return deferred.machineId();
            }

            @Override
            public SFMHistoryGraphRuntime.MachineSnapshot snapshot() {
                return deferred;
            }

            @Override
            public SFMHistoryGraphRuntime.OperationResult apply(SFMHistoryGraphRuntime.Operation operation) {
                return SFMHistoryGraphRuntime.OperationResult.noChange("test controller");
            }
        });
        SFMHistoryGraphPanel panel = new SFMHistoryGraphPanel(
                runtime,
                SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, "episode-a"),
                ignored -> { }
        );
        panel.opened(null, new SFMScreenPanelBounds(0, 0, 320, 240),
                SFMWorkspacePanelContext.unhosted(new SFMWorkspacePanelId(9)));

        awaitPanel(panel, () -> projectionTasks.size() == 1);
        assertEquals(0, projections.get());
        assertTrue(panel.model().rows().isEmpty());

        projectionTasks.remove().run();
        awaitPanel(panel, () -> panel.model().selected().isPresent());
        assertEquals(1, projections.get());
        panel.closed();
    }

    @Test
    void narrowPanelsWrapControlsAndMinimumPanelsCompactThemWithoutClipping() {
        SFMScreenPanelBounds panel = new SFMScreenPanelBounds(10, 20, 120, 240);
        List<SFMScreenPanelBounds> controls = SFMHistoryGraphPanel.controlBounds(panel, 40, 7);

        assertEquals(7, controls.size());
        assertTrue(controls.stream().allMatch(bounds -> bounds.width() > 0 && bounds.height() > 0));
        assertTrue(controls.stream().allMatch(bounds -> bounds.x() >= panel.x()
                && bounds.x() + bounds.width() <= panel.x() + panel.width()));
        assertTrue(controls.stream().map(SFMScreenPanelBounds::y).distinct().count() > 1);

        SFMScreenPanelBounds minimumPanel = new SFMScreenPanelBounds(10, 20, 48, 48);
        List<SFMScreenPanelBounds> compact = SFMHistoryGraphPanel.controlBounds(
                minimumPanel,
                50,
                7
        );
        assertEquals(7, compact.size());
        assertTrue(compact.stream().allMatch(bounds -> bounds.width() > 0 && bounds.height() > 0));
        assertTrue(compact.stream().allMatch(bounds -> bounds.x() >= minimumPanel.x()
                && bounds.x() + bounds.width() <= minimumPanel.x() + minimumPanel.width()));
        assertTrue(compact.stream().allMatch(bounds -> bounds.y() >= minimumPanel.y()
                && bounds.y() + bounds.height() <= minimumPanel.y() + minimumPanel.height()));
    }

    @Test
    void everyMinimumPanelControlIsReachableByKeyboardAndMouse() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        runtime.register(new SFMHistoryGraphTestFixture.MutableController("episode-a"));
        List<String> actions = new ArrayList<>();
        SFMHistoryGraphPanel panel = new SFMHistoryGraphPanel(
                runtime,
                SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, "episode-a"),
                actions::add
        );
        SFMScreenPanelBounds minimumPanel = new SFMScreenPanelBounds(10, 20, 48, 48);
        panel.opened(null, minimumPanel,
                SFMWorkspacePanelContext.unhosted(new SFMWorkspacePanelId(12)));
        awaitPanel(panel, () -> panel.model().selected().isPresent());
        List<SFMScreenPanelBounds> layout = SFMHistoryGraphPanel.controlBounds(
                minimumPanel,
                50,
                SFMTrajectoryMachineAction.Kind.values().length
        );
        panel.layoutControls(minimumPanel, 50);
        SFMPanelWidgetHost host = panel.widgetHost().orElseThrow();

        HashSet<Object> keyboardVisited = new HashSet<>();
        for (int index = 0; index < host.children().size(); index++) {
            assertTrue(host.changeFocus(true));
            keyboardVisited.add(host.focusedElementId().orElseThrow());
        }
        assertEquals(SFMTrajectoryMachineAction.Kind.values().length, keyboardVisited.size());

        for (SFMScreenPanelBounds control : layout) {
            assertTrue(host.mouseClicked(
                    control.x() + control.width() / 2.0D,
                    control.y() + control.height() / 2.0D,
                    GLFW.GLFW_MOUSE_BUTTON_LEFT
            ));
        }
        assertEquals(SFMTrajectoryMachineAction.Kind.values().length, actions.size());
        assertFalse(actions.stream().anyMatch(String::isBlank));
        panel.closed();
    }

    private static void awaitPanel(SFMHistoryGraphPanel panel, BooleanSupplier condition) {
        awaitCondition(() -> {
            panel.tick();
            return condition.getAsBoolean();
        });
        panel.tick();
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for asynchronous History Graph work");
    }
}
