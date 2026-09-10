package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetPointer;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** B-5a live proof over the editor's natural right-click contextual surface. */
public final class ExerciseContextualPaletteCancelPuppetAction implements SFMPuppetAction {
    private static final String DEFINITION_COMMAND =
            "sfm action invoke sfm:symbol/definition/open";
    private static final String REFERENCES_COMMAND =
            "sfm action invoke sfm:symbol/references/open";
    private static final String COPY_DETAILS_PREFIX =
            "sfm action invoke sfm:symbol/copy/details ";
    private static final int MAXIMUM_TICKS = SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS * 4;

    private enum Phase {
        WAIT_SOURCE,
        OPEN_FIRST,
        WAIT_FIRST,
        CAPTURE_FIRST,
        CLICK_CANCEL,
        WAIT_CANCELLED,
        OPEN_SECOND,
        WAIT_SECOND,
        EXECUTE_REAL_ACTION,
        WAIT_EXECUTED,
        CAPTURE_EXECUTED,
        COMPLETE
    }

    private final Path sourceFile;
    private final String symbol;
    private final int occurrence;
    private final String artifactName;
    private final JsonObject evidence = new JsonObject();
    private Phase phase = Phase.WAIT_SOURCE;
    private int ticks;
    private int settleTicks;
    private List<String> firstChoices = List.of();
    private String executedCommand;
    private String originalClipboard;
    private boolean clipboardCaptured;

    public ExerciseContextualPaletteCancelPuppetAction(
            Path sourceFile,
            String symbol,
            int occurrence,
            String artifactName
    ) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile").toAbsolutePath().normalize();
        this.symbol = requireNonBlank(symbol, "symbol");
        if (occurrence < -1) throw new IllegalArgumentException("Occurrence must be -1 or non-negative");
        this.occurrence = occurrence;
        this.artifactName = requireNonBlank(artifactName, "artifactName");
        evidence.addProperty("schema", "sfm.contextual-palette-cancel-live/1");
        evidence.addProperty("source", SFMPath.fromNative(this.sourceFile).canonical());
        evidence.addProperty("symbol", this.symbol);
        evidence.addProperty("occurrence", this.occurrence);
    }

    @Override
    public String description() {
        return "right-click source context, click Cancel, reopen, and execute a contextual action";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (++ticks > MAXIMUM_TICKS) fail("Contextual palette journey timed out in " + phase);
        try {
            return switch (phase) {
                case WAIT_SOURCE -> waitSource();
                case OPEN_FIRST -> openByRightClick(Phase.WAIT_FIRST);
                case WAIT_FIRST -> waitFirst();
                case CAPTURE_FIRST -> captureFirst(runtime);
                case CLICK_CANCEL -> clickCancel();
                case WAIT_CANCELLED -> waitCancelled();
                case OPEN_SECOND -> openByRightClick(Phase.WAIT_SECOND);
                case WAIT_SECOND -> waitSecond();
                case EXECUTE_REAL_ACTION -> executeRealAction();
                case WAIT_EXECUTED -> waitExecuted();
                case CAPTURE_EXECUTED -> captureExecuted(runtime);
                case COMPLETE -> true;
            };
        } catch (RuntimeException failure) {
            restoreClipboard();
            throw failure;
        }
    }

    private boolean waitSource() {
        SFMScreenMultiplexer workspace = workspaceOrNull();
        if (workspace == null) return false;
        SFMSourcePuppetProbe.EditorHandle editor = SFMSourcePuppetProbe.editor(workspace, sourceFile).orElse(null);
        if (editor == null || editor.resolvedPanel().isEmpty()) return false;
        SFMTextDocumentSnapshot document = editor.state().documentSnapshot().orElse(null);
        if (document == null || !document.ready()) return false;
        if (!workspace.focusPanel(editor.panelId())) fail("The source editor could not be focused");
        phase = Phase.OPEN_FIRST;
        return false;
    }

    private boolean openByRightClick(Phase next) {
        SFMScreenMultiplexer workspace = requireWorkspace();
        SFMSourcePuppetProbe.EditorHandle editor = SFMSourcePuppetProbe.editor(workspace, sourceFile)
                .orElseThrow(() -> new IllegalStateException("The addressed source editor is unavailable"));
        if (!workspace.focusPanel(editor.panelId())) fail("The addressed source editor could not be focused");
        SFMTextDocumentSnapshot document = editor.state().documentSnapshot().orElseThrow();
        SFMTextDocumentRange range = SFMSourcePuppetProbe.symbolRange(document.text(), symbol, occurrence);
        C11SourceNavigationPuppetProbe.Pointer pointer =
                C11SourceNavigationPuppetProbe.pointer(workspace, editor, range);
        SFMGamePuppetPointer.moveVirtual(workspace, pointer.globalX(), pointer.globalY());
        if (!workspace.mouseClicked(pointer.globalX(), pointer.globalY(), GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            fail("The text editor did not consume the contextual right-click");
        }
        workspace.mouseReleased(pointer.globalX(), pointer.globalY(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        settleTicks = 0;
        phase = next;
        return false;
    }

    private boolean waitFirst() {
        SFMCommandPaletteScreen palette = paletteOrNull();
        if (palette == null) return false;
        firstChoices = palette.choiceCommandsForAutomation();
        requireNavigationChoices(firstChoices, "first right-click");
        SFMCommandPaletteScreen.CancelControlAutomationSnapshot cancel =
                palette.focusCancelForAutomation();
        evidence.add("first_choices", strings(firstChoices));
        evidence.add("cancel", cancel(cancel));
        Minecraft minecraft = Minecraft.getInstance();
        evidence.addProperty("configured_gui_scale", minecraft.options.guiScale().get());
        evidence.addProperty("effective_gui_scale", minecraft.getWindow().getGuiScale());
        evidence.addProperty("logical_width", minecraft.getWindow().getGuiScaledWidth());
        evidence.addProperty("logical_height", minecraft.getWindow().getGuiScaledHeight());
        phase = Phase.CAPTURE_FIRST;
        return false;
    }

    private boolean captureFirst(ISFMGamePuppetRuntime runtime) {
        if (!runtime.capture(
                "contextual-actions-cancel",
                caption("A real editor right-click exposes the focused, narrated, pointer-reachable Cancel control.")
        )) return false;
        phase = Phase.CLICK_CANCEL;
        return false;
    }

    private boolean clickCancel() {
        requirePalette().clickCancelForAutomation();
        evidence.addProperty("cancel_pointer_consumed", true);
        phase = Phase.WAIT_CANCELLED;
        settleTicks = 0;
        return false;
    }

    private boolean waitCancelled() {
        if (workspaceOrNull() == null) return false;
        if (++settleTicks <= SFMGamePuppetHelper.RENDER_SETTLE_TICKS) return false;
        evidence.addProperty("cancel_restored_origin", true);
        phase = Phase.OPEN_SECOND;
        return false;
    }

    private boolean waitSecond() {
        SFMCommandPaletteScreen palette = paletteOrNull();
        if (palette == null) return false;
        List<String> choices = palette.choiceCommandsForAutomation();
        requireNavigationChoices(choices, "second right-click");
        if (!actionIds(choices).equals(actionIds(firstChoices))) {
            fail("Reopened contextual actions changed identity/order: "
                    + actionIds(firstChoices) + " -> " + actionIds(choices));
        }
        executedCommand = choices.stream()
                .filter(command -> command.startsWith(COPY_DETAILS_PREFIX))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Contextual actions did not expose the immediate Copy symbol details action: " + choices));
        evidence.add("second_choices", strings(choices));
        evidence.addProperty("reopened_by_right_click", true);
        evidence.addProperty("executed_command", executedCommand);
        phase = Phase.EXECUTE_REAL_ACTION;
        return false;
    }

    private boolean executeRealAction() {
        SFMCommandPaletteScreen palette = requirePalette();
        if (!palette.choiceReadyForPointerAutomation(executedCommand)) return false;
        Minecraft minecraft = Minecraft.getInstance();
        originalClipboard = minecraft.keyboardHandler.getClipboard();
        clipboardCaptured = true;
        minecraft.keyboardHandler.setClipboard("sfm-b5a-context-action-not-yet-executed");
        palette.clickChoiceForAutomation(executedCommand);
        phase = Phase.WAIT_EXECUTED;
        settleTicks = 0;
        return false;
    }

    private boolean waitExecuted() {
        if (workspaceOrNull() == null) return false;
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (!clipboard.contains("schema: sfm.symbol-inspection-details/1")) {
            fail("Copy symbol details did not replace the clipboard with its versioned report");
        }
        if (!clipboard.contains(SFMPath.fromNative(sourceFile).canonical())) {
            fail("Copied symbol details did not identify the addressed source file");
        }
        if (++settleTicks <= SFMGamePuppetHelper.RENDER_SETTLE_TICKS) return false;
        evidence.addProperty("real_action_completed", true);
        evidence.addProperty("clipboard_schema", "sfm.symbol-inspection-details/1");
        phase = Phase.CAPTURE_EXECUTED;
        return false;
    }

    private boolean captureExecuted(ISFMGamePuppetRuntime runtime) {
        if (!runtime.capture(
                "contextual-actions-executed",
                caption("After Cancel restores the editor, a second real right-click executes Copy symbol details.")
        )) return false;
        runtime.writeArtifact(
                artifactName,
                SFMGamePuppetArtifactFormat.JSON,
                new GsonBuilder().setPrettyPrinting().create().toJson(evidence)
        );
        restoreClipboard();
        phase = Phase.COMPLETE;
        return true;
    }

    private static void requireNavigationChoices(List<String> choices, String leg) {
        if (!choices.contains(DEFINITION_COMMAND) || !choices.contains(REFERENCES_COMMAND)) {
            throw new IllegalStateException(leg + " omitted Definition/References: " + choices);
        }
    }

    private static List<String> actionIds(List<String> commands) {
        String prefix = "sfm action invoke ";
        return commands.stream().map(command -> {
            if (!command.startsWith(prefix)) return command;
            String tail = command.substring(prefix.length());
            int separator = tail.indexOf(' ');
            return separator < 0 ? tail : tail.substring(0, separator);
        }).toList();
    }

    private static JsonArray strings(List<String> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }

    private static JsonObject cancel(SFMCommandPaletteScreen.CancelControlAutomationSnapshot snapshot) {
        JsonObject result = new JsonObject();
        result.addProperty("label", snapshot.label());
        result.addProperty("x", snapshot.x());
        result.addProperty("y", snapshot.y());
        result.addProperty("width", snapshot.width());
        result.addProperty("height", snapshot.height());
        result.addProperty("visible", snapshot.visible());
        result.addProperty("active", snapshot.active());
        result.addProperty("focused", snapshot.focused());
        result.addProperty("narration_priority", snapshot.narrationPriority());
        return result;
    }

    private static Component caption(String text) {
        return Component.literal("SFM Contextual Actions — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }

    private static SFMScreenMultiplexer workspaceOrNull() {
        return Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace
                ? workspace
                : null;
    }

    private static SFMScreenMultiplexer requireWorkspace() {
        SFMScreenMultiplexer workspace = workspaceOrNull();
        if (workspace == null) throw new IllegalStateException("Expected the source workspace");
        return workspace;
    }

    private static SFMCommandPaletteScreen paletteOrNull() {
        return Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette
                ? palette
                : null;
    }

    private static SFMCommandPaletteScreen requirePalette() {
        SFMCommandPaletteScreen palette = paletteOrNull();
        if (palette == null) throw new IllegalStateException("Expected the constrained contextual palette");
        return palette;
    }

    private void restoreClipboard() {
        if (!clipboardCaptured) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(originalClipboard);
        clipboardCaptured = false;
    }

    private static String requireNonBlank(String value, String label) {
        String canonical = Objects.requireNonNull(value, label).strip();
        if (canonical.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return canonical;
    }

    private void fail(String message) {
        restoreClipboard();
        throw new IllegalStateException(message);
    }
}
