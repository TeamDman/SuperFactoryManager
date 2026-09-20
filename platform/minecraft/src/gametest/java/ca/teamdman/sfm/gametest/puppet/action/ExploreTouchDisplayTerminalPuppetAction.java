package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetRenderHarness;
import ca.teamdman.sfm.gametest.tests.general.TouchDisplayTerminalVisualControl;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Opt-in real world renderer and gameplay-input proof. No OS input or synthetic render/upload seams. */
public final class ExploreTouchDisplayTerminalPuppetAction implements SFMPuppetAction {
    private static final BlockPos DISPLAY = new BlockPos(2, 2, 2);
    private final TouchDisplayTerminalVisualControl control;
    private final Path directory = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize()
            .resolve("sfm-puppet/touch-display-terminal-control/session-" + UUID.randomUUID());
    private final Set<String> witnesses = new LinkedHashSet<>();
    private CompletableFuture<JsonObject> read;
    private CompletableFuture<Void> write;
    private JsonObject request;
    private JsonObject result;
    private BlockPos displayPosition;
    private int sequence = 1;
    private long nextPoll, requestStarted, actionNanos, frame, initialEvaluations;
    private boolean initialized, aimed, applied, finishStarted, finished;
    private int failedRequests;

    public ExploreTouchDisplayTerminalPuppetAction(TouchDisplayTerminalVisualControl control) {
        this.control = java.util.Objects.requireNonNull(control);
    }

    @Override public String description() { return "file-driven in-world terminal: request " + sequence + " in " + directory; }

    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!initialized) {
            initialized = true;
            displayPosition = runtime.absoluteGameTestPos(DISPLAY);
            JsonObject ready = observation();
            ready.addProperty("schema", "sfm:touch_display_terminal_puppet@1");
            ready.addProperty("control_directory", directory.toString());
            ready.addProperty("request_pattern", "000001.request.json (then monotonically increasing)");
            ready.addProperty("operations", "aim, await_ready, press, await_ack, finish, observe");
            ready.addProperty("fixture_consent", "fixed test-only program; not a consent UI acceptance proof");
            ready.addProperty("screenshots_directory", Minecraft.getInstance().gameDirectory.toPath()
                    .toAbsolutePath().normalize().resolve("screenshots").toString());
            write = writeAsync(directory.resolve("ready.json"), ready);
            return false;
        }
        if (write != null) {
            if (!write.isDone()) return false;
            write.join();
            write = null;
            if (finished) return true;
        }
        if (request != null) {
            if (!applied) {
                try {
                    require(control.snapshot().stage() != TouchDisplayTerminalVisualControl.Stage.FAILED, control.snapshot().failure());
                    require(System.nanoTime() - requestStarted < 110_000_000_000L, "Timed out waiting for terminal file operation");
                    if (!apply(runtime, request.get("op").getAsString())) return false;
                    applied = true;
                    actionNanos = System.nanoTime();
                    frame = SFMGamePuppetRenderHarness.completedFrames();
                } catch (RuntimeException failure) {
                    failedRequests++;
                    result.addProperty("status", "error");
                    result.addProperty("error", failure.getMessage());
                    result.add("observation", observation());
                    writeResponse();
                    return false;
                }
            }
            if (SFMGamePuppetRenderHarness.completedFrames() <= frame + 2 || System.nanoTime() - actionNanos < 150_000_000L) return false;
            require(Minecraft.getInstance().screen == null, "Terminal puppet opened a screen");
            String operation = request.get("op").getAsString();
            if (operation.equals("await_ready") || operation.equals("await_ack")) requireRenderedFace();
            String capture = "terminal-world-step-" + String.format(Locale.ROOT, "%04d", sequence);
            if (!runtime.captureWithHud(capture, Component.literal("In-world terminal " + sequence + ": " + operation))) return false;
            if (operation.equals("await_ready")) witnesses.add("rendered_before");
            if (operation.equals("await_ack")) witnesses.add("rendered_after");
            result.addProperty("status", "dispatched");
            result.addProperty("capture_name", capture);
            result.addProperty("completed_frame", SFMGamePuppetRenderHarness.completedFrames());
            result.add("observation", observation());
            writeResponse();
            return false;
        }
        require(control.snapshot().stage() != TouchDisplayTerminalVisualControl.Stage.FAILED, control.snapshot().failure());
        if (read == null) {
            if (System.nanoTime() < nextPoll) return false;
            nextPoll = System.nanoTime() + 100_000_000L;
            Path path = directory.resolve(prefix() + ".request.json");
            read = CompletableFuture.supplyAsync(() -> readRequest(path));
            return false;
        }
        if (!read.isDone()) return false;
        request = read.join();
        read = null;
        if (request == null) return false;
        result = new JsonObject();
        result.addProperty("sequence", sequence);
        requestStarted = System.nanoTime();
        applied = false;
        return false;
    }

    private boolean apply(ISFMGamePuppetRuntime runtime, String operation) {
        require(Minecraft.getInstance().screen == null, "Terminal puppet must remain in the world");
        switch (operation) {
            case "observe" -> { }
            case "aim" -> {
                require(!control.pressRequested(), "Do not move the camera after pressing during raster comparison");
                runtime.positionForTouchDisplayFace(DISPLAY, Direction.SOUTH, TouchDisplayTerminalVisualControl.U, TouchDisplayTerminalVisualControl.V);
                aimed = true;
            }
            case "await_ready" -> {
                require(aimed, "Aim at the terminal before waiting for its actual renderer");
                if (control.snapshot().stage() != TouchDisplayTerminalVisualControl.Stage.READY) return false;
                requireRenderedFace();
                initialEvaluations = evaluations();
                require(initialEvaluations >= 2, "The actual block renderer has not evaluated the program twice");
            }
            case "press" -> {
                require(witnesses.contains("rendered_before"), "Capture the rendered ready terminal before pressing");
                requireRenderedFace();
                control.requestPress();
                try {
                    runtime.pressTouchDisplayFace(DISPLAY, Direction.SOUTH, TouchDisplayTerminalVisualControl.U, TouchDisplayTerminalVisualControl.V);
                } catch (RuntimeException failure) {
                    control.fail("Actual gameplay terminal press failed");
                    throw failure;
                }
                witnesses.add("gameplay_press");
            }
            case "await_ack" -> {
                require(witnesses.contains("gameplay_press"), "Send the one real gameplay press first");
                if (control.snapshot().stage() != TouchDisplayTerminalVisualControl.Stage.ACKNOWLEDGED) return false;
                requireRenderedFace();
                require(evaluations() > initialEvaluations, "The actual renderer did not continue after the touch");
                witnesses.add("worker_ack");
            }
            case "finish" -> {
                require(failedRequests == 0 && witnesses.containsAll(Set.of("rendered_before", "gameplay_press", "worker_ack", "rendered_after")),
                        "Complete both world captures and the real touch before finishing");
                if (!finishStarted) {
                    control.requestFinish();
                    finishStarted = true;
                }
                if (control.snapshot().stage() != TouchDisplayTerminalVisualControl.Stage.CLEANED) return false;
                witnesses.add("owned_cleanup");
                finished = true;
            }
            default -> throw new IllegalArgumentException("Unsupported terminal proof operation");
        }
        return true;
    }

    private void requireRenderedFace() {
        Minecraft minecraft = Minecraft.getInstance();
        require(minecraft.screen == null && minecraft.level != null && minecraft.player != null, "No screen-free world is available");
        require(minecraft.level.getBlockEntity(displayPosition) instanceof TouchDisplayBlockEntity, "Terminal display is not loaded");
        var display = (TouchDisplayBlockEntity) minecraft.level.getBlockEntity(displayPosition);
        require(ClientManagerFrameRuntime.renderEligible(display), "Terminal face is not render-relevant and front-facing");
        require(minecraft.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(displayPosition)
                && hit.getDirection() == Direction.SOUTH, "Crosshair does not reach the actual terminal face");
        require(!control.snapshot().textureSha256().isBlank(), "No BER-uploaded terminal pixels are available");
    }

    private long evaluations() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(displayPosition) instanceof TouchDisplayBlockEntity display
                ? ClientManagerFrameRuntime.observation(display).evaluations() : 0;
    }

    private JsonObject observation() {
        var minecraft = Minecraft.getInstance();
        var snapshot = control.snapshot();
        JsonObject result = new JsonObject();
        result.addProperty("process_id", ProcessHandle.current().pid());
        result.addProperty("screen", minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
        result.addProperty("stage", snapshot.stage().name());
        result.addProperty("texture_sha256", snapshot.textureSha256());
        result.addProperty("texture_width", snapshot.width());
        result.addProperty("texture_height", snapshot.height());
        result.addProperty("attempted", snapshot.attempted());
        result.addProperty("acknowledged", snapshot.acknowledged());
        result.addProperty("rejected", snapshot.rejected());
        result.addProperty("failure", snapshot.failure());
        result.addProperty("failed_requests", failedRequests);
        result.addProperty("synthetic_frame_selection", false);
        result.addProperty("client_program_evaluations", displayPosition == null ? 0 : evaluations());
        if (displayPosition != null && minecraft.level != null
                && minecraft.level.getBlockEntity(displayPosition) instanceof TouchDisplayBlockEntity display) {
            result.addProperty("render_eligible", ClientManagerFrameRuntime.renderEligible(display));
            result.addProperty("content_revision", display.content().revision());
            result.addProperty("server_image_ref", display.content().imageRef().toString());
        }
        JsonArray proof = new JsonArray();
        witnesses.forEach(proof::add);
        result.add("witnesses", proof);
        return result;
    }

    @Override public void abort() { if (!finished) control.fail("Terminal visual puppet aborted"); }

    private void writeResponse() {
        result.add("request", request);
        write = writeAsync(directory.resolve(prefix() + ".response.json"), result);
        request = null;
        result = null;
        sequence++;
    }

    private String prefix() { return String.format(Locale.ROOT, "%06d", sequence); }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }

    private static JsonObject readRequest(Path path) {
        try {
            if (!Files.isRegularFile(path)) return null;
            if (Files.size(path) > 1024) throw new IOException("Terminal request exceeds 1024 bytes");
            byte[] bytes;
            try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(1025); }
            if (bytes.length > 1024) throw new IOException("Terminal request exceeds 1024 bytes");
            JsonObject value = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (value.size() != 1 || !value.has("op") || !value.get("op").isJsonPrimitive()
                    || !value.getAsJsonPrimitive("op").isString()) throw new IOException("Terminal requests contain only a string op");
            return value;
        } catch (Exception failure) {
            JsonObject invalid = new JsonObject();
            invalid.addProperty("op", "invalid");
            return invalid;
        }
    }

    private static CompletableFuture<Void> writeAsync(Path path, JsonObject value) {
        String contents = value.toString();
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(path.getParent());
                Path staging = path.resolveSibling(path.getFileName() + ".staging");
                Files.writeString(staging, contents, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
                Files.move(staging, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException failure) { throw new IllegalStateException("Could not write terminal puppet evidence", failure); }
        });
    }
}
