package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lazy bounded bridge to {@code review surface generate}. All filesystem and
 * process work runs on owned daemon threads. Successful surfaces are cached by
 * immutable input identity and registered for later comment source projection.
 */
public final class SFMReleaseReviewSurfaceRuntime implements AutoCloseable {
    public static final String EXECUTABLE_PROPERTY = "sfm.review.surfaceExecutable";
    public static final String DEFAULT_EXECUTABLE = "sfm-propagate-changes.exe";
    private static final SFMReleaseReviewSurfaceRuntime INSTANCE = new SFMReleaseReviewSurfaceRuntime();

    public record Configuration(
            String executable,
            Duration timeout,
            Duration shutdownGrace,
            int maximumStdoutBytes,
            int maximumStderrBytes
    ) {
        public Configuration {
            executable = Objects.requireNonNull(executable, "executable").strip();
            if (executable.isEmpty()) throw new IllegalArgumentException("Surface executable must not be blank");
            timeout = positive(timeout, "timeout");
            shutdownGrace = Objects.requireNonNull(shutdownGrace, "shutdownGrace");
            if (shutdownGrace.isNegative()) throw new IllegalArgumentException("shutdownGrace must not be negative");
            if (maximumStdoutBytes <= 0 || maximumStderrBytes <= 0) {
                throw new IllegalArgumentException("Surface process output bounds must be positive");
            }
        }

        public static Configuration defaults() {
            String configured = System.getProperty(EXECUTABLE_PROPERTY, "").strip();
            return new Configuration(
                    configured.isEmpty() ? DEFAULT_EXECUTABLE : configured,
                    Duration.ofSeconds(15),
                    Duration.ofSeconds(1),
                    SFMReleaseReviewSurfaceV1.DEFAULT_MAX_OUTPUT_BYTES * 2,
                    64 * 1024
            );
        }
    }

    public record ProcessResult(int exitCode, String stdout, String stderr, boolean stdoutOverflow,
                                boolean stderrOverflow) {
        public ProcessResult {
            Objects.requireNonNull(stdout, "stdout");
            Objects.requireNonNull(stderr, "stderr");
        }

        public ProcessResult(int exitCode, String stdout, String stderr) {
            this(exitCode, stdout, stderr, false, false);
        }
    }

    @FunctionalInterface
    interface Generator {
        ProcessResult invoke(
                Configuration configuration,
                String encodedRequest,
                SFMExplorerCancellationToken cancellation
        ) throws Exception;
    }

    public record GeneratedDocumentIdentity(SFMPath path, String snapshotSha256) {
        public GeneratedDocumentIdentity {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(snapshotSha256, "snapshotSha256");
            if (!snapshotSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Generated document snapshot SHA-256 is invalid");
            }
        }
    }

    public record GeneratedDocument(
            GeneratedDocumentIdentity identity,
            SFMPath authorizedRoot,
            SFMReleaseReviewSurfaceV1.Surface surface
    ) {
        public GeneratedDocument {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(authorizedRoot, "authorizedRoot");
            Objects.requireNonNull(surface, "surface");
        }

        SFMTextDocumentSnapshot snapshot() {
            SFMTextDocumentSnapshot pinned = SFMTextDocumentSnapshot.pinned(
                    identity.path(), authorizedRoot, surface.text(), identity.snapshotSha256(),
                    Optional.empty(), Optional.empty());
            if (!pinned.ready() || surface.diagnostics().isEmpty()) return pinned;
            List<String> diagnostics = surface.diagnostics().stream()
                    .map(SFMReleaseReviewSurfaceV1.Diagnostic::displayText)
                    .toList();
            return new SFMTextDocumentSnapshot(
                    pinned.state(), pinned.text(), pinned.mutationCapability(), pinned.path(), pinned.authorizedRoot(),
                    pinned.sha256(), pinned.byteLength(), pinned.lastModified(), pinned.lineEndingKind(),
                    pinned.targetRange(), diagnostics, pinned.sourceRootIdentity(), pinned.analysisIdentity());
        }
    }

    public record Telemetry(long submitted, long cacheHits, long completed, long failed, long cancelled,
                            int cachedSurfaces, int registeredDocuments) {
    }

    public static final class SurfaceUnavailableException extends IOException {
        private final String code;

        SurfaceUnavailableException(String code, String message) {
            super(message);
            this.code = code;
        }

        SurfaceUnavailableException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() { return code; }
    }

    private final Configuration configuration;
    private final ExecutorService executor;
    private final Generator generator;
    private final boolean ownsExecutor;
    private final AtomicLong requestIds = new AtomicLong();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong cancelled = new AtomicLong();
    private final Map<String, CompletableFuture<GeneratedDocument>> cache = new ConcurrentHashMap<>();
    private final Map<GeneratedDocumentIdentity, SFMReleaseReviewSurfaceV1.Surface> sourceMaps =
            new ConcurrentHashMap<>();

    private SFMReleaseReviewSurfaceRuntime() {
        this(Configuration.defaults(), Executors.newFixedThreadPool(2, daemonThreads("sfm-review-surface")),
                new ProcessGenerator(), true);
    }

    SFMReleaseReviewSurfaceRuntime(
            Configuration configuration,
            ExecutorService executor,
            Generator generator,
            boolean ownsExecutor
    ) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.ownsExecutor = ownsExecutor;
    }

    public static SFMReleaseReviewSurfaceRuntime get() {
        return INSTANCE;
    }

    public Telemetry telemetry() {
        return new Telemetry(submitted.get(), cacheHits.get(), completed.get(), failed.get(), cancelled.get(),
                cache.size(), sourceMaps.size());
    }

    public CompletableFuture<SFMTextDocumentSnapshot> loadDocument(
            SFMReleaseReviewSurfaceV1.Recipe recipe,
            long reviewGeneration,
            SFMExplorerCancellationToken cancellation
    ) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(cancellation, "cancellation");
        if (reviewGeneration <= 0) throw new IllegalArgumentException("Review generation must be positive");
        return generate(recipe, reviewGeneration, cancellation).handle((document, failure) -> {
            if (failure == null) return document.snapshot();
            Throwable cause = unwrap(failure);
            if (cause instanceof CancellationException) throw new CompletionException(cause);
            String code = cause instanceof SurfaceUnavailableException unavailable
                    ? unavailable.code() : "review.surface.unavailable";
            SFMPath path = generatedPath(recipe);
            return SFMTextDocumentSnapshot.failure(
                    SFMTextDocumentSnapshot.State.UNAVAILABLE,
                    path,
                    generatedRoot(recipe),
                    List.of(code + ": " + boundedMessage(cause))
            );
        });
    }

    public CompletableFuture<GeneratedDocument> generate(
            SFMReleaseReviewSurfaceV1.Recipe recipe,
            long reviewGeneration,
            SFMExplorerCancellationToken cancellation
    ) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(cancellation, "cancellation");
        if (reviewGeneration <= 0) throw new IllegalArgumentException("Review generation must be positive");
        cancellation.throwIfCancelled();
        String cacheKey = cacheKey(recipe);
        CompletableFuture<GeneratedDocument> existing = cache.get(cacheKey);
        if (existing != null) {
            cacheHits.incrementAndGet();
            return existing;
        }
        CompletableFuture<GeneratedDocument> created = CompletableFuture.supplyAsync(() -> {
            cancellation.throwIfCancelled();
            long requestId = nextRequestId();
            SFMReleaseReviewSurfaceV1.Request request = recipe.request(requestId, reviewGeneration);
            submitted.incrementAndGet();
            try {
                ProcessResult process = generator.invoke(
                        configuration,
                        SFMReleaseReviewSurfaceJsonCodec.encodeRequest(request),
                        cancellation
                );
                cancellation.throwIfCancelled();
                if (process.stdoutOverflow() || process.stdout().getBytes(StandardCharsets.UTF_8).length
                        > configuration.maximumStdoutBytes()) {
                    throw new SurfaceUnavailableException(
                            "review.surface.stdout-limit", "Review-surface process exceeded stdout limit");
                }
                if (process.stderrOverflow()) {
                    throw new SurfaceUnavailableException(
                            "review.surface.stderr-limit", "Review-surface process exceeded stderr limit");
                }
                if (process.exitCode() != 0) {
                    throw new SurfaceUnavailableException(
                            "review.surface.process-failed",
                            "Review-surface process exited " + process.exitCode() + ": " + bounded(process.stderr(), 512));
                }
                SFMReleaseReviewSurfaceV1.Surface surface;
                try {
                    surface = SFMReleaseReviewSurfaceJsonCodec.decodeSurface(process.stdout());
                    surface.validateAgainst(request);
                } catch (IllegalArgumentException invalid) {
                    throw new SurfaceUnavailableException(
                            "review.surface.invalid-output", "Review-surface process returned invalid or stale output",
                            invalid);
                }
                if (!surface.complete()) {
                    String diagnostic = surface.diagnostics().stream().findFirst()
                            .map(SFMReleaseReviewSurfaceV1.Diagnostic::displayText)
                            .orElse("producer returned an incomplete surface");
                    throw new SurfaceUnavailableException("review.surface.incomplete", diagnostic);
                }
                GeneratedDocument answer = new GeneratedDocument(
                        new GeneratedDocumentIdentity(
                                generatedPath(recipe),
                                SFMReleaseReviewSurfaceV1.snapshotSha256(surface.textSha256())),
                        generatedRoot(recipe),
                        surface
                );
                sourceMaps.put(answer.identity(), surface);
                completed.incrementAndGet();
                SFM.LOGGER.info(
                        "SFM_REVIEW_SURFACE_GENERATED pair={} kind={} outcome={} mappings={} regions={}",
                        recipe.filePair().id(), recipe.surfaceKind().wireName(), surface.outcome().wireName(),
                        surface.mappings().size(), surface.regions().size());
                return answer;
            } catch (CancellationException cancellationFailure) {
                cancelled.incrementAndGet();
                throw cancellationFailure;
            } catch (TimeoutException timeout) {
                failed.incrementAndGet();
                throw new CompletionException(new SurfaceUnavailableException(
                        "review.surface.timeout", "Review-surface process timed out", timeout));
            } catch (SurfaceUnavailableException unavailable) {
                failed.incrementAndGet();
                throw new CompletionException(unavailable);
            } catch (Exception failure) {
                failed.incrementAndGet();
                throw new CompletionException(new SurfaceUnavailableException(
                        "review.surface.transport-failed", "Review-surface process invocation failed", failure));
            }
        }, executor);
        CompletableFuture<GeneratedDocument> raced = cache.putIfAbsent(cacheKey, created);
        if (raced != null) {
            created.cancel(false);
            cacheHits.incrementAndGet();
            return raced;
        }
        created.whenComplete((ignored, failure) -> {
            if (failure != null) cache.remove(cacheKey, created);
        });
        return created;
    }

    public Optional<SFMReleaseReviewSurfaceV1.Surface> sourceMap(SFMTextDocumentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.ready() || snapshot.path().isEmpty() || snapshot.sha256().isEmpty()) return Optional.empty();
        try {
            return Optional.ofNullable(sourceMaps.get(new GeneratedDocumentIdentity(
                    snapshot.path().orElseThrow(), snapshot.sha256().orElseThrow())));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public List<SFMReleaseReviewSurfaceV1.SourceRange> projectToSources(
            SFMTextDocumentSnapshot snapshot,
            SFMReleaseReviewSurfaceV1.Utf8Range selected
    ) {
        return sourceMap(snapshot).map(surface -> surface.sourceRangesFor(selected)).orElseGet(List::of);
    }

    public String stableCacheIdentity(SFMReleaseReviewSurfaceV1.Recipe recipe) {
        return cacheKey(recipe);
    }

    private String cacheKey(SFMReleaseReviewSurfaceV1.Recipe recipe) {
        return SFMReleaseReviewSurfaceV1.sha256(
                SFMReleaseReviewSurfaceJsonCodec.encodeRequest(recipe.request(1, 1)));
    }

    private SFMPath generatedRoot(SFMReleaseReviewSurfaceV1.Recipe recipe) {
        return new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                "review-surface",
                "generated",
                List.of(cacheKey(recipe).substring("sha256:".length(), "sha256:".length() + 16)),
                Optional.empty(),
                true
        );
    }

    private SFMPath generatedPath(SFMReleaseReviewSurfaceV1.Recipe recipe) {
        SFMPath root = generatedRoot(recipe);
        return new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                root.scheme(),
                root.authority(),
                List.of(root.segments().get(0), recipe.surfaceKind().wireName() + ".diff"),
                Optional.empty(),
                false
        );
    }

    private long nextRequestId() {
        long value = requestIds.incrementAndGet();
        if (value <= 0) throw new IllegalStateException("Review-surface request identity exhausted");
        return value;
    }

    @Override
    public void close() {
        if (ownsExecutor) executor.shutdownNow();
    }

    private static final class ProcessGenerator implements Generator {
        @Override
        public ProcessResult invoke(
                Configuration configuration,
                String encodedRequest,
                SFMExplorerCancellationToken cancellation
        ) throws Exception {
            Path requestFile = Files.createTempFile("sfm-review-surface-", ".json");
            Process process = null;
            try {
                Files.writeString(requestFile, encodedRequest, StandardCharsets.UTF_8);
                process = new ProcessBuilder(
                        configuration.executable(),
                        "--output-format", "json",
                        "review", "surface", "generate",
                        "--request-file", requestFile.toString()
                ).start();
                BoundedRead stdout = new BoundedRead(process.getInputStream(), configuration.maximumStdoutBytes());
                BoundedRead stderr = new BoundedRead(process.getErrorStream(), configuration.maximumStderrBytes());
                Thread stdoutThread = daemonThread("sfm-review-surface-stdout", stdout);
                Thread stderrThread = daemonThread("sfm-review-surface-stderr", stderr);
                stdoutThread.start();
                stderrThread.start();
                long deadline = System.nanoTime() + configuration.timeout().toNanos();
                while (process.isAlive()) {
                    if (cancellation.isCancelled()) {
                        terminateTree(process, configuration.shutdownGrace());
                        throw new CancellationException("Review-surface request was cancelled");
                    }
                    if (System.nanoTime() >= deadline) {
                        terminateTree(process, configuration.shutdownGrace());
                        throw new TimeoutException("Review-surface process timed out");
                    }
                    process.waitFor(20, TimeUnit.MILLISECONDS);
                }
                stdoutThread.join(Math.max(1, configuration.shutdownGrace().toMillis()));
                stderrThread.join(Math.max(1, configuration.shutdownGrace().toMillis()));
                if (stdoutThread.isAlive() || stderrThread.isAlive()) {
                    terminateTree(process, Duration.ZERO);
                    throw new IOException("Review-surface output readers did not finish");
                }
                if (stdout.failure != null) throw stdout.failure;
                if (stderr.failure != null) throw stderr.failure;
                return new ProcessResult(
                        process.exitValue(),
                        strictUtf8(stdout.bytes(), "stdout"),
                        strictUtf8(stderr.bytes(), "stderr"),
                        stdout.overflow,
                        stderr.overflow
                );
            } finally {
                if (process != null && process.isAlive()) terminateTree(process, configuration.shutdownGrace());
                Files.deleteIfExists(requestFile);
            }
        }
    }

    private static final class BoundedRead implements Runnable {
        private final InputStream input;
        private final int maximum;
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        private volatile boolean overflow;
        private volatile IOException failure;

        private BoundedRead(InputStream input, int maximum) {
            this.input = input;
            this.maximum = maximum;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[8192];
            try (input) {
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    int room = maximum - captured.size();
                    if (room > 0) captured.write(buffer, 0, Math.min(room, count));
                    if (count > room) overflow = true;
                }
            } catch (IOException error) {
                failure = error;
            }
        }

        private byte[] bytes() { return captured.toByteArray(); }
    }

    private static void terminateTree(Process process, Duration grace) throws InterruptedException {
        ProcessHandle root = process.toHandle();
        List<ProcessHandle> descendants = root.descendants().toList();
        for (int index = descendants.size() - 1; index >= 0; index--) descendants.get(index).destroy();
        process.destroy();
        if (process.isAlive() && !grace.isZero()) process.waitFor(grace.toMillis(), TimeUnit.MILLISECONDS);
        for (int index = descendants.size() - 1; index >= 0; index--) {
            if (descendants.get(index).isAlive()) descendants.get(index).destroyForcibly();
        }
        if (process.isAlive()) process.destroyForcibly();
    }

    private static String strictUtf8(byte[] bytes, String label) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) {
            throw new IOException("Review-surface " + label + " is not UTF-8", invalid);
        }
    }

    private static java.util.concurrent.ThreadFactory daemonThreads(String base) {
        AtomicInteger sequence = new AtomicInteger();
        return action -> daemonThread(base + "-" + sequence.incrementAndGet(), action);
    }

    private static Thread daemonThread(String name, Runnable action) {
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        return thread;
    }

    private static Duration positive(Duration value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(label + " must be positive");
        return value;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable answer = failure;
        while ((answer instanceof CompletionException || answer instanceof java.util.concurrent.ExecutionException)
                && answer.getCause() != null) answer = answer.getCause();
        return answer;
    }

    private static String boundedMessage(Throwable failure) {
        String message = failure.getMessage();
        return bounded(message == null || message.isBlank() ? failure.getClass().getSimpleName() : message, 512);
    }

    private static String bounded(String value, int maximum) {
        String normalized = Objects.requireNonNullElse(value, "").replace('\r', ' ').replace('\n', ' ').strip();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }
}
