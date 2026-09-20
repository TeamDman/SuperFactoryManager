package ca.teamdman.sfm.client.control;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Bounded handoff from control-protocol workers to Minecraft's client thread.
 *
 * <p>The returned future is normally completed by {@code completionExecutor},
 * never inline on the client thread. Cancelling that future while its task is
 * still queued prevents the task from running.</p>
 */
final class SFMClientThreadGate implements AutoCloseable {
    private final Executor clientExecutor;
    private final Executor completionExecutor;
    private final Semaphore capacity;
    private final Set<PendingTask<?>> tasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();

    SFMClientThreadGate(
            Executor clientExecutor,
            Executor completionExecutor,
            int maximumPendingTasks
    ) {
        this.clientExecutor = Objects.requireNonNull(clientExecutor);
        this.completionExecutor = Objects.requireNonNull(completionExecutor);
        if (maximumPendingTasks <= 0) {
            throw new IllegalArgumentException("maximumPendingTasks must be positive");
        }
        this.capacity = new Semaphore(maximumPendingTasks);
    }

    <T> CompletableFuture<T> submit(Supplier<T> work) {
        Objects.requireNonNull(work);
        PendingTask<T> task;
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failedFuture(new ShuttingDownException());
            }
            if (!capacity.tryAcquire()) {
                return failedFuture(new CapacityExceededException());
            }
            task = new PendingTask<>(work);
            tasks.add(task);
        }
        task.result.whenComplete((ignored, failure) -> {
            if (task.result.isCancelled()) {
                task.cancelBeforeExecution();
            }
        });

        try {
            clientExecutor.execute(task);
        } catch (RejectedExecutionException failure) {
            task.stop(closed.get()
                    ? new ShuttingDownException()
                    : new ClientUnavailableException(failure));
        } catch (RuntimeException failure) {
            task.stop(new ClientUnavailableException(failure));
        }
        return task.result;
    }

    @Override
    public void close() {
        List<PendingTask<?>> stopping;
        synchronized (lifecycleLock) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            stopping = List.copyOf(tasks);
        }
        for (PendingTask<?> task : stopping) {
            task.stop(new ShuttingDownException());
        }
    }

    private void completeOnWorker(Runnable completion) {
        try {
            completionExecutor.execute(completion);
        } catch (RejectedExecutionException failure) {
            // The owner may be tearing its worker pool down. Never strand a
            // caller merely to preserve completion-thread affinity at shutdown.
            completion.run();
        }
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable failure) {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(failure);
        return result;
    }

    static final class CapacityExceededException extends RejectedExecutionException {
        CapacityExceededException() {
            super("Minecraft client-thread control queue is full");
        }
    }

    static final class ClientUnavailableException extends RejectedExecutionException {
        ClientUnavailableException(Throwable cause) {
            super("Minecraft client thread rejected control work", cause);
        }
    }

    static final class ShuttingDownException extends RejectedExecutionException {
        ShuttingDownException() {
            super("Minecraft client control service is stopping");
        }
    }

    private enum State {
        PENDING,
        RUNNING,
        COMPLETED,
        CANCELLED
    }

    private final class PendingTask<T> implements Runnable {
        private final Supplier<T> work;
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
        private final AtomicBoolean released = new AtomicBoolean();

        private PendingTask(Supplier<T> work) {
            this.work = work;
        }

        @Override
        public void run() {
            if (!state.compareAndSet(State.PENDING, State.RUNNING)) {
                return;
            }

            T value = null;
            Throwable failure = null;
            try {
                value = work.get();
            } catch (Throwable caught) {
                failure = caught;
            } finally {
                state.set(State.COMPLETED);
                release();
            }

            T completedValue = value;
            Throwable completedFailure = failure;
            completeOnWorker(() -> {
                if (completedFailure == null) {
                    result.complete(completedValue);
                } else {
                    result.completeExceptionally(completedFailure);
                }
            });
        }

        private void cancelBeforeExecution() {
            if (state.compareAndSet(State.PENDING, State.CANCELLED)) {
                release();
            }
        }

        private void stop(Throwable failure) {
            if (state.compareAndSet(State.PENDING, State.CANCELLED)) {
                release();
                completeOnWorker(() -> result.completeExceptionally(failure));
                return;
            }
            if (state.get() == State.RUNNING) {
                completeOnWorker(() -> result.completeExceptionally(failure));
            }
        }

        private void release() {
            if (!released.compareAndSet(false, true)) {
                return;
            }
            tasks.remove(this);
            capacity.release();
        }
    }
}
