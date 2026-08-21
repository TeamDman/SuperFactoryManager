package ca.teamdman.sfm.client.history;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMHistoryGraphRuntimeTests {
    @Test
    void lateSubscriberReceivesLatestSnapshotAndPushesRemainOrdered() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        SFMHistoryGraphTestFixture.MutableController controller =
                new SFMHistoryGraphTestFixture.MutableController("episode-a");
        runtime.register(controller);
        controller.advanceOutsideAction();
        runtime.publish(controller.machineId());

        List<SFMHistoryGraphRuntime.CatalogEvent> events = new CopyOnWriteArrayList<>();
        SFMHistoryGraphRuntime.Subscription subscription = runtime.subscribe(events::add);
        controller.advanceOutsideAction();
        runtime.publish(controller.machineId());
        awaitCondition(() -> events.size() == 2);
        subscription.close();
        controller.advanceOutsideAction();
        runtime.publish(controller.machineId());

        assertEquals(2, events.size());
        assertEquals(1, events.get(0).activeMachine().orElseThrow().revision());
        assertEquals(2, events.get(1).activeMachine().orElseThrow().revision());
        assertTrue(events.get(1).revision() > events.get(0).revision());
    }

    @Test
    void selectorsAreSetValuedDeterministicAndFocusedIsExplicit() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        SFMHistoryGraphTestFixture.MutableController a =
                new SFMHistoryGraphTestFixture.MutableController("episode-a");
        SFMHistoryGraphTestFixture.MutableController b =
                new SFMHistoryGraphTestFixture.MutableController("episode-b");
        runtime.register(b);
        runtime.register(a);

        SFMEntitySelector allExceptB = SFMEntitySelector.parseCanonical(
                SFMEntitySelector.Domain.EPISODE,
                "difference(all,id(episode-b))"
        );
        assertEquals(List.of("episode-a"), runtime.resolveSnapshots(allExceptB, Optional.empty()).stream()
                .map(SFMHistoryGraphRuntime.MachineSnapshot::machineId).toList());

        SFMHistoryGraphRuntime.BatchResult focused = runtime.execute(
                SFMEntitySelector.parseCanonical(SFMEntitySelector.Domain.EPISODE, "focused"),
                Optional.of("episode-b"),
                new SFMHistoryGraphRuntime.Step()
        );
        assertEquals(List.of("episode-b"), focused.targets().stream()
                .map(SFMHistoryGraphRuntime.TargetResult::machineId).toList());
        assertTrue(b.operations().get(0) instanceof SFMHistoryGraphRuntime.Step);
        assertTrue(a.operations().isEmpty());
    }

    @Test
    void operationsPublishControllerSnapshotAndNoMatchFailsClosed() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        SFMHistoryGraphTestFixture.MutableController controller =
                new SFMHistoryGraphTestFixture.MutableController("episode-a");
        runtime.register(controller);
        List<SFMHistoryGraphRuntime.CatalogEvent> events = new CopyOnWriteArrayList<>();
        runtime.subscribe(events::add);

        SFMHistoryGraphRuntime.BatchResult applied = runtime.execute(
                SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, "episode-a"),
                Optional.empty(),
                new SFMHistoryGraphRuntime.Run(3)
        );
        SFMHistoryGraphRuntime.BatchResult missing = runtime.execute(
                SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, "missing"),
                Optional.empty(),
                new SFMHistoryGraphRuntime.Plan()
        );

        assertTrue(applied.appliedAny());
        assertEquals(1, controller.revision());
        awaitCondition(() -> events.stream()
                .anyMatch(event -> event.machine("episode-a")
                        .filter(snapshot -> snapshot.revision() == 1)
                        .isPresent()));
        assertEquals(1, events.get(events.size() - 1).machine("episode-a").orElseThrow().revision());
        assertTrue(missing.targets().isEmpty());
        assertFalse(missing.diagnostics().isEmpty());
    }

    @Test
    void appliedResultIsNotDuplicatedOrRejectedWhenPostApplySnapshotFails() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        SnapshotFailingController controller = new SnapshotFailingController("episode-a");
        runtime.register(controller);

        SFMHistoryGraphRuntime.BatchResult result = runtime.execute(
                SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, "episode-a"),
                Optional.empty(),
                new SFMHistoryGraphRuntime.Step()
        );

        assertEquals(1, controller.applyCount());
        assertEquals(1, result.targets().size());
        assertEquals(
                SFMHistoryGraphRuntime.OperationStatus.APPLIED,
                result.targets().get(0).result().status()
        );
        assertEquals(1, result.diagnostics().size());
        assertTrue(result.diagnostics().get(0).contains(
                "publication failed after APPLIED: snapshot unavailable"));
    }

    @Test
    void failedBaselineSnapshotDoesNotLeakASubscription() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        SnapshotFailingController controller = new SnapshotFailingController("episode-a");
        runtime.register(controller);
        AtomicInteger deliveries = new AtomicInteger();
        controller.setFailSnapshot(true);

        assertThrows(IllegalStateException.class, () -> runtime.subscribe(event ->
                deliveries.incrementAndGet()));

        controller.setFailSnapshot(false);
        runtime.publish(controller.machineId());
        assertEquals(0, deliveries.get());
    }

    @Test
    void failingListenerIsIsolatedAndCallbacksNeverHoldRuntimeMonitor() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        SFMHistoryGraphTestFixture.MutableController controller =
                new SFMHistoryGraphTestFixture.MutableController("episode-a");
        runtime.register(controller);

        AtomicInteger failingDeliveries = new AtomicInteger();
        runtime.subscribe(event -> {
            failingDeliveries.incrementAndGet();
            throw new IllegalStateException("listener failure");
        });
        List<SFMHistoryGraphRuntime.CatalogEvent> healthyEvents = new CopyOnWriteArrayList<>();
        List<Boolean> callbackHeldRuntimeMonitor = new CopyOnWriteArrayList<>();
        runtime.subscribe(event -> {
            callbackHeldRuntimeMonitor.add(Thread.holdsLock(runtime));
            healthyEvents.add(event);
        });

        SFMHistoryGraphRuntime.BatchResult result = runtime.execute(
                SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, "episode-a"),
                Optional.empty(),
                new SFMHistoryGraphRuntime.Step()
        );

        assertEquals(1, result.targets().size());
        assertEquals(
                SFMHistoryGraphRuntime.OperationStatus.APPLIED,
                result.targets().get(0).result().status()
        );
        assertTrue(result.diagnostics().isEmpty());
        awaitCondition(() -> failingDeliveries.get() == 2 && healthyEvents.size() == 2);
        assertEquals(2, failingDeliveries.get());
        assertEquals(List.of(1L, 2L), healthyEvents.stream()
                .map(SFMHistoryGraphRuntime.CatalogEvent::revision)
                .toList());
        assertEquals(List.of(false, false), callbackHeldRuntimeMonitor);
    }

    @Test
    void blockedListenerCannotStallPublicationOrAnotherListener() throws Exception {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        SFMHistoryGraphTestFixture.MutableController controller =
                new SFMHistoryGraphTestFixture.MutableController("episode-a");
        runtime.register(controller);

        CountDownLatch baselineDelivered = new CountDownLatch(1);
        CountDownLatch liveEntered = new CountDownLatch(1);
        CountDownLatch releaseLive = new CountDownLatch(1);
        List<Long> revisions = new CopyOnWriteArrayList<>();
        List<Boolean> callbackHeldRuntimeMonitor = new CopyOnWriteArrayList<>();
        SFMHistoryGraphRuntime.Subscription blocked = runtime.subscribe(event -> {
            callbackHeldRuntimeMonitor.add(Thread.holdsLock(runtime));
            revisions.add(event.revision());
            if (event.revision() == 1) baselineDelivered.countDown();
            if (event.revision() == 2) {
                liveEntered.countDown();
                await(releaseLive);
            }
        });
        assertTrue(baselineDelivered.await(2, TimeUnit.SECONDS));

        List<Long> healthyRevisions = new CopyOnWriteArrayList<>();
        SFMHistoryGraphRuntime.Subscription healthy = runtime.subscribe(event ->
                healthyRevisions.add(event.revision()));
        ExecutorService publisher = Executors.newSingleThreadExecutor();
        try {
            awaitCondition(() -> healthyRevisions.equals(List.of(1L)));

            controller.advanceOutsideAction();
            Future<?> publishing = publisher.submit(() -> runtime.publish(controller.machineId()));
            assertTrue(liveEntered.await(2, TimeUnit.SECONDS));
            publishing.get(2, TimeUnit.SECONDS);
            assertEquals(List.of(1L, 2L), revisions);
            awaitCondition(() -> healthyRevisions.equals(List.of(1L, 2L)));

            releaseLive.countDown();
            assertEquals(List.of(1L, 2L), revisions);
            assertEquals(List.of(false, false), callbackHeldRuntimeMonitor);
        } finally {
            releaseLive.countDown();
            blocked.close();
            healthy.close();
            publisher.shutdownNow();
            assertTrue(publisher.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void presentationIsLazyMemoizedAndCanBeObservedWithoutWaiting() {
        SFMHistoryGraphRuntime.MachineSnapshot contracts =
                SFMHistoryGraphTestFixture.snapshot("episode-a", 0);
        ArrayDeque<Runnable> scheduled = new ArrayDeque<>();
        AtomicInteger projections = new AtomicInteger();
        SFMHistoryGraphRuntime.MachineSnapshot snapshot =
                SFMHistoryGraphTestFixture.snapshotWithProjection(
                        contracts,
                        scheduled::addLast,
                        () -> {
                            projections.incrementAndGet();
                            return contracts.presentation();
                        }
                );

        assertEquals(0, projections.get());
        assertTrue(snapshot.presentationState() instanceof SFMHistoryGraphRuntime.PresentationPending);
        assertTrue(snapshot.presentationState() instanceof SFMHistoryGraphRuntime.PresentationPending);
        assertEquals(1, scheduled.size());
        assertEquals(0, projections.get());

        scheduled.removeFirst().run();
        SFMHistoryGraphRuntime.PresentationReady ready =
                (SFMHistoryGraphRuntime.PresentationReady) snapshot.presentationState();
        assertEquals(1, projections.get());
        assertSame(ready.presentation(), snapshot.presentation());
        assertSame(snapshot.presentation(), snapshot.presentation());
        assertEquals(1, projections.get());
    }

    @Test
    void projectionSchedulerRetainsOnlyTheNewestQueuedRevisionPerMachine() throws Exception {
        SFMHistoryGraphRuntime.LatestProjectionExecutor executor =
                new SFMHistoryGraphRuntime.LatestProjectionExecutor(1, 2, runnable -> {
                    Thread thread = new Thread(runnable, "history-projection-test");
                    thread.setDaemon(true);
                    return thread;
                });
        var contracts0 = SFMHistoryGraphTestFixture.snapshot("episode-a", 0);
        var contracts1 = SFMHistoryGraphTestFixture.snapshot("episode-a", 1);
        var contracts2 = SFMHistoryGraphTestFixture.snapshot("episode-a", 2);
        var expected = contracts0.presentation();
        CountDownLatch firstProjectionEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstProjection = new CountDownLatch(1);
        AtomicInteger projections = new AtomicInteger();
        var snapshot0 = SFMHistoryGraphTestFixture.snapshotWithProjection(
                contracts0,
                executor,
                () -> {
                    projections.incrementAndGet();
                    firstProjectionEntered.countDown();
                    await(releaseFirstProjection);
                    return expected;
                }
        );
        var snapshot1 = SFMHistoryGraphTestFixture.snapshotWithProjection(
                contracts1,
                executor,
                () -> {
                    projections.incrementAndGet();
                    return expected;
                }
        );
        var snapshot2 = SFMHistoryGraphTestFixture.snapshotWithProjection(
                contracts2,
                executor,
                () -> {
                    projections.incrementAndGet();
                    return expected;
                }
        );

        assertTrue(snapshot0.presentationState() instanceof SFMHistoryGraphRuntime.PresentationPending);
        assertTrue(firstProjectionEntered.await(2, TimeUnit.SECONDS));
        assertTrue(snapshot1.presentationState() instanceof SFMHistoryGraphRuntime.PresentationPending);
        assertTrue(snapshot2.presentationState() instanceof SFMHistoryGraphRuntime.PresentationPending);
        assertTrue(snapshot1.presentationState() instanceof SFMHistoryGraphRuntime.PresentationFailed);

        releaseFirstProjection.countDown();
        awaitCondition(() -> snapshot2.presentationState()
                instanceof SFMHistoryGraphRuntime.PresentationReady);
        assertEquals(2, projections.get(), "the superseded middle revision must never project");
    }

    @Test
    void projectionQueueEvictionCompletesTheEvictedFuture() throws Exception {
        SFMHistoryGraphRuntime.LatestProjectionExecutor executor =
                new SFMHistoryGraphRuntime.LatestProjectionExecutor(1, 2, runnable -> {
                    Thread thread = new Thread(runnable, "history-projection-capacity-test");
                    thread.setDaemon(true);
                    return thread;
                });
        var expected = SFMHistoryGraphTestFixture.snapshot("expected", 0).presentation();
        CountDownLatch activeEntered = new CountDownLatch(1);
        CountDownLatch releaseActive = new CountDownLatch(1);
        AtomicInteger projections = new AtomicInteger();
        var active = SFMHistoryGraphTestFixture.snapshotWithProjection(
                SFMHistoryGraphTestFixture.snapshot("active", 0),
                executor,
                () -> {
                    projections.incrementAndGet();
                    activeEntered.countDown();
                    await(releaseActive);
                    return expected;
                }
        );
        var evicted = SFMHistoryGraphTestFixture.snapshotWithProjection(
                SFMHistoryGraphTestFixture.snapshot("evicted", 0),
                executor,
                () -> {
                    projections.incrementAndGet();
                    return expected;
                }
        );
        var retainedA = SFMHistoryGraphTestFixture.snapshotWithProjection(
                SFMHistoryGraphTestFixture.snapshot("retained-a", 0),
                executor,
                () -> {
                    projections.incrementAndGet();
                    return expected;
                }
        );
        var retainedB = SFMHistoryGraphTestFixture.snapshotWithProjection(
                SFMHistoryGraphTestFixture.snapshot("retained-b", 0),
                executor,
                () -> {
                    projections.incrementAndGet();
                    return expected;
                }
        );

        try {
            assertTrue(active.presentationState() instanceof SFMHistoryGraphRuntime.PresentationPending);
            assertTrue(activeEntered.await(2, TimeUnit.SECONDS));
            assertTrue(evicted.presentationState() instanceof SFMHistoryGraphRuntime.PresentationPending);
            assertTrue(retainedA.presentationState() instanceof SFMHistoryGraphRuntime.PresentationPending);
            assertTrue(retainedB.presentationState() instanceof SFMHistoryGraphRuntime.PresentationPending);

            SFMHistoryGraphRuntime.PresentationFailed failure =
                    (SFMHistoryGraphRuntime.PresentationFailed) evicted.presentationState();
            assertTrue(failure.message().contains("capacity"));
            assertEquals(1, projections.get(), "queued projections must not run while the worker is blocked");

            releaseActive.countDown();
            awaitCondition(() -> retainedA.presentationState()
                    instanceof SFMHistoryGraphRuntime.PresentationReady);
            awaitCondition(() -> retainedB.presentationState()
                    instanceof SFMHistoryGraphRuntime.PresentationReady);
            assertEquals(3, projections.get(), "the evicted projection must never execute");
        } finally {
            releaseActive.countDown();
        }
    }

    @Test
    void registrationIdentityAndActiveSelectionAreValidated() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        SFMHistoryGraphTestFixture.MutableController a =
                new SFMHistoryGraphTestFixture.MutableController("episode-a");
        runtime.register(a);
        assertThrows(IllegalArgumentException.class, () -> runtime.register(a));
        assertThrows(IllegalArgumentException.class, () -> runtime.setActiveMachine("missing"));
        assertThrows(IllegalArgumentException.class, () -> runtime.execute(
                SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, "anything"),
                Optional.empty(),
                new SFMHistoryGraphRuntime.Plan()
        ));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for deterministic test coordination");
            }
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for deterministic test coordination", interruption);
        }
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for asynchronous History Graph work");
    }

    private static final class SnapshotFailingController implements SFMHistoryGraphRuntime.Controller {
        private final String machineId;
        private long revision;
        private int applyCount;
        private boolean failSnapshot;

        private SnapshotFailingController(String machineId) {
            this.machineId = machineId;
        }

        @Override
        public String machineId() {
            return machineId;
        }

        @Override
        public SFMHistoryGraphRuntime.MachineSnapshot snapshot() {
            if (failSnapshot) throw new IllegalStateException("snapshot unavailable");
            return SFMHistoryGraphTestFixture.snapshot(machineId, revision);
        }

        @Override
        public SFMHistoryGraphRuntime.OperationResult apply(SFMHistoryGraphRuntime.Operation operation) {
            applyCount++;
            revision++;
            failSnapshot = true;
            return SFMHistoryGraphRuntime.OperationResult.applied("operation applied");
        }

        private int applyCount() {
            return applyCount;
        }

        private void setFailSnapshot(boolean failSnapshot) {
            this.failSnapshot = failSnapshot;
        }
    }
}
