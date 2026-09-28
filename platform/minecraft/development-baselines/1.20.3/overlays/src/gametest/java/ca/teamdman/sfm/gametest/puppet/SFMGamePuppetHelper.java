package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.client.screen.ManagerScreen;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSource;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMSizeDisplayWorkspace;
import ca.teamdman.sfm.gametest.puppet.action.*;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Declarative action builder for one annotated game puppet definition.
 */
public final class SFMGamePuppetHelper {
    public static final int SCREEN_TIMEOUT_TICKS = 200;
    public static final int RENDER_SETTLE_TICKS = 6;
    private final List<SFMPuppetAction> actions = new ArrayList<>();
    private int currentAction = 0;

    public void createFreshFlatWorld() {
        add(new CreateFreshWorldPuppetAction());
    }

    public void runGameTest(String testName) {
        if (testName == null || testName.isBlank()) {
            throw new IllegalArgumentException("Game puppet GameTest name must not be blank");
        }
        add(new RunGameTestPuppetAction(testName));
    }

    public void captureOrbit(
            String capturePrefix,
            BlockPos localTarget,
            int count,
            double radius,
            double height,
            Component caption
    ) {
        if (count < 1) {
            throw new IllegalArgumentException("Orbit capture count must be at least one");
        }
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2D * index / count;
            add(new PositionOrbitCameraPuppetAction(localTarget, radius, height, angle));
            capture(String.format("%s-%02d", capturePrefix, index), caption);
        }
    }

    /**
     * Captures the completed GameTest around the center of its actual structure bounds.
     */
    public void captureGameTestOrbit(
            String capturePrefix,
            int count,
            Component caption
    ) {
        if (count < 1) {
            throw new IllegalArgumentException("Orbit capture count must be at least one");
        }
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2D * index / count;
            add(new PositionGameTestOrbitCameraPuppetAction(angle));
            capture(String.format("%s-%02d", capturePrefix, index), caption);
        }
    }

    public void captureContainerAt(String captureName, BlockPos localTarget, Component caption) {
        captureBlockScreen(captureName, localTarget, AbstractContainerScreen.class, true, caption);
    }

    public void captureManagerAt(String captureName, BlockPos localTarget, Component caption) {
        captureBlockScreen(captureName, localTarget, ManagerScreen.class, false, caption);
    }

    public void captureManagerProgramEditor(String captureName, Component caption) {
        add(new OpenManagerProgramEditorPuppetAction());
        add(new WaitForScreenPuppetAction(ISFMTextEditScreen.class));
        capture(captureName, caption);
        add(new CloseScreenPuppetAction());
    }

    /**
     * Waits until a specific overlay type is absent without suppressing unrelated overlays.
     */
    public void waitForOverlayToNotBePresent(Class<? extends Overlay> overlayType) {
        add(new WaitForOverlayToNotBePresentPuppetAction(overlayType));
    }

    /**
     * Waits until a specific overlay type is present without constraining unrelated overlays.
     */
    public void waitForOverlayToBePresent(Class<? extends Overlay> overlayType) {
        add(new WaitForOverlayToBePresentPuppetAction(overlayType));
    }

    /**
     * Waits for a fixed number of client ticks before continuing the puppet.
     */
    public void waitTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("Wait ticks must not be negative");
        }
        if (ticks > 0) {
            add(new WaitTicksPuppetAction(ticks));
        }
    }

    /**
     * Opens the contextual client command palette from the current screen.
     */
    public void openCommandPalette() {
        add(new OpenCommandPalettePuppetAction());
    }

    public void showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View view) {
        add(new ShowDynamicKeyBindingPuppetAction(view));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void showRuntimeTheme(ShowRuntimeThemePuppetAction.View view) {
        add(new ShowRuntimeThemePuppetAction(view));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
    public void showThemeSettings(ShowThemeSettingsPuppetAction.View view) {
        add(new ShowThemeSettingsPuppetAction(view));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void applySourceReviewFixtureCommand(String command) {
        add(new ApplySourceReviewFixturePuppetAction(command));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
    public void applyReviewCommentFixtureCommand(String command) {
        add(new ApplyReviewCommentFixturePuppetAction(command));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /**
     * Executes a command through the visible palette input and waits for its
     * rendered output to settle.
     */
    public void executeCommandPalette(String command) {
        add(new ExecuteCommandPalettePuppetAction(command));
    }

    public void openTerminal() {
        add(new OpenTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void executeTerminal(String command) {
        add(new ExecuteTerminalPuppetAction(command));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void cancelTerminal() {
        add(new CancelTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Exercises an owned Rust server stop/restart and fresh Vox session. */
    public void restartRustTerminalServer() {
        add(new RestartRustTerminalServerPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Delivers printable characters through the real terminal charTyped callback. */
    public void typeTerminalText(String text) {
        add(new TypeTerminalTextPuppetAction(Objects.requireNonNull(text, "text")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Exercises Ctrl+V through the terminal's real clipboard callback. */
    public void pasteTerminalText(String text) {
        add(new PasteTerminalTextPuppetAction(Objects.requireNonNull(text, "text")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Writes the Rust-owned visible terminal text and checks optional witnesses. */
    public void writeTerminalContent(String artifactName, String requiredText, String forbiddenText) {
        add(new WriteTerminalContentPuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                requiredText,
                forbiddenText
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Sends a real mouse click into the visible Rust terminal panel. */
    public void clickTerminal() {
        add(new ClickTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Sends a real mouse drag into the visible Rust terminal panel. */
    public void dragTerminal() {
        add(new DragTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Requests a deterministic logical resize of the Rust terminal. */
    public void resizeTerminal(int columns, int rows) {
        if (columns < 1 || rows < 1) throw new IllegalArgumentException("Terminal dimensions must be positive");
        add(new ResizeTerminalPuppetAction(columns, rows));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void scrollTerminal(double delta) {
        add(new ScrollTerminalPuppetAction(delta));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void pressTerminalKey(int keyCode) {
        add(new PressTerminalKeyPuppetAction(keyCode));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Sends a terminal key with a GLFW modifier mask through the panel callbacks. */
    public void pressTerminalKey(int keyCode, int modifiers) {
        add(new PressTerminalKeyPuppetAction(keyCode, modifiers));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Sends a terminal key directly to Rust without invoking SFM focus gestures. */
    public void pressTerminalKeyDirect(int keyCode, int modifiers) {
        add(new PressTerminalKeyDirectPuppetAction(keyCode, modifiers));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void setCommandPaletteInput(String command) {
        add(new SetCommandPaletteInputPuppetAction(command, null));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void prepareIncompleteCommandPaletteInput(String command, String expected) {
        add(new SetCommandPaletteInputPuppetAction(command, expected));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void waitForScreen(Class<? extends Screen> screenType) {
        add(new WaitForScreenPuppetAction(screenType));
    }

    /** Sends a real mouse-click callback to the center of one workspace panel. */
    public void clickWorkspacePanel(int panelIndex) {
        add(new ClickWorkspacePanelPuppetAction(panelIndex));
    }

    public void openSizeDisplay(SFMSizeDisplayWorkspace.Allocation allocation) {
        add(new OpenSizeDisplayPuppetAction(Objects.requireNonNull(allocation)));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void openFalsifiedInventoryTimeline() {
        add(new OpenFalsifiedInventoryTimelinePuppetAction());
    }

    public void seekFalsifiedInventoryTimeline(int timestep) {
        add(new SeekFalsifiedInventoryTimelinePuppetAction(timestep));
    }

    public void seekFalsifiedInventoryKeyframePosition(double position) {
        add(new SeekFalsifiedInventoryKeyframePositionPuppetAction(position));
    }

    public void seekFalsifiedInventoryElapsedTicks(double ticks) {
        add(new SeekFalsifiedInventoryElapsedTicksPuppetAction(ticks));
    }

    public void jumpFalsifiedInventoryKeyframe(int direction) {
        add(new JumpFalsifiedInventoryKeyframePuppetAction(direction));
    }

    public void dragFalsifiedInventoryTimeline(int fromTimestep, int toTimestep) {
        add(new DragFalsifiedInventoryTimelinePuppetAction(fromTimestep, toTimestep));
    }

    public void openColorInput(boolean toSide) { add(new OpenColorInputPuppetAction(toSide)); }
    public void setColorInputHueSaturation(double hue, double saturation) {
        add(new SetColorInputHueSaturationPuppetAction(hue, saturation));
    }
    public void setColorInputValue(double value) { add(new SetColorInputValuePuppetAction(value)); }
    public void adjustColorInputChannel(int channel, int direction, int clicks) {
        add(new AdjustColorInputChannelPuppetAction(channel, direction, clicks));
    }
    public void selectColorInputRecent(int index) { add(new SelectColorInputRecentPuppetAction(index)); }
    public void resetColorInput() { add(new ResetColorInputPuppetAction()); }
    public void setColorInputHex(String hex, boolean rgbaOrder) { add(new SetColorInputHexPuppetAction(hex, rgbaOrder)); }
    public void confirmColorInput() { add(new ConfirmColorInputPuppetAction()); }
    public void applyRepositoryReviewCommand(String command) { add(new ApplyRepositoryReviewPuppetAction(command)); }
    public void prepareRepositoryReviewFixture() { add(new PrepareRepositoryReviewFixturePuppetAction()); }

    /** Invokes the current screen's own close/back behavior. */
    public void closeScreenNaturally() {
        add(new CloseScreenNaturallyPuppetAction());
    }

    /** Executes a palette action whose success replaces the palette with a screen. */
    public void executeCommandPaletteAndWaitForScreen(
            String command,
            Class<?> expectedScreen
    ) {
        add(new ExecuteCommandPaletteAndWaitForScreenPuppetAction(command, expectedScreen));
    }

    public void pressFileExplorerKey(int keyCode) {
        add(new PressFileExplorerKeyPuppetAction(keyCode));
    }

    public void setFileExplorerSnapshot(SFMFileExplorerSnapshot snapshot) {
        add(new SetFileExplorerSnapshotPuppetAction(snapshot));
    }

    public void openFileExplorer(SFMFileExplorerSource source) {
        add(new OpenFileExplorerPuppetAction(source));
    }

    public void openItemIconGallery() {
        add(new OpenItemIconGalleryPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void openItemPicker(boolean multiplexed) {
        add(new OpenItemPickerPuppetAction(multiplexed));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void configureItemPicker(ConfigureItemPickerPuppetAction.View view) {
        add(new ConfigureItemPickerPuppetAction(view));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void showLiteralGlobDiagnostic() {
        add(new ShowLiteralGlobDiagnosticPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void deliverFileExplorerDropFixture() {
        add(new DeliverFileExplorerDropFixturePuppetAction());
    }

    public void clickFileExplorerRow(int visibleRowIndex) {
        add(new ClickFileExplorerRowPuppetAction(visibleRowIndex));
    }

    public void assertFileExplorerWorkspace(
            int panelCount,
            String expectedRootName,
            String expectedViewerPath,
            String expectedViewerText,
            boolean rememberOrRequireViewerIdentity
    ) {
        add(new AssertFileExplorerWorkspacePuppetAction(
                panelCount,
                expectedRootName,
                expectedViewerPath,
                expectedViewerText,
                rememberOrRequireViewerIdentity
        ));
    }

    /**
     * Captures the currently rendered client frame with a numbered, styled caption.
     */
    public void capture(String captureName, Component caption) {
        add(new CapturePuppetAction(captureName, Objects.requireNonNull(caption, "caption").copy()));
    }

    public boolean isComplete() {
        return currentAction >= actions.size();
    }

    public String currentActionDescription() {
        return isComplete() ? "complete" : actions.get(currentAction).description();
    }

    public void validate() {
        if (actions.isEmpty()) {
            throw new IllegalStateException("SFM game puppet declared no actions");
        }
    }

    boolean tick(ISFMGamePuppetRuntime runtime) {
        if (isComplete()) {
            return true;
        }
        if (actions.get(currentAction).tick(runtime)) {
            currentAction++;
        }
        return isComplete();
    }

    private void captureBlockScreen(
            String captureName,
            BlockPos localTarget,
            Class<?> expectedScreen,
            boolean closeAfterCapture,
            Component caption
    ) {
        add(new UseBlockPuppetAction(localTarget));
        add(new WaitForScreenPuppetAction(expectedScreen));
        capture(captureName, caption);
        if (closeAfterCapture) {
            add(new CloseScreenPuppetAction());
        }
    }

    private void add(SFMPuppetAction action) {
        if (currentAction != 0) {
            throw new IllegalStateException("Cannot add game puppet actions after execution has begun");
        }
        actions.add(action);
    }

}
