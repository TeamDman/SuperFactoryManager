package ca.teamdman.sfm.gametest.puppet;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
{% endif %}
{% if features.workspace_panels or features.workspace_dividers %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceAxis;
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
import ca.teamdman.sfm.client.terminal.SFMTerminalInteractionPuppetProbe;
{% endif %}
{% endif %}
{% endif %}
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
{% endif %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSource;
{% endif %}
{% endcase %}
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.core.Direction;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import net.minecraft.network.chat.Component;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import java.util.List;

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
public interface ISFMGamePuppetRuntime {
    boolean createFreshFlatWorld();

{% case minecraft_version %}
{% when "1.19.2" %}
    /** Identifier of this puppet's disposable save, for an actual leave/rejoin probe. */
    String puppetWorldId();

    /** Publishes the owned integrated server to LAN and waits for the live state transition. */
    boolean publishIntegratedServerToLan();

    /** Starts one GameTest and returns once its tracker and structure are available. */
    boolean startGameTest(String testName);

    /** Starts one puppet-owned GameTest definition that is not part of ordinary test discovery. */
    boolean startGameTest(SFMGameTestDefinition testDefinition);

    /** Waits for the GameTest previously started by this puppet. */
    boolean waitForGameTest(String testName);

{% when "1.19.4" %}
    /** Publishes the owned integrated server to LAN and waits for the live state transition. */
    boolean publishIntegratedServerToLan();

    /** Starts one GameTest and returns once its tracker and structure are available. */
    boolean startGameTest(String testName);

    /** Starts one puppet-owned GameTest definition that is not part of ordinary test discovery. */
    boolean startGameTest(SFMGameTestDefinition testDefinition);

    /** Waits for the GameTest previously started by this puppet. */
    boolean waitForGameTest(String testName);

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    boolean runGameTest(String testName);

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /** Begin another fixture run in the same disposable world, after the previous run finishes. */
    void prepareGameTest();

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    void positionOrbitCamera(BlockPos localTarget, double radius, double height, double angleRadians);

    void positionGameTestOrbitCamera(double angleRadians);

    void positionForBlockUse(BlockPos localTarget);

    void useBlock(BlockPos localTarget);

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /** Aim at one point on the visible Touch Display face without OS pointer input. */
{% if features.touch_display %}
    void positionForTouchDisplayFace(BlockPos localTarget, Direction face, double u, double v);
{% endif %}

    /** Send a normal client gameplay use packet with the specified face hit. */
{% if features.touch_display %}
    void pressTouchDisplayFace(BlockPos localTarget, Direction face, double u, double v);
{% endif %}

    /** Resolve a fixture-local position using the active GameTest origin. */
    BlockPos absoluteGameTestPos(BlockPos localTarget);

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    boolean isScreen(Class<?> expectedType);

    String currentScreenName();
{% if features.command_palette %}

    boolean openCommandPalette();
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}

    /** Places command text in the real palette input without submitting it. */
{% if features.command_palette %}
    void setCommandPaletteInput(String command);
{% endif %}

    /** Submits the real palette input through the same path as Enter. */
{% if features.command_palette %}
    void submitCommandPalette();
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
{% if features.command_palette %}

    void executeCommandPalette(String command);
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}

    void pressScreenKey(int keyCode, int modifiers);

    /** Delivers one BMP character through Forge's ordinary screen-character event path. */
    void typeScreenCharacter(char character, int modifiers);
{% if features.command_palette %}

    void exerciseCommandPaletteViewport();
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_server_lifecycle and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    void assertFormerTerminalStartButtonRoutesToTerminal();
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_server_lifecycle and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    boolean isFormerTerminalStartButtonRoutingReady();
{% endif %}
{% endif %}
{% endif %}
{% if features.command_palette %}

    void assertActionChoice(List<String> expectedCommands);
{% endif %}
{% if features.command_palette %}

    void clickActionChoice(String command);
{% endif %}
{% if features.workspace_panels and features.workspace_stack_controls and features.workspace_panel_metadata and features.workspace_panel_measurement and features.workspace_focus_tracking %}

    void assertWorkspaceState(
            int totalEntries,
            int visibleEntries,
            int focusedSlotEntries,
            String expectedFocusedNarration,
            int expectedFocusedScale
    );
{% endif %}
{% if features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement %}

    void assertWorkspacePanelExtentComparison(
            int firstPanelIndex,
            int secondPanelIndex,
            SFMWorkspaceAxis axis,
            int expectedComparison
    );
{% endif %}
{% if features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement %}

    void assertWorkspacePanelInstancesDistinct(int firstPanelIndex, int secondPanelIndex);
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    void openTerminal();
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_repl_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    void executeTerminal(String command);
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    void openTerminal();
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_repl_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    void executeTerminal(String command);
{% endif %}
{% endif %}
{% endcase %}

    /** Requests server-side cancellation without closing the Rust session. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    void cancelTerminal();
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
    void cancelTerminal();
{% endif %}
{% endif %}
{% endcase %}

    /** Stops and restarts only the Rust server process owned by SFM, then reconnects the panel. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_server_lifecycle and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    void restartRustTerminalServer();
{% endif %}
{% endif %}
{% endif %}

    /** Activates the disconnected panel's focused Start/Retry widget with Space. */
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_server_lifecycle and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    void startRustTerminalThroughUi();
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_keyboard_input %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    void typeTerminalText(String text);
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
    void restartRustTerminalServer();
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    void typeTerminalText(String text);
{% endif %}
{% endif %}
{% endcase %}

    /** Places deterministic text in the clipboard and exercises the terminal Ctrl+V path. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    void pasteTerminalText(String text);
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    SFMTerminalInteractionPuppetProbe.TextRange locateTerminalText(String exactText);
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    SFMTerminalInteractionPuppetProbe.Observation observeTerminalInteraction(
            String rendererId,
            String transportId
    );
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    void dragTerminalRange(SFMTerminalInteractionPuppetProbe.TextRange range, boolean reverse);
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    void copyTerminalSelection(boolean rightClick);
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    String terminalClipboard();
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard and features.terminal_paste_confirmation %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    void pasteTerminalTextByRightClick(String text);
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard and features.terminal_paste_confirmation %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    boolean isTerminalPasteWarningOpen();
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard and features.terminal_paste_confirmation %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    String terminalPasteWarningText();
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard and features.terminal_paste_confirmation %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    boolean terminalPasteWarningCancelFocused();
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    boolean terminalContentHasExactLine(String line);
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    void writeTerminalInteractionEvidence(String artifactName, String evidence);
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
    void pasteTerminalText(String text);
{% endif %}
{% endif %}
{% endcase %}

    /** Writes the current terminal text and validates optional content witnesses. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    void writeTerminalContent(String artifactName, String requiredText, String forbiddenText);
{% endif %}
{% endif %}
{% endif %}

    /** Validates bounded Vox push invariants and writes their machine-readable evidence. */
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    void assertTerminalPushEvidence(String artifactName, boolean reconnectExpected);
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_properties and features.workspace_panel_lookup %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

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
{% endif %}
{% endif %}
{% endif %}

    /**
     * Scrolls toward and clicks one real +/-/auto control in the focused
     * terminal-properties panel. Returns false while another rendered frame is
     * needed after scrolling the control toward the viewport.
     */
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_properties %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    boolean clickTerminalPropertiesControl(String operation);
{% endif %}
{% endif %}
{% endif %}

    /**
     * Samples one already-pushed presentation without polling and writes typed
     * content plus renderer/transport/native-frame evidence.
     */
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_presentation_actions and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    void assertTerminalPresentationEvidence(
            String artifactName,
            String rendererId,
            String transportId,
            String requiredContentLine,
            boolean initialDefaultExpected,
            boolean freshPresentationExpected,
            boolean panelResizeExpected
    );
{% endif %}
{% endif %}
{% endif %}

    /** Selects one presentation axis through the panel's real click/keyboard UI path. */
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_presentation_actions and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    void selectTerminalPresentationThroughUi(boolean rendererAxis, String optionId);
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_focus_gestures and features.workspace_panel_measurement %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    void clickTerminal();
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_focus_gestures and features.workspace_panel_measurement %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    void dragTerminal();
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    void resizeTerminal(int columns, int rows);
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_focus_gestures and features.workspace_panel_measurement %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    void scrollTerminal(double delta);
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_keyboard_input %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    void pressTerminalKey(int keyCode);
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
    void writeTerminalContent(String artifactName, String requiredText, String forbiddenText);
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    void clickTerminal();
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    void dragTerminal();
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    void resizeTerminal(int columns, int rows);
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    void scrollTerminal(double delta);
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    void pressTerminalKey(int keyCode);
{% endif %}
{% endif %}
{% endcase %}

    /** Sends one terminal key press/release pair with a GLFW modifier mask. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_keyboard_input %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    default void pressTerminalKey(int keyCode, int modifiers) {
        pressTerminalKey(keyCode);
    }
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
    default void pressTerminalKey(int keyCode, int modifiers) {
        pressTerminalKey(keyCode);
    }
{% endif %}
{% endif %}
{% endcase %}

    /** Sends a key directly to the Rust PTY without applying SFM focus gestures. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_keyboard_input %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    void pressTerminalKeyDirect(int keyCode, int modifiers);
{% endif %}
{% endif %}
{% endif %}
{% if features.file_explorer and features.workspace_panels %}

    void pressFileExplorerKey(int keyCode);
{% endif %}
{% if features.file_explorer and features.workspace_panels %}

    void setFileExplorerSnapshot(SFMFileExplorerSnapshot snapshot);
{% endif %}
{% if features.file_explorer and features.workspace_panels %}

    boolean isFileExplorerOpen();
{% endif %}
{% if features.file_explorer and features.workspace_panels %}

    void deliverFileExplorerDropFixture();
{% endif %}
{% if features.file_explorer and features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement and features.workspace_focus_tracking %}

    void clickFileExplorerRow(int visibleRowIndex);
{% endif %}
{% if features.file_explorer and features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement and features.workspace_focus_tracking %}

    void assertFileExplorerWorkspace(
            int panelCount,
            String expectedRootName,
            String expectedViewerPath,
            String expectedViewerText,
            boolean rememberOrRequireViewerIdentity
    );
{% endif %}
{% if features.file_explorer and features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement and features.workspace_focus_tracking %}

    void assertFileExplorerPreviewFocus(boolean previewFocused);
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
    void pressTerminalKeyDirect(int keyCode, int modifiers);
{% endif %}
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    void pressFileExplorerKey(int keyCode);
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    void setFileExplorerSnapshot(SFMFileExplorerSnapshot snapshot);
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    void openFileExplorer(SFMFileExplorerSource source);
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    boolean isFileExplorerOpen();
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    void deliverFileExplorerDropFixture();
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    void clickFileExplorerRow(int visibleRowIndex);
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    void assertFileExplorerWorkspace(
            int panelCount,
            String expectedRootName,
            String expectedViewerPath,
            String expectedViewerText,
            boolean rememberOrRequireViewerIdentity
    );
{% endif %}
{% endcase %}

    boolean isOverlay(Class<? extends Overlay> expectedType);

    boolean capture(String captureName, Component caption);

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /** Captures the rendered frame without suppressing HUD and Forge overlay rendering. */
    boolean captureWithHud(String captureName, Component caption);

    /** Stages one bounded, portable evidence artifact for durable preview publication. */
    void writeArtifact(
            String artifactName,
            SFMGamePuppetArtifactFormat format,
            String contents
    );

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    void closeScreen();

    void closeScreenNaturally();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement %}

    boolean clickWorkspacePanel(int panelIndex);
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}

    boolean clickWorkspacePanel(int panelIndex);
{% endif %}
{% endcase %}
{% if features.timeline_panels and features.workspace_panels %}

    void openFalsifiedInventoryTimeline();
{% endif %}
{% if features.timeline_panels and features.workspace_panels %}

    void seekFalsifiedInventoryTimeline(int timestep);
{% endif %}
{% if features.timeline_panels and features.workspace_panels %}

    void seekFalsifiedInventoryKeyframePosition(double position);
{% endif %}
{% if features.timeline_panels and features.workspace_panels %}

    void seekFalsifiedInventoryElapsedTicks(double ticks);
{% endif %}
{% if features.timeline_panels and features.workspace_panels %}

    void jumpFalsifiedInventoryKeyframe(int direction);
{% endif %}
{% if features.timeline_panels and features.workspace_panels %}

    void dragFalsifiedInventoryTimeline(int fromTimestep, int toTimestep);
{% endif %}

    void openManagerProgramEditor();
{% if features.workspace_panels %}

    void openColorInput(boolean toSide);
{% endif %}
{% if features.workspace_panels %}

    void setColorInputHueSaturation(double hue, double saturation);
{% endif %}
{% if features.workspace_panels %}

    void setColorInputValue(double value);
{% endif %}
{% if features.workspace_panels %}

    void adjustColorInputChannel(int channel, int direction, int clicks);
{% endif %}
{% if features.workspace_panels %}

    void selectColorInputRecent(int index);
{% endif %}
{% if features.workspace_panels %}

    void resetColorInput();
{% endif %}
{% if features.workspace_panels %}

    void setColorInputHex(String hex, boolean rgbaOrder);
{% endif %}
{% if features.workspace_panels %}

    void confirmColorInput();
{% endif %}
}
