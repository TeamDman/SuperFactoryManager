package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceAxis;
import ca.teamdman.sfm.client.terminal.SFMTerminalInteractionPuppetProbe;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

public interface ISFMGamePuppetRuntime {
    boolean createFreshFlatWorld();

    boolean runGameTest(String testName);

    void positionOrbitCamera(BlockPos localTarget, double radius, double height, double angleRadians);

    void positionGameTestOrbitCamera(double angleRadians);

    void positionForBlockUse(BlockPos localTarget);

    void useBlock(BlockPos localTarget);

    boolean isScreen(Class<?> expectedType);

    String currentScreenName();

    boolean openCommandPalette();

    /** Places command text in the real palette input without submitting it. */
    void setCommandPaletteInput(String command);

    /** Submits the real palette input through the same path as Enter. */
    void submitCommandPalette();

    void executeCommandPalette(String command);

    void pressScreenKey(int keyCode, int modifiers);

    /** Delivers one BMP character through Forge's ordinary screen-character event path. */
    void typeScreenCharacter(char character, int modifiers);

    void exerciseCommandPaletteViewport();

    void assertFormerTerminalStartButtonRoutesToTerminal();

    boolean isFormerTerminalStartButtonRoutingReady();

    void assertActionChoice(List<String> expectedCommands);

    void clickActionChoice(String command);

    void assertWorkspaceState(
            int totalEntries,
            int visibleEntries,
            int focusedSlotEntries,
            String expectedFocusedNarration,
            int expectedFocusedScale
    );

    void assertWorkspacePanelExtentComparison(
            int firstPanelIndex,
            int secondPanelIndex,
            SFMWorkspaceAxis axis,
            int expectedComparison
    );

    void assertWorkspacePanelInstancesDistinct(int firstPanelIndex, int secondPanelIndex);

    void openTerminal();

    void executeTerminal(String command);

    /** Requests server-side cancellation without closing the Rust session. */
    void cancelTerminal();

    /** Stops and restarts only the Rust server process owned by SFM, then reconnects the panel. */
    void restartRustTerminalServer();

    /** Activates the disconnected panel's focused Start/Retry widget with Space. */
    void startRustTerminalThroughUi();

    void typeTerminalText(String text);

    /** Places deterministic text in the clipboard and exercises the terminal Ctrl+V path. */
    void pasteTerminalText(String text);

    SFMTerminalInteractionPuppetProbe.TextRange locateTerminalText(String exactText);

    SFMTerminalInteractionPuppetProbe.Observation observeTerminalInteraction(
            String rendererId,
            String transportId
    );

    void dragTerminalRange(SFMTerminalInteractionPuppetProbe.TextRange range, boolean reverse);

    void copyTerminalSelection(boolean rightClick);

    String terminalClipboard();

    void pasteTerminalTextByRightClick(String text);

    boolean isTerminalPasteWarningOpen();

    String terminalPasteWarningText();

    boolean terminalPasteWarningCancelFocused();

    boolean terminalContentHasExactLine(String line);

    void writeTerminalInteractionEvidence(String artifactName, String evidence);

    /** Writes the current terminal text and validates optional content witnesses. */
    void writeTerminalContent(String artifactName, String requiredText, String forbiddenText);

    /** Validates bounded Vox push invariants and writes their machine-readable evidence. */
    void assertTerminalPushEvidence(String artifactName, boolean reconnectExpected);

    boolean assertTerminalPropertiesEvidence(
            String artifactName,
            String expectedRendererId,
            String expectedTransportId,
            String expectedSurfaceMode,
            String expectedFontMode,
            String expectedCellsMode,
            Integer expectedConfiguredGuiScale,
            Integer expectedPanelGuiScaleOverride,
            String expectedRejectionCode,
            boolean retainedFrameExpected
    );

    /**
     * Scrolls toward and clicks one real +/-/auto control in the focused
     * terminal-properties panel. Returns false while another rendered frame is
     * needed after scrolling the control toward the viewport.
     */
    boolean clickTerminalPropertiesControl(String operation);

    /**
     * Samples one already-pushed presentation without polling and writes typed
     * content plus renderer/transport/native-frame evidence.
     */
    void assertTerminalPresentationEvidence(
            String artifactName,
            String rendererId,
            String transportId,
            String requiredContentLine,
            boolean initialDefaultExpected,
            boolean freshPresentationExpected,
            boolean panelResizeExpected
    );

    /** Selects one presentation axis through the panel's real click/keyboard UI path. */
    void selectTerminalPresentationThroughUi(boolean rendererAxis, String optionId);

    void clickTerminal();

    void dragTerminal();

    void resizeTerminal(int columns, int rows);

    void scrollTerminal(double delta);

    void pressTerminalKey(int keyCode);

    /** Sends one terminal key press/release pair with a GLFW modifier mask. */
    default void pressTerminalKey(int keyCode, int modifiers) {
        pressTerminalKey(keyCode);
    }

    /** Sends a key directly to the Rust PTY without applying SFM focus gestures. */
    void pressTerminalKeyDirect(int keyCode, int modifiers);

    void pressFileExplorerKey(int keyCode);

    void setFileExplorerSnapshot(SFMFileExplorerSnapshot snapshot);

    boolean isFileExplorerOpen();

    void deliverFileExplorerDropFixture();

    void clickFileExplorerRow(int visibleRowIndex);

    void assertFileExplorerWorkspace(
            int panelCount,
            String expectedRootName,
            String expectedViewerPath,
            String expectedViewerText,
            boolean rememberOrRequireViewerIdentity
    );

    void assertFileExplorerPreviewFocus(boolean previewFocused);

    boolean isOverlay(Class<? extends Overlay> expectedType);

    boolean capture(String captureName, Component caption);

    /** Captures the rendered frame without suppressing HUD and Forge overlay rendering. */
    boolean captureWithHud(String captureName, Component caption);

    /** Stages one bounded, portable evidence artifact for durable preview publication. */
    void writeArtifact(
            String artifactName,
            SFMGamePuppetArtifactFormat format,
            String contents
    );

    void closeScreen();

    void closeScreenNaturally();

    boolean clickWorkspacePanel(int panelIndex);

    void openFalsifiedInventoryTimeline();

    void seekFalsifiedInventoryTimeline(int timestep);

    void seekFalsifiedInventoryKeyframePosition(double position);

    void seekFalsifiedInventoryElapsedTicks(double ticks);

    void jumpFalsifiedInventoryKeyframe(int direction);

    void dragFalsifiedInventoryTimeline(int fromTimestep, int toTimestep);

    void openManagerProgramEditor();

    void openColorInput(boolean toSide);

    void setColorInputHueSaturation(double hue, double saturation);

    void setColorInputValue(double value);

    void adjustColorInputChannel(int channel, int direction, int clicks);

    void selectColorInputRecent(int index);

    void resetColorInput();

    void setColorInputHex(String hex, boolean rgbaOrder);

    void confirmColorInput();
}
