package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.program.*;
import ca.teamdman.sfm.client.program.signing.ClientProgramSigningRuntime;
import ca.teamdman.sfm.client.screen.*;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.widget.SFMSigningPassphraseWidget;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.signature.ProgramSignatureDescriptor;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetRenderHarness;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Bounded, file-driven human-interface acceptance. No approval or signature is inserted directly.
 * Fixed source replacement is an explicit second-author fixture operation, not save/sign evidence.
 * Requests select synthetic input by name; they can never contain a real passphrase or arbitrary path.
 */
public final class ExploreClientProgramSigningPuppetAction implements SFMPuppetAction {
    public static final BlockPos DISPLAY = new BlockPos(2, 2, 2);
    public static final BlockPos MANAGER = new BlockPos(1, 1, 2);
    private static final int MAX_REQUEST_BYTES = 4096;
    private static final Set<String> REQUIRED = Set.of("no_auto_sign", "cancel", "delayed", "pen",
            "alternative", "keyboard_alternative", "delayed_armed", "stale", "diff", "network_signature", "editor_saved_ack", "save_did_not_sign");
    private final AtomicBoolean proofComplete;
    private final Path directory, keysDirectory;
    private final Set<ClientProgramIdentity> identities = new LinkedHashSet<>();
    private final Set<String> witnesses = new TreeSet<>();
    private final Map<String, String> fingerprints = new LinkedHashMap<>();
    private final Set<String> submittedFingerprints = new HashSet<>();
    private ClientProgramIdentity overrideIdentity;
    private AutoCloseable keyOverride;
    private BlockPos absoluteManager, absoluteDisplay;
    private CompletableFuture<JsonObject> read, serverWork;
    private CompletableFuture<Void> write;
    private JsonObject request, response;
    private BooleanSupplier awaited;
    private long awaitDeadline, frame, actionNanos, nextPoll;
    private long staleBaseRevision = -1;
    private long editorBaseRevision = -1;
    private String editorExpectedSource;
    private boolean editorSaveSubmitted;
    private int sequence = 1, failedRequests;
    private boolean initialized, finish;
    private String keyRole = "alice";

    public ExploreClientProgramSigningPuppetAction(AtomicBoolean proofComplete) {
        this.proofComplete = Objects.requireNonNull(proofComplete);
        directory = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize()
                .resolve("sfm-puppet/client-signing-control/session-" + UUID.randomUUID());
        keysDirectory = directory.resolve("keys");
    }

    public static String source(boolean edited) {
        return "CLIENT BTW\n" + (edited ? "-- Second author fixture revision\n" : "")
                + "EVERY FRAME FOR displays AS display DO\nRENDER IMAGE \""
                + TouchDisplayBlockEntity.BLUE_FIXTURE_IMAGE + "\" TO display\nEND";
    }

    @Override public String description() {
        return "file-driven client signing request " + sequence + " in " + directory;
    }

    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!initialized) {
            absoluteManager = runtime.absoluteGameTestPos(MANAGER);
            absoluteDisplay = runtime.absoluteGameTestPos(DISPLAY);
            if (Minecraft.getInstance().level == null
                    || !(Minecraft.getInstance().level.getBlockEntity(absoluteManager) instanceof ClientManagerBlockEntity)
                    || ClientManagerFrameRuntime.identityFor(manager()).isEmpty()) return false;
            ensureIdentity();
            runtime.positionForTouchDisplayFace(DISPLAY, Direction.NORTH, 0.5, 0.5);
            initialized = true;
            JsonObject ready = observation();
            ready.addProperty("control_directory", directory.toString());
            ready.addProperty("request_pattern", "000001.request.json, then monotonically increasing");
            ready.addProperty("key_directory", keysDirectory.toString());
            ready.addProperty("fixture_secret_policy", "Only type_fixture inserts a built-in disposable test phrase; never put secrets in requests.");
            ready.addProperty("operations", "observe, open_consent_panel, panel_control, confirmation_button, click, type_fixture,"
                    + " key_role, arm_fixture, assert_review_diff, assert_ui, await_ui, source_edit, type_fixture_source, editor_save, screen_key, assert_history, await_history, assert_consent, close_review, finish");
            ready.add("required_witnesses", json(REQUIRED));
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
            if (serverWork != null) {
                if (!serverWork.isDone()) return false;
                try {
                    JsonObject server = serverWork.join();
                    response.add("server", server);
                    if (string(request, "op", "").equals("assert_history")
                            || string(request, "op", "").equals("await_history")) completeHistoryProof(server);
                } catch (RuntimeException failure) { failed(failure); }
                serverWork = null;
            }
            if (awaited != null) {
                try {
                    if (!awaited.getAsBoolean()) {
                        if (System.nanoTime() < awaitDeadline) return false;
                        throw new IllegalStateException("The expected live UI state did not arrive");
                    }
                    awaited = null;
                } catch (RuntimeException failure) { awaited = null; failed(failure); }
            }
            if (serverWork != null) return false;
            if (SFMGamePuppetRenderHarness.completedFrames() <= frame || System.nanoTime() - actionNanos < 150_000_000L) return false;
            String capture = "signing-step-" + String.format(Locale.ROOT, "%04d", sequence);
            if (!runtime.captureWithHud(capture, Component.literal("Signing " + sequence + ": " + string(request, "op", "observe")))) return false;
            response.add("observation", observation());
            response.addProperty("capture_name", capture);
            response.addProperty("input_to_observation_ms", (System.nanoTime() - actionNanos) / 1_000_000L);
            response.add("request", request);
            write = writeAsync(directory.resolve(prefix() + ".response.json"), response);
            request = null;
            response = null;
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
        actionNanos = System.nanoTime();
        response = new JsonObject();
        response.addProperty("sequence", sequence);
        response.addProperty("status", "dispatched");
        try { apply(runtime, request); }
        catch (RuntimeException failure) { failed(failure); }
        frame = SFMGamePuppetRenderHarness.completedFrames();
        return false;
    }

    private void apply(ISFMGamePuppetRuntime runtime, JsonObject step) {
        switch (string(step, "op", "observe")) {
            case "observe" -> { }
            case "open_consent_panel" -> {
                ensureIdentity();
                if (!runtime.openCommandPalette()) throw new IllegalStateException("Command palette unavailable");
                runtime.executeCommandPalette("sfm action invoke sfm:panel/open sfm:client_script_consents");
            }
            case "panel_control" -> clickPanel(step);
            case "confirmation_button" -> clickConfirmation(step);
            case "click" -> clickControl(string(step, "control", ""), !step.has("handled") || step.get("handled").getAsBoolean());
            case "key_role" -> {
                String role = string(step, "role", "alice");
                if (!role.equals("alice") && !role.equals("bob")) throw new IllegalArgumentException("Only disposable Alice and Bob fixtures exist");
                keyRole = role;
            }
            case "type_fixture" -> typeFixture(runtime, string(step, "control", "passphrase"));
            case "arm_fixture" -> {
                String ceremony = string(step, "ceremony", "alternative");
                if (!ceremony.equals("pad") && !ceremony.equals("alternative")) throw new IllegalArgumentException("Unsupported ceremony");
                // A fresh real review starts its delay at the actual acknowledgement, independent of file-poll latency.
                clickControl("review", true);
                awaited = () -> {
                    if (!"READY".equals(uiState().get("state"))) return false;
                    armFixture(runtime, ceremony);
                    return true;
                };
                awaitDeadline = System.nanoTime() + 20_000_000_000L;
            }
            case "assert_review_diff" -> assertReviewDiff();
            case "type_fixture_source" -> {
                if (!(Minecraft.getInstance().screen instanceof ISFMTextEditScreen editor)) throw new IllegalStateException("Open the real program editor first");
                String initial = editor.openContext().initialValue();
                if (!initial.equals(source(false)) && !initial.equals(source(true))) throw new IllegalStateException("Only a fixed fixture document may be edited");
                // Plain arrows/backspace avoid older vanilla fields polling the OS's Control key.
                for (int index = 0; index < initial.length(); index++) runtime.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
                for (int index = 0; index < initial.length(); index++) runtime.pressScreenKey(GLFW.GLFW_KEY_BACKSPACE, 0);
                editorExpectedSource = source(step.has("edited") && step.get("edited").getAsBoolean());
                type(runtime, editorExpectedSource);
            }
            case "editor_save" -> {
                var screen = Minecraft.getInstance().screen;
                if (!(screen instanceof ISFMTextEditScreen) || editorExpectedSource == null || editorBaseRevision < 0) {
                    throw new IllegalStateException("Edit the fixed fixture through the real signing editor first");
                }
                var done = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .filter(widget -> widget.getMessage().getString().equals(CommonComponents.GUI_DONE.getString())).findFirst();
                editorSaveSubmitted = true;
                if (done.isPresent()) {
                    var button = done.orElseThrow();
                    if (!button.active || !button.visible) throw new IllegalStateException("Editor Done is disabled");
                    click(screen, button.x + button.getWidth() / 2.0, button.y + button.getHeight() / 2.0, true);
                } else runtime.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_SHIFT);
            }
            case "screen_key" -> {
                int code = switch (string(step, "key", "ESCAPE")) {
                    case "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
                    case "ENTER" -> GLFW.GLFW_KEY_ENTER;
                    case "PAGE_DOWN" -> GLFW.GLFW_KEY_PAGE_DOWN;
                    case "PAGE_UP" -> GLFW.GLFW_KEY_PAGE_UP;
                    case "TAB" -> GLFW.GLFW_KEY_TAB;
                    case "SPACE" -> GLFW.GLFW_KEY_SPACE;
                    default -> throw new IllegalArgumentException("Unsupported review key");
                };
                runtime.pressScreenKey(code, 0);
            }
            case "assert_ui", "await_ui" -> {
                JsonObject expected = step.getAsJsonObject("expected");
                if (expected == null || expected.size() == 0) throw new IllegalArgumentException("Expected UI fields are required");
                if (string(step, "op", "").equals("assert_ui")) {
                    if (!matchesUi(expected)) throw new IllegalStateException("Live UI did not match the expected fields");
                } else {
                    awaited = () -> matchesUi(expected);
                    awaitDeadline = System.nanoTime() + 20_000_000_000L;
                }
            }
            case "source_edit" -> replaceSource(step.has("edited") && step.get("edited").getAsBoolean());
            case "assert_history" -> assertHistory(step);
            case "await_history" -> {
                int total = step.get("total").getAsInt();
                int active = step.has("active") ? step.get("active").getAsInt() : total;
                if (total < 0 || total > 32 || active < 0 || active > total) throw new IllegalArgumentException("Invalid expected history size");
                awaited = () -> {
                    var identity = ensureIdentity();
                    var descriptor = ProgramSignatureDescriptor.fromSource(manager().storedSource(), identity.runtimeRevision(), identity.requestedCapabilities());
                    if (manager().attestations().size() != total || manager().attestations().stream()
                            .filter(attestation -> attestation.verifies(descriptor)).count() != active) return false;
                    assertHistory(step);
                    return true;
                };
                awaitDeadline = System.nanoTime() + 20_000_000_000L;
            }
            case "assert_consent" -> assertConsent(step);
            case "close_review" -> {
                if (!runtime.openCommandPalette()) throw new IllegalStateException("Command palette unavailable");
                runtime.executeCommandPalette("sfm action invoke sfm:screen/close");
            }
            case "finish" -> finish();
            default -> throw new IllegalArgumentException("Unsupported signing operation");
        }
    }

    private ClientManagerBlockEntity manager() {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(absoluteManager) instanceof ClientManagerBlockEntity manager)) {
            throw new IllegalStateException("Fixture manager is not loaded");
        }
        return manager;
    }

    private ClientProgramIdentity ensureIdentity() {
        var manager = manager();
        if (!manager.storedSource().equals(source(false)) && !manager.storedSource().equals(source(true))) {
            throw new IllegalStateException("Only the two fixed fixture sources are in scope");
        }
        var identity = ClientManagerFrameRuntime.identityFor(manager).orElseThrow();
        identities.add(identity);
        if (!identity.equals(overrideIdentity)) {
            closeOverride();
            keyOverride = ClientProgramSigningRuntime.installFixtureKeyDirectory(identity, keysDirectory);
            overrideIdentity = identity;
        }
        return identity;
    }

    private void clickPanel(JsonObject step) {
        var identity = ensureIdentity();
        var screen = Minecraft.getInstance().screen;
        if (!(screen instanceof SFMScreenMultiplexer workspace)
                || !(workspace.focusedPanelInstance() instanceof ClientProgramConsentsPanel panel)) {
            throw new IllegalStateException("Focus the real consent panel first");
        }
        var control = ClientProgramConsentsPanel.Control.valueOf(string(step, "control", "VIEW").toUpperCase(Locale.ROOT));
        if (control == ClientProgramConsentsPanel.Control.STOP_ALL || control == ClientProgramConsentsPanel.Control.RESUME
                || control == ClientProgramConsentsPanel.Control.SAVE) {
            throw new IllegalArgumentException("Global consent mutations are outside this isolated signing fixture");
        }
        if (control != ClientProgramConsentsPanel.Control.PREVIOUS && control != ClientProgramConsentsPanel.Control.NEXT
                && !panel.selectedIdentity().filter(identity::equals).isPresent()) {
            throw new IllegalStateException("Select the exact current fixture identity before mutating it");
        }
        var local = panel.controlBoundsForAutomation(control).orElseThrow();
        var bounds = workspace.measure(workspace.focusedPanelId(), local).orElseThrow().globalGuiLogicalBounds();
        click(screen, bounds.x() + bounds.width() / 2.0, bounds.y() + bounds.height() / 2.0, true);
    }

    private void clickConfirmation(JsonObject step) {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof SFMConfirmationScreen)) throw new IllegalStateException("No real confirmation is open");
        String label = string(step, "label", "Cancel");
        var button = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
        if (!button.active || !button.visible) throw new IllegalStateException("Confirmation is not enabled");
        click(screen, button.x + button.getWidth() / 2.0, button.y + button.getHeight() / 2.0, true);
    }

    private void clickControl(String control, boolean expectedHandled) {
        Screen screen = Minecraft.getInstance().screen;
        Map<String, Object> before = uiState();
        double x, y;
        if (screen instanceof ClientProgramSigningScreen signing) {
            var bounds = Objects.requireNonNull(signing.automationControls().get(control), "Unknown signing control");
            x = bounds.x() + bounds.width() / 2.0;
            y = bounds.y() + bounds.height() / 2.0;
            if ("edit".equals(control)) {
                editorBaseRevision = manager().signingRevision();
                editorExpectedSource = null;
                editorSaveSubmitted = false;
            }
            if ("sign".equals(control) && Boolean.TRUE.equals(before.get("sign_active"))) {
                submittedFingerprints.add((String) before.get("fingerprint"));
            }
        } else if (screen instanceof ClientSigningKeysScreen keys) {
            var bounds = Objects.requireNonNull(keys.automationControls().get(control), "Unknown key control");
            x = bounds.x() + bounds.width() / 2.0;
            y = bounds.y() + bounds.height() / 2.0;
            if ("use".equals(control)) {
                String fingerprint = (String) before.get("fingerprint");
                if (fingerprint == null || fingerprint.isEmpty()) throw new IllegalStateException("No fixture key is selected");
                String old = fingerprints.putIfAbsent(keyRole, fingerprint);
                if (old != null && !old.equals(fingerprint)) throw new IllegalStateException("Fixture role already names a different key");
            }
        } else throw new IllegalStateException("No signing or key screen is open");
        click(screen, x, y, expectedHandled);
        if (expectedHandled && screen instanceof ClientProgramSigningScreen) {
            if (control.equals("cancel")) witnesses.add("cancel");
            if (control.equals("pad") && Boolean.TRUE.equals(uiState().get("cosmetic_acknowledged"))) witnesses.add("pen");
            if (control.equals("alternative") && Boolean.TRUE.equals(uiState().get("cosmetic_acknowledged"))) witnesses.add("alternative");
        }
    }

    private static void click(Screen screen, double x, double y, boolean expectedHandled) {
        boolean handled = screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
        if (handled != expectedHandled) throw new IllegalStateException("Control click handling did not match expectation");
    }

    private void typeFixture(ISFMGamePuppetRuntime runtime, String control) {
        Screen screen = Minecraft.getInstance().screen;
        if (control.equals("passphrase") || control.equals("confirmation")) {
            if (!(screen instanceof ClientProgramSigningScreen) && !(screen instanceof ClientSigningKeysScreen)) {
                throw new IllegalStateException("Fixture phrase may only enter a masked signing widget");
            }
            clickControl(control, true);
            runtime.pressScreenKey(GLFW.GLFW_KEY_DELETE, 0);
            char[] fixture = "Disposable puppet key 2026!".toCharArray();
            try { for (char character : fixture) runtime.typeScreenCharacter(character, 0); }
            finally { Arrays.fill(fixture, '\0'); }
        } else if (control.equals("name") || control.equals("external_path")) {
            if (!(screen instanceof ClientSigningKeysScreen)) throw new IllegalStateException("Open fixture key management first");
            clickControl(control, true);
            runtime.pressScreenKey(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL);
            type(runtime, control.equals("name") ? keyRole : directory.resolve("backup-" + keyRole + ".sfmkey").toString());
        } else throw new IllegalArgumentException("Only fixed fixture fields can be typed");
    }

    private static void type(ISFMGamePuppetRuntime runtime, String value) {
        for (char character : value.toCharArray()) {
            if (character == '\n') runtime.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
            else runtime.typeScreenCharacter(character, 0);
        }
    }

    private void armFixture(ISFMGamePuppetRuntime runtime, String ceremony) {
        if (!(Minecraft.getInstance().screen instanceof ClientProgramSigningScreen signing)) {
            throw new IllegalStateException("Open the actual signing screen first");
        }
        typeFixture(runtime, "passphrase");
        if (ceremony.equals("pad")) clickControl("pad", true);
        else {
            for (int tabs = 0; tabs < 16 && !focusedControl(signing).equals("alternative"); tabs++) {
                runtime.pressScreenKey(GLFW.GLFW_KEY_TAB, 0);
            }
            if (!focusedControl(signing).equals("alternative")) throw new IllegalStateException("Keyboard navigation did not reach the accessible alternative");
            runtime.pressScreenKey(GLFW.GLFW_KEY_SPACE, 0);
            if (!Boolean.TRUE.equals(uiState().get("cosmetic_acknowledged"))) throw new IllegalStateException("Space did not activate the accessible alternative");
            witnesses.add("alternative");
            witnesses.add("keyboard_alternative");
        }
        boolean usablePhrase = signing.children().stream().filter(SFMSigningPassphraseWidget.class::isInstance)
                .map(SFMSigningPassphraseWidget.class::cast).anyMatch(SFMSigningPassphraseWidget::usable);
        var armed = uiState();
        if (!usablePhrase || !Boolean.TRUE.equals(armed.get("cosmetic_acknowledged"))
                || (int) armed.get("delay_ticks") <= 0 || !Boolean.FALSE.equals(armed.get("sign_active"))) {
            throw new IllegalStateException("The review delay was not enforced with both phrase and ceremony ready");
        }
        clickControl("sign", false);
        witnesses.add("delayed_armed");
    }

    private static String focusedControl(ClientProgramSigningScreen screen) {
        if (!(screen.getFocused() instanceof AbstractWidget focused)) return "";
        return screen.automationControls().entrySet().stream().filter(entry -> {
            var bounds = entry.getValue();
            return bounds.x() == focused.x && bounds.y() == focused.y
                    && bounds.width() == focused.getWidth() && bounds.height() == focused.getHeight();
        }).map(Map.Entry::getKey).findFirst().orElse("");
    }

    private void assertReviewDiff() {
        if (!(Minecraft.getInstance().screen instanceof ClientProgramSigningScreen signing)
                || !"DIFF".equals(uiState().get("view"))) throw new IllegalStateException("Display the actual source diff first");
        var identity = ensureIdentity();
        var store = ClientProgramConsentRuntime.service().store();
        var current = store.snapshots().stream().filter(snapshot -> snapshot.identity().equals(identity)).findFirst().orElseThrow();
        var previous = ClientProgramConsentReview.previous(store, current).flatMap(ClientProgramConsentStore.Snapshot::evidence).orElseThrow();
        if (!previous.source().equals(source(true)) || !manager().storedSource().equals(source(false))) {
            throw new IllegalStateException("The remembered and current sources are not the expected distinct fixture revisions");
        }
        var visible = signing.automationReviewText();
        var expected = ClientProgramConsentReview.diff(source(true), source(false));
        if (!visible.containsAll(expected) || expected.stream().noneMatch(line -> line.startsWith("- 2 | -- Second author fixture revision"))) {
            throw new IllegalStateException("The actual review text does not display the exact prior/current source difference");
        }
        response.add("displayed_diff", json(visible));
        witnesses.add("diff");
    }

    private Map<String, Object> uiState() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof ClientProgramSigningScreen signing) {
            var state = signing.automationState();
            if (Boolean.TRUE.equals(state.get("sign_active")) && !"READY".equals(state.get("state"))) {
                throw new IllegalStateException("Sign remained active outside the current READY gate");
            }
            if ("READY".equals(state.get("state")) && (int) state.get("delay_ticks") > 0
                    && Boolean.FALSE.equals(state.get("sign_active"))) witnesses.add("delayed");
            if (editorSaveSubmitted && editorExpectedSource != null && "READY".equals(state.get("state"))
                    && "SAVED".equals(state.get("server_status")) && manager().signingRevision() == editorBaseRevision + 1
                    && Objects.equals(state.get("ack_revision"), editorBaseRevision + 1)
                    && manager().storedSource().equals(editorExpectedSource)
                    && Objects.equals(state.get("ack_source_sha256"), manager().signingSnapshot().body().sourceSha256())) {
                witnesses.add("editor_saved_ack");
            }
            if (staleBaseRevision >= 0 && manager().signingRevision() != staleBaseRevision
                    && ("CANCELLED".equals(state.get("state")) || "REJECTED".equals(state.get("state")))
                    && Boolean.FALSE.equals(state.get("sign_active"))) witnesses.add("stale");
            return state;
        }
        if (screen instanceof ClientSigningKeysScreen keys) return keys.automationState();
        return Map.of("screen", screen == null ? "none" : screen.getClass().getSimpleName());
    }

    private boolean matchesUi(JsonObject expected) {
        var actual = uiState();
        for (var field : expected.entrySet()) {
            if (!field.getValue().equals(new Gson().toJsonTree(actual.get(field.getKey())))) return false;
        }
        return true;
    }

    /** Test setup only: another actor changes authoritative source; the block retains its signature history. */
    private void replaceSource(boolean edited) {
        var identity = ensureIdentity();
        staleBaseRevision = manager().signingRevision();
        var server = Objects.requireNonNull(Minecraft.getInstance().getSingleplayerServer());
        var dimension = Minecraft.getInstance().level.dimension();
        serverWork = CompletableFuture.supplyAsync(() -> {
            var level = server.getLevel(dimension);
            if (level == null || !(level.getBlockEntity(absoluteManager) instanceof ClientManagerBlockEntity manager)) {
                throw new IllegalStateException("Fixture manager unloaded");
            }
            ItemStack disk = new ItemStack(SFMItems.DISK.get());
            DiskItem.setProgram(disk, source(edited));
            LabelPositionHolder.empty().add("displays", absoluteDisplay).save(disk);
            manager.setDisk(disk);
            JsonObject result = new JsonObject();
            result.addProperty("fixture_setup_only", true);
            result.addProperty("revision", manager.signingRevision());
            result.addProperty("previous_source_hash", identity.sourceSha256());
            return result;
        }, server);
    }

    private void assertHistory(JsonObject step) {
        var identity = ensureIdentity();
        var server = Objects.requireNonNull(Minecraft.getInstance().getSingleplayerServer());
        var dimension = Minecraft.getInstance().level.dimension();
        int total = step.get("total").getAsInt();
        int active = step.has("active") ? step.get("active").getAsInt() : total;
        if (total < 0 || total > 32 || active < 0 || active > total) throw new IllegalArgumentException("Invalid expected history size");
        // Independently verify the received projection as well as authoritative server storage.
        var current = ProgramSignatureDescriptor.fromSource(manager().storedSource(), identity.runtimeRevision(), identity.requestedCapabilities());
        var clientHistory = manager().attestations();
        if (clientHistory.size() != total || clientHistory.stream().filter(attestation -> attestation.verifies(current)).count() != active) {
            throw new IllegalStateException("Client projected signature history did not match");
        }
        serverWork = CompletableFuture.supplyAsync(() -> {
            var level = server.getLevel(dimension);
            if (level == null || !(level.getBlockEntity(absoluteManager) instanceof ClientManagerBlockEntity manager)) throw new IllegalStateException("Fixture manager unloaded");
            var expected = ProgramSignatureDescriptor.fromSource(manager.storedSource(), identity.runtimeRevision(), identity.requestedCapabilities());
            var history = manager.attestations();
            long count = history.stream().filter(attestation -> attestation.verifies(expected)).count();
            if (history.size() != total || count != active) throw new IllegalStateException("Authoritative signature history did not match");
            JsonObject result = new JsonObject();
            result.addProperty("total", total);
            result.addProperty("active", active);
            result.add("fingerprints", json(history.stream().map(attestation -> attestation.fingerprint()).distinct().sorted().toList()));
            result.addProperty("revision", manager.signingRevision());
            return result;
        }, server);
    }

    private void completeHistoryProof(JsonObject server) {
        if (server.get("total").getAsInt() == 0 && submittedFingerprints.isEmpty()
                && Minecraft.getInstance().screen instanceof ClientProgramSigningScreen
                && "READY".equals(uiState().get("state"))) witnesses.add("no_auto_sign");
        if (server.get("total").getAsInt() == 0 && submittedFingerprints.isEmpty()
                && witnesses.contains("editor_saved_ack")) witnesses.add("save_did_not_sign");
        if (server.get("active").getAsInt() > 0) {
            for (var fingerprint : server.getAsJsonArray("fingerprints")) {
                if (submittedFingerprints.contains(fingerprint.getAsString())) witnesses.add("network_signature");
            }
        }
    }

    private void assertConsent(JsonObject step) {
        var identity = ensureIdentity();
        var capability = new ResourceLocation(string(step, "capability", "sfm:client_program/execute"));
        var evaluated = ClientManagerFrameRuntime.consent().evaluate(identity, capability, ClientManagerFrameRuntime::policyBlockers);
        if (step.has("state") && !evaluated.consent().name().equals(step.get("state").getAsString())
                || step.has("effective") && !evaluated.effective().name().equals(step.get("effective").getAsString())
                || step.has("authority") && !evaluated.authority().name().equals(step.get("authority").getAsString())) {
            throw new IllegalStateException("Actual client permission did not match");
        }
        if (evaluated.authority() == ClientProgramConsentGate.Authority.TRUSTED_SIGNER) {
            witnesses.add("trusted_signer");
            if (manager().attestations().size() > 1) witnesses.add("extraneous_signer_does_not_veto");
        }
        if (evaluated.consent() == ClientProgramConsentGate.ConsentState.DENIED
                && evaluated.authority() == ClientProgramConsentGate.Authority.NONE
                && ClientProgramSignerTrustRuntime.service().permits(identity, capability)) witnesses.add("exact_denial_precedence");
    }

    private void finish() {
        if (Minecraft.getInstance().screen != null) throw new IllegalStateException("Close all review screens before finishing");
        if (failedRequests != 0) throw new IllegalStateException("A failed request invalidates this acceptance run");
        if (!witnesses.containsAll(REQUIRED)) {
            var missing = new TreeSet<>(REQUIRED);
            missing.removeAll(witnesses);
            throw new IllegalStateException("Required signing evidence is missing: " + missing);
        }
        cleanup();
        proofComplete.set(true);
        finish = true;
    }

    private void cleanup() {
        closeOverride();
        for (var identity : identities) {
            // Removal is exact-scope cleanup; never grant authority through a test helper.
            try { ClientProgramSignerTrustRuntime.service().revokeAtLocation(identity); }
            catch (IOException failure) { throw new IllegalStateException("Fixture signer cleanup did not persist", failure); }
            if (!ClientProgramConsentRuntime.service().forget(identity).successful()) {
                throw new IllegalStateException("Fixture consent cleanup did not persist");
            }
        }
        identities.clear();
        // Encrypted disposable keys remain with the run's evidence for reproducibility, never in user config.
    }

    private void closeOverride() {
        if (keyOverride == null) return;
        try { keyOverride.close(); }
        catch (Exception failure) { throw new IllegalStateException("Fixture key directory override could not be removed", failure); }
        keyOverride = null;
        overrideIdentity = null;
    }

    @Override public void abort() { cleanup(); }

    private JsonObject observation() {
        JsonObject result = new JsonObject();
        result.addProperty("schema", "sfm:client_program_signing_puppet@1");
        result.addProperty("process_id", ProcessHandle.current().pid());
        result.addProperty("os_pointer_injection", false);
        result.addProperty("failed_requests", failedRequests);
        result.addProperty("screen", Minecraft.getInstance().screen == null ? "none" : Minecraft.getInstance().screen.getClass().getName());
        result.add("ui", json(uiState()));
        result.add("witnesses", json(witnesses));
        result.add("fixture_fingerprints", json(fingerprints));
        if (absoluteManager != null && Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(absoluteManager) instanceof ClientManagerBlockEntity manager) {
            result.addProperty("manager_revision", manager.signingRevision());
            result.addProperty("source_edited", manager.storedSource().equals(source(true)));
            result.addProperty("projected_attestations", manager.attestations().size());
            result.add("current_signers", json(ClientManagerFrameRuntime.identityFor(manager)
                    .map(ClientProgramSignerTrustRuntime.service()::currentSigners).orElse(List.of())));
        }
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof SFMConfirmationScreen) {
            JsonArray choices = new JsonArray();
            screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).forEach(widget -> {
                JsonObject choice = new JsonObject();
                choice.addProperty("label", widget.getMessage().getString());
                choice.addProperty("active", widget.active);
                choice.addProperty("focused", screen.getFocused() == widget);
                choices.add(choice);
            });
            result.add("confirmation_choices", choices);
        }
        if (screen instanceof SFMScreenMultiplexer workspace && workspace.focusedPanelInstance() instanceof ClientProgramConsentsPanel panel) {
            result.addProperty("panel_message", panel.statusMessage());
            result.addProperty("panel_current_fixture", panel.selectedIdentity().map(identity -> identities.contains(identity)
                    && identity.sourceSha256().equals(ProgramSignatureDescriptor.fromSource(manager().storedSource(),
                    identity.runtimeRevision(), identity.requestedCapabilities()).sourceSha256())).orElse(false));
        }
        return result;
    }

    private void failed(RuntimeException failure) {
        failedRequests++;
        response.addProperty("status", "error");
        // Requests never contain secrets, and diagnostics never include widget contents or key exceptions.
        response.addProperty("error", failure.getClass().getSimpleName() + ": "
                + (failure instanceof IllegalStateException || failure instanceof IllegalArgumentException ? failure.getMessage() : "operation rejected"));
    }
    private String prefix() { return String.format(Locale.ROOT, "%06d", sequence); }
    private static String string(JsonObject value, String key, String fallback) {
        return value.has(key) ? value.get(key).getAsString() : fallback;
    }
    private static JsonElement json(Object value) { return new Gson().toJsonTree(value); }
    private static JsonObject readRequest(Path path) {
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null;
            if (Files.size(path) > MAX_REQUEST_BYTES) throw new IOException("Oversized request");
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception invalid) {
            JsonObject result = new JsonObject();
            result.addProperty("op", "invalid");
            return result;
        }
    }
    private static CompletableFuture<Void> writeAsync(Path path, JsonObject value) {
        String contents = value.toString();
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(path.getParent());
                Path staged = path.resolveSibling(path.getFileName() + ".tmp");
                Files.writeString(staged, contents, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException failure) { throw new IllegalStateException("Could not write signing puppet evidence", failure); }
        });
    }
}
