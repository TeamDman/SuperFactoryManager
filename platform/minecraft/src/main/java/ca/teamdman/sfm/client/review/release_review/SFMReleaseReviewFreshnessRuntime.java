package ca.teamdman.sfm.client.review.release_review;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** One bounded background probe for the open review lease, not one subprocess per panel/frame. */
public final class SFMReleaseReviewFreshnessRuntime implements AutoCloseable {
    private record Key(Path path, long epoch, List<SFMReleaseReviewV1.RepositoryBinding> bindings) { }
    @FunctionalInterface interface Probe {
        SFMReleaseReviewFreshness.Evidence inspect(Path file, List<SFMReleaseReviewV1.RepositoryBinding> bindings)
                throws Exception;
    }
    private static final SFMReleaseReviewFreshnessRuntime INSTANCE = new SFMReleaseReviewFreshnessRuntime();
    private final ExecutorService worker;
    private final Probe probe;
    private Key key;
    private long generation;
    private Future<?> pending;
    private SFMReleaseReviewFreshness.Evidence evidence = SFMReleaseReviewFreshness.unavailable("Not checked");

    public static SFMReleaseReviewFreshnessRuntime get() { return INSTANCE; }
    public SFMReleaseReviewFreshnessRuntime() {
        this(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfm-release-review-freshness");
            thread.setDaemon(true);
            return thread;
        }), SFMReleaseReviewFreshnessRuntime::inspect);
    }
    SFMReleaseReviewFreshnessRuntime(ExecutorService worker, Probe probe) {
        this.worker = worker;
        this.probe = probe;
    }
    private static Key key(SFMReleaseReviewRuntime.Snapshot snapshot) {
        return snapshot.path().isEmpty() ? null : new Key(snapshot.path().orElseThrow(), snapshot.openEpoch(),
                snapshot.document().orElseThrow().repositoryBindings());
    }
    public synchronized SFMReleaseReviewFreshness.Evidence evidence(SFMReleaseReviewRuntime.Snapshot snapshot) {
        return java.util.Objects.equals(key, key(snapshot)) ? evidence : SFMReleaseReviewFreshness.checking();
    }
    /** Called by tick/open or an explicit action; never performs filesystem work on the caller. */
    public synchronized void ensure(SFMReleaseReviewRuntime.Snapshot snapshot, boolean force) {
        Key requested = key(snapshot);
        if (requested == null || (!force && requested.equals(key))) return;
        key = requested;
        long requestGeneration = ++generation;
        if (pending != null) pending.cancel(true);
        evidence = SFMReleaseReviewFreshness.checking();
        pending = worker.submit(() -> {
            SFMReleaseReviewFreshness.Evidence result;
            try { result = probe.inspect(requested.path, requested.bindings); }
            catch (Exception failure) {
                result = SFMReleaseReviewFreshness.unavailable(failure.getClass().getSimpleName() + ": " + failure.getMessage());
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            }
            synchronized (this) {
                if (requestGeneration == generation && requested.equals(key)) evidence = result;
            }
        });
    }
    @Override public synchronized void close() {
        generation++;
        key = null;
        if (pending != null) pending.cancel(true);
        worker.shutdownNow();
    }

    private static SFMReleaseReviewFreshness.Evidence inspect(Path file,
            List<SFMReleaseReviewV1.RepositoryBinding> bindings) throws Exception {
        String executable = System.getProperty("sfm.releaseReviewToolchainExecutable", "sfm-propagate-changes.exe");
        Path request = java.nio.file.Files.createTempFile("sfm-review-observed-bindings-", ".json");
        try {
            java.nio.file.Files.writeString(request, SFMReleaseReviewV1Codec.writeSourceBindings(bindings), StandardCharsets.UTF_8);
            return inspectRequest(file, bindings, executable, request);
        } finally { java.nio.file.Files.deleteIfExists(request); }
    }

    private static SFMReleaseReviewFreshness.Evidence inspectRequest(Path file,
            List<SFMReleaseReviewV1.RepositoryBinding> bindings, String executable, Path request) throws Exception {
        Process process = new ProcessBuilder(executable, "--output-format", "json", "review", "session", "freshness-of",
                "--file", file.toString(), "--request-file", request.toString()).directory(file.getParent().toFile()).start();
        process.getOutputStream().close();
        var stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream(), 8 * 1024 * 1024));
        var stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream(), 64 * 1024));
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) throw new IOException("Freshness check exceeded 30 seconds; retry");
            String output = stdout.get(2, TimeUnit.SECONDS);
            String errors = stderr.get(2, TimeUnit.SECONDS);
            if (process.exitValue() != 0) throw new IOException("Companion freshness check failed ("
                    + process.exitValue() + "): " + errors.substring(0, Math.min(2048, errors.length())));
            return SFMReleaseReviewFreshness.parse(output, bindings);
        } finally {
            // Only this probe's owned descendants; never terminates another client or unrelated process.
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            process.getInputStream().close();
            process.getErrorStream().close();
            stdout.cancel(true);
            stderr.cancel(true);
        }
    }
    private static String read(InputStream input, int limit) {
        try (input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) >= 0;) {
                if (bytes.size() + count > limit) throw new IOException("Freshness output exceeds " + limit + " bytes");
                bytes.write(buffer, 0, count);
            }
            return bytes.toString(StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
