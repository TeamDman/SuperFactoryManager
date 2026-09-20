package ca.teamdman.sfm.client.program.signing;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One bounded worker for explicit local key management; no unbounded queue or render-thread KDF. */
public final class ClientSigningUiWorker {
    private static final Executor WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "sfm-signing-key-ui");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private ClientSigningUiWorker() {}
    public static Executor executor() { return WORKER; }
}
