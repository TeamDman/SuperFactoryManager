package ca.teamdman.sfm.client.control;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMClientThreadGateTests {
    private final ExecutorService completionExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "test-control-worker");
        thread.setDaemon(true);
        return thread;
    });

    @AfterEach
    void stopCompletionExecutor() throws InterruptedException {
        completionExecutor.shutdownNow();
        assertTrue(completionExecutor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void workRunsExactlyOnceOnClientExecutorAndCompletesOnControlWorker() throws Exception {
        ManualExecutor clientExecutor = new ManualExecutor();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> workThread = new AtomicReference<>();
        AtomicReference<String> completionThread = new AtomicReference<>();
        try (SFMClientThreadGate gate = new SFMClientThreadGate(clientExecutor, completionExecutor, 1)) {
            CompletableFuture<Integer> result = gate.submit(() -> {
                workThread.set(Thread.currentThread().getName());
                return calls.incrementAndGet();
            });
            CompletableFuture<Void> observed = result.thenAccept(ignored ->
                    completionThread.set(Thread.currentThread().getName()));

            Thread clientThread = new Thread(clientExecutor::runAll, "test-minecraft-client");
            clientThread.start();
            clientThread.join(Duration.ofSeconds(5).toMillis());

            assertEquals(1, result.get(5, TimeUnit.SECONDS));
            observed.get(5, TimeUnit.SECONDS);
            assertEquals(1, calls.get());
            assertEquals("test-minecraft-client", workThread.get());
            assertEquals("test-control-worker", completionThread.get());
        }
    }

    @Test
    void capacityIsBoundedUntilQueuedWorkRunsOrIsCancelled() throws Exception {
        ManualExecutor clientExecutor = new ManualExecutor();
        try (SFMClientThreadGate gate = new SFMClientThreadGate(clientExecutor, completionExecutor, 1)) {
            CompletableFuture<Integer> first = gate.submit(() -> 1);
            CompletableFuture<Integer> rejected = gate.submit(() -> 2);

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> rejected.get(5, TimeUnit.SECONDS)
            );
            assertInstanceOf(SFMClientThreadGate.CapacityExceededException.class, failure.getCause());

            assertTrue(first.cancel(false));
            CompletableFuture<Integer> replacement = gate.submit(() -> 3);
            clientExecutor.runAll();
            assertEquals(3, replacement.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void cancellationBeforeClientExecutionPreventsMutation() {
        ManualExecutor clientExecutor = new ManualExecutor();
        AtomicInteger mutations = new AtomicInteger();
        try (SFMClientThreadGate gate = new SFMClientThreadGate(clientExecutor, completionExecutor, 1)) {
            CompletableFuture<Integer> result = gate.submit(mutations::incrementAndGet);

            assertTrue(result.cancel(false));
            clientExecutor.runAll();

            assertTrue(result.isCancelled());
            assertEquals(0, mutations.get());
        }
    }

    @Test
    void shutdownRejectsNewWorkAndResolvesPendingWorkWithoutMutation() throws Exception {
        ManualExecutor clientExecutor = new ManualExecutor();
        AtomicInteger mutations = new AtomicInteger();
        SFMClientThreadGate gate = new SFMClientThreadGate(clientExecutor, completionExecutor, 1);
        CompletableFuture<Integer> pending = gate.submit(mutations::incrementAndGet);

        gate.close();

        ExecutionException pendingFailure = assertThrows(
                ExecutionException.class,
                () -> pending.get(5, TimeUnit.SECONDS)
        );
        assertInstanceOf(SFMClientThreadGate.ShuttingDownException.class, pendingFailure.getCause());
        clientExecutor.runAll();
        assertEquals(0, mutations.get());

        CompletableFuture<Integer> rejected = gate.submit(mutations::incrementAndGet);
        ExecutionException rejectedFailure = assertThrows(
                ExecutionException.class,
                () -> rejected.get(5, TimeUnit.SECONDS)
        );
        assertInstanceOf(SFMClientThreadGate.ShuttingDownException.class, rejectedFailure.getCause());
    }

    @Test
    void clientExecutorRejectionIsTypedAndReleasesCapacity() throws Exception {
        try (SFMClientThreadGate gate = new SFMClientThreadGate(
                ignored -> {
                    throw new RejectedExecutionException("client stopped");
                },
                completionExecutor,
                1
        )) {
            CompletableFuture<Integer> first = gate.submit(() -> 1);
            ExecutionException firstFailure = assertThrows(
                    ExecutionException.class,
                    () -> first.get(5, TimeUnit.SECONDS)
            );
            assertInstanceOf(SFMClientThreadGate.ClientUnavailableException.class, firstFailure.getCause());

            CompletableFuture<Integer> second = gate.submit(() -> 2);
            ExecutionException secondFailure = assertThrows(
                    ExecutionException.class,
                    () -> second.get(5, TimeUnit.SECONDS)
            );
            assertInstanceOf(SFMClientThreadGate.ClientUnavailableException.class, secondFailure.getCause());
        }
    }

    private static final class ManualExecutor implements java.util.concurrent.Executor {
        private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }
    }
}
