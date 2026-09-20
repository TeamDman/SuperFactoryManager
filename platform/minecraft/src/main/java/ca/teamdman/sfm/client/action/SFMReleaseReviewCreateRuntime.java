package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Nonblocking bridge from the ordinary in-game action to the complete Rust Git producer. */
final class SFMReleaseReviewCreateRuntime {
    static final String EXECUTABLE_PROPERTY = "sfm.releaseReviewToolchainExecutable";
    private static final int MAXIMUM_RETAINED_OUTPUT_BYTES = 64 * 1024;
    private static final java.util.concurrent.atomic.AtomicLong CAPTURE_IDS = new java.util.concurrent.atomic.AtomicLong();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sfm-release-review-create");
        thread.setDaemon(true);
        return thread;
    });

    record Request(
            Path file,
            String lane,
            String before,
            String candidate,
            Optional<Path> repositoryRoot,
            Optional<String> workingTreeScope,
            Optional<ca.teamdman.sfm.client.review.release_review.SFMWorkingTreeCaptureV1> previousCapture,
            List<String> additionalExclusions
    ) {
        Request(Path file, String lane, String before, String candidate, Optional<Path> repositoryRoot,
                Optional<String> workingTreeScope) {
            this(file, lane, before, candidate, repositoryRoot, workingTreeScope, Optional.empty(), List.of());
        }
        Request(Path file, String lane, String before, String candidate, Optional<Path> repositoryRoot) {
            this(file, lane, before, candidate, repositoryRoot, Optional.empty());
        }
        Request {
            Objects.requireNonNull(file, "file");
            previousCapture = Objects.requireNonNull(previousCapture, "previousCapture");
            additionalExclusions = List.copyOf(additionalExclusions);
            lane = requireText(lane, "lane");
            before = requireText(before, "before");
            workingTreeScope = Objects.requireNonNull(workingTreeScope, "workingTreeScope")
                    .map(scope -> requireText(scope, "scope"));
            if (workingTreeScope.isEmpty() && (previousCapture.isPresent() || !additionalExclusions.isEmpty()))
                throw new IllegalArgumentException("Capture policy requires a working-tree request");
            if (workingTreeScope.isPresent()) {
                if (candidate != null) throw new IllegalArgumentException("Choose Git candidate or working-tree scope, not both");
            } else candidate = requireText(candidate, "candidate");
            repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        }
    }

    private SFMReleaseReviewCreateRuntime() {
    }

    static int queue(Request request, Consumer<Component> feedback) {
        return queue(request, feedback, output -> SFMReleaseReviewRuntime.get().openAsync(output, true)
                .whenComplete((opened, failure) -> publish(feedback, Component.literal(
                        failure != null ? "Created review, but open failed: " + message(failure)
                                : opened.document().isEmpty() ? "Created review, but open failed: " + String.join("; ", opened.diagnostics())
                                : "Created and opened release review " + output))));
    }

    static int queue(Request request, Consumer<Component> feedback, Consumer<Path> created) {
        return queue(request, feedback, created, feedback);
    }

    static int queue(Request request, SFMClientActionContext context, Consumer<Component> feedback, Consumer<Path> created) {
        // Negative IDs have their own persistent toast lane without falsely offering
        // the persistence runtime's cancellation action for a different subprocess.
        var status = new SFMReleaseReviewOperationFeedback(context, -CAPTURE_IDS.incrementAndGet(), feedback);
        return queue(request, status::complete, output -> {
            status.complete(ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastContent.pathMessage(
                    "Created ", ca.teamdman.sfm.client.explorer.SFMPath.fromNative(output), ""));
            created.accept(output);
        }, status::pending);
    }

    private static int queue(Request request, Consumer<Component> feedback, Consumer<Path> created, Consumer<Component> queued) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(feedback, "feedback");
        Path repositoryRoot = request.repositoryRoot()
                .map(path -> path.toAbsolutePath().normalize())
                .orElseGet(SFMReleaseReviewCreateRuntime::discoverRepositoryRoot);
        Path output = resolveOutput(repositoryRoot, request.file());
        List<String> argv = command(request, repositoryRoot);
        queued.accept(ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastContent.pathMessage(
                "Release-review creation queued: ", ca.teamdman.sfm.client.explorer.SFMPath.fromNative(output), "")
                .copy().withStyle(ChatFormatting.GRAY));
        WORKER.execute(() -> execute(argv, repositoryRoot, output, feedback, created));
        return 1;
    }

    static List<String> command(Request request, Path repositoryRoot) {
        Objects.requireNonNull(request, "request");
        Path normalizedRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot")
                .toAbsolutePath().normalize();
        ArrayList<String> argv = new ArrayList<>();
        argv.add(System.getProperty(EXECUTABLE_PROPERTY, "sfm-propagate-changes.exe"));
        argv.addAll(List.of(
                "--output-format", "json",
                "review", "session", "create-ledger",
                "--file", request.file().toString(),
                "--branch", request.lane(),
                "--before", request.before()
        ));
        if (request.workingTreeScope().isPresent()) {
            argv.add("--working-tree");
            for (String scope : request.previousCapture().map(value -> value.scopePaths())
                    .orElseGet(() -> List.of(request.workingTreeScope().orElseThrow()))) {
                argv.addAll(List.of("--scope", scope));
            }
            var exclusions = new java.util.LinkedHashSet<>(request.additionalExclusions());
            request.previousCapture().ifPresent(capture -> {
                exclusions.addAll(capture.excludedPaths());
                if (!capture.includeUntracked()) argv.add("--tracked-only");
            });
            exclusions.forEach(path -> argv.addAll(List.of("--exclude", path)));
        } else argv.addAll(List.of("--candidate", request.candidate()));
        argv.addAll(List.of("--repository-root", normalizedRoot.toString()));
        return List.copyOf(argv);
    }

    private static void execute(
            List<String> argv,
            Path repositoryRoot,
            Path output,
            Consumer<Component> feedback,
            Consumer<Path> created
    ) {
        try {
            ProcessBuilder builder = new ProcessBuilder(argv)
                    .directory(repositoryRoot.toFile())
                    .redirectErrorStream(true);
            Process process = builder.start();
            process.getOutputStream().close();
            java.util.concurrent.CompletableFuture<String> drained = new java.util.concurrent.CompletableFuture<>();
            Thread reader = new Thread(() -> {
                try { drained.complete(drain(process.getInputStream())); }
                catch (Throwable failure) { drained.completeExceptionally(failure); }
            }, "sfm-release-review-create-output");
            reader.setDaemon(true);
            reader.start();
            String processOutput;
            int exitCode;
            try {
                if (!process.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IOException("Review creation exceeded 180 seconds; choose a narrower scope and a new output path");
                }
                exitCode = process.exitValue();
                processOutput = drained.get(5, java.util.concurrent.TimeUnit.SECONDS);
            } finally {
                if (process.isAlive()) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                }
            }
            if (exitCode != 0) {
                publish(feedback, Component.literal("Release-review creation failed (exit " + exitCode + "): "
                                + summarize(processOutput))
                        .withStyle(ChatFormatting.RED));
                return;
            }
            if (!Files.isRegularFile(output)) {
                publish(feedback, Component.literal(
                                "Release-review creation reported success but did not create " + output)
                        .withStyle(ChatFormatting.RED));
                return;
            }
            Minecraft.getInstance().execute(() -> {
                try { ca.teamdman.sfm.client.explorer.SFMExplorerRuntime.get().fileCreated(output); }
                catch (RuntimeException refreshFailure) { ca.teamdman.sfm.SFM.LOGGER.warn("Created review directory refresh unavailable", refreshFailure); }
                created.accept(output);
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            publish(feedback, Component.literal("Release-review creation was interrupted")
                    .withStyle(ChatFormatting.RED));
        } catch (Throwable failure) {
            publish(feedback, Component.literal("Release-review creation failed: " + message(failure))
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static Path discoverRepositoryRoot() {
        Path cursor = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.exists(cursor.resolve(".git"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalArgumentException(
                "Could not discover a Git repository above the Minecraft game directory; provide repository_root");
    }

    private static Path resolveOutput(Path repositoryRoot, Path requested) {
        return (requested.isAbsolute() ? requested : repositoryRoot.resolve(requested))
                .toAbsolutePath().normalize();
    }

    private static String drain(InputStream input) throws IOException {
        try (input; ByteArrayOutputStream retained = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                int remaining = MAXIMUM_RETAINED_OUTPUT_BYTES - retained.size();
                if (remaining > 0) retained.write(buffer, 0, Math.min(remaining, count));
            }
            String answer = retained.toString(StandardCharsets.UTF_8);
            if (total > retained.size()) answer += "\n... " + (total - retained.size()) + " bytes omitted ...";
            return answer;
        }
    }

    private static void publish(Consumer<Component> feedback, Component message) {
        Minecraft.getInstance().execute(() -> feedback.accept(message));
    }

    private static String summarize(String output) {
        String compact = output.replace('\r', ' ').replace('\n', ' ').trim();
        if (compact.isEmpty()) return "no diagnostic output";
        return compact.length() <= 1000 ? compact : compact.substring(0, 1000) + "…";
    }

    private static String message(Throwable failure) {
        return Optional.ofNullable(failure.getMessage()).orElse(failure.getClass().getSimpleName());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
