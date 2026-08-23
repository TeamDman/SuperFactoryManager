package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.puppet.action.AssertOrdinaryDocumentHistoryPuppetAction;
import ca.teamdman.sfm.gametest.puppet.action.AssertProgressivePaletteCompletionPuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Natural title-screen proof for progressive completion and ordinary retained document history. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.FIXED_1280X720_AUTO,
        timeoutTicks = 20 * 12 * 60
)
public final class TitleScreenOrdinaryDocumentHistoryGamePuppet {
    private TitleScreenOrdinaryDocumentHistoryGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);

        // Seed one real complete panel-open MRU entry, then browse its grammar
        // naturally rather than injecting a preselected suggestion.
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:palette/history/clear");
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:text_editor sfm:text_editor_v3",
                SFMScreenMultiplexer.class
        );
        puppet.assertWorkspaceState(1, 1, 1, "Text Editor v3", -1);
        puppet.openCommandPalette();
        paletteCheckpoint(puppet, AssertProgressivePaletteCompletionPuppetAction.Stage.BLANK_MRU,
                "palette-blank-mru", "palette-blank-mru",
                "Blank input keeps the newest complete command first.");
        puppet.typeScreenText("open");
        paletteCheckpoint(puppet, AssertProgressivePaletteCompletionPuppetAction.Stage.OPEN_QUERY,
                "palette-open-query", "palette-open-query",
                "Typing open ranks the reusable panel/open boundary above its complete history leaf.");
        puppet.pressScreenKey(GLFW.GLFW_KEY_TAB, 0);
        paletteCheckpoint(puppet, AssertProgressivePaletteCompletionPuppetAction.Stage.ACTION_BOUNDARY,
                "palette-action-boundary", "palette-action-boundary",
                "The first Tab accepts exactly panel/open without adding a space.");
        puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        puppet.assertProgressivePaletteCompletion(
                AssertProgressivePaletteCompletionPuppetAction.Stage.UNDO_QUERY,
                "palette-completion-undo"
        );
        puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
        puppet.assertProgressivePaletteCompletion(
                AssertProgressivePaletteCompletionPuppetAction.Stage.REDO_BOUNDARY,
                "palette-completion-redo"
        );
        puppet.pressScreenKey(GLFW.GLFW_KEY_TAB, 0);
        paletteCheckpoint(puppet, AssertProgressivePaletteCompletionPuppetAction.Stage.STRICT_DESCENDANT,
                "palette-strict-descendant", "palette-strict-descendant",
                "A later Tab advances to a deterministic strict panel/open descendant.");
        puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        puppet.typeScreenText(" ");
        paletteCheckpoint(puppet, AssertProgressivePaletteCompletionPuppetAction.Stage.SCENE_FRONTIER,
                "palette-scene-frontier", "palette-scene-frontier",
                "An explicit Space enters the shared scene slot and reuses text-editor argument history.");

        // Characterize dynamic and explanatory frontiers in the same live palette.
        puppet.setCommandPaletteInput("sfm action invoke sfm:episode/trajectory/plan ");
        puppet.assertProgressivePaletteCompletion(
                AssertProgressivePaletteCompletionPuppetAction.Stage.TRAJECTORY_SELECTOR,
                "palette-trajectory-selector"
        );
        puppet.setCommandPaletteInput("sfm action invoke sfm:overlay/visibility/set ");
        puppet.assertProgressivePaletteCompletion(
                AssertProgressivePaletteCompletionPuppetAction.Stage.OVERLAY_SELECTOR,
                "palette-overlay-selector"
        );
        puppet.setCommandPaletteInput(
                "sfm action invoke sfm:overlay/visibility/set id(sfm%3Ahistory) ");
        puppet.assertProgressivePaletteCompletion(
                AssertProgressivePaletteCompletionPuppetAction.Stage.OVERLAY_VISIBILITY,
                "palette-overlay-visibility"
        );
        puppet.setCommandPaletteInput("sfm action invoke sfm:echo ");
        paletteCheckpoint(puppet, AssertProgressivePaletteCompletionPuppetAction.Stage.USAGE_HINT,
                "palette-required-usage", "palette-required-usage",
                "An unsuggested message value still exposes a named, non-executable usage row.");
        puppet.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.assertWorkspaceState(1, 1, 1, "Text Editor v3", -1);

        // Ordinary editor history: raw events remain exact while semantic
        // transactions become approachable undo units.
        puppet.typeScreenText("hello");
        puppet.typeScreenText(" ");
        puppet.typeScreenText("world!");
        historyCheckpoint(puppet, AssertOrdinaryDocumentHistoryPuppetAction.Stage.TYPED,
                "history-typed", "history-editor-typed",
                "Natural typing records hello, space, world, and punctuation as exact semantic transactions.");
        undo(puppet, 4);
        historyCheckpoint(puppet, AssertOrdinaryDocumentHistoryPuppetAction.Stage.ROOT_AFTER_UNDO,
                "history-root-after-undo", "history-editor-root-after-undo",
                "Ctrl+Z returns to the empty root without deleting hello world!.");
        puppet.typeScreenText("new");
        puppet.typeScreenText(" ");
        puppet.typeScreenText("content");
        historyCheckpoint(puppet, AssertOrdinaryDocumentHistoryPuppetAction.Stage.BRANCHED,
                "history-branched", "history-editor-branched",
                "Typing after undo creates a sibling branch and keeps the departed branch reachable.");

        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open/right sfm:document/history focused",
                SFMScreenMultiplexer.class
        );
        puppet.assertWorkspaceState(2, 2, 1, "Document History", -1);
        historyCheckpoint(puppet, AssertOrdinaryDocumentHistoryPuppetAction.Stage.CANVAS_READY,
                "history-canvas-ready", "history-canvas-top-down",
                "Blue actions, amber states, and curved undo jumps present ordinary editing top-down.");

        puppet.pressScreenKey(GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);
        puppet.typeScreenText("!");
        historyCheckpoint(puppet, AssertOrdinaryDocumentHistoryPuppetAction.Stage.LIVE_PUSH,
                "history-live-push", "history-canvas-live-push",
                "The graph receives the new edit immediately while keyboard focus stays in the editor.");

        undo(puppet, 4);
        puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
        historyCheckpoint(puppet, AssertOrdinaryDocumentHistoryPuppetAction.Stage.AMBIGUOUS_REDO,
                "history-ambiguous-redo", "history-ambiguous-redo-choice",
                "Redo at the shared root uses the constrained palette to expose both retained branches.");
        puppet.chooseOrdinaryDocumentHistoryBranch(AssertOrdinaryDocumentHistoryPuppetAction.DEPARTED_TEXT);
        redo(puppet, 3);
        historyCheckpoint(puppet, AssertOrdinaryDocumentHistoryPuppetAction.Stage.RESTORED_DEPARTED,
                "history-restored-departed", "history-restored-hello-world",
                "Choosing the old branch restores hello world! and keeps new content! reachable.");

        puppet.pressScreenKey(GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_T, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_LEFT, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.exerciseOrdinaryDocumentHistoryCanvas("history-canvas-pan-zoom");
        puppet.pressScreenKey(GLFW.GLFW_KEY_R, 0);
        historyCheckpoint(puppet, AssertOrdinaryDocumentHistoryPuppetAction.Stage.INTERACTED,
                "history-canvas-interacted", "history-canvas-left-right",
                "Transpose, selection details, zoom, pan, and readable reset framing preserve stable graph identity.");
        puppet.pressScreenKey(GLFW.GLFW_KEY_V, 0);
        historyCheckpoint(puppet, AssertOrdinaryDocumentHistoryPuppetAction.Stage.TRANSCRIPT,
                "history-transcript", "history-chronological-transcript",
                "The same graph remains available as a visible chronological text transcript.");
    }

    private static void undo(SFMGamePuppetHelper puppet, int count) {
        for (int index = 0; index < count; index++) {
            puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        }
    }

    private static void redo(SFMGamePuppetHelper puppet, int count) {
        for (int index = 0; index < count; index++) {
            puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
        }
    }

    private static void paletteCheckpoint(
            SFMGamePuppetHelper puppet,
            AssertProgressivePaletteCompletionPuppetAction.Stage stage,
            String artifact,
            String capture,
            String caption
    ) {
        puppet.assertProgressivePaletteCompletion(stage, artifact);
        puppet.waitTicks(SFMGamePuppetHelper.COMMAND_PALETTE_OBSERVATION_TICKS);
        puppet.capture(capture, caption("Progressive palette", caption));
    }

    private static void historyCheckpoint(
            SFMGamePuppetHelper puppet,
            AssertOrdinaryDocumentHistoryPuppetAction.Stage stage,
            String artifact,
            String capture,
            String caption
    ) {
        puppet.assertOrdinaryDocumentHistory(stage, artifact);
        puppet.capture(capture, caption("Ordinary history", caption));
    }

    private static Component caption(String heading, String text) {
        return Component.literal(heading + ": ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
