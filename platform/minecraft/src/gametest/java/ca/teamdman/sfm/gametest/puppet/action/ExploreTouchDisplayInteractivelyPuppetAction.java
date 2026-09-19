package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.common.block.TouchDisplaySurface;
import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;
import ca.teamdman.sfm.client.program.ClientProgramConsentGate;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetRenderHarness;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opt-in file-driven in-world proof. It never opens a screen or moves the OS pointer.
 * The ordinary ambient GameTest suite does not discover or run this puppet.
 */
public final class ExploreTouchDisplayInteractivelyPuppetAction implements SFMPuppetAction {
    private static final int MAX_REQUEST_BYTES = 16_384;
    private static final BlockPos DISPLAY = new BlockPos(2, 2, 2);
    private static final Direction FACE = Direction.NORTH;

    private final AtomicReference<TouchDisplaySurface.UV> requestedTouch;
    private final Path directory = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("sfm-puppet/touch-display-control/session-" + UUID.randomUUID());
    private CompletableFuture<JsonObject> read;
    private CompletableFuture<Void> write;
    private JsonObject request;
    private JsonObject result;
    private BlockPos absoluteDisplay;
    private ClientProgramIdentity fixtureIdentity;
    private int sequence = 1;
    private long nextPoll;
    private long frame;
    private long actionNanos;
    private boolean initialized;
    private boolean aimed;
    private boolean pressed;
    private boolean finish;

    public ExploreTouchDisplayInteractivelyPuppetAction(AtomicReference<TouchDisplaySurface.UV> requestedTouch) {
        this.requestedTouch = requestedTouch;
    }

    @Override
    public String description() {
        return "file-driven Touch Display exploration: request " + sequence + " in " + directory;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!initialized) {
            initialized = true;
            absoluteDisplay = runtime.absoluteGameTestPos(DISPLAY);
            JsonObject ready = observation();
            ready.addProperty("control_directory", directory.toString());
            ready.addProperty("request_pattern", "000001.request.json (then monotonically increasing)");
            ready.addProperty("screenshots_directory", Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("screenshots").toString());
            ready.addProperty("example_aim", "{\"op\":\"aim\",\"u\":0.25,\"v\":0.75}");
            ready.addProperty("example_press", "{\"op\":\"press\",\"u\":0.25,\"v\":0.75}");
            ready.addProperty("example_finish", "{\"op\":\"finish\"}");
            ready.addProperty("client_manager_fixture_operations",
                    "install_client_manager, approve_fixture_client_program, revoke_fixture_client_program");
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
            if (SFMGamePuppetRenderHarness.completedFrames() <= frame) return false;
            if (System.nanoTime() - actionNanos < 150_000_000L) return false;
            String name = "touch-step-" + String.format(Locale.ROOT, "%04d", sequence);
            if (!runtime.captureWithHud(name, Component.literal("Touch Display " + sequence + ": "
                    + string(request, "op", "observe")))) return false;
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
            read = CompletableFuture.supplyAsync(() -> readRequest(path));
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
        } catch (RuntimeException failure) {
            result.addProperty("status", "error");
            result.addProperty("error", failure.toString());
            ca.teamdman.sfm.SFM.LOGGER.error("SFM_TOUCH_DISPLAY_EXPLORATION_INPUT_FAILED sequence={}", sequence, failure);
        }
        result.addProperty("dispatch_micros", (System.nanoTime() - actionNanos) / 1_000L);
        frame = SFMGamePuppetRenderHarness.completedFrames();
        return false;
    }

    private void apply(ISFMGamePuppetRuntime runtime, JsonObject step) {
        switch (string(step, "op", "observe")) {
            case "observe" -> { }
            case "install_client_manager" -> installClientManagerFixture();
            case "approve_fixture_client_program" -> approveClientManagerFixture();
            case "assert_client_program_rendered" -> {
                var minecraft = Minecraft.getInstance();
                if (minecraft.level == null || minecraft.screen != null
                    || !(minecraft.level.getBlockEntity(absoluteDisplay) instanceof TouchDisplayBlockEntity display)) {
                    throw new IllegalStateException("The fixture is not in its screen-free world");
                }
                var observation = ClientManagerFrameRuntime.observation(display);
                if (!TouchDisplayBlockEntity.BLUE_FIXTURE_IMAGE.equals(observation.texture())
                    || observation.evaluations() < 2 || observation.changedFrames() != 1
                    || !TouchDisplayBlockEntity.RED_FIXTURE_IMAGE.equals(display.content().imageRef())) {
                    throw new IllegalStateException("The renderer did not present the unchanged blue client frame over red semantic content");
                }
            }
            case "revoke_fixture_client_program" -> {
                if (fixtureIdentity == null) throw new IllegalStateException("No fixture program was approved");
                ClientManagerFrameRuntime.consent().revoke(fixtureIdentity, ClientProgramConsentGate.EXECUTE);
            }
            case "aim" -> {
                runtime.positionForTouchDisplayFace(DISPLAY, FACE, coordinate(step, "u"), coordinate(step, "v"));
                aimed = true;
            }
            case "press" -> {
                if (!aimed) throw new IllegalStateException("Aim at the display before pressing it");
                if (pressed) throw new IllegalStateException("This acceptance fixture expects exactly one press");
                double u = coordinate(step, "u");
                double v = coordinate(step, "v");
                requestedTouch.set(new TouchDisplaySurface.UV(u, v));
                try {
                    runtime.pressTouchDisplayFace(DISPLAY, FACE, u, v);
                } catch (RuntimeException failure) {
                    requestedTouch.set(null);
                    throw failure;
                }
                pressed = true;
            }
            case "finish" -> {
                if (!pressed) throw new IllegalStateException("A press is required before finishing the proof");
                if (fixtureIdentity != null) ClientManagerFrameRuntime.consent().store().forget(fixtureIdentity);
                finish = true;
            }
            default -> throw new IllegalArgumentException("Unsupported Touch Display operation: " + step);
        }
    }

    /** Installs a fixed test-only program; file input cannot supply arbitrary source or consent identity. */
    private void installClientManagerFixture() {
        Minecraft minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        if (server == null || server.isPublished() || minecraft.level == null) {
            throw new IllegalStateException("The fixture requires its private integrated world");
        }
        var dimension = minecraft.level.dimension();
        BlockPos managerPosition = absoluteDisplay.offset(-1, -1, 0);
        server.execute(() -> {
            var world = server.getLevel(dimension);
            if (world == null || !(world.getBlockEntity(absoluteDisplay) instanceof TouchDisplayBlockEntity)) return;
            world.setBlockAndUpdate(managerPosition, SFMBlocks.CLIENT_MANAGER.get().defaultBlockState());
            if (!(world.getBlockEntity(managerPosition) instanceof ClientManagerBlockEntity manager)) return;
            ItemStack disk = new ItemStack(SFMItems.DISK.get());
            DiskItem.setProgram(disk, fixtureSource());
            LabelPositionHolder.empty().add("displays", absoluteDisplay).save(disk);
            manager.setDisk(disk);
        });
    }

    private void approveClientManagerFixture() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getBlockEntity(absoluteDisplay.offset(-1, -1, 0))
                instanceof ClientManagerBlockEntity manager)) {
            throw new IllegalStateException("The fixture Client Manager has not synchronized yet");
        }
        if (!manager.storedSource().equals(fixtureSource())) {
            throw new IllegalStateException("Only the exact fixed puppet program may receive this fixture approval");
        }
        fixtureIdentity = ClientManagerFrameRuntime.identityFor(manager)
                .orElseThrow(() -> new IllegalStateException("The fixture program has not synchronized yet"));
        var gate = ClientManagerFrameRuntime.consent();
        for (var capability : fixtureIdentity.requestedCapabilities()) {
            var request = gate.reopenDenied(fixtureIdentity, capability);
            if (request.state() == ClientProgramConsentGate.ConsentState.PENDING) {
                gate.decide(fixtureIdentity, capability, ClientProgramConsentGate.Decision.APPROVE);
            }
        }
    }

    private static String fixtureSource() {
        return "CLIENT BTW\nEVERY FRAME FOR displays AS display DO\nRENDER IMAGE \""
               + TouchDisplayBlockEntity.BLUE_FIXTURE_IMAGE + "\" TO display\nEND";
    }

    private JsonObject observation() {
        Minecraft minecraft = Minecraft.getInstance();
        JsonObject state = new JsonObject();
        state.addProperty("schema", "sfm.touch-display-exploration-observation/1");
        state.addProperty("process_id", ProcessHandle.current().pid());
        state.addProperty("screen", minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
        state.addProperty("completed_frames", SFMGamePuppetRenderHarness.completedFrames());
        state.addProperty("os_pointer_injection", false);
        state.addProperty("aimed", aimed);
        state.addProperty("press_dispatched", pressed);
        state.addProperty("fixture_local_x", DISPLAY.getX());
        state.addProperty("fixture_local_y", DISPLAY.getY());
        state.addProperty("fixture_local_z", DISPLAY.getZ());
        state.addProperty("fixture_face", FACE.getName());
        if (absoluteDisplay != null) {
            state.addProperty("fixture_world_x", absoluteDisplay.getX());
            state.addProperty("fixture_world_y", absoluteDisplay.getY());
            state.addProperty("fixture_world_z", absoluteDisplay.getZ());
            if (minecraft.level != null
                && minecraft.level.getBlockEntity(absoluteDisplay) instanceof TouchDisplayBlockEntity display) {
                state.addProperty("client_image_ref", display.content().imageRef().toString());
                state.addProperty("client_content_revision", display.content().revision());
                state.addProperty("client_has_image_snapshot", display.content().imageSnapshot() != null);
                var frameState = ClientManagerFrameRuntime.observation(display);
                state.addProperty("client_program_evaluations", frameState.evaluations());
                state.addProperty("client_program_changed_frames", frameState.changedFrames());
                state.addProperty("client_program_texture", frameState.texture() == null ? "none" : frameState.texture().toString());
                state.addProperty("client_program_diagnostic", ClientManagerFrameRuntime.diagnosticFor(display).orElse("none"));
                if (minecraft.level.getBlockEntity(absoluteDisplay.offset(-1, -1, 0)) instanceof ClientManagerBlockEntity manager) {
                    state.addProperty("client_manager_synced", ClientManagerFrameRuntime.identityFor(manager).isPresent());
                    state.addProperty("client_manager_diagnostic", ClientManagerFrameRuntime.diagnosticFor(manager).orElse("none"));
                }
            }
        }
        if (minecraft.player != null) {
            state.addProperty("selected_hotbar_slot", minecraft.player.getInventory().selected);
            state.addProperty("main_hand_empty", minecraft.player.getMainHandItem().isEmpty());
            state.addProperty("player_x", minecraft.player.getX());
            state.addProperty("player_y", minecraft.player.getY());
            state.addProperty("player_z", minecraft.player.getZ());
            state.addProperty("player_yaw", minecraft.player.getYRot());
            state.addProperty("player_pitch", minecraft.player.getXRot());
        }
        if (minecraft.hitResult instanceof BlockHitResult hit) {
            JsonObject target = new JsonObject();
            target.addProperty("x", hit.getBlockPos().getX());
            target.addProperty("y", hit.getBlockPos().getY());
            target.addProperty("z", hit.getBlockPos().getZ());
            target.addProperty("face", hit.getDirection().getName());
            target.addProperty("hit_x", hit.getLocation().x);
            target.addProperty("hit_y", hit.getLocation().y);
            target.addProperty("hit_z", hit.getLocation().z);
            state.add("crosshair_block_hit", target);
        }
        return state;
    }

    private static JsonObject readRequest(Path path) {
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
    }

    private String prefix() {
        return String.format(Locale.ROOT, "%06d", sequence);
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static double coordinate(JsonObject json, String key) {
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < 0D || value > 1D) {
            throw new IllegalArgumentException(key + " must be finite and in [0,1]");
        }
        return value;
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
                throw new IllegalStateException("Cannot write Touch Display exploration evidence: " + path, failure);
            }
        });
    }
}
