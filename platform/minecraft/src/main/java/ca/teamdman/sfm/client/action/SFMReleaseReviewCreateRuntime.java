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
            Optional<Path> repositoryRoot
    ) {
        Request {
            Objects.requireNonNull(file, "file");
            lane = requireText(lane, "lane");
            before = requireText(before, "before");
            candidate = requireText(candidate, "candidate");
            repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        }
    }

    private SFMReleaseReviewCreateRuntime() {
    }

    static int queue(Request request, Consumer<Component> feedback) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(feedback, "feedback");
        Path repositoryRoot = request.repositoryRoot()
                .map(path -> path.toAbsolutePath().normalize())
                .orElseGet(SFMReleaseReviewCreateRuntime::discoverRepositoryRoot);
        Path output = resolveOutput(repositoryRoot, request.file());
        List<String> argv = command(request, repositoryRoot);
        feedback.accept(Component.literal("Release-review creation queued: " + output)
                .withStyle(ChatFormatting.GRAY));
        WORKER.execute(() -> execute(argv, repositoryRoot, output, feedback));
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
                "review", "session", "create",
                "--file", request.file().toString(),
                "--branch", request.lane(),
                "--before", request.before(),
                "--candidate", request.candidate(),
                "--repository-root", normalizedRoot.toString()
        ));
        return List.copyOf(argv);
    }

    private static void execute(
            List<String> argv,
            Path repositoryRoot,
            Path output,
            Consumer<Component> feedback
    ) {
        try {
            ProcessBuilder builder = new ProcessBuilder(argv)
                    .directory(repositoryRoot.toFile())
                    .redirectErrorStream(true);
            Process process = builder.start();
            process.getOutputStream().close();
            String processOutput = drain(process.getInputStream());
            int exitCode = process.waitFor();
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
                try {
                    var opened = SFMReleaseReviewRuntime.get().open(output, true);
                    if (opened.document().isEmpty()) {
                        throw new IllegalStateException(String.join("; ", opened.diagnostics()));
                    }
                    feedback.accept(Component.literal("Created and opened release review " + output)
                            .withStyle(ChatFormatting.AQUA));
                } catch (Throwable failure) {
                    feedback.accept(Component.literal("Created " + output + " but could not open it: "
                                    + message(failure))
                            .withStyle(ChatFormatting.RED));
                }
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
