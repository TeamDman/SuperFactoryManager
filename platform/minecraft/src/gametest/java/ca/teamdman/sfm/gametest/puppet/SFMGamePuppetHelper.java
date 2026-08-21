package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.client.screen.ManagerScreen;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceAxis;
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
import ca.teamdman.sfm.client.explorer.SFMPath;
import java.nio.file.Path;

/**
 * Declarative action builder for one annotated game puppet definition.
 */
public final class SFMGamePuppetHelper {
    public static final int SCREEN_TIMEOUT_TICKS = 200;
    public static final int RENDER_SETTLE_TICKS = 6;
    /** Ten 20 Hz client ticks make palette automation observable for 500 ms. */
    public static final int COMMAND_PALETTE_OBSERVATION_TICKS = 10;
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

    /** Launches {@code sfm.exe invoke} and observes its size-display panel result. */
    public void invokeExternalCliSizeDisplay() {
        add(new InvokeExternalCliSizeDisplayPuppetAction());
    }

    /**
     * Runs the complete real-process lazy-explorer control journey without
     * requiring an operator or a visible companion terminal.
     */
    public void invokeExternalCliLazyExplorer() {
        add(new InvokeExternalCliLazyExplorerPuppetAction());
    }

    /** Waits until one resolver row is materialized and optionally selects/focuses it. */
    public void waitForExplorerPath(SFMPath path, boolean select) {
        add(new WaitForExplorerPathPuppetAction(Objects.requireNonNull(path, "path"), select));
    }

    /** Runs and records the self-contained X-8b focus/filter/wheel interaction journey. */
    public void exerciseExplorerInteractionFidelity(SFMPath javaPath) {
        add(new ExerciseExplorerInteractionFidelityPuppetAction(
                Objects.requireNonNull(javaPath, "javaPath")
        ));
    }

    /** Records the final real-source identity, layout, focus, and no-write witness. */
    public void assertAddressedSfmJava(Path root, Path file) {
        add(new AssertAddressedSfmJavaPuppetAction(
                Objects.requireNonNull(root, "root"),
                Objects.requireNonNull(file, "file")
        ));
    }

    /** Waits for styled Java publication and records source-free explorer/editor evidence. */
    public void assertSfmJavaSyntaxPresentation(Path root, Path file, String artifactName) {
        add(new AssertSfmJavaSyntaxPresentationPuppetAction(
                Objects.requireNonNull(root, "root"),
                Objects.requireNonNull(file, "file"),
                Objects.requireNonNull(artifactName, "artifactName")
        ));
    }

    /** Repeats the exact immutable Java document and requires cache/session reuse. */
    public void assertWarmSfmJavaSyntaxPresentation(
            Path file,
            String artifactName,
            String mandatoryScreenshotCaptureId
    ) {
        add(new AssertWarmSfmJavaSyntaxPresentationPuppetAction(
                Objects.requireNonNull(file, "file"),
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(mandatoryScreenshotCaptureId, "mandatoryScreenshotCaptureId")
        ));
    }

    /** Positions an exact source occurrence, drives F12, and requires one exact SFM-owned target. */
    public void assertJumpToDefinition(
            Path sourceFile,
            String symbol,
            int occurrence,
            Path expectedTargetFile,
            String artifactName
    ) {
        add(new AssertJumpToDefinitionPuppetAction(
                Objects.requireNonNull(sourceFile, "sourceFile"),
                Objects.requireNonNull(symbol, "symbol"),
                occurrence,
                Objects.requireNonNull(expectedTargetFile, "expectedTargetFile"),
                Objects.requireNonNull(artifactName, "artifactName")
        ));
    }

    /** Proves that a naturally submitted F12 result cannot navigate after its editor bytes change. */
    public void assertStaleDocumentJumpRejection(
            Path sourceFile,
            String symbol,
            int occurrence,
            String artifactName
    ) {
        add(new AssertStaleDocumentJumpPuppetAction(
                Objects.requireNonNull(sourceFile, "sourceFile"),
                Objects.requireNonNull(symbol, "symbol"),
                occurrence,
                Objects.requireNonNull(artifactName, "artifactName")
        ));
    }

    /** Copies one immutable contextual symbol report and artifacts its exact clipboard bytes. */
    public void assertSymbolInspectionCopy(
            Path sourceFile,
            String symbol,
            int occurrence,
            String artifactName,
            String phase
    ) {
        add(new AssertSymbolInspectionCopyPuppetAction(
                Objects.requireNonNull(sourceFile, "sourceFile"),
                Objects.requireNonNull(symbol, "symbol"),
                occurrence,
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(phase, "phase")
        ));
    }

    /** Repeats one exact symbol lookup and requires reuse of its existing addressed target panel. */
    public void assertWarmJumpToDefinition(
            Path sourceFile,
            String symbol,
            int occurrence,
            Path expectedTargetFile,
            String artifactName,
            String mandatoryScreenshotCaptureId
    ) {
        add(new AssertWarmJumpToDefinitionPuppetAction(
                Objects.requireNonNull(sourceFile, "sourceFile"),
                Objects.requireNonNull(symbol, "symbol"),
                occurrence,
                Objects.requireNonNull(expectedTargetFile, "expectedTargetFile"),
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(mandatoryScreenshotCaptureId, "mandatoryScreenshotCaptureId")
        ));
    }

    /** Drives a real two-candidate definition choice and records the selected exact target. */
    public void assertAmbiguousJumpToDefinition(
            Path sourceFile,
            Path authorizedRoot,
            Path fixturePath,
            Path selectedTargetFile,
            String artifactName,
            String choiceCaptureName,
            Component choiceCaption,
            String targetCaptureName,
            Component targetCaption
    ) {
        add(new AssertAmbiguousJumpToDefinitionPuppetAction(
                Objects.requireNonNull(sourceFile, "sourceFile"),
                Objects.requireNonNull(authorizedRoot, "authorizedRoot"),
                Objects.requireNonNull(fixturePath, "fixturePath"),
                Objects.requireNonNull(selectedTargetFile, "selectedTargetFile"),
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(choiceCaptureName, "choiceCaptureName"),
                Objects.requireNonNull(choiceCaption, "choiceCaption"),
                Objects.requireNonNull(targetCaptureName, "targetCaptureName"),
                Objects.requireNonNull(targetCaption, "targetCaption")
        ));
    }

    /** Runs a dependency jump only when the pinned index uniquely resolves its preflight selector. */
    public void assertDependencyJumpToDefinitionIfIndexed(
            Path sourceFile,
            String symbol,
            int occurrence,
            String dependencySelector,
            String artifactName,
            String captureName,
            Component captureCaption
    ) {
        add(new AssertJumpToDefinitionPuppetAction(
                Objects.requireNonNull(sourceFile, "sourceFile"),
                Objects.requireNonNull(symbol, "symbol"),
                occurrence,
                Objects.requireNonNull(dependencySelector, "dependencySelector"),
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(captureName, "captureName"),
                Objects.requireNonNull(captureCaption, "captureCaption")
        ));
    }

    /** Enqueues the integrated C-11 source-navigation journey as one stateful live proof. */
    public void assertSourceNavigationJourney(
            Path sourceRoot,
            Path sourceFile,
            String artifactName
    ) {
        add(new AssertSourceNavigationJourneyPuppetAction(
                Objects.requireNonNull(sourceRoot, "sourceRoot"),
                Objects.requireNonNull(sourceFile, "sourceFile"),
                Objects.requireNonNull(artifactName, "artifactName")
        ));
    }

    /** Exercises copy, pin/resume, exact dismiss, and later-message survival through the live toast UI. */
    public void exerciseActionableToast(String artifactName) {
        add(new ExerciseActionableToastPuppetAction(Objects.requireNonNull(artifactName, "artifactName")));
    }

    /** Runs spatial coverage through the real short-lived external {@code sfm.exe} remoting client. */
    public void invokeExternalCliSpatialCoverage(Path sourceFile, Path artifactDirectory, String artifactName) {
        add(new InvokeExternalCliSpatialCoveragePuppetAction(
                Objects.requireNonNull(sourceFile, "sourceFile"),
                Objects.requireNonNull(artifactDirectory, "artifactDirectory"),
                Objects.requireNonNull(artifactName, "artifactName")
        ));
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

    public void assertReviewExplorer(
            AssertReviewExplorerPuppetAction.Projection projection
    ) {
        add(new AssertReviewExplorerPuppetAction(projection));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /**
     * Executes a command through the visible palette input and waits for its
     * rendered output to settle.
     */
    public void executeCommandPalette(String command) {
        add(new ExecuteCommandPalettePuppetAction(command));
    }

    public void pressScreenKey(int keyCode, int modifiers) {
        add(new PressScreenKeyPuppetAction(keyCode, modifiers));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Types printable BMP text one character per client tick through the real screen callback. */
    public void typeScreenText(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) return;
        add(new TypeScreenTextPuppetAction(text));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Validates and artifacts one natural temporal-numbering journey checkpoint. */
    public void assertTemporalTrajectoryMachine(
            AssertTemporalTrajectoryMachinePuppetAction.Stage stage,
            String artifactName
    ) {
        add(new AssertTemporalTrajectoryMachinePuppetAction(
                Objects.requireNonNull(stage, "stage"),
                Objects.requireNonNull(artifactName, "artifactName")
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Validates and artifacts one natural exact-replay/semantic-rebase checkpoint. */
    public void assertTemporalReplayRebase(
            AssertTemporalReplayRebasePuppetAction.Stage stage,
            String artifactName,
            String screenshotCaptureId
    ) {
        add(new AssertTemporalReplayRebasePuppetAction(
                Objects.requireNonNull(stage, "stage"),
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(screenshotCaptureId, "screenshotCaptureId")
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Validates and artifacts one natural whole-workspace counterfactual checkpoint. */
    public void assertWorkspaceCounterfactual(
            AssertWorkspaceCounterfactualPuppetAction.Stage stage,
            String artifactName,
            String screenshotCaptureId
    ) {
        add(new AssertWorkspaceCounterfactualPuppetAction(
                Objects.requireNonNull(stage, "stage"),
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(screenshotCaptureId, "screenshotCaptureId")
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Runs X6's complete in-world History Graph overlay journey and writes its durable evidence. */
    public void exerciseHistoryGraphOverlay() {
        add(new ExerciseHistoryOverlayPuppetAction());
    }

    /** Chooses exact source/source replay from its visible constrained action palette. */
    public void clickTemporalExactReplayChoice() {
        add(new ClickTemporalExactReplayChoicePuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Resolves retained state ids and types an ordinary selector-explicit replay action. */
    public void invokeTemporalReplay(
            ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.ReplayMode mode,
            InvokeTemporalReplayPuppetAction.Target target
    ) {
        add(new InvokeTemporalReplayPuppetAction(
                Objects.requireNonNull(mode, "mode"),
                Objects.requireNonNull(target, "target")
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void exerciseCommandPaletteViewport() {
        add(new ExerciseCommandPaletteViewportPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Focuses the real narrated Cancel widget without activating it. */
    public void focusCommandPaletteCancel() {
        add(new FocusCommandPaletteCancelPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Proves the B-5a right-click, pointer-Cancel, reopen, and real-action journey. */
    public void exerciseContextualPaletteCancel(
            Path sourceFile,
            String symbol,
            int occurrence,
            String artifactName
    ) {
        add(new ExerciseContextualPaletteCancelPuppetAction(
                Objects.requireNonNull(sourceFile, "sourceFile"),
                Objects.requireNonNull(symbol, "symbol"),
                occurrence,
                Objects.requireNonNull(artifactName, "artifactName")
        ));
    }

    /** Validates and artifacts one read-only candidate-history scrub checkpoint. */
    public void assertCandidateHistory(
            AssertCandidateHistoryPuppetAction.Stage stage,
            String artifactName
    ) {
        add(new AssertCandidateHistoryPuppetAction(
                Objects.requireNonNull(stage, "stage"),
                Objects.requireNonNull(artifactName, "artifactName")
        ));
    }

    /** Installs the deterministic non-materialized candidate-status route used by X1's visual proof. */
    public void registerCandidateHistoryStatusFixture() {
        add(CandidateHistoryStatusFixturePuppetAction.register());
    }

    public void assertCandidateHistoryStatus(
            CandidateHistoryStatusFixturePuppetAction.Stage stage,
            String artifactName
    ) {
        add(CandidateHistoryStatusFixturePuppetAction.assertStage(stage, artifactName));
    }

    public void unregisterCandidateHistoryStatusFixture() {
        add(CandidateHistoryStatusFixturePuppetAction.unregister());
    }

    /** Resets one persisted candidate-comment session before a deterministic puppet journey. */
    public void resetCandidateCommentSession(CandidateCommentSessionPuppetAction.SessionRole role) {
        add(new CandidateCommentSessionPuppetAction(
                CandidateCommentSessionPuppetAction.Operation.RESET,
                Objects.requireNonNull(role, "role")
        ));
    }

    /** Reloads one candidate-comment session from its production V2 store. */
    public void reloadCandidateCommentSession(CandidateCommentSessionPuppetAction.SessionRole role) {
        add(new CandidateCommentSessionPuppetAction(
                CandidateCommentSessionPuppetAction.Operation.RELOAD,
                Objects.requireNonNull(role, "role")
        ));
    }

    /** Validates and artifacts one natural candidate-comment journey checkpoint. */
    public void assertCandidateCommentReview(
            AssertCandidateCommentReviewPuppetAction.Stage stage,
            String artifactName
    ) {
        add(new AssertCandidateCommentReviewPuppetAction(
                Objects.requireNonNull(stage, "stage"),
                Objects.requireNonNull(artifactName, "artifactName")
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Selects a retained trajectory through the visible constrained command palette. */
    public void clickCandidateCommentRouteChoice(String commentId) {
        add(new ClickCandidateCommentRouteChoicePuppetAction(Objects.requireNonNull(commentId, "commentId")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Proves one persisted candidate target reopened its exact immutable route frame. */
    public void assertCandidateCommentNavigation(
            CandidateCommentSessionPuppetAction.SessionRole role,
            String commentId,
            String artifactName
    ) {
        add(new AssertCandidateCommentNavigationPuppetAction(
                Objects.requireNonNull(role, "role"),
                Objects.requireNonNull(commentId, "commentId"),
                Objects.requireNonNull(artifactName, "artifactName")
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Restores default cursors/mode/dispositions for the focused persisted comparison. */
    public void resetRouteComparisonSession() {
        add(new RouteComparisonSessionPuppetAction(RouteComparisonSessionPuppetAction.Operation.RESET));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Evicts and reloads the focused comparison from its production store. */
    public void reloadRouteComparisonSession() {
        add(new RouteComparisonSessionPuppetAction(RouteComparisonSessionPuppetAction.Operation.RELOAD));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Validates and artifacts one natural retained-route comparison checkpoint. */
    public void assertRouteComparison(
            AssertRouteComparisonPuppetAction.Stage stage,
            String artifactName
    ) {
        add(new AssertRouteComparisonPuppetAction(
                Objects.requireNonNull(stage, "stage"),
                Objects.requireNonNull(artifactName, "artifactName")
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void assertFormerTerminalStartButtonRoutesToTerminal() {
        add(new AssertFormerTerminalStartButtonPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void assertActionChoice(List<String> expectedCommands) {
        add(new AssertActionChoicePuppetAction(expectedCommands));
    }

    public void clickActionChoice(String command) {
        add(new ClickActionChoicePuppetAction(Objects.requireNonNull(command, "command")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void assertWorkspaceState(
            int totalEntries,
            int visibleEntries,
            int focusedSlotEntries,
            String expectedFocusedNarration,
            int expectedFocusedScale
    ) {
        add(new AssertWorkspaceStatePuppetAction(
                totalEntries,
                visibleEntries,
                focusedSlotEntries,
                expectedFocusedNarration,
                expectedFocusedScale
        ));
    }

    public void assertWorkspacePanelExtentComparison(
            int firstPanelIndex,
            int secondPanelIndex,
            SFMWorkspaceAxis axis,
            int expectedComparison
    ) {
        add(new AssertWorkspacePanelExtentPuppetAction(
                firstPanelIndex,
                secondPanelIndex,
                axis,
                expectedComparison));
    }

    public void assertWorkspacePanelInstancesDistinct(int firstPanelIndex, int secondPanelIndex) {
        add(new AssertWorkspacePanelInstancesDistinctPuppetAction(firstPanelIndex, secondPanelIndex));
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

    public void startRustTerminalThroughUi() {
        add(new StartRustTerminalThroughUiPuppetAction());
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

    /** Selects one exact visible line and retains zero-raster-work evidence. */
    public void selectTerminalText(
            String artifactName,
            String rendererId,
            String transportId,
            String exactText,
            boolean reverse
    ) {
        add(new SelectTerminalTextPuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(rendererId, "rendererId"),
                Objects.requireNonNull(transportId, "transportId"),
                Objects.requireNonNull(exactText, "exactText"),
                reverse));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Copies and clears the current selection through Ctrl+C or secondary click. */
    public void copyTerminalSelection(
            String artifactName,
            String rendererId,
            String transportId,
            String expectedText,
            boolean rightClick
    ) {
        add(new CopyTerminalSelectionPuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(rendererId, "rendererId"),
                Objects.requireNonNull(transportId, "transportId"),
                Objects.requireNonNull(expectedText, "expectedText"),
                rightClick));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void pasteTerminalTextByRightClick(String text) {
        add(new RightClickPasteTerminalTextPuppetAction(Objects.requireNonNull(text, "text")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void assertTerminalPasteWarning(String artifactName, String canonicalPreview) {
        add(new AssertTerminalPasteWarningPuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(canonicalPreview, "canonicalPreview")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void assertTerminalPrivateContent(
            String artifactName,
            List<String> requiredExactLines,
            List<String> forbiddenExactLines
    ) {
        add(new AssertTerminalPrivateContentPuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                List.copyOf(requiredExactLines),
                List.copyOf(forbiddenExactLines)));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void assertTerminalSelectionAbsent(
            String artifactName,
            String rendererId,
            String transportId,
            boolean childMouseForwarded
    ) {
        add(new AssertTerminalSelectionAbsentPuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(rendererId, "rendererId"),
                Objects.requireNonNull(transportId, "transportId"),
                childMouseForwarded));
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

    /** Asserts the push transport's zero-polling and bounded-queue evidence. */
    public void assertTerminalPushEvidence(String artifactName, boolean reconnectExpected) {
        add(new AssertTerminalPushEvidencePuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                reconnectExpected
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void assertTerminalPropertiesEvidence(
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
    ) {
        add(new AssertTerminalPropertiesEvidencePuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(expectedRendererId, "expectedRendererId"),
                Objects.requireNonNull(expectedTransportId, "expectedTransportId"),
                Objects.requireNonNull(expectedSurfaceMode, "expectedSurfaceMode"),
                Objects.requireNonNull(expectedFontMode, "expectedFontMode"),
                Objects.requireNonNull(expectedCellsMode, "expectedCellsMode"),
                expectedConfiguredGuiScale,
                expectedPanelGuiScaleOverride,
                expectedRejectionCode,
                retainedFrameExpected));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void clickTerminalPropertiesControl(String operation) {
        add(new ClickTerminalPropertiesControlPuppetAction(
                Objects.requireNonNull(operation, "operation")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    /** Samples one pushed frame/content state and records its complete presentation identity. */
    public void assertTerminalPresentationEvidence(
            String artifactName,
            String rendererId,
            String transportId,
            String requiredContentLine,
            boolean freshPresentationExpected
    ) {
        assertTerminalPresentationEvidence(
                artifactName, rendererId, transportId, requiredContentLine,
                false, freshPresentationExpected, false);
    }

    public void assertTerminalPresentationEvidence(
            String artifactName,
            String rendererId,
            String transportId,
            String requiredContentLine,
            boolean initialDefaultExpected,
            boolean freshPresentationExpected,
            boolean panelResizeExpected
    ) {
        add(new AssertTerminalPresentationEvidencePuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(rendererId, "rendererId"),
                Objects.requireNonNull(transportId, "transportId"),
                requiredContentLine,
                initialDefaultExpected,
                freshPresentationExpected,
                panelResizeExpected
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void selectTerminalRendererThroughUi(String rendererId) {
        add(new SelectTerminalPresentationUiPuppetAction(true,
                Objects.requireNonNull(rendererId, "rendererId")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void selectTerminalTransportThroughUi(String transportId) {
        add(new SelectTerminalPresentationUiPuppetAction(false,
                Objects.requireNonNull(transportId, "transportId")));
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

    public void waitForCommandPaletteSuggestions(String input, List<String> expectedSuggestions) {
        add(new WaitForCommandPaletteSuggestionsPuppetAction(input, expectedSuggestions));
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

    public void openWorkspaceDividerFixture(int paneCount) {
        add(new OpenWorkspaceDividerFixturePuppetAction(paneCount));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }

    public void hoverWorkspaceDividerIntersection() {
        add(new MoveWorkspaceDividerPointerPuppetAction(
                MoveWorkspaceDividerPointerPuppetAction.Operation.HOVER, 0, 0));
    }

    public void dragWorkspaceDividerIntersection(int deltaX, int deltaY) {
        add(new MoveWorkspaceDividerPointerPuppetAction(
                MoveWorkspaceDividerPointerPuppetAction.Operation.PRESS_AND_DRAG,
                deltaX,
                deltaY));
    }

    public void releaseWorkspaceDividerIntersection() {
        add(new MoveWorkspaceDividerPointerPuppetAction(
                MoveWorkspaceDividerPointerPuppetAction.Operation.RELEASE, 0, 0));
    }

    public void writeWorkspaceDividerEvidence(
            String artifactName,
            String stage,
            int paneCount
    ) {
        add(new WriteWorkspaceDividerEvidencePuppetAction(artifactName, stage, paneCount));
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

    public void assertFileExplorerPreviewFocus(boolean previewFocused) {
        add(new AssertFileExplorerPreviewFocusPuppetAction(previewFocused));
    }

    /**
     * Captures the currently rendered client frame with a numbered, styled caption.
     */
    public void capture(String captureName, Component caption) {
        add(new CapturePuppetAction(captureName, Objects.requireNonNull(caption, "caption").copy()));
    }

    /** Captures the rendered client frame while retaining HUD and Forge overlays. */
    public void captureWithHud(String captureName, Component caption) {
        add(new CaptureWithHudPuppetAction(
                captureName,
                Objects.requireNonNull(caption, "caption").copy()
        ));
    }

    /** Stages a bounded UTF-8 text artifact for publication beside this puppet's screenshots. */
    public void writeUtf8Artifact(String artifactName, String contents) {
        writeArtifact(artifactName, SFMGamePuppetArtifactFormat.UTF8, contents);
    }

    /** Stages a bounded, syntactically validated JSON artifact beside this puppet's screenshots. */
    public void writeJsonArtifact(String artifactName, String contents) {
        writeArtifact(artifactName, SFMGamePuppetArtifactFormat.JSON, contents);
    }

    public void writeArtifact(
            String artifactName,
            SFMGamePuppetArtifactFormat format,
            String contents
    ) {
        add(new WriteGamePuppetArtifactPuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(format, "format"),
                Objects.requireNonNull(contents, "contents")
        ));
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
