package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualController;
import ca.teamdman.sfm.client.overlay.scene.SFMClientOverlayRuntime;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.Bounds;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.ClipPolicy;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.InputMode;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.OverlayState;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.Placement;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.ReferenceFrame;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.SizeConstraints;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMExternalCliPuppetProcess;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.mixins.KeyboardHandlerInvoker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Natural X6 proof for the reusable History Graph gameplay-overlay substrate. */
public final class ExerciseHistoryOverlayPuppetAction implements SFMPuppetAction {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String HISTORY_SELECTOR = SFMEntitySelector.exact(
            SFMEntitySelector.Domain.OVERLAY,
            SFMOverlaySceneContract.HISTORY_OVERLAY_ID
    ).canonical();
    private static final String AMBIENT_HASH = "sha256:" + "0".repeat(64);
    private static final int WORLD_TIMEOUT_TICKS = 20 * 60;
    private static final int MOVEMENT_TICKS = 30;
    private static final int SETTLE_TICKS = 20;
    private static final int CLI_TIMEOUT_TICKS = 20 * 60;
    private static final double REQUIRED_MOVEMENT = 0.05D;
    private static final int CLI_Z_ORDER = 321;
    private static final Placement MOVED_PLACEMENT = new Placement(
            ReferenceFrame.GUI_SAFE_VIEWPORT,
            0.0D,
            1.0D,
            0.0D,
            1.0D,
            8,
            -8,
            Optional.of(new SizeConstraints(96, 72, 320, 190, 4096, 4096)),
            ClipPolicy.CLAMP_TO_SAFE_VIEWPORT
    );

    private final JsonObject sceneEvidence = evidence("sfm.history-overlay.scene-evidence/1");
    private final JsonObject inputEvidence = evidence("sfm.history-overlay.input-evidence/1");
    private final JsonObject actionEvidence = evidence("sfm.history-overlay.action-evidence/1");
    private final JsonObject cliEvidence = evidence("sfm.history-overlay.cli-evidence/1");
    private final JsonObject restoreEvidence = evidence("sfm.history-overlay.restore-evidence/1");
    private final JsonObject cleanupEvidence = evidence("sfm.history-overlay.cleanup-evidence/1");
    private final JsonObject sceneSnapshots = new JsonObject();
    private final JsonObject inputSnapshots = new JsonObject();
    private final JsonArray actionInvocations = new JsonArray();
    private final JsonArray mandatoryScreenshots = new JsonArray();

    private Phase phase = Phase.WAIT_WORLD;
    private int phaseTicks;
    private Vec3 movementStart = Vec3.ZERO;
    private long movementGameTime;
    private long movementInputSequence;
    private double passiveDistance;
    private double interactiveDistance;
    private double releasedDistance;
    private String narrationBeforeInteraction = "";
    private String retainedNarration = "";
    private String retainedPlacement = "";
    private String retainedCanonical = "";
    private Bounds retainedBounds;
    private long restoreCountBefore;
    private long cliActionCountBefore;
    private boolean clearLevelReturned;
    private SFMHistoryGraphRuntime.Registration historyRegistration;
    private SFMExternalCliPuppetProcess cliProcess;

    public ExerciseHistoryOverlayPuppetAction() {
        sceneEvidence.add("snapshots", sceneSnapshots);
        inputEvidence.add("snapshots", inputSnapshots);
        actionEvidence.add("invocations", actionInvocations);
        sceneEvidence.add("mandatory_screenshot_capture_ids", mandatoryScreenshots);
    }

    @Override
    public String description() {
        return "exercise the reusable in-world History Graph overlay";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        try {
            return switch (phase) {
                case WAIT_WORLD -> waitWorld();
                case SETUP -> setup();
                case WAIT_PASSIVE_READY -> waitPassiveReady();
                case START_PASSIVE_MOVEMENT -> startMovement(false);
                case WAIT_PASSIVE_MOVEMENT -> finishPassiveMovement();
                case CAPTURE_PASSIVE -> capture(runtime, "history-overlay-passive",
                        "Passive History Graph remains visible while ordinary world movement and ticks continue.",
                        Phase.SETTLE_AFTER_PASSIVE);
                case SETTLE_AFTER_PASSIVE -> settleThen(Phase.ENTER_INTERACTIVE);
                case ENTER_INTERACTIVE -> enterInteractive();
                case WAIT_INTERACTIVE_READY -> waitInteractiveReady();
                case WAIT_INTERACTIVE_MOVEMENT -> finishInteractiveMovement();
                case CAPTURE_INTERACTIVE -> capture(runtime, "history-overlay-interactive",
                        "Interactive focus routes History Graph navigation and consumes gameplay movement input.",
                        Phase.RELEASE_INTERACTIVE);
                case RELEASE_INTERACTIVE -> releaseInteractive();
                case WAIT_RELEASED_MOVEMENT -> finishReleasedMovement();
                case CAPTURE_RELEASED -> capture(runtime, "history-overlay-released",
                        "Explicit focus release restores ordinary gameplay movement without hiding the graph.",
                        Phase.MOVE_OVERLAY);
                case MOVE_OVERLAY -> moveOverlay();
                case WAIT_MOVED -> waitMoved();
                case CAPTURE_MOVED -> capture(runtime, "history-overlay-moved",
                        "Selector-explicit placement moves the same retained History Graph to the lower-left.",
                        Phase.HIDE_OVERLAY);
                case HIDE_OVERLAY -> hideOverlay();
                case WAIT_HIDDEN -> waitHidden();
                case SHOW_OVERLAY -> showOverlay();
                case WAIT_RESHOWN -> waitReshown();
                case CAPTURE_RESHOWN -> capture(runtime, "history-overlay-reshown",
                        "Hide/show preserves placement and host-local History Graph selection state.",
                        Phase.START_EXTERNAL_CLI);
                case START_EXTERNAL_CLI -> startExternalCli();
                case WAIT_EXTERNAL_CLI -> waitExternalCli();
                case WAIT_CLI_RENDER -> waitCliRender();
                case CAPTURE_CLI -> capture(runtime, "history-overlay-cli",
                        "The external sfm.exe invocation reaches the same registered overlay scene runtime.",
                        Phase.DIRECT_RESTORE);
                case DIRECT_RESTORE -> directRestore();
                case WAIT_DIRECT_RESTORE_RENDER -> waitDirectRestoreRender();
                case CAPTURE_DIRECT_RESTORE -> capture(runtime, "history-overlay-direct-restore",
                        "Direct canonical restore reproduces the retained scene without replaying layout actions.",
                        Phase.PREPARE_CLEANUP);
                case PREPARE_CLEANUP -> prepareCleanup();
                case WORLD_UNLOAD -> unloadWorld();
                case WAIT_WORLD_UNLOAD -> finishWorldUnload();
                case WRITE_ARTIFACTS -> writeArtifacts(runtime);
                case COMPLETE -> true;
            };
        } catch (RuntimeException | Error failure) {
            closeResources();
            throw failure;
        }
    }

    private boolean waitWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null && minecraft.screen == null) {
            next(Phase.SETUP);
            return false;
        }
        if (++phaseTicks > WORLD_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for the fresh puppet world");
        }
        return false;
    }

    private boolean setup() {
        Minecraft minecraft = requireWorld();
        SFMClientOverlayRuntime overlay = SFMClientOverlayRuntime.get();
        overlay.restore(SFMOverlaySceneContract.SceneState.defaults());
        SFMWorkspaceCounterfactualController controller = new SFMWorkspaceCounterfactualController(
                "episode:history-overlay-puppet",
                () -> AMBIENT_HASH
        );
        SFMHistoryGraphRuntime history = SFMHistoryGraphRuntime.get();
        historyRegistration = history.register(controller);
        history.setActiveMachine(controller.machineId());
        overlay.tick(minecraft);
        invokeAction("sfm:overlay/visibility/set " + HISTORY_SELECTOR + " visible");
        recordScene("setup");
        next(Phase.WAIT_PASSIVE_READY);
        return false;
    }

    private boolean waitPassiveReady() {
        SFMClientOverlayRuntime.RuntimeSnapshot snapshot = overlay().snapshot();
        OverlayState state = historyOverlay();
        if (snapshot.hostedOverlayIds().contains(SFMOverlaySceneContract.HISTORY_OVERLAY_ID)
                && snapshot.resolvedBounds().containsKey(SFMOverlaySceneContract.HISTORY_OVERLAY_ID)) {
            require(state.visible(), "History overlay was not visible");
            require(state.inputMode() == InputMode.PASSIVE, "History overlay did not begin passive");
            require(snapshot.scene().focusedOverlay().isEmpty(), "Passive overlay unexpectedly owned focus");
            recordScene("passive-ready");
            recordInput("passive-ready");
            next(Phase.START_PASSIVE_MOVEMENT);
            return false;
        }
        if (++phaseTicks > WORLD_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for passive History Graph overlay render");
        }
        return false;
    }

    private boolean startMovement(boolean interactive) {
        Minecraft minecraft = requireWorld();
        movementStart = minecraft.player.position();
        movementGameTime = minecraft.level.getGameTime();
        movementInputSequence = overlay().inputEvidence().sequence();
        rawKey(GLFW.GLFW_KEY_W, GLFW.GLFW_PRESS);
        next(interactive ? Phase.WAIT_INTERACTIVE_MOVEMENT : Phase.WAIT_PASSIVE_MOVEMENT);
        return false;
    }

    private boolean finishPassiveMovement() {
        if (++phaseTicks < MOVEMENT_TICKS) return false;
        rawKey(GLFW.GLFW_KEY_W, GLFW.GLFW_RELEASE);
        Minecraft minecraft = requireWorld();
        passiveDistance = horizontalDistance(movementStart, minecraft.player.position());
        long elapsed = minecraft.level.getGameTime() - movementGameTime;
        require(elapsed >= MOVEMENT_TICKS - 2L, "World ticks did not continue beneath passive overlay");
        require(passiveDistance > REQUIRED_MOVEMENT,
                "Gameplay movement did not continue beneath passive overlay: " + passiveDistance);
        require(overlay().inputEvidence().keyForwarded() >= 2,
                "Passive overlay did not publish forwarded keyboard evidence");
        JsonObject movement = movementEvidence(elapsed, passiveDistance, movementInputSequence,
                overlay().inputEvidence().sequence());
        movement.addProperty("mode", "passive");
        inputEvidence.add("passive_movement", movement);
        recordInput("after-passive-movement");
        next(Phase.CAPTURE_PASSIVE);
        return false;
    }

    private boolean settleThen(Phase target) {
        if (++phaseTicks >= SETTLE_TICKS) next(target);
        return false;
    }

    private boolean enterInteractive() {
        invokeAction("sfm:overlay/input-mode/set " + HISTORY_SELECTOR + " interactive");
        invokeAction("sfm:overlay/focus/acquire " + HISTORY_SELECTOR);
        next(Phase.WAIT_INTERACTIVE_READY);
        return false;
    }

    private boolean waitInteractiveReady() {
        if (overlay().scene().focusedOverlay().map(id -> id.value().equals(
                SFMOverlaySceneContract.HISTORY_OVERLAY_ID)).orElse(false)) {
            narrationBeforeInteraction = currentNarration();
            Minecraft minecraft = requireWorld();
            movementStart = minecraft.player.position();
            movementGameTime = minecraft.level.getGameTime();
            movementInputSequence = overlay().inputEvidence().sequence();
            rawKey(GLFW.GLFW_KEY_DOWN, GLFW.GLFW_PRESS);
            rawKey(GLFW.GLFW_KEY_DOWN, GLFW.GLFW_RELEASE);
            rawKey(GLFW.GLFW_KEY_W, GLFW.GLFW_PRESS);
            next(Phase.WAIT_INTERACTIVE_MOVEMENT);
            return false;
        }
        if (++phaseTicks > 100) throw new IllegalStateException("Interactive overlay did not acquire focus");
        return false;
    }

    private boolean finishInteractiveMovement() {
        if (++phaseTicks < MOVEMENT_TICKS) return false;
        rawKey(GLFW.GLFW_KEY_W, GLFW.GLFW_RELEASE);
        Minecraft minecraft = requireWorld();
        interactiveDistance = horizontalDistance(movementStart, minecraft.player.position());
        long elapsed = minecraft.level.getGameTime() - movementGameTime;
        String narrationAfter = currentNarration();
        require(elapsed >= MOVEMENT_TICKS - 2L, "World ticks stopped while overlay owned input focus");
        require(interactiveDistance < Math.max(0.08D, passiveDistance * 0.25D),
                "Focused overlay failed to consume gameplay movement: passive=" + passiveDistance
                        + ", interactive=" + interactiveDistance);
        require(!narrationBeforeInteraction.equals(narrationAfter),
                "Arrow-key interaction did not change History Graph selection narration");
        require(overlay().inputEvidence().keyConsumed() >= 4,
                "Interactive overlay did not publish consumed keyboard evidence");
        JsonObject movement = movementEvidence(elapsed, interactiveDistance, movementInputSequence,
                overlay().inputEvidence().sequence());
        movement.addProperty("mode", "interactive");
        movement.addProperty("narration_before", narrationBeforeInteraction);
        movement.addProperty("narration_after", narrationAfter);
        inputEvidence.add("interactive_movement", movement);
        recordInput("after-interactive-movement");
        recordScene("interactive");
        next(Phase.CAPTURE_INTERACTIVE);
        return false;
    }

    private boolean releaseInteractive() {
        invokeAction("sfm:overlay/focus/release " + HISTORY_SELECTOR);
        require(overlay().scene().focusedOverlay().isEmpty(), "Explicit release left overlay focused");
        Minecraft minecraft = requireWorld();
        movementStart = minecraft.player.position();
        movementGameTime = minecraft.level.getGameTime();
        movementInputSequence = overlay().inputEvidence().sequence();
        rawKey(GLFW.GLFW_KEY_W, GLFW.GLFW_PRESS);
        next(Phase.WAIT_RELEASED_MOVEMENT);
        return false;
    }

    private boolean finishReleasedMovement() {
        if (++phaseTicks < MOVEMENT_TICKS) return false;
        rawKey(GLFW.GLFW_KEY_W, GLFW.GLFW_RELEASE);
        Minecraft minecraft = requireWorld();
        releasedDistance = horizontalDistance(movementStart, minecraft.player.position());
        long elapsed = minecraft.level.getGameTime() - movementGameTime;
        require(releasedDistance > REQUIRED_MOVEMENT,
                "Gameplay movement was not restored after overlay focus release: " + releasedDistance);
        JsonObject movement = movementEvidence(elapsed, releasedDistance, movementInputSequence,
                overlay().inputEvidence().sequence());
        movement.addProperty("mode", "released");
        inputEvidence.add("released_movement", movement);
        recordInput("after-release-movement");
        recordScene("released");
        next(Phase.CAPTURE_RELEASED);
        return false;
    }

    private boolean moveOverlay() {
        invokeAction("sfm:overlay/placement/set " + HISTORY_SELECTOR + " " + MOVED_PLACEMENT.canonical());
        next(Phase.WAIT_MOVED);
        return false;
    }

    private boolean waitMoved() {
        if (++phaseTicks < 8) return false;
        SFMClientOverlayRuntime.RuntimeSnapshot snapshot = overlay().snapshot();
        retainedPlacement = historyOverlay().placement().canonical();
        retainedNarration = currentNarration();
        retainedBounds = requireBounds(snapshot);
        require(retainedPlacement.equals(MOVED_PLACEMENT.canonical()), "Moved placement did not persist");
        recordScene("moved");
        next(Phase.CAPTURE_MOVED);
        return false;
    }

    private boolean hideOverlay() {
        invokeAction("sfm:overlay/visibility/set " + HISTORY_SELECTOR + " hidden");
        next(Phase.WAIT_HIDDEN);
        return false;
    }

    private boolean waitHidden() {
        if (++phaseTicks < 6) return false;
        require(!historyOverlay().visible(), "Hide action did not hide overlay");
        require(overlay().snapshot().hostedOverlayIds().contains(SFMOverlaySceneContract.HISTORY_OVERLAY_ID),
                "Hide action discarded retained host-local content state");
        recordScene("hidden");
        next(Phase.SHOW_OVERLAY);
        return false;
    }

    private boolean showOverlay() {
        invokeAction("sfm:overlay/visibility/set " + HISTORY_SELECTOR + " visible");
        next(Phase.WAIT_RESHOWN);
        return false;
    }

    private boolean waitReshown() {
        if (++phaseTicks < 8) return false;
        require(historyOverlay().visible(), "Show action did not reveal overlay");
        require(historyOverlay().placement().canonical().equals(retainedPlacement),
                "Hide/show changed overlay placement");
        require(currentNarration().equals(retainedNarration),
                "Hide/show discarded History Graph host-local selection state");
        require(requireBounds(overlay().snapshot()).equals(retainedBounds),
                "Hide/show changed resolved overlay bounds");
        recordScene("reshown");
        next(Phase.CAPTURE_RESHOWN);
        return false;
    }

    private boolean startExternalCli() {
        ArrayList<String> argv = new ArrayList<>();
        String executable = System.getProperty(SFMExternalCliPuppetProcess.EXECUTABLE_PROPERTY, "sfm.exe").trim();
        if (executable.isEmpty()) throw new IllegalStateException("SFM CLI executable property is empty");
        argv.add(executable);
        argv.addAll(List.of(
                "invoke",
                "sfm:overlay/z-order/set",
                HISTORY_SELECTOR,
                Integer.toString(CLI_Z_ORDER),
                "--instance-pid",
                Long.toString(ProcessHandle.current().pid()),
                "--output-format",
                "json"
        ));
        cliActionCountBefore = overlay().snapshot().actionCount();
        cliProcess = SFMExternalCliPuppetProcess.start(argv);
        cliEvidence.add("argv", GSON.toJsonTree(argv));
        SFM.LOGGER.info("SFM_GAME_PUPPET_HISTORY_OVERLAY_CLI_STARTED command={}", String.join(" ", argv));
        next(Phase.WAIT_EXTERNAL_CLI);
        return false;
    }

    private boolean waitExternalCli() {
        if (++phaseTicks > CLI_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for external overlay CLI invocation");
        }
        Optional<SFMExternalCliPuppetProcess.Completed> completed = cliProcess.poll();
        if (completed.isEmpty()) return false;
        SFMExternalCliPuppetProcess.Completed result = completed.orElseThrow();
        require(result.exitCode() == 0,
                "External overlay CLI exited " + result.exitCode() + "; stderr=" + result.stderr());
        JsonObject stdout;
        try {
            stdout = JsonParser.parseString(result.stdout()).getAsJsonObject();
        } catch (RuntimeException failure) {
            throw new IllegalStateException("External overlay CLI did not emit one JSON object: "
                    + result.stdout(), failure);
        }
        cliEvidence.addProperty("process_id", result.processId());
        cliEvidence.addProperty("exit_code", result.exitCode());
        cliEvidence.addProperty("duration_ms", result.durationMillis());
        cliEvidence.add("stdout", stdout);
        cliEvidence.addProperty("stderr", result.stderr());
        require(historyOverlay().zOrder() == CLI_Z_ORDER,
                "External CLI did not reach the overlay z-order action");
        require(overlay().snapshot().actionCount() > cliActionCountBefore,
                "External CLI bypassed the shared registered-action runtime");
        cliEvidence.addProperty("same_runtime_action_count_advanced", true);
        cliProcess.close();
        cliProcess = null;
        next(Phase.WAIT_CLI_RENDER);
        return false;
    }

    private boolean waitCliRender() {
        if (++phaseTicks < 6) return false;
        recordScene("external-cli");
        next(Phase.CAPTURE_CLI);
        return false;
    }

    private boolean directRestore() {
        SFMClientOverlayRuntime overlay = overlay();
        retainedCanonical = overlay.encodeCanonical();
        retainedPlacement = historyOverlay().placement().canonical();
        retainedBounds = requireBounds(overlay.snapshot());
        restoreCountBefore = overlay.snapshot().directRestoreCount();
        invokeAction("sfm:overlay/placement/set " + HISTORY_SELECTOR + " "
                + SFMOverlaySceneContract.SceneState.defaults().overlays().stream()
                .filter(state -> state.id().value().equals(SFMOverlaySceneContract.HISTORY_OVERLAY_ID))
                .findFirst()
                .orElseThrow()
                .placement()
                .canonical());
        invokeAction("sfm:overlay/visibility/set " + HISTORY_SELECTOR + " hidden");
        overlay.restoreCanonical(retainedCanonical);
        String immediate = overlay.encodeCanonical();
        require(immediate.equals(retainedCanonical),
                "Direct scene restore did not byte-identically re-encode before render");
        require(overlay.snapshot().directRestoreCount() == restoreCountBefore + 1,
                "Direct restore count did not advance exactly once");
        restoreEvidence.addProperty("canonical_before_restore", retainedCanonical);
        restoreEvidence.addProperty("canonical_immediately_after_restore", immediate);
        restoreEvidence.addProperty("byte_identical_before_render", true);
        restoreEvidence.addProperty("historical_layout_actions_replayed", false);
        next(Phase.WAIT_DIRECT_RESTORE_RENDER);
        return false;
    }

    private boolean waitDirectRestoreRender() {
        if (++phaseTicks < 8) return false;
        Bounds restoredBounds = requireBounds(overlay().snapshot());
        require(historyOverlay().placement().canonical().equals(retainedPlacement),
                "Direct restore changed retained placement");
        require(restoredBounds.equals(retainedBounds), "Direct restore changed retained resolved bounds");
        restoreEvidence.add("bounds_before", bounds(retainedBounds));
        restoreEvidence.add("bounds_after", bounds(restoredBounds));
        restoreEvidence.addProperty("bounds_equal_after_render", true);
        recordScene("direct-restored");
        next(Phase.CAPTURE_DIRECT_RESTORE);
        return false;
    }

    private boolean prepareCleanup() {
        invokeAction("sfm:overlay/input-mode/set " + HISTORY_SELECTOR + " interactive");
        invokeAction("sfm:overlay/focus/acquire " + HISTORY_SELECTOR);
        rawKey(GLFW.GLFW_KEY_W, GLFW.GLFW_PRESS);
        require(overlay().scene().focusedOverlay().isPresent(), "Cleanup precondition did not own focus");
        next(Phase.WORLD_UNLOAD);
        return false;
    }

    private boolean unloadWorld() {
        Minecraft minecraft = requireWorld();
        // clearLevel pumps nested client ticks while waiting for the integrated
        // server. Advance first so those nested ticks cannot call clearLevel
        // recursively through this same puppet action.
        next(Phase.WAIT_WORLD_UNLOAD);
        // Match Minecraft's ordinary "Save and Quit to Title" order: the
        // client-level disconnect asks the integrated server to stop before
        // clearLevel waits for that stop while pumping render ticks.
        minecraft.level.disconnect();
        minecraft.clearLevel();
        clearLevelReturned = true;
        return false;
    }

    private boolean finishWorldUnload() {
        if (!clearLevelReturned) return false;
        Minecraft minecraft = Minecraft.getInstance();
        overlay().tick(minecraft);
        rawKey(GLFW.GLFW_KEY_W, GLFW.GLFW_RELEASE);
        SFMClientOverlayRuntime.RuntimeSnapshot snapshot = overlay().snapshot();
        require(snapshot.scene().focusedOverlay().isEmpty(), "World unload retained overlay focus");
        require(snapshot.hostedOverlayIds().isEmpty(), "World unload retained hosted overlay content");
        require(snapshot.lastLifecycleReason().contains("world"),
                "World unload did not publish a world lifecycle reason: " + snapshot.lastLifecycleReason());
        cleanupEvidence.addProperty("world_unloaded", Minecraft.getInstance().level == null);
        cleanupEvidence.addProperty("focused_overlay_present", snapshot.scene().focusedOverlay().isPresent());
        cleanupEvidence.add("hosted_overlay_ids", GSON.toJsonTree(snapshot.hostedOverlayIds()));
        cleanupEvidence.addProperty("last_lifecycle_reason", snapshot.lastLifecycleReason());
        cleanupEvidence.add("final_input", input(snapshot.input()));
        recordScene("world-unloaded");
        closeHistoryRegistration();
        next(Phase.WRITE_ARTIFACTS);
        return false;
    }

    private boolean writeArtifacts(ISFMGamePuppetRuntime runtime) {
        inputEvidence.addProperty("passive_distance", passiveDistance);
        inputEvidence.addProperty("interactive_distance", interactiveDistance);
        inputEvidence.addProperty("released_distance", releasedDistance);
        runtime.writeArtifact("history-overlay-scene", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(sceneEvidence));
        runtime.writeArtifact("history-overlay-input", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(inputEvidence));
        runtime.writeArtifact("history-overlay-actions", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(actionEvidence));
        runtime.writeArtifact("history-overlay-cli", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(cliEvidence));
        runtime.writeArtifact("history-overlay-restore", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(restoreEvidence));
        runtime.writeArtifact("history-overlay-cleanup", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(cleanupEvidence));
        phase = Phase.COMPLETE;
        return true;
    }

    private boolean capture(ISFMGamePuppetRuntime runtime, String name, String text, Phase next) {
        if (!runtime.captureWithHud(name, Component.literal("History overlay: ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK)))) return false;
        mandatoryScreenshots.add(name);
        next(next);
        return false;
    }

    private void invokeAction(String action) {
        String canonical = "sfm action invoke " + action;
        ArrayList<String> feedback = new ArrayList<>();
        int affected;
        try {
            affected = SFMClientActionExecutor.execute(
                    canonical,
                    SFMClientActionContext.create(this, () -> Minecraft.getInstance().level != null),
                    component -> feedback.add(component.getString())
            );
        } catch (CommandSyntaxException failure) {
            throw new IllegalStateException("Overlay action failed: " + canonical, failure);
        }
        require(affected >= 1, "Overlay action affected no targets: " + canonical);
        JsonObject invocation = new JsonObject();
        invocation.addProperty("canonical", canonical);
        invocation.addProperty("affected", affected);
        invocation.add("feedback", GSON.toJsonTree(feedback));
        invocation.addProperty("scene_revision_after", overlay().scene().revision());
        actionInvocations.add(invocation);
    }

    private void recordScene(String label) {
        sceneSnapshots.add(label, JsonParser.parseString(overlay().encodeCanonical()));
    }

    private void recordInput(String label) {
        inputSnapshots.add(label, input(overlay().inputEvidence()));
    }

    private static JsonObject input(SFMClientOverlayRuntime.InputEvidence evidence) {
        JsonObject answer = new JsonObject();
        answer.addProperty("sequence", evidence.sequence());
        answer.addProperty("key_consumed", evidence.keyConsumed());
        answer.addProperty("key_forwarded", evidence.keyForwarded());
        answer.addProperty("character_consumed", evidence.characterConsumed());
        answer.addProperty("character_forwarded", evidence.characterForwarded());
        answer.addProperty("pointer_consumed", evidence.pointerConsumed());
        answer.addProperty("pointer_forwarded", evidence.pointerForwarded());
        answer.addProperty("scroll_consumed", evidence.scrollConsumed());
        answer.addProperty("scroll_forwarded", evidence.scrollForwarded());
        answer.add("recent_events", GSON.toJsonTree(evidence.recentEvents()));
        return answer;
    }

    private static JsonObject movementEvidence(long ticks, double distance, long beforeSequence, long afterSequence) {
        JsonObject answer = new JsonObject();
        answer.addProperty("world_ticks_elapsed", ticks);
        answer.addProperty("horizontal_distance", distance);
        answer.addProperty("input_sequence_before", beforeSequence);
        answer.addProperty("input_sequence_after", afterSequence);
        return answer;
    }

    private static JsonObject bounds(Bounds value) {
        JsonObject answer = new JsonObject();
        answer.addProperty("x", value.x());
        answer.addProperty("y", value.y());
        answer.addProperty("width", value.width());
        answer.addProperty("height", value.height());
        return answer;
    }

    private static JsonObject evidence(String schema) {
        JsonObject answer = new JsonObject();
        answer.addProperty("schema", schema);
        return answer;
    }

    private SFMClientOverlayRuntime overlay() {
        return SFMClientOverlayRuntime.get();
    }

    private OverlayState historyOverlay() {
        return overlay().scene().overlays().stream()
                .filter(state -> state.id().value().equals(SFMOverlaySceneContract.HISTORY_OVERLAY_ID))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("History overlay instance is missing"));
    }

    private String currentNarration() {
        return Objects.requireNonNullElse(
                overlay().snapshot().contentNarration().get(SFMOverlaySceneContract.HISTORY_OVERLAY_ID),
                ""
        );
    }

    private static Bounds requireBounds(SFMClientOverlayRuntime.RuntimeSnapshot snapshot) {
        Bounds answer = snapshot.resolvedBounds().get(SFMOverlaySceneContract.HISTORY_OVERLAY_ID);
        if (answer == null) throw new IllegalStateException("History overlay has no resolved bounds");
        return answer;
    }

    private static Minecraft requireWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            throw new IllegalStateException("History overlay puppet requires a loaded client world");
        }
        return minecraft;
    }

    private static void rawKey(int keyCode, int action) {
        Minecraft minecraft = Minecraft.getInstance();
        ((KeyboardHandlerInvoker) (Object) minecraft.keyboardHandler).sfm$invokeKeyPress(
                minecraft.getWindow().getWindow(),
                keyCode,
                0,
                action,
                0
        );
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        return Math.hypot(second.x - first.x, second.z - first.z);
    }

    private void next(Phase next) {
        phase = Objects.requireNonNull(next, "next");
        phaseTicks = 0;
    }

    private void closeHistoryRegistration() {
        if (historyRegistration == null) return;
        historyRegistration.close();
        historyRegistration = null;
    }

    private void closeResources() {
        if (cliProcess != null) {
            cliProcess.close();
            cliProcess = null;
        }
        closeHistoryRegistration();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Phase {
        WAIT_WORLD,
        SETUP,
        WAIT_PASSIVE_READY,
        START_PASSIVE_MOVEMENT,
        WAIT_PASSIVE_MOVEMENT,
        CAPTURE_PASSIVE,
        SETTLE_AFTER_PASSIVE,
        ENTER_INTERACTIVE,
        WAIT_INTERACTIVE_READY,
        WAIT_INTERACTIVE_MOVEMENT,
        CAPTURE_INTERACTIVE,
        RELEASE_INTERACTIVE,
        WAIT_RELEASED_MOVEMENT,
        CAPTURE_RELEASED,
        MOVE_OVERLAY,
        WAIT_MOVED,
        CAPTURE_MOVED,
        HIDE_OVERLAY,
        WAIT_HIDDEN,
        SHOW_OVERLAY,
        WAIT_RESHOWN,
        CAPTURE_RESHOWN,
        START_EXTERNAL_CLI,
        WAIT_EXTERNAL_CLI,
        WAIT_CLI_RENDER,
        CAPTURE_CLI,
        DIRECT_RESTORE,
        WAIT_DIRECT_RESTORE_RENDER,
        CAPTURE_DIRECT_RESTORE,
        PREPARE_CLEANUP,
        WORLD_UNLOAD,
        WAIT_WORLD_UNLOAD,
        WRITE_ARTIFACTS,
        COMPLETE
    }
}
