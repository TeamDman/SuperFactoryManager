package ca.teamdman.sfm.gametest.puppet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded nonblocking child-process harness for self-orchestrating puppets. */
public final class SFMExternalCliPuppetProcess implements AutoCloseable {
    public static final String EXECUTABLE_PROPERTY = "sfm.controlCliExecutable";
    private static final int MAXIMUM_STREAM_BYTES = 1024 * 1024;
    private static final AtomicLong READER_ORDINAL = new AtomicLong(1);
    private static final ExecutorService READERS = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "sfm-puppet-cli-reader-" + READER_ORDINAL.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });

    public record Completed(
            List<String> argv,
            long processId,
            int exitCode,
            String stdout,
            String stderr,
            long durationMillis
    ) {
        public Completed {
            argv = List.copyOf(argv);
            Objects.requireNonNull(stdout, "stdout");
            Objects.requireNonNull(stderr, "stderr");
        }
    }

    private final List<String> argv;
    private final Process process;
    private final CompletableFuture<String> stdout;
    private final CompletableFuture<String> stderr;
    private final long startedAtNanos = System.nanoTime();
    private Completed completed;

    private SFMExternalCliPuppetProcess(List<String> argv, Process process) {
        this.argv = List.copyOf(argv);
        this.process = process;
        stdout = CompletableFuture.supplyAsync(() -> drain(process.getInputStream()), READERS);
        stderr = CompletableFuture.supplyAsync(() -> drain(process.getErrorStream()), READERS);
    }

    public static SFMExternalCliPuppetProcess start(List<String> argv) {
        Objects.requireNonNull(argv, "argv");
        if (argv.isEmpty()) throw new IllegalArgumentException("CLI argv must contain an executable");
        try {
            Process process = new ProcessBuilder(argv).start();
            process.getOutputStream().close();
            return new SFMExternalCliPuppetProcess(argv, process);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not launch external SFM CLI: " + argv.get(0), failure);
        }
    }

    public Optional<Completed> poll() {
        if (completed != null) return Optional.of(completed);
        if (process.isAlive() || !stdout.isDone() || !stderr.isDone()) return Optional.empty();
        completed = new Completed(
                argv,
                process.pid(),
                process.exitValue(),
                stdout.join(),
                stderr.join(),
                (System.nanoTime() - startedAtNanos) / 1_000_000L
        );
        return Optional.of(completed);
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public void close() {
        if (!process.isAlive()) return;
        process.destroy();
        if (process.isAlive()) process.destroyForcibly();
    }

    private static String drain(InputStream stream) {
        try (stream; ByteArrayOutputStream retained = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                total += read;
                int remaining = MAXIMUM_STREAM_BYTES - retained.size();
                if (remaining > 0) retained.write(buffer, 0, Math.min(remaining, read));
            }
            String text = retained.toString(StandardCharsets.UTF_8);
            if (total > retained.size()) {
                text += "\n... " + (total - retained.size()) + " bytes omitted ...\n";
            }
            return text;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not drain external SFM CLI output", failure);
        }
    }
}
