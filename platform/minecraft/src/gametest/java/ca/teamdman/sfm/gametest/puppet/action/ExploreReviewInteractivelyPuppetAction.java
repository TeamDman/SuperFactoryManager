package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.SFMScreenDiagnosticsContributor;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetPointer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetRenderHarness;
import ca.teamdman.sfm.mixins.MouseHandlerInvoker;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Opt-in test-only, one-step-at-a-time exploration through real GUI inputs.
 * No socket, shell commands, review model mutation, or OS pointer injection.
 * The agent decides the NEXT step after reading the preceding observation.
 */
public final class ExploreReviewInteractivelyPuppetAction implements SFMPuppetAction {
    private static final int MAX_REQUEST_BYTES = 16_384;
    private final Path directory = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("sfm-puppet/exploration-control/session-" + UUID.randomUUID());
    private CompletableFuture<JsonObject> read;
    private CompletableFuture<Void> write;
    private JsonObject request;
    private JsonObject result;
    private int sequence = 1;
    private long nextPoll;
    private long frame;
    private long actionNanos;
    private boolean initialized;
    private boolean finish;
    private java.util.concurrent.CountDownLatch reviewWorkerRelease;
    private CompletableFuture<Void> reviewWorkerBarrier;
    private final java.util.concurrent.atomic.AtomicBoolean reviewWorkerEntered = new java.util.concurrent.atomic.AtomicBoolean();
    private java.util.concurrent.CountDownLatch navigationRelease;
    private java.util.concurrent.Executor navigationOriginalExecutor;
    private final java.util.concurrent.atomic.AtomicBoolean navigationEntered = new java.util.concurrent.atomic.AtomicBoolean();

    @Override
    public String description() {
        return "adaptive review exploration: request " + sequence + " in " + directory;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!initialized) {
            initialized = true;
            JsonObject ready = observation();
            ready.addProperty("control_directory", directory.toString());
            ready.addProperty("request_pattern", "000001.request.json (then monotonically increasing)");
            write = writeAsync(directory.resolve("ready.json"), ready);
            return false;
        }
        if (write != null) {
            if (!write.isDone()) return false;
            write.join();
            write = null;
            if (finish) return true;
        }
        if (request != null) {
            // An input result is not a completed rendered frame or an async job result.
            if (SFMGamePuppetRenderHarness.completedFrames() <= frame) return false;
            if (System.nanoTime() - actionNanos < 150_000_000L) return false;
            String name = "explore-step-" + String.format(Locale.ROOT, "%04d", sequence);
            if (!runtime.captureWithHud(name, Component.literal("Exploration " + sequence + ": "
                    + string(request, "note", string(request, "op", "observe"))))) return false;
            result.add("observation", observation());
            result.addProperty("capture_name", name);
            result.addProperty("input_to_observation_ms", (System.nanoTime() - actionNanos) / 1_000_000L);
            result.add("request", request);
            write = writeAsync(directory.resolve(prefix() + ".response.json"), result);
            request = null;
            result = null;
            sequence++;
            return false;
        }
        if (read == null) {
            if (System.nanoTime() < nextPoll) return false;
            nextPoll = System.nanoTime() + 100_000_000L;
            Path path = directory.resolve(prefix() + ".request.json");
            read = CompletableFuture.supplyAsync(() -> {
                try {
                    if (!Files.isRegularFile(path)) return null;
                    if (Files.size(path) > MAX_REQUEST_BYTES) throw new IOException("Request exceeds 16384 bytes");
                    return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (Exception failure) {
                    JsonObject invalid = new JsonObject();
                    invalid.addProperty("op", "invalid");
                    invalid.addProperty("error", failure.toString());
                    return invalid;
                }
            });
            return false;
        }
        if (!read.isDone()) return false;
        request = read.join();
        read = null;
        if (request == null) return false;
        result = new JsonObject();
        result.addProperty("sequence", sequence);
        actionNanos = System.nanoTime();
        try {
            apply(runtime, request);
            result.addProperty("status", "dispatched");
        } catch (Exception failure) {
            result.addProperty("status", "error");
            result.addProperty("error", failure.toString());
            var causes = new JsonArray();
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause())
                causes.add(cause.toString());
            result.add("error_causes", causes);
            ca.teamdman.sfm.SFM.LOGGER.error("SFM_EXPLORATION_INPUT_FAILED sequence={}", sequence, failure);
        }
        result.addProperty("dispatch_micros", (System.nanoTime() - actionNanos) / 1_000L);
        frame = SFMGamePuppetRenderHarness.completedFrames();
        return false;
    }

    private void apply(ISFMGamePuppetRuntime runtime, JsonObject step) {
        switch (string(step, "op", "observe")) {
            case "observe" -> { }
            case "finish" -> {
                releaseReviewWorker();
                releaseReviewNavigation();
                finish = true;
            }
            // Explicit fault-injection controls, never document/model mutation.
            // The real palette still submits work and the real persistence executor performs it.
            case "review_worker_pause" -> pauseReviewWorker();
            case "review_worker_resume" -> releaseReviewWorker();
            case "review_navigation_pause" -> pauseReviewNavigation();
            case "review_navigation_resume" -> releaseReviewNavigation();
            case "palette" -> {
                runtime.openCommandPalette();
                runtime.setCommandPaletteInput(string(step, "text", ""));
            }
            case "key" -> runtime.pressScreenKey(integer(step, "key", 0), integer(step, "modifiers", 0));
            case "type" -> {
                String text = string(step, "text", "");
                if (text.length() > 4096) throw new IllegalArgumentException("Type at most 4096 characters per step");
                for (char character : text.toCharArray()) runtime.typeScreenCharacter(character, integer(step, "modifiers", 0));
            }
            case "move" -> move(step);
            case "click" -> {
                move(step);
                int button = integer(step, "button", 0);
                int modifiers = integer(step, "modifiers", 0);
                int count = integer(step, "count", 1);
                if (count < 1 || count > 2) throw new IllegalArgumentException("Click count must be one or two");
                for (int click = 0; click < count; click++) {
                    SFMGamePuppetPointer.buttonVirtual(button, GLFW.GLFW_PRESS, modifiers);
                    SFMGamePuppetPointer.buttonVirtual(button, GLFW.GLFW_RELEASE, modifiers);
                }
            }
            case "button" -> SFMGamePuppetPointer.buttonVirtual(integer(step, "button", 0),
                    integer(step, "action", GLFW.GLFW_RELEASE), integer(step, "modifiers", 0));
            case "scroll" -> {
                double delta = step.get("delta").getAsDouble();
                if (!Double.isFinite(delta) || Math.abs(delta) > 100) {
                    throw new IllegalArgumentException("Wheel delta must be finite and bounded by 100");
                }
                SFMGamePuppetPointer.scrollVirtual(0, delta, integer(step, "modifiers", 0));
            }
            default -> throw new IllegalArgumentException("Unsupported exploration operation: " + step);
        }
    }

    private void pauseReviewWorker() {
        if (reviewWorkerBarrier != null && !reviewWorkerBarrier.isDone()) {
            throw new IllegalStateException("Review worker is already paused");
        }
        if (ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime.get().pendingOperation().isPresent()) {
            throw new IllegalStateException("Pause before submitting the review operation under test");
        }
        try {
            // Keep the injection exclusively in gametest code rather than adding a production control API.
            var field = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime.class
                    .getDeclaredField("PERSISTENCE_EXECUTOR");
            field.setAccessible(true);
            var executor = (java.util.concurrent.Executor) field.get(null);
            var release = new java.util.concurrent.CountDownLatch(1);
            reviewWorkerRelease = release;
            reviewWorkerEntered.set(false);
            reviewWorkerBarrier = CompletableFuture.runAsync(() -> {
                reviewWorkerEntered.set(true);
                try {
                    if (!release.await(180, java.util.concurrent.TimeUnit.SECONDS)) {
                        ca.teamdman.sfm.SFM.LOGGER.warn("SFM_REVIEW_PUPPET_WORKER_PAUSE_EXPIRED timeout_seconds=180");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    reviewWorkerEntered.set(false);
                }
            }, executor);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot acquire the scoped review persistence test barrier", failure);
        }
    }

    private void releaseReviewWorker() {
        if (reviewWorkerRelease != null) reviewWorkerRelease.countDown();
    }

    private void pauseReviewNavigation() {
        if (navigationOriginalExecutor != null) throw new IllegalStateException("Review navigation is already paused");
        try {
            var field = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime.class
                    .getDeclaredField("commentNavigationExecutor");
            field.setAccessible(true);
            var resolver = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime.get();
            var original = (java.util.concurrent.Executor) field.get(resolver);
            navigationOriginalExecutor = original;
            var release = new java.util.concurrent.CountDownLatch(1);
            navigationRelease = release;
            navigationEntered.set(false);
            java.util.concurrent.Executor delayed = task -> original.execute(() -> {
                navigationEntered.set(true);
                try {
                    if (!release.await(180, java.util.concurrent.TimeUnit.SECONDS)) {
                        ca.teamdman.sfm.SFM.LOGGER.warn("SFM_REVIEW_PUPPET_NAVIGATION_PAUSE_EXPIRED timeout_seconds=180");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    release.countDown();
                    navigationEntered.set(false);
                    task.run();
                }
            });
            field.set(resolver, delayed);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot acquire the scoped review navigation test barrier", failure);
        }
    }

    private void releaseReviewNavigation() {
        if (navigationRelease != null) navigationRelease.countDown();
        if (navigationOriginalExecutor == null) return;
        try {
            var field = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime.class
                    .getDeclaredField("commentNavigationExecutor");
            field.setAccessible(true);
            field.set(ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime.get(), navigationOriginalExecutor);
            navigationOriginalExecutor = null;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot release the scoped review navigation test barrier", failure);
        }
    }

    private static void move(JsonObject step) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) throw new IllegalStateException("Virtual GUI input requires a screen");
        double x = step.get("x").getAsDouble();
        double y = step.get("y").getAsDouble();
        var window = minecraft.getWindow();
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || y < 0
                || x >= window.getGuiScaledWidth() || y >= window.getGuiScaledHeight()) {
            throw new IllegalArgumentException("Coordinates must be inside the logical GUI viewport");
        }
        ((MouseHandlerInvoker) minecraft.mouseHandler).sfm$invokeOnMove(window.getWindow(),
                x * window.getScreenWidth() / window.getGuiScaledWidth(),
                y * window.getScreenHeight() / window.getGuiScaledHeight());
    }

    private JsonObject observation() {
        Minecraft minecraft = Minecraft.getInstance();
        JsonObject state = new JsonObject();
        state.addProperty("schema", "sfm.exploration-observation/1");
        state.addProperty("review_navigation_test_barrier_entered", navigationEntered.get());
        state.addProperty("process_id", ProcessHandle.current().pid());
        state.addProperty("screen", minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
        state.addProperty("logical_width", minecraft.getWindow().getGuiScaledWidth());
        state.addProperty("logical_height", minecraft.getWindow().getGuiScaledHeight());
        state.addProperty("completed_frames", SFMGamePuppetRenderHarness.completedFrames());
        state.addProperty("os_pointer_injection", false);
        state.addProperty("review_worker_test_barrier_entered", reviewWorkerEntered.get());
        var reviewRuntime = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime.get();
        var review = reviewRuntime.snapshot();
        JsonObject reviewState = new JsonObject();
        reviewState.addProperty("path", review.path().map(Object::toString).orElse("none"));
        reviewState.addProperty("open_epoch", review.openEpoch());
        reviewState.addProperty("generation", review.generation());
        reviewState.addProperty("writable", review.writable());
        reviewState.addProperty("dirty", review.dirty());
        reviewState.addProperty("comment_count", review.document().map(value -> value.reviewSession().comments().size()).orElse(0));
        review.document().ifPresent(document -> {
            var resume = document.resumeState();
            reviewState.addProperty("active_query_expression", resume.activeQueryExpression().orElse("none"));
            reviewState.addProperty("current_unit_id", resume.currentUnitId().orElse("none"));
            reviewState.addProperty("resume_generation", resume.generation());
            reviewState.addProperty("deferred_unit_count", resume.deferredUnitIds().size());
        });
        reviewRuntime.pendingOperation().ifPresent(pending -> {
            JsonObject operation = new JsonObject();
            operation.addProperty("id", pending.id());
            operation.addProperty("kind", pending.kind());
            operation.addProperty("path", pending.path().toString());
            operation.addProperty("phase", pending.phase().name());
            reviewState.add("pending", operation);
        });
        state.add("review", reviewState);
        JsonArray widgets = new JsonArray();
        if (minecraft.screen != null) {
            state.addProperty("title", minecraft.screen.getTitle().getString());
            state.addProperty("focus", String.valueOf(minecraft.screen.getFocused()));
            widgets(minecraft.screen, widgets, 0);
        }
        state.add("widgets", widgets);
        if (minecraft.screen instanceof SFMScreenDiagnosticsContributor diagnostics) {
            JsonArray details = new JsonArray();
            diagnostics.screenDiagnostics().forEach(details::add);
            state.add("details", details);
        }
        if (minecraft.screen instanceof SFMCommandPaletteScreen palette) {
            state.addProperty("input", palette.inputForAutomation());
            state.addProperty("suggestions_current", palette.suggestionsMatchCurrentInputForAutomation());
            JsonArray candidates = new JsonArray();
            palette.candidateSnapshotsForAutomation().stream().limit(80).forEach(candidate -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("order", candidate.order());
                entry.addProperty("display", candidate.displayText());
                entry.addProperty("replacement", candidate.replacementText());
                entry.addProperty("activatable", candidate.activatable());
                candidates.add(entry);
            });
            state.add("candidates", candidates);
        }
        if (minecraft.screen instanceof SFMScreenMultiplexer workspace) {
            JsonArray panelNarrations = new JsonArray();
            workspace.visiblePanelEntries().forEach(entry -> panelNarrations.add(entry.panel().narration().getString()));
            state.add("panel_narrations", panelNarrations);
            JsonArray toasts = new JsonArray();
            workspace.activeWorkspaceToastIds().forEach(id -> workspace.workspaceToastSnapshot(id).ifPresent(toast -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", id.value());
                entry.addProperty("text", toast.text());
                entry.addProperty("bounds", workspace.workspaceToastBounds(id).map(Object::toString).orElse("not rendered"));
                toasts.add(entry);
            }));
            state.add("toasts", toasts);
        }
        return state;
    }

    private static void widgets(ContainerEventHandler host, JsonArray result, int depth) {
        if (depth > 5 || result.size() >= 160) return;
        for (GuiEventListener child : host.children()) {
            if (child instanceof AbstractWidget widget) {
                JsonObject entry = new JsonObject();
                entry.addProperty("class", widget.getClass().getSimpleName());
                entry.addProperty("text", widget.getMessage().getString());
                entry.addProperty("x", widget.x);
                entry.addProperty("y", widget.y);
                entry.addProperty("width", widget.getWidth());
                entry.addProperty("height", widget.getHeight());
                entry.addProperty("active", widget.active);
                entry.addProperty("visible", widget.visible);
                if (widget instanceof net.minecraft.client.gui.components.EditBox input) {
                    entry.addProperty("value", input.getValue());
                    entry.addProperty("cursor", input.getCursorPosition());
                    entry.addProperty("selected_text", input.getHighlighted());
                }
                result.add(entry);
            }
            if (child instanceof ContainerEventHandler nested) widgets(nested, result, depth + 1);
        }
    }

    private String prefix() {
        return String.format(Locale.ROOT, "%06d", sequence);
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static CompletableFuture<Void> writeAsync(Path path, JsonObject value) {
        String contents = value.toString();
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(path.getParent());
                Path staged = path.resolveSibling(path.getFileName() + ".tmp");
                Files.writeString(staged, contents, StandardCharsets.UTF_8);
                Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot write exploration evidence: " + path, failure);
            }
        });
    }
}
