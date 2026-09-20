package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.tooltip.SFMTooltipModeService;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetPointer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetRenderHarness;
import ca.teamdman.sfm.mixins.KeyboardHandlerInvoker;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Actual vanilla slot rendering, semantic tooltip actions and Alt+D ingress, driven only by bounded files.
 * Native key polling is not simulated: tooltip modes are actuated through the real command palette.
 * The disposable local container is not server inventory or network evidence.
 */
public final class ExplorePacketInspectionPuppetAction implements SFMPuppetAction {
    private static final List<String> OPERATIONS = List.of("hover_packet", "expand_packet", "compact_packet",
            "inspect_packet", "prove_snapshot", "hover_ordinary", "inspect_ordinary", "hover_empty",
            "inspect_empty", "reset_tooltip", "finish");
    private static final SFMValue PACKET_VALUE = SFMValue.object(Map.of(
            "JobId", SFMValue.of("packet-inspection"), "type", SFMValue.of("Response")));
    private final Path directory = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize()
            .resolve("sfm-puppet/packet-inspection-control/session-" + UUID.randomUUID());
    private final SimpleContainer contents = new SimpleContainer(27);
    private final JsonArray witnesses = new JsonArray();
    private ContainerScreen fixture;
    private SFMScreenMultiplexer ownedWorkspace;
    private SFMCommandPaletteScreen ownedPalette;
    private SFMTooltipModeService.Mode previousTooltipMode;
    private ItemStack packetSnapshot, ordinarySnapshot;
    private String capturedPacketText, previousEditor;
    private SFMGamePuppetPointer.Position originalPointer;
    private CompletableFuture<JsonObject> read;
    private CompletableFuture<Void> write;
    private RuntimeException terminalFailure;
    private JsonObject request, response;
    private int sequence = 1, completed, failedRequests;
    private long nextPoll, frame, started;
    private double targetX, targetY;
    private boolean dispatched, finished;

    @Override public String description() { return "file-driven packet inspection: request " + sequence + " in " + directory; }

    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (fixture == null) {
            initialize();
            JsonObject ready = observation();
            ready.addProperty("schema", "sfm:packet_inspection_puppet@1");
            ready.addProperty("control_directory", directory.toString());
            ready.addProperty("request_pattern", "000001.request.json (then monotonically increasing)");
            JsonArray operations = new JsonArray();
            OPERATIONS.forEach(operations::add);
            ready.add("required_operations_in_order", operations);
            ready.addProperty("screenshots_directory", Minecraft.getInstance().gameDirectory.toPath()
                    .toAbsolutePath().normalize().resolve("screenshots").toString());
            write = writeAsync(directory.resolve("ready.json"), ready);
            return false;
        }
        if (write != null) {
            if (!write.isDone()) return false;
            write.join();
            write = null;
            if (terminalFailure != null) throw terminalFailure;
            if (finished) return true;
        }
        if (request != null) {
            try {
                require(System.nanoTime() - started < 20_000_000_000L, "Packet inspection request timed out");
                String operation = request.get("op").getAsString();
                if (!dispatched) {
                    require(completed < OPERATIONS.size() && OPERATIONS.get(completed).equals(operation),
                            "Expected operation " + (completed < OPERATIONS.size() ? OPERATIONS.get(completed) : "none"));
                    dispatch(runtime, operation);
                    dispatched = true;
                    frame = SFMGamePuppetRenderHarness.completedFrames();
                    return false;
                }
                // Vanilla render, not direct slot assignment, computes the actual hovered slot.
                if (SFMGamePuppetRenderHarness.completedFrames() <= frame + 2
                        || System.nanoTime() - started < 150_000_000L) return false;
                verify(operation);
                String capture = "packet-inspection-step-" + String.format(Locale.ROOT, "%04d", sequence);
                if (!runtime.captureWithHud(capture, Component.literal("Packet inspection: " + operation
                        + " (semantic tooltip actions)"))) return false;
                witnesses.add(operation);
                completed++;
                finished = operation.equals("finish");
                response.addProperty("status", "passed");
                response.addProperty("capture_name", capture);
                response.addProperty("completed_frame", SFMGamePuppetRenderHarness.completedFrames());
                response.add("observation", observation());
                writeResponse();
            } catch (RuntimeException failure) {
                failedRequests++;
                response.addProperty("status", "error");
                response.addProperty("error", failure.toString());
                response.add("failure_observation", observation());
                // A rejected request stops the file driver. Restore now, not at the puppet timeout.
                try { cleanup(); }
                catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                response.add("observation", observation());
                terminalFailure = failure;
                writeResponse();
            }
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
        response = new JsonObject();
        response.addProperty("sequence", sequence);
        started = System.nanoTime();
        dispatched = false;
        return false;
    }

    private void initialize() {
        Minecraft minecraft = Minecraft.getInstance();
        require(minecraft.player != null && minecraft.level != null && minecraft.screen == null,
                "Packet fixture requires an unobstructed puppet-owned world");
        require(minecraft.player.containerMenu == minecraft.player.inventoryMenu
                && minecraft.player.containerMenu.getCarried().isEmpty(), "An existing player menu cannot be borrowed by this fixture");
        originalPointer = SFMGamePuppetPointer.current();
        previousEditor = SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.get();
        previousTooltipMode = SFMTooltipModeService.INSTANCE.mode();
        SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.set("sfm:text_editor_v3");
        packetSnapshot = PacketItem.create(PACKET_VALUE);
        packetSnapshot.setCount(3);
        ordinarySnapshot = new ItemStack(Items.PAPER, 7);
        ordinarySnapshot.getOrCreateTag().putString("fixture", "ordinary-item-snapshot");
        contents.setItem(0, packetSnapshot.copy());
        contents.setItem(1, ordinarySnapshot.copy());
        fixture = new ContainerScreen(ChestMenu.threeRows(0, minecraft.player.getInventory(), contents),
                minecraft.player.getInventory(), Component.literal("Packet inspection fixture"));
        minecraft.setScreen(fixture);
    }

    private void dispatch(ISFMGamePuppetRuntime runtime, String operation) {
        switch (operation) {
            case "hover_packet", "hover_ordinary", "hover_empty" -> {
                if (Minecraft.getInstance().screen == ownedWorkspace && ownedWorkspace != null) runtime.closeScreenNaturally();
                require(Minecraft.getInstance().screen == fixture, "The owned vanilla container is not active");
                var slot = fixture.getMenu().getSlot(operation.equals("hover_packet") ? 0 : 1);
                targetX = fixture.getGuiLeft() + (operation.equals("hover_empty") ? 4 : slot.x + 8);
                targetY = fixture.getGuiTop() + (operation.equals("hover_empty") ? 4 : slot.y + 8);
                SFMGamePuppetPointer.moveVirtual(fixture, targetX, targetY);
                if (operation.equals("hover_packet")) {
                    tooltipAction(runtime, "sfm action invoke sfm:tooltip/more_info/compact", SFMTooltipModeService.Mode.COMPACT);
                }
            }
            case "expand_packet" -> {
                requireHover(0);
                tooltipAction(runtime, "sfm action invoke sfm:tooltip/more_info/expand", SFMTooltipModeService.Mode.EXPANDED);
            }
            case "compact_packet" -> {
                requireHover(0);
                tooltipAction(runtime, "sfm action invoke sfm:tooltip/more_info/compact", SFMTooltipModeService.Mode.COMPACT);
            }
            case "inspect_packet", "inspect_ordinary", "inspect_empty" -> {
                requireHover(operation.equals("inspect_packet") ? 0 : operation.equals("inspect_ordinary") ? 1 : -1);
                // Full Minecraft ingress includes Forge dynamic bindings and the original container host.
                try { rawKey(GLFW.GLFW_KEY_D, GLFW.GLFW_PRESS, GLFW.GLFW_MOD_ALT); }
                finally { rawKey(GLFW.GLFW_KEY_D, GLFW.GLFW_RELEASE, GLFW.GLFW_MOD_ALT); }
                require(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer, "Alt+D did not open the real workspace");
                ownedWorkspace = (SFMScreenMultiplexer) Minecraft.getInstance().screen;
                var editor = editor();
                if (operation.equals("inspect_packet")) {
                    requireDocument(editor, packetSnapshot, true);
                    capturedPacketText = editor.currentText();
                } else if (operation.equals("inspect_ordinary")) requireDocument(editor, ordinarySnapshot, false);
                else {
                    require(!editor.isReadOnly() && editor.currentText().isEmpty(), "Empty hover must open a blank writable editor");
                    runtime.typeScreenCharacter('x', 0);
                    require(editor.currentText().equals("x"), "Blank fallback did not accept real character input");
                    backspace();
                    require(editor.currentText().isEmpty(), "Could not restore the blank fixture document");
                }
            }
            case "prove_snapshot" -> {
                require(capturedPacketText != null, "Inspect the packet before mutating its fixture");
                PacketItem.setValue(contents.getItem(0), SFMValue.of("mutated-after-capture"));
                contents.getItem(0).setCount(1);
                runtime.typeScreenCharacter('x', 0);
                require(editor().currentText().equals(capturedPacketText), "Read-only inspection accepted character input");
                backspace();
            }
            case "reset_tooltip" -> {
                if (ownedWorkspace != null && Minecraft.getInstance().screen == ownedWorkspace) runtime.closeScreenNaturally();
                tooltipAction(runtime, "sfm action invoke sfm:tooltip/more_info/reset", SFMTooltipModeService.Mode.AUTO);
            }
            case "finish" -> {
                require(failedRequests == 0, "A failed request prevents acceptance; rerun the puppet");
                cleanup();
            }
            default -> throw new IllegalArgumentException("Unsupported packet inspection operation");
        }
    }

    private void verify(String operation) {
        switch (operation) {
            case "hover_packet", "compact_packet" -> {
                requireHover(0);
                require(SFMTooltipModeService.INSTANCE.mode() == SFMTooltipModeService.Mode.COMPACT
                        && !SFMItemUtils.isClientAndMoreInfoRequested(), "Compact action did not select compact presentation");
                String tooltip = tooltipText();
                require(tooltip.contains("Contains packet data") && !tooltip.contains("JobId"), "Compact tooltip leaked packet contents");
            }
            case "expand_packet" -> {
                requireHover(0);
                require(SFMTooltipModeService.INSTANCE.mode() == SFMTooltipModeService.Mode.EXPANDED
                        && SFMItemUtils.isClientAndMoreInfoRequested(), "Expand action did not select expanded presentation");
                require(tooltipText().contains(SFMValueJsonCodec.encodePretty(PACKET_VALUE)),
                        "Expanded production tooltip omitted the complete pretty packet value");
            }
            case "hover_ordinary" -> requireHover(1);
            case "hover_empty" -> requireHover(-1);
            case "inspect_packet", "prove_snapshot" -> {
                requireDocument(editor(), packetSnapshot, true);
                require(editor().currentText().equals(capturedPacketText), "Captured packet document changed after input or fixture mutation");
            }
            case "inspect_ordinary" -> requireDocument(editor(), ordinarySnapshot, false);
            case "inspect_empty" -> require(!editor().isReadOnly() && editor().currentText().isEmpty(), "Blank fallback changed before capture");
            case "reset_tooltip" -> {
                require(Minecraft.getInstance().screen == fixture, "Reset action changed the host screen");
                require(SFMTooltipModeService.INSTANCE.mode() == SFMTooltipModeService.Mode.AUTO,
                        "Reset did not restore configured-key mode");
            }
            case "finish" -> {
                require(Minecraft.getInstance().screen == null, "Owned fixture screens did not close");
                require(SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.get().equals(previousEditor), "Editor preference was not restored");
                require(SFMTooltipModeService.INSTANCE.mode() == previousTooltipMode, "Tooltip mode was not restored");
            }
            default -> throw new IllegalArgumentException("Unsupported packet inspection operation");
        }
    }

    private void tooltipAction(ISFMGamePuppetRuntime runtime, String command, SFMTooltipModeService.Mode expected) {
        require(Minecraft.getInstance().screen == fixture, "Tooltip action requires the owned container");
        require(runtime.openCommandPalette(), "Registered command palette did not open");
        ownedPalette = (SFMCommandPaletteScreen) Minecraft.getInstance().screen;
        runtime.executeCommandPalette(command);
        require(SFMTooltipModeService.INSTANCE.mode() == expected, "Registered tooltip action did not execute: " + command);
        runtime.closeScreenNaturally();
        require(Minecraft.getInstance().screen == fixture, "Palette close did not return to the hovered container");
        ownedPalette = null;
        SFMGamePuppetPointer.moveVirtual(fixture, targetX, targetY);
    }

    private String tooltipText() {
        return String.join("\n", contents.getItem(0).getTooltipLines(
                Minecraft.getInstance().player, TooltipFlag.Default.NORMAL).stream().map(Component::getString).toList());
    }

    private void requireHover(int slotIndex) {
        require(Minecraft.getInstance().screen == fixture, "Hover evidence requires the active vanilla container");
        require(SFMGamePuppetPointer.current().callbackIsWithin(targetX, targetY, 1D), "Virtual callback pointer moved before observation");
        require(fixture.getSlotUnderMouse() == (slotIndex < 0 ? null : fixture.getMenu().getSlot(slotIndex)),
                "Vanilla rendered hover does not match the requested slot");
    }

    private SFMTextEditorPanel editor() {
        require(ownedWorkspace != null && Minecraft.getInstance().screen == ownedWorkspace, "The owned Alt+D workspace is not active");
        var editors = ownedWorkspace.panels().stream().filter(SFMTextEditorPanel.class::isInstance).map(SFMTextEditorPanel.class::cast).toList();
        require(editors.size() == 1, "Expected exactly one real inspection editor");
        require(editors.get(0).editorId().equals("sfm:text_editor_v3"), "Live document assertions require the v3 editor");
        return editors.get(0);
    }

    private static void requireDocument(SFMTextEditorPanel editor, ItemStack expected, boolean packet) {
        String text = editor.currentText();
        require(editor.isReadOnly(), "Hovered item inspection must be read-only");
        require(text.startsWith("schema: sfm.item-inspection/1\n") && text.contains("\ncount: " + expected.getCount() + "\n")
                && text.contains("\nitem-id: " + (packet ? "sfm:packet" : "minecraft:paper") + "\n")
                && text.contains("\ntooltip:\n"), "Inspection metadata is incomplete");
        for (var line : expected.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL)) {
            require(text.contains("  - " + line.getString().replace("\r", "\\r").replace("\n", "\\n") + "\n"),
                    "Inspection omitted a visible tooltip line");
        }
        String stackMarker = "\nitem-stack-data-snbt:\n";
        int stackStart = text.indexOf(stackMarker);
        require(stackStart >= 0, "Inspection omitted complete stack data");
        try {
            require(TagParser.parseTag(text.substring(stackStart + stackMarker.length()).trim()).equals(expected.save(new CompoundTag())),
                    "Inspection SNBT does not preserve the complete original stack");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new IllegalStateException("Inspection SNBT cannot be parsed", failure);
        }
        String packetMarker = "\npacket-value-json:\n";
        int packetStart = text.indexOf(packetMarker);
        if (packet) {
            require(packetStart >= 0 && text.substring(packetStart + packetMarker.length(), stackStart).trim()
                    .equals(SFMValueJsonCodec.encodePretty(PACKET_VALUE)), "Inspection omitted complete pretty packet JSON");
        } else require(packetStart < 0, "Ordinary item inspection unexpectedly contains packet JSON");
    }

    private static void backspace() {
        try { rawKey(GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_PRESS, 0); }
        finally { rawKey(GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_RELEASE, 0); }
    }

    private static void rawKey(int code, int action, int modifiers) {
        Minecraft minecraft = Minecraft.getInstance();
        ((KeyboardHandlerInvoker) (Object) minecraft.keyboardHandler).sfm$invokeKeyPress(
                minecraft.getWindow().getWindow(), code, 0, action, modifiers);
    }

    private JsonObject observation() {
        JsonObject result = new JsonObject();
        var minecraft = Minecraft.getInstance();
        result.addProperty("process_id", ProcessHandle.current().pid());
        result.addProperty("screen", minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
        result.addProperty("physical_shift_proven", false);
        result.addProperty("tooltip_mode", SFMTooltipModeService.INSTANCE.mode().name());
        result.addProperty("more_info_requested", SFMItemUtils.isClientAndMoreInfoRequested());
        result.addProperty("tooltip_mode_restored", previousTooltipMode != null
                && SFMTooltipModeService.INSTANCE.mode() == previousTooltipMode);
        result.addProperty("fixture_scope", "disposable client-only container; no server inventory claim");
        result.addProperty("failed_requests", failedRequests);
        result.add("witnesses", witnesses.deepCopy());
        if (fixture != null && minecraft.screen == fixture) {
            var slot = fixture.getSlotUnderMouse();
            result.addProperty("hovered_slot", slot == null ? -1 : slot.index);
            result.addProperty("pointer_x", SFMGamePuppetPointer.current().cachedLogicalX());
            result.addProperty("pointer_y", SFMGamePuppetPointer.current().cachedLogicalY());
            if (slot != null && slot.hasItem()) {
                JsonArray lines = new JsonArray();
                slot.getItem().getTooltipLines(minecraft.player, TooltipFlag.Default.NORMAL)
                        .forEach(line -> lines.add(line.getString()));
                result.add("tooltip_lines", lines);
            }
        }
        if (ownedWorkspace != null && minecraft.screen == ownedWorkspace) {
            ownedWorkspace.panels().stream().filter(SFMTextEditorPanel.class::isInstance)
                    .map(SFMTextEditorPanel.class::cast).findFirst().ifPresent(editor -> {
                        result.addProperty("editor_id", editor.editorId());
                        result.addProperty("read_only", editor.isReadOnly());
                        result.addProperty("document", editor.currentText());
                    });
        }
        return result;
    }

    private void cleanup() {
        var minecraft = Minecraft.getInstance();
        try {
            if (ownedPalette != null && minecraft.screen == ownedPalette) ownedPalette.onClose();
            if (ownedWorkspace != null && minecraft.screen == ownedWorkspace) ownedWorkspace.onClose();
            if (fixture != null && minecraft.screen == fixture) {
                if (originalPointer != null) SFMGamePuppetPointer.moveVirtual(fixture,
                        originalPointer.cachedLogicalX(), originalPointer.cachedLogicalY());
                // Do not call ContainerScreen.onClose(): that closes the real player's server menu.
                minecraft.setScreen(null);
            }
        } finally {
            if (previousEditor != null) SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.set(previousEditor);
            if (previousTooltipMode != null) SFMTooltipModeService.INSTANCE.setMode(previousTooltipMode);
        }
    }

    @Override public void abort() { cleanup(); }

    private void writeResponse() {
        response.add("request", request);
        write = writeAsync(directory.resolve(prefix() + ".response.json"), response);
        request = null;
        response = null;
        sequence++;
    }

    private String prefix() { return String.format(Locale.ROOT, "%06d", sequence); }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }

    private static JsonObject readRequest(Path path) {
        try {
            if (!Files.isRegularFile(path)) return null;
            byte[] bytes;
            try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(1025); }
            if (bytes.length > 1024) throw new IOException("Packet request exceeds 1024 bytes");
            JsonObject value = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (value.size() != 1 || !value.has("op") || !value.get("op").isJsonPrimitive()
                    || !value.getAsJsonPrimitive("op").isString()) throw new IOException("Requests contain only a string op");
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
                Files.writeString(staging, contents, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                Files.move(staging, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException failure) { throw new IllegalStateException("Could not write packet inspection evidence", failure); }
        });
    }
}
