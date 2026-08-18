package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionDispatcherCompiler;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.action.SFMToastAction;
import ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastLayout;
import ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastQueue;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMWorkspaceToastTests {
    private static final long MILLIS = 1_000_000L;

    @Test
    void replacementUsesFreshIdsAndDelayedDismissCannotTargetTheReplacement() {
        MutableNanoClock clock = new MutableNanoClock();
        SFMWorkspaceToastQueue queue = new SFMWorkspaceToastQueue(clock);
        var presentation = SFMWorkspaceToastQueue.Presentation.actionableStatus(false);

        var first = queue.publish("operation", "Looking up definition...", presentation);
        var replacement = queue.publish("operation", "No symbol is present", presentation);

        assertTrue(replacement.value() > first.value());
        assertTrue(queue.snapshot(first).isEmpty());
        assertEquals(SFMWorkspaceToastQueue.MutationResult.STALE, queue.dismiss(first));
        assertEquals("No symbol is present", queue.snapshot(replacement).orElseThrow().text());
        assertEquals(SFMWorkspaceToastQueue.MutationResult.APPLIED, queue.dismiss(replacement));

        var future = queue.publish("operation", "A later request still appears", presentation);
        assertTrue(future.value() > replacement.value());
        assertEquals("A later request still appears", queue.snapshot(future).orElseThrow().text());
    }

    @Test
    void queueAndTextAreBoundedWithoutSplittingUnicodeCodePoints() {
        MutableNanoClock clock = new MutableNanoClock();
        SFMWorkspaceToastQueue queue = new SFMWorkspaceToastQueue(clock);
        var presentation = SFMWorkspaceToastQueue.Presentation.actionableStatus(false);
        String oversized = "😀".repeat(SFMWorkspaceToastQueue.MAX_TEXT_CODE_POINTS + 20);
        var bounded = queue.publish(null, oversized, presentation);
        String stored = queue.snapshot(bounded).orElseThrow().text();

        assertEquals(SFMWorkspaceToastQueue.MAX_TEXT_CODE_POINTS,
                stored.codePointCount(0, stored.length()));
        assertTrue(stored.endsWith("…"));

        List<SFMWorkspaceToastQueue.ToastId> ids = new ArrayList<>();
        for (int index = 0; index < SFMWorkspaceToastQueue.MAX_TOASTS + 1; index++) {
            ids.add(queue.publish("lane-" + index, "message " + index, presentation));
        }
        assertEquals(SFMWorkspaceToastQueue.MAX_TOASTS, queue.snapshots().size());
        assertTrue(queue.snapshot(ids.get(0)).isEmpty());
        assertEquals("message " + SFMWorkspaceToastQueue.MAX_TOASTS,
                queue.latestSnapshot().orElseThrow().text());
    }

    @Test
    void monotonicActiveTimeDrivesProgressFadeAndExpiry() {
        MutableNanoClock clock = new MutableNanoClock();
        SFMWorkspaceToastQueue queue = new SFMWorkspaceToastQueue(clock);
        var id = queue.publish("scale", "gui scale auto (4)",
                SFMWorkspaceToastQueue.Presentation.workspaceStatus(false));

        var initial = queue.snapshot(id).orElseThrow();
        assertEquals(1.0D, initial.remainingFraction(), 0.00001D);
        assertEquals(0.0F, initial.opacity(), 0.00001F);

        clock.advanceMillis(70);
        var fadingIn = queue.snapshot(id).orElseThrow();
        assertEquals(0.5F, fadingIn.opacity(), 0.01F);
        assertEquals(70 * MILLIS, fadingIn.activeElapsedNanos());

        clock.rewindMillis(20);
        assertEquals(70 * MILLIS, queue.snapshot(id).orElseThrow().activeElapsedNanos());
        clock.advanceMillis(30);
        assertEquals(80 * MILLIS, queue.snapshot(id).orElseThrow().activeElapsedNanos());

        clock.advanceNanos(SFMWorkspaceToastQueue.Presentation.DEFAULT_DURATION_NANOS
                - 80 * MILLIS - 1);
        assertTrue(queue.snapshot(id).isPresent());
        clock.advanceNanos(1);
        queue.tick();
        assertTrue(queue.snapshot(id).isEmpty());
    }

    @Test
    void hoverPinAndChoiceLeaseResumeThePreviouslyRemainingLifetime() {
        MutableNanoClock clock = new MutableNanoClock();
        SFMWorkspaceToastQueue queue = new SFMWorkspaceToastQueue(clock);
        var id = queue.publish("failure", "A readable failure",
                SFMWorkspaceToastQueue.Presentation.actionableStatus(false));
        clock.advanceMillis(600);
        long beforePause = queue.snapshot(id).orElseThrow().remainingNanos();

        queue.setHovered(id);
        clock.advanceMillis(4_000);
        assertEquals(beforePause, queue.snapshot(id).orElseThrow().remainingNanos());
        assertEquals(1.0F, queue.snapshot(id).orElseThrow().opacity());

        queue.setHovered(null);
        clock.advanceMillis(100);
        long afterHover = queue.snapshot(id).orElseThrow().remainingNanos();
        assertEquals(beforePause - 100 * MILLIS, afterHover);

        assertEquals(SFMWorkspaceToastQueue.MutationResult.APPLIED, queue.pin(id));
        clock.advanceMillis(5_000);
        assertEquals(afterHover, queue.snapshot(id).orElseThrow().remainingNanos());
        assertEquals(SFMWorkspaceToastQueue.MutationResult.APPLIED, queue.resume(id));
        clock.advanceMillis(100);
        long afterPin = queue.snapshot(id).orElseThrow().remainingNanos();
        assertEquals(afterHover - 100 * MILLIS, afterPin);

        var lease = queue.acquireInteractionLease(id).orElseThrow();
        clock.advanceMillis(5_000);
        assertEquals(afterPin, queue.snapshot(id).orElseThrow().remainingNanos());
        lease.close();
        clock.advanceMillis(100);
        assertEquals(afterPin - 100 * MILLIS, queue.snapshot(id).orElseThrow().remainingNanos());
        assertFalse(queue.disposed(), "Closing an overlay lease must not dispose its workspace queue");
    }

    @Test
    void exactDismissAndWorkspaceDisposalCloseOwnedChoiceLeases() {
        MutableNanoClock clock = new MutableNanoClock();
        SFMWorkspaceToastQueue queue = new SFMWorkspaceToastQueue(clock);
        var first = queue.publish("one", "first",
                SFMWorkspaceToastQueue.Presentation.actionableStatus(false));
        var second = queue.publish("two", "second",
                SFMWorkspaceToastQueue.Presentation.actionableStatus(false));
        AtomicInteger firstSurfaceCloses = new AtomicInteger();
        var firstLease = queue.acquireInteractionLease(first).orElseThrow();
        firstLease.onToastRemoved(firstSurfaceCloses::incrementAndGet);

        assertEquals(SFMWorkspaceToastQueue.MutationResult.APPLIED, queue.dismiss(first));
        assertEquals(1, firstSurfaceCloses.get());
        assertFalse(firstLease.active());
        assertTrue(queue.snapshot(second).isPresent());

        AtomicInteger secondSurfaceCloses = new AtomicInteger();
        var secondLease = queue.acquireInteractionLease(second).orElseThrow();
        secondLease.onToastRemoved(secondSurfaceCloses::incrementAndGet);
        queue.close();
        assertTrue(queue.disposed());
        assertEquals(1, secondSurfaceCloses.get());
        assertTrue(queue.snapshots().isEmpty());
        assertThrows(IllegalStateException.class, () -> queue.publish(
                "later", "not accepted", SFMWorkspaceToastQueue.Presentation.actionableStatus(false)));
    }

    @Test
    void scaleReplacementRetainsDurationFadeAutoTextAndBoundaryShake() {
        MutableNanoClock clock = new MutableNanoClock();
        SFMWorkspaceToastQueue queue = new SFMWorkspaceToastQueue(clock);
        var first = queue.publish("scale", "gui scale auto (4)",
                SFMWorkspaceToastQueue.Presentation.workspaceStatus(false));
        assertEquals(SFMWorkspaceToastQueue.Presentation.DEFAULT_DURATION_NANOS,
                queue.snapshot(first).orElseThrow().durationNanos());
        assertEquals("gui scale auto (4)", queue.snapshot(first).orElseThrow().text());

        var repeatedBoundary = queue.publish("scale", "gui scale auto (4)",
                SFMWorkspaceToastQueue.Presentation.workspaceStatus(true));
        assertTrue(repeatedBoundary.value() > first.value());
        clock.advanceMillis(30);
        assertNotEquals(0, queue.snapshot(repeatedBoundary).orElseThrow().shakeOffset());
        clock.advanceNanos(SFMWorkspaceToastQueue.Presentation.DEFAULT_SHAKE_NANOS);
        assertEquals(0, queue.snapshot(repeatedBoundary).orElseThrow().shakeOffset());
    }

    @Test
    void rightClickChoicesAddressTheExactToastAndSwitchBetweenStopAndResume() {
        MutableNanoClock clock = new MutableNanoClock();
        SFMWorkspaceToastQueue queue = new SFMWorkspaceToastQueue(clock);
        var id = queue.publish("failure", "failure",
                SFMWorkspaceToastQueue.Presentation.actionableStatus(false));

        assertEquals(List.of(
                        "sfm action invoke sfm:toast/copy " + id.value(),
                        "sfm action invoke sfm:toast/timer/stop " + id.value(),
                        "sfm action invoke sfm:toast/dismiss " + id.value()),
                SFMScreenMultiplexer.workspaceToastChoices(queue.snapshot(id).orElseThrow())
                        .stream().map(choice -> choice.command()).toList());

        queue.pin(id);
        assertEquals("sfm action invoke sfm:toast/timer/resume " + id.value(),
                SFMScreenMultiplexer.workspaceToastChoices(queue.snapshot(id).orElseThrow())
                        .get(1).command());
    }

    @Test
    void canonicalDismissActionRemovesOnlyItsExactLiveId() throws Exception {
        SFMScreenMultiplexer workspace = headlessWorkspace();
        var first = workspace.showWorkspaceToast("one", Component.literal("first"), false);
        var second = workspace.showWorkspaceToast("two", Component.literal("second"), false);
        ResourceLocation actionId = new ResourceLocation("sfm", "toast/dismiss");
        var tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(Map.entry(
                actionId, new SFMToastAction(SFMToastAction.Operation.DISMISS))));
        SFMClientActionSource source = new SFMClientActionSource(
                new SFMClientActionContext(workspace, () -> true, null));

        assertEquals(1, tree.execute(
                "sfm action invoke sfm:toast/dismiss " + first.value(), source));
        assertTrue(workspace.workspaceToastSnapshot(first).isEmpty());
        assertTrue(workspace.workspaceToastSnapshot(second).isPresent());
        assertThrows(CommandSyntaxException.class, () -> tree.execute(
                "sfm action invoke sfm:toast/dismiss " + first.value(), source));
        assertTrue(workspace.workspaceToastSnapshot(second).isPresent());
    }

    @Test
    void logicalLayoutStaysInsideResizedAndGuiScaledViewportsWithHalfOpenHits() {
        SFMWorkspaceToastLayout layout = new SFMWorkspaceToastLayout();
        var one = new SFMWorkspaceToastQueue.ToastId(1);
        var two = new SFMWorkspaceToastQueue.ToastId(2);
        List<SFMWorkspaceToastLayout.Bounds> large = layout.place(320, 180, List.of(
                new SFMWorkspaceToastLayout.Measure(one, 260, 28),
                new SFMWorkspaceToastLayout.Measure(two, 180, 40)));

        assertEquals(2, large.size());
        assertTrue(large.get(1).y() > large.get(0).y(), "Newest toast should be closest to the bottom edge");
        large.forEach(bounds -> {
            assertTrue(bounds.x() >= SFMWorkspaceToastLayout.VIEWPORT_INSET);
            assertTrue(bounds.x() + bounds.width() <= 320 - SFMWorkspaceToastLayout.VIEWPORT_INSET);
            assertTrue(bounds.y() >= SFMWorkspaceToastLayout.VIEWPORT_INSET);
            assertTrue(bounds.y() + bounds.height() <= 180 - SFMWorkspaceToastLayout.BOTTOM_INSET);
            assertTrue(bounds.contains(bounds.x(), bounds.y()));
            assertFalse(bounds.contains(bounds.x() + bounds.width(), bounds.y() + bounds.height() - 1));
        });

        List<SFMWorkspaceToastLayout.Bounds> scaledSmall = layout.place(96, 72, List.of(
                new SFMWorkspaceToastLayout.Measure(two, 300, 24)));
        assertEquals(92, scaledSmall.get(0).width());
        assertTrue(scaledSmall.get(0).x() >= 2);
    }

    private static final class MutableNanoClock implements LongSupplier {
        private long now;

        @Override
        public long getAsLong() {
            return now;
        }

        private void advanceMillis(long millis) {
            advanceNanos(millis * MILLIS);
        }

        private void rewindMillis(long millis) {
            now -= millis * MILLIS;
        }

        private void advanceNanos(long nanos) {
            now += nanos;
        }
    }

    private static SFMScreenMultiplexer headlessWorkspace() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        SFMScreenMultiplexer workspace =
                (SFMScreenMultiplexer) unsafe.allocateInstance(SFMScreenMultiplexer.class);
        Field queueField = SFMScreenMultiplexer.class.getDeclaredField("workspaceToasts");
        queueField.setAccessible(true);
        queueField.set(workspace, new SFMWorkspaceToastQueue());
        return workspace;
    }
}
