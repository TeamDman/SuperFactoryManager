package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.client.screen.ManagerScreen;
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.client_manager_gui %}
import ca.teamdman.sfm.client.screen.ClientManagerScreen;
{% endif %}
{% if features.file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
{% endif %}
{% when "1.19.4" %}
{% if features.file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
{% endif %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSource;
{% endif %}
{% endcase %}
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels or features.workspace_dividers %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceAxis;
{% endif %}
{% if features.screen_diagnostics and features.workspace_panels %}
import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMSizeDisplayWorkspace;
{% endif %}
{% if features.touch_display %}
import ca.teamdman.sfm.common.block.TouchDisplaySurface;
{% endif %}
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMSizeDisplayWorkspace;
{% endif %}
{% endcase %}
import ca.teamdman.sfm.gametest.puppet.action.*;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import java.util.concurrent.atomic.AtomicReference;
{% if features.file_explorer or features.registry_explorer or features.java_symbols or features.release_review or features.spatial_coverage or features.context_actions %}
import ca.teamdman.sfm.client.explorer.SFMPath;
{% endif %}
import java.nio.file.Path;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}

/**
 * Declarative action builder for one annotated game puppet definition.
 */
public final class SFMGamePuppetHelper {
    public static final int SCREEN_TIMEOUT_TICKS = 200;
    public static final int RENDER_SETTLE_TICKS = 6;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /** Ten 20 Hz client ticks make palette automation observable for 500 ms. */
    public static final int COMMAND_PALETTE_OBSERVATION_TICKS = 10;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    private final List<SFMPuppetAction> actions = new ArrayList<>();
    private int currentAction = 0;

    public void createFreshFlatWorld() {
        add(new CreateFreshWorldPuppetAction());
    }

{% case minecraft_version %}
{% when "1.19.2" %}
    /** Leaves the puppet-owned save and loads it again through vanilla world opening. */
    public void rejoinCurrentWorld() {
        add(new RejoinCurrentWorldPuppetAction());
    }

    /** Recharges real Mekanism cubes after rejoin and compares direct versus tunnelled delivery. */
    public void assertMekanismTunnelEnergyAfterRejoin() {
        add(new AssertMekanismTunnelEnergyAfterRejoinPuppetAction());
    }

    /** Publishes the current integrated world to LAN through the real server API. */
    public void publishIntegratedServerToLan() {
        add(new PublishIntegratedServerToLanPuppetAction());
    }

    /** Starts a GameTest without waiting for its terminal condition. */
    public void startGameTest(String testName) {
        requireGameTestName(testName);
        add(new StartGameTestPuppetAction(testName));
    }

    /** Starts a puppet-owned fixture without enrolling it in ordinary GameTest discovery. */
    public void startGameTest(SFMGameTestDefinition testDefinition) {
        add(new StartGameTestDefinitionPuppetAction(Objects.requireNonNull(testDefinition, "testDefinition")));
    }

    /** Waits for the GameTest previously started by this puppet. */
    public void waitForGameTest(String testName) {
        requireGameTestName(testName);
        add(new WaitForGameTestPuppetAction(testName));
    }

    /** Waits until a fixture request has traversed the production server-to-client packet path. */
{% if features.packet_values and features.packet_transport_private %}
    public void waitForPacketObservation(String fixtureId) {
        add(new WaitForPacketFixtureObservationPuppetAction(fixtureId));
    }
{% endif %}

    /** Runs the Slice D deterministic ACK and duplicate-response worker in the real terminal. */
{% if features.packet_terminal_worker_tests and features.packet_computation and features.packet_transport_private and features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_repl_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void invokePacketLanguageWorkerThroughTerminal() {
        add(new InvokePacketLanguageWorkerThroughTerminalPuppetAction());
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}

{% when "1.19.4" %}
    /** Publishes the current integrated world to LAN through the real server API. */
    public void publishIntegratedServerToLan() {
        add(new PublishIntegratedServerToLanPuppetAction());
    }

    /** Starts a GameTest without waiting for its terminal condition. */
    public void startGameTest(String testName) {
        requireGameTestName(testName);
        add(new StartGameTestPuppetAction(testName));
    }

    /** Starts a puppet-owned fixture without enrolling it in ordinary GameTest discovery. */
    public void startGameTest(SFMGameTestDefinition testDefinition) {
        add(new StartGameTestDefinitionPuppetAction(Objects.requireNonNull(testDefinition, "testDefinition")));
    }

    /** Waits for the GameTest previously started by this puppet. */
    public void waitForGameTest(String testName) {
        requireGameTestName(testName);
        add(new WaitForGameTestPuppetAction(testName));
    }

    /** Waits until a fixture request has traversed the production server-to-client packet path. */
{% if features.packet_values and features.packet_transport_private %}
    public void waitForPacketObservation(String fixtureId) {
        add(new WaitForPacketFixtureObservationPuppetAction(fixtureId));
    }
{% endif %}

    /** Runs the Slice D deterministic ACK and duplicate-response worker in the real terminal. */
{% if features.packet_terminal_worker_tests and features.packet_computation and features.packet_transport_private and features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_repl_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void invokePacketLanguageWorkerThroughTerminal() {
        add(new InvokePacketLanguageWorkerThroughTerminalPuppetAction());
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    public void runGameTest(String testName) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        requireGameTestName(testName);
        add(new RunGameTestPuppetAction(testName));
    }

    private static void requireGameTestName(String testName) {
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
        if (testName == null || testName.isBlank()) {
            throw new IllegalArgumentException("Game puppet GameTest name must not be blank");
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        add(new RunGameTestPuppetAction(testName));
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2" %}
    /** Opens and captures the dedicated Client Manager screen through the file puppet path. */
{% if features.client_manager_gui %}
    public void captureClientManagerAt(String captureName, BlockPos localTarget, Component caption) {
        captureBlockScreen(captureName, localTarget, ClientManagerScreen.class, true, caption);
    }
{% endif %}

{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /** Launches {@code sfm.exe invoke} and observes its size-display panel result. */
{% if features.screen_diagnostics and features.workspace_panels and features.client_control_cli and features.workspace_panel_actions %}
    public void invokeExternalCliSizeDisplay() {
        add(new InvokeExternalCliSizeDisplayPuppetAction());
    }
{% endif %}

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    /**
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
     * Runs the complete real-process lazy-explorer control journey without
     * requiring an operator or a visible companion terminal.
     */
{% if features.file_explorer and features.workspace_panels and features.explorer_compaction %}
{% if features.file_explorer and features.workspace_panels and features.explorer_compaction and features.registry_explorer and features.client_control_cli %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}
    public void invokeExternalCliLazyExplorer() {
        add(new InvokeExternalCliLazyExplorerPuppetAction());
    }
{% endif %}
{% endif %}
{% endif %}

    /** Waits until one resolver row is materialized and optionally selects/focuses it. */
{% if features.file_explorer and features.workspace_panels %}
{% if features.file_explorer and features.workspace_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_toast_path_actions %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_notifications %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement or features.screen_diagnostics %}
    public void waitForExplorerPath(SFMPath path, boolean select) {
        add(new WaitForExplorerPathPuppetAction(Objects.requireNonNull(path, "path"), select));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Runs and records the self-contained X-8b focus/filter/wheel interaction journey. */
{% if features.file_explorer and features.workspace_panels %}
{% if features.file_explorer and features.workspace_panels and features.registry_explorer and features.client_theme and features.explorer_search and features.client_actions and features.workspace_widget_hosts %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_toast_path_actions %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_notifications %}
    public void exerciseExplorerInteractionFidelity(SFMPath javaPath) {
        add(new ExerciseExplorerInteractionFidelityPuppetAction(
                Objects.requireNonNull(javaPath, "javaPath")
        ));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Runs the mouse-only RCS-UX1..4 release-review Explorer journey. */
{% if features.command_palette and features.release_review and features.workspace_panels and features.file_explorer %}
{% if features.command_palette and features.release_review and features.workspace_panels and features.file_explorer and features.context_actions and features.editor_documents and features.editor_document_panels and features.canvas_text_editor and features.canvas_pointer_defaults and features.workspace_panel_entry_controls and features.workspace_stack_controls and features.workspace_panel_actions and features.workspace_lifecycle and features.typed_command_palette and features.workspace_panel_metadata and features.workspace_dividers %}
    public void exerciseReleaseReviewExplorerUx(Path reviewFile) {
        add(new ExerciseReleaseReviewExplorerUxPuppetAction(
                Objects.requireNonNull(reviewFile, "reviewFile")
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}

    /** Records the final real-source identity, layout, focus, and no-write witness. */
{% if features.editor_documents and features.editor_document_panels and features.file_explorer and features.workspace_panels %}
{% if features.editor_documents and features.editor_document_panels and features.file_explorer and features.workspace_panels and features.workspace_panel_reopening %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement or features.screen_diagnostics %}
    public void assertAddressedSfmJava(Path root, Path file) {
        add(new AssertAddressedSfmJavaPuppetAction(
                Objects.requireNonNull(root, "root"),
                Objects.requireNonNull(file, "file")
        ));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Waits for styled Java publication and records source-free explorer/editor evidence. */
{% if features.syntax_languages and features.editor_documents and features.canvas_text_editor and features.workspace_panels %}
{% if features.syntax_languages and features.editor_documents and features.canvas_text_editor and features.workspace_panels and features.file_explorer and features.editor_document_panels %}
{% if features.client_actions or features.client_theme %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement or features.screen_diagnostics %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_toast_path_actions %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_notifications %}
    public void assertSfmJavaSyntaxPresentation(Path root, Path file, String artifactName) {
        add(new AssertSfmJavaSyntaxPresentationPuppetAction(
                Objects.requireNonNull(root, "root"),
                Objects.requireNonNull(file, "file"),
                Objects.requireNonNull(artifactName, "artifactName")
        ));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Repeats the exact immutable Java document and requires cache/session reuse. */
{% if features.syntax_languages and features.editor_documents and features.canvas_text_editor and features.workspace_panels %}
{% if features.syntax_languages and features.editor_documents and features.canvas_text_editor and features.workspace_panels and features.editor_document_panels and features.workspace_panel_metadata and features.workspace_stack_controls and features.workspace_panel_reopening and features.workspace_panel_actions %}
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
{% endif %}
{% endif %}

    /** Positions an exact source occurrence, drives F12, and requires one exact SFM-owned target. */
{% if features.java_symbols and features.editor_documents and features.workspace_panels %}
{% if features.java_symbols and features.editor_documents and features.workspace_panels and features.editor_document_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}
{% if features.workspace_notifications %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_toast_path_actions %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_notifications %}
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
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Proves that a naturally submitted F12 result cannot navigate after its editor bytes change. */
{% if features.java_symbols and features.editor_documents and features.workspace_panels %}
{% if features.java_symbols and features.editor_documents and features.workspace_panels and features.editor_document_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}
{% if features.workspace_notifications and features.canvas_text_editor %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_toast_path_actions %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_notifications %}
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
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Copies one immutable contextual symbol report and artifacts its exact clipboard bytes. */
{% if features.command_palette and features.java_symbols and features.editor_documents and features.workspace_panels %}
{% if features.command_palette and features.java_symbols and features.editor_documents and features.workspace_panels and features.editor_document_panels and features.workspace_notifications %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_toast_path_actions %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_notifications %}
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
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Repeats one exact symbol lookup and requires reuse of its existing addressed target panel. */
{% if features.java_symbols and features.editor_documents and features.workspace_panels %}
{% if features.java_symbols and features.editor_documents and features.workspace_panels and features.editor_document_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_toast_path_actions %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_notifications %}
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
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Drives a real two-candidate definition choice and records the selected exact target. */
{% if features.java_symbols and features.editor_documents and features.workspace_panels %}
{% if features.java_symbols and features.editor_documents and features.workspace_panels and features.editor_document_panels and features.workspace_panel_metadata and features.workspace_stack_controls and features.workspace_panel_reopening and features.workspace_panel_actions %}
{% if features.canvas_text_editor %}
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
{% endif %}
{% endif %}
{% endif %}

    /** Runs a dependency jump only when the pinned index uniquely resolves its preflight selector. */
{% if features.java_symbols and features.editor_documents and features.workspace_panels %}
{% if features.java_symbols and features.editor_documents and features.workspace_panels and features.editor_document_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}
{% if features.workspace_notifications %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_toast_path_actions %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_panel_reopening or features.workspace_notifications %}
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
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Enqueues the integrated C-11 source-navigation journey as one stateful live proof. */
{% if features.java_symbols and features.editor_documents and features.workspace_panels and features.spatial_coverage and features.context_actions and features.command_palette and features.file_explorer %}
{% if features.java_symbols and features.editor_documents and features.workspace_panels and features.spatial_coverage and features.context_actions and features.command_palette and features.file_explorer and features.explorer_navigation and features.canvas_text_editor and features.editor_document_panels and features.client_actions and features.workspace_panel_actions %}
{% if features.workspace_stack_controls or features.workspace_dividers %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement or features.screen_diagnostics %}
{% if features.workspace_panel_measurement and features.workspace_notifications %}
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
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Exercises copy, pin/resume, exact dismiss, and later-message survival through the live toast UI. */
{% if features.workspace_toast_actions and features.workspace_panels and features.command_palette %}
{% if features.workspace_toast_actions and features.workspace_panels and features.command_palette and features.workspace_notifications %}
    public void exerciseActionableToast(String artifactName) {
        add(new ExerciseActionableToastPuppetAction(Objects.requireNonNull(artifactName, "artifactName")));
    }
{% endif %}
{% endif %}
{% if features.client_theme and features.theme_preview_rules and features.item_picker and features.file_explorer and features.command_palette and features.workspace_panels %}
{% if features.client_theme and features.theme_preview_rules and features.item_picker and features.file_explorer and features.command_palette and features.workspace_panels and features.icon_rules and features.workspace_panel_actions %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement or features.screen_diagnostics %}
    public void exerciseItemstackPreviewRules(boolean resume) {
        add(new ItemstackPreviewRulesPuppetAction(resume));
    }
{% endif %}
{% endif %}
{% endif %}

    /** Runs spatial coverage through the real short-lived external {@code sfm.exe} remoting client. */
{% if features.spatial_coverage and features.workspace_panels %}
{% if features.spatial_coverage and features.workspace_panels and features.client_control_cli and features.editor_documents and features.editor_document_panels and features.java_symbols %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}
    public void invokeExternalCliSpatialCoverage(Path sourceFile, Path artifactDirectory, String artifactName) {
        add(new InvokeExternalCliSpatialCoveragePuppetAction(
                Objects.requireNonNull(sourceFile, "sourceFile"),
                Objects.requireNonNull(artifactDirectory, "artifactDirectory"),
                Objects.requireNonNull(artifactName, "artifactName")
        ));
    }
{% endif %}
{% endif %}
{% endif %}

    /**
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
     * Opens the contextual client command palette from the current screen.
     */
{% if features.command_palette %}
    public void openCommandPalette() {
        add(new OpenCommandPalettePuppetAction());
    }
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.keybinding_settings and features.keyboard_profiles and features.command_palette %}

    public void showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View view) {
        add(new ShowDynamicKeyBindingPuppetAction(view));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.keyboard_profiles and features.command_palette %}

    public void showDynamicKeyBindings(ShowDynamicKeyBindingPuppetAction.View view) {
        add(new ShowDynamicKeyBindingPuppetAction(view));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endcase %}
{% if features.command_palette and features.client_theme %}

    public void showRuntimeTheme(ShowRuntimeThemePuppetAction.View view) {
        add(new ShowRuntimeThemePuppetAction(view));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_theme and features.theme_preview_rules and features.item_picker and features.workspace_panels %}
    public void showThemeSettings(ShowThemeSettingsPuppetAction.View view) {
        add(new ShowThemeSettingsPuppetAction(view));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% if features.release_review and features.workspace_panels and features.file_explorer %}

{% if features.release_review and features.workspace_panels and features.file_explorer %}
    public void assertReviewExplorer(
            AssertReviewExplorerPuppetAction.Projection projection
    ) {
        add(new AssertReviewExplorerPuppetAction(projection));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}

    /** Exercises one natural checkpoint in the portable release-review journey. */
{% if features.command_palette and features.release_review and features.workspace_panels and features.file_explorer %}
{% if features.command_palette and features.release_review and features.workspace_panels and features.file_explorer and features.context_actions and features.editor_documents and features.editor_document_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement or features.screen_diagnostics %}
    public void releaseReviewJourney(ReleaseReviewJourneyPuppetAction.Operation operation) {
        add(new ReleaseReviewJourneyPuppetAction(Objects.requireNonNull(operation, "operation")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Exercises one checkpoint in the two-process real release-review journey. */
{% if features.command_palette and features.release_review and features.workspace_panels and features.file_explorer %}
{% if features.command_palette and features.release_review and features.workspace_panels and features.file_explorer and features.java_symbols and features.editor_documents and features.editor_document_panels and features.context_actions %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void realReleaseReviewJourney(RealReleaseReviewJourneyPuppetAction.Operation operation) {
        add(new RealReleaseReviewJourneyPuppetAction(Objects.requireNonNull(operation, "operation")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.command_palette and features.release_review and features.workspace_panels and features.file_explorer %}

{% if features.command_palette and features.release_review and features.workspace_panels and features.file_explorer and features.workspace_notifications and features.screen_diagnostics %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}
    public void exploreReviewInteractively() {
        add(new ca.teamdman.sfm.gametest.puppet.action.ExploreReviewInteractivelyPuppetAction());
    }
{% endif %}
{% endif %}
{% endif %}

    /** Wait for bounded file requests to inspect and press a real in-world Touch Display fixture. */
{% if features.command_palette and features.touch_display and features.client_manager %}
{% if features.command_palette and features.touch_display and features.client_manager and features.client_frame_render and features.workspace_panel_actions and features.client_screen_actions and features.workspace_panels and features.client_frame_language and features.client_program_consent and features.client_actions and features.confirmation_review_callbacks and features.workspace_panel_lookup and features.workspace_widget_hosts and features.workspace_panel_measurement %}
    public void exploreTouchDisplayInteractively(AtomicReference<TouchDisplaySurface.UV> requestedTouch) {
        add(new ExploreTouchDisplayInteractivelyPuppetAction(requestedTouch));
    }
{% endif %}
{% endif %}

    /** Separate opt-in consent UI journey; never part of the ambient GameTest suite. */
{% if features.command_palette and features.touch_display and features.client_manager and features.client_program_consent %}
{% if features.command_palette and features.touch_display and features.client_manager and features.client_frame_render and features.workspace_panel_actions and features.client_screen_actions and features.workspace_panels and features.client_frame_language and features.client_program_consent and features.client_actions and features.confirmation_review_callbacks and features.workspace_panel_lookup and features.workspace_widget_hosts and features.workspace_panel_measurement %}
    public void exploreClientProgramConsentInteractively(AtomicReference<TouchDisplaySurface.UV> requestedTouch) {
        add(new ExploreTouchDisplayInteractivelyPuppetAction(requestedTouch, true));
    }
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.client_theme and features.item_picker and features.workspace_panels %}
    public void showThemeSettings(ShowThemeSettingsPuppetAction.View view) {
        add(new ShowThemeSettingsPuppetAction(view));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /** Real authoring controls and transport, with synthetic keys confined to this puppet's run directory. */
{% if features.client_program_signing and features.touch_display and features.client_manager and features.command_palette %}
{% if features.client_program_signing and features.touch_display and features.client_manager and features.command_palette and features.client_frame_render and features.workspace_panel_actions and features.client_screen_actions and features.workspace_panels and features.client_frame_language and features.client_program_consent and features.client_actions and features.confirmation_review_callbacks and features.workspace_panel_lookup and features.workspace_widget_hosts and features.workspace_panel_measurement %}
    public void exploreClientProgramSigningInteractively(java.util.concurrent.atomic.AtomicBoolean proofComplete) {
        add(new ExploreClientProgramSigningPuppetAction(proofComplete));
    }
{% endif %}
{% endif %}
{% if features.command_palette and features.release_review and features.workspace_panels and features.file_explorer %}

{% if features.command_palette and features.release_review and features.workspace_panels and features.file_explorer and features.editor_documents and features.editor_document_panels and features.typed_command_palette and features.workspace_panel_actions and features.context_actions and features.workspace_dividers %}
{% if features.workspace_panel_metadata or features.workspace_widget_hosts or features.workspace_panel_measurement or features.screen_diagnostics %}
    public void exactReleaseReviewJourney(boolean resume) {
        add(new ca.teamdman.sfm.gametest.puppet.action.ExactReleaseReviewJourneyPuppetAction(resume));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.legacy_source_review_ui and features.command_palette %}
    public void applySourceReviewFixtureCommand(String command) {
        add(new ApplySourceReviewFixturePuppetAction(command));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% if features.legacy_comment_review_ui and features.command_palette %}

    public void applyReviewCommentFixtureCommand(String command) {
        add(new ApplyReviewCommentFixturePuppetAction(command));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endcase %}

    /**
     * Executes a command through the visible palette input and waits for its
     * rendered output to settle.
     */
{% if features.command_palette %}
    public void executeCommandPalette(String command) {
        add(new ExecuteCommandPalettePuppetAction(command));
    }
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}

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

    /** Captures one verified live progressive-completion frontier. */
{% if features.typed_command_palette %}
{% if features.typed_command_palette and features.command_history and features.single_line_input and features.workspace_panel_actions and features.editor_document_panels and features.canvas_text_editor and features.trajectory_panels and features.client_overlay_scenes and features.echo_action %}
    public void assertProgressivePaletteCompletion(
            AssertProgressivePaletteCompletionPuppetAction.Stage stage,
            String artifactName
    ) {
        add(new AssertProgressivePaletteCompletionPuppetAction(stage, artifactName));
    }
{% endif %}
{% endif %}

    /** Captures one verified ordinary-document history/canvas milestone. */
{% if features.document_history and features.editor_documents %}
{% if features.document_history and features.editor_documents and features.workspace_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void assertOrdinaryDocumentHistory(
            AssertOrdinaryDocumentHistoryPuppetAction.Stage stage,
            String artifactName
    ) {
        add(new AssertOrdinaryDocumentHistoryPuppetAction(stage, artifactName));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}

    /** Chooses a dynamic retained redo branch by its eventual exact document bytes. */
{% if features.document_history and features.editor_documents %}
{% if features.document_history and features.editor_documents and features.workspace_panels %}
    public void chooseOrdinaryDocumentHistoryBranch(String expectedDescendantText) {
        add(new ChooseOrdinaryDocumentHistoryBranchPuppetAction(expectedDescendantText));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}

    /** Uses the real workspace pointer router to pan and zoom the focused history canvas. */
{% if features.document_history and features.editor_documents and features.canvas_text_editor and features.workspace_panels %}
{% if features.document_history and features.editor_documents and features.canvas_text_editor and features.workspace_panels and features.workspace_panel_measurement %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void exerciseOrdinaryDocumentHistoryCanvas(String artifactName) {
        add(new ExerciseOrdinaryDocumentHistoryCanvasPuppetAction(artifactName));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}

    /** Validates and artifacts one natural temporal-numbering journey checkpoint. */
{% if features.trajectory_panels and features.workspace_panels %}
{% if features.trajectory_panels and features.workspace_panels %}
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
{% endif %}
{% endif %}

    /** Validates and artifacts one natural exact-replay/semantic-rebase checkpoint. */
{% if features.trajectory_panels %}
{% if features.trajectory_panels and features.editor_document_panels and features.workspace_panel_lookup %}
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
{% endif %}
{% endif %}

    /** Validates and artifacts one natural whole-workspace counterfactual checkpoint. */
{% if features.workspace_counterfactuals %}
{% if features.workspace_counterfactuals %}
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
{% endif %}
{% endif %}

    /** Runs X6's complete in-world History Graph overlay journey and writes its durable evidence. */
{% if features.trajectory_panels and features.workspace_counterfactuals and features.client_overlay_scenes and features.client_actions %}
{% if features.trajectory_panels and features.workspace_counterfactuals and features.client_overlay_scenes and features.client_actions and features.client_control_cli %}
    public void exerciseHistoryGraphOverlay() {
        add(new ExerciseHistoryOverlayPuppetAction());
    }
{% endif %}
{% endif %}

    /** Chooses exact source/source replay from its visible constrained action palette. */
{% if features.trajectory_panels and features.command_palette %}
{% if features.trajectory_panels and features.command_palette and features.editor_document_panels and features.workspace_panel_lookup %}
    public void clickTemporalExactReplayChoice() {
        add(new ClickTemporalExactReplayChoicePuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}

    /** Resolves retained state ids and types an ordinary selector-explicit replay action. */
{% if features.trajectory_panels and features.command_palette %}
{% if features.trajectory_panels and features.command_palette and features.editor_document_panels and features.workspace_panel_lookup %}
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
{% endif %}
{% endif %}
{% if features.command_palette %}

    public void exerciseCommandPaletteViewport() {
        add(new ExerciseCommandPaletteViewportPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}

    /** Focuses the real narrated Cancel widget without activating it. */
{% if features.command_palette %}
    public void focusCommandPaletteCancel() {
        add(new FocusCommandPaletteCancelPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}

    /** Proves the B-5a right-click, pointer-Cancel, reopen, and real-action journey. */
{% if features.context_actions and features.command_palette and features.editor_documents and features.workspace_panels %}
{% if features.context_actions and features.command_palette and features.editor_documents and features.workspace_panels and features.java_symbols and features.editor_document_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions or features.workspace_stack_controls %}
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
{% endif %}
{% endif %}
{% endif %}

    /** Validates and artifacts one read-only candidate-history scrub checkpoint. */
{% if features.trajectory_panels and features.timeline_panels and features.workspace_panels %}
{% if features.trajectory_panels and features.timeline_panels and features.workspace_panels and features.timeline_wrapper_transparency %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void assertCandidateHistory(
            AssertCandidateHistoryPuppetAction.Stage stage,
            String artifactName
    ) {
        add(new AssertCandidateHistoryPuppetAction(
                Objects.requireNonNull(stage, "stage"),
                Objects.requireNonNull(artifactName, "artifactName")
        ));
    }
{% endif %}
{% endif %}
{% endif %}

    /** Installs the deterministic non-materialized candidate-status route used by X1's visual proof. */
{% if features.trajectory_panels and features.timeline_panels and features.workspace_panels %}
{% if features.trajectory_panels and features.timeline_panels and features.workspace_panels and features.timeline_wrapper_transparency %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void registerCandidateHistoryStatusFixture() {
        add(CandidateHistoryStatusFixturePuppetAction.register());
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.trajectory_panels and features.timeline_panels and features.workspace_panels %}

{% if features.trajectory_panels and features.timeline_panels and features.workspace_panels and features.timeline_wrapper_transparency %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void assertCandidateHistoryStatus(
            CandidateHistoryStatusFixturePuppetAction.Stage stage,
            String artifactName
    ) {
        add(CandidateHistoryStatusFixturePuppetAction.assertStage(stage, artifactName));
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.trajectory_panels and features.timeline_panels and features.workspace_panels %}

{% if features.trajectory_panels and features.timeline_panels and features.workspace_panels and features.timeline_wrapper_transparency %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void unregisterCandidateHistoryStatusFixture() {
        add(CandidateHistoryStatusFixturePuppetAction.unregister());
    }
{% endif %}
{% endif %}
{% endif %}

    /** Resets one persisted candidate-comment session before a deterministic puppet journey. */
{% if features.review_sessions and features.trajectory_panels and features.timeline_panels and features.workspace_panels %}
{% if features.review_sessions and features.trajectory_panels and features.timeline_panels and features.workspace_panels and features.timeline_wrapper_transparency %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void resetCandidateCommentSession(CandidateCommentSessionPuppetAction.SessionRole role) {
        add(new CandidateCommentSessionPuppetAction(
                CandidateCommentSessionPuppetAction.Operation.RESET,
                Objects.requireNonNull(role, "role")
        ));
    }
{% endif %}
{% endif %}
{% endif %}

    /** Reloads one candidate-comment session from its production V2 store. */
{% if features.review_sessions and features.trajectory_panels and features.timeline_panels and features.workspace_panels %}
{% if features.review_sessions and features.trajectory_panels and features.timeline_panels and features.workspace_panels and features.timeline_wrapper_transparency %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void reloadCandidateCommentSession(CandidateCommentSessionPuppetAction.SessionRole role) {
        add(new CandidateCommentSessionPuppetAction(
                CandidateCommentSessionPuppetAction.Operation.RELOAD,
                Objects.requireNonNull(role, "role")
        ));
    }
{% endif %}
{% endif %}
{% endif %}

    /** Validates and artifacts one natural candidate-comment journey checkpoint. */
{% if features.review_sessions and features.trajectory_panels and features.timeline_panels and features.workspace_panels %}
{% if features.review_sessions and features.trajectory_panels and features.timeline_panels and features.workspace_panels and features.timeline_wrapper_transparency %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
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
{% endif %}
{% endif %}
{% endif %}

    /** Selects a retained trajectory through the visible constrained command palette. */
{% if features.review_sessions and features.client_actions and features.command_palette %}
{% if features.review_sessions and features.client_actions and features.command_palette and features.trajectory_panels %}
    public void clickCandidateCommentRouteChoice(String commentId) {
        add(new ClickCandidateCommentRouteChoicePuppetAction(Objects.requireNonNull(commentId, "commentId")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}

    /** Proves one persisted candidate target reopened its exact immutable route frame. */
{% if features.review_sessions and features.trajectory_panels and features.timeline_panels and features.workspace_panels %}
{% if features.review_sessions and features.trajectory_panels and features.timeline_panels and features.workspace_panels and features.timeline_wrapper_transparency %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
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
{% endif %}
{% endif %}
{% endif %}

    /** Restores default cursors/mode/dispositions for the focused persisted comparison. */
{% if features.route_comparison and features.workspace_panels %}
{% if features.route_comparison and features.workspace_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void resetRouteComparisonSession() {
        add(new RouteComparisonSessionPuppetAction(RouteComparisonSessionPuppetAction.Operation.RESET));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}

    /** Evicts and reloads the focused comparison from its production store. */
{% if features.route_comparison and features.workspace_panels %}
{% if features.route_comparison and features.workspace_panels %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void reloadRouteComparisonSession() {
        add(new RouteComparisonSessionPuppetAction(RouteComparisonSessionPuppetAction.Operation.RELOAD));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}

    /** Validates and artifacts one natural retained-route comparison checkpoint. */
{% if features.route_comparison and features.review_sessions and features.trajectory_panels %}
{% if features.route_comparison and features.review_sessions and features.trajectory_panels %}
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
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_server_lifecycle and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    public void assertFormerTerminalStartButtonRoutesToTerminal() {
        add(new AssertFormerTerminalStartButtonPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.command_palette %}

    public void assertActionChoice(List<String> expectedCommands) {
        add(new AssertActionChoicePuppetAction(expectedCommands));
    }
{% endif %}
{% if features.command_palette %}

    public void clickActionChoice(String command) {
        add(new ClickActionChoicePuppetAction(Objects.requireNonNull(command, "command")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% if features.workspace_panels and features.workspace_stack_controls and features.workspace_panel_metadata and features.workspace_panel_measurement and features.workspace_focus_tracking %}

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
{% endif %}
{% if features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement %}

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
{% endif %}
{% if features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement %}

    public void assertWorkspacePanelInstancesDistinct(int firstPanelIndex, int secondPanelIndex) {
        add(new AssertWorkspacePanelInstancesDistinctPuppetAction(firstPanelIndex, secondPanelIndex));
    }
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    public void openTerminal() {
        add(new OpenTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_repl_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    public void executeTerminal(String command) {
        add(new ExecuteTerminalPuppetAction(command));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Runs the deterministic packet echo through the Rust-owned terminal PTY. */
{% if features.multiplayer_packets and features.packet_terminal_worker_tests and features.packet_values and features.packet_transport_private and features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_repl_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void invokePacketEchoThroughTerminal() {
        add(new InvokePacketEchoThroughTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Proves LAN publication disables the external CLI packet-send boundary. */
{% if features.multiplayer_packets and features.packet_transport_private and features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_repl_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void invokePacketLanDisabledThroughTerminal() {
        add(new InvokePacketLanDisabledThroughTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Proves locally accepted external CLI sends can still be dropped by world IO. */
{% if features.multiplayer_packets and features.packet_terminal_worker_tests and features.packet_transport_private and features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_repl_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void invokePacketLossThroughTerminal() {
        add(new InvokePacketLossThroughTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Releases the LAN-negative fixture only after the terminal result was observed. */
{% if features.multiplayer_packets and features.packet_transport_private %}
    public void completePacketLanDisabledAttempt() {
        add(new CompletePacketLanDisabledAttemptPuppetAction());
    }
{% endif %}

    /** Waits nonblockingly for one exact line produced by the Rust terminal PTY. */
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void waitForTerminalLine(String line) {
        add(new WaitForTerminalLinePuppetAction(Objects.requireNonNull(line, "line")));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    public void cancelTerminal() {
        add(new CancelTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    public void openTerminal() {
        add(new OpenTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_repl_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    public void executeTerminal(String command) {
        add(new ExecuteTerminalPuppetAction(command));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    public void cancelTerminal() {
        add(new CancelTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endcase %}

    /** Exercises an owned Rust server stop/restart and fresh Vox session. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_server_lifecycle and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void restartRustTerminalServer() {
        add(new RestartRustTerminalServerPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_server_lifecycle and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    public void startRustTerminalThroughUi() {
        add(new StartRustTerminalThroughUiPuppetAction());
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
    public void restartRustTerminalServer() {
        add(new RestartRustTerminalServerPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endcase %}

    /** Delivers printable characters through the real terminal charTyped callback. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_keyboard_input %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void typeTerminalText(String text) {
        add(new TypeTerminalTextPuppetAction(Objects.requireNonNull(text, "text")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
    public void typeTerminalText(String text) {
        add(new TypeTerminalTextPuppetAction(Objects.requireNonNull(text, "text")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endcase %}

    /** Exercises Ctrl+V through the terminal's real clipboard callback. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void pasteTerminalText(String text) {
        add(new PasteTerminalTextPuppetAction(Objects.requireNonNull(text, "text")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Selects one exact visible line and retains zero-raster-work evidence. */
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
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
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Copies and clears the current selection through Ctrl+C or secondary click. */
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
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
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard and features.terminal_paste_confirmation %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    public void pasteTerminalTextByRightClick(String text) {
        add(new RightClickPasteTerminalTextPuppetAction(Objects.requireNonNull(text, "text")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard and features.terminal_paste_confirmation %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    public void assertTerminalPasteWarning(String artifactName, String canonicalPreview) {
        add(new AssertTerminalPasteWarningPuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                Objects.requireNonNull(canonicalPreview, "canonicalPreview")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

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
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

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
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
    public void pasteTerminalText(String text) {
        add(new PasteTerminalTextPuppetAction(Objects.requireNonNull(text, "text")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endcase %}

    /** Writes the Rust-owned visible terminal text and checks optional witnesses. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void writeTerminalContent(String artifactName, String requiredText, String forbiddenText) {
        add(new WriteTerminalContentPuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                requiredText,
                forbiddenText
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Asserts the push transport's zero-polling and bounded-queue evidence. */
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void assertTerminalPushEvidence(String artifactName, boolean reconnectExpected) {
        add(new AssertTerminalPushEvidencePuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                reconnectExpected
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_properties and features.workspace_panel_lookup %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

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
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_properties %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    public void clickTerminalPropertiesControl(String operation) {
        add(new ClickTerminalPropertiesControlPuppetAction(
                Objects.requireNonNull(operation, "operation")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}

    /** Samples one pushed frame/content state and records its complete presentation identity. */
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_presentation_actions and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
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
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_presentation_actions and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

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
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_presentation_actions %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    public void selectTerminalRendererThroughUi(String rendererId) {
        add(new SelectTerminalPresentationUiPuppetAction(true,
                Objects.requireNonNull(rendererId, "rendererId")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_presentation_actions %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    public void selectTerminalTransportThroughUi(String transportId) {
        add(new SelectTerminalPresentationUiPuppetAction(false,
                Objects.requireNonNull(transportId, "transportId")));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
    public void writeTerminalContent(String artifactName, String requiredText, String forbiddenText) {
        add(new WriteTerminalContentPuppetAction(
                Objects.requireNonNull(artifactName, "artifactName"),
                requiredText,
                forbiddenText
        ));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endcase %}

    /** Sends a real mouse click into the visible Rust terminal panel. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_focus_gestures and features.workspace_panel_measurement %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void clickTerminal() {
        add(new ClickTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
    public void clickTerminal() {
        add(new ClickTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endcase %}

    /** Sends a real mouse drag into the visible Rust terminal panel. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_focus_gestures and features.workspace_panel_measurement %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void dragTerminal() {
        add(new DragTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
    public void dragTerminal() {
        add(new DragTerminalPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endcase %}

    /** Requests a deterministic logical resize of the Rust terminal. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void resizeTerminal(int columns, int rows) {
        if (columns < 1 || rows < 1) throw new IllegalArgumentException("Terminal dimensions must be positive");
        add(new ResizeTerminalPuppetAction(columns, rows));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_focus_gestures and features.workspace_panel_measurement %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    public void scrollTerminal(double delta) {
        add(new ScrollTerminalPuppetAction(delta));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_keyboard_input %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    public void pressTerminalKey(int keyCode) {
        add(new PressTerminalKeyPuppetAction(keyCode));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
    public void resizeTerminal(int columns, int rows) {
        if (columns < 1 || rows < 1) throw new IllegalArgumentException("Terminal dimensions must be positive");
        add(new ResizeTerminalPuppetAction(columns, rows));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    public void scrollTerminal(double delta) {
        add(new ScrollTerminalPuppetAction(delta));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    public void pressTerminalKey(int keyCode) {
        add(new PressTerminalKeyPuppetAction(keyCode));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endcase %}

    /** Sends a terminal key with a GLFW modifier mask through the panel callbacks. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_keyboard_input %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void pressTerminalKey(int keyCode, int modifiers) {
        add(new PressTerminalKeyPuppetAction(keyCode, modifiers));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
    public void pressTerminalKey(int keyCode, int modifiers) {
        add(new PressTerminalKeyPuppetAction(keyCode, modifiers));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endcase %}

    /** Sends a terminal key directly to Rust without invoking SFM focus gestures. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_keyboard_input %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    public void pressTerminalKeyDirect(int keyCode, int modifiers) {
        add(new PressTerminalKeyDirectPuppetAction(keyCode, modifiers));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
    public void pressTerminalKeyDirect(int keyCode, int modifiers) {
        add(new PressTerminalKeyDirectPuppetAction(keyCode, modifiers));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endif %}
{% endif %}
{% endcase %}
{% if features.command_palette %}

    public void setCommandPaletteInput(String command) {
        add(new SetCommandPaletteInputPuppetAction(command, null));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.command_palette %}

    public void waitForCommandPaletteSuggestions(String input, List<String> expectedSuggestions) {
        add(new WaitForCommandPaletteSuggestionsPuppetAction(input, expectedSuggestions));
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
{% if features.command_palette %}

    public void prepareIncompleteCommandPaletteInput(String command, String expected) {
        add(new SetCommandPaletteInputPuppetAction(command, expected));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}

    public void waitForScreen(Class<? extends Screen> screenType) {
        add(new WaitForScreenPuppetAction(screenType));
    }

    /** Sends a real mouse-click callback to the center of one workspace panel. */
{% if features.workspace_panels %}
    public void clickWorkspacePanel(int panelIndex) {
        add(new ClickWorkspacePanelPuppetAction(panelIndex));
    }
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_dividers %}

    public void openWorkspaceDividerFixture(int paneCount) {
        add(new OpenWorkspaceDividerFixturePuppetAction(paneCount));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% if features.workspace_panels and features.workspace_dividers and features.pointer_modifier_ingress %}

    public void hoverWorkspaceDividerIntersection() {
        add(new MoveWorkspaceDividerPointerPuppetAction(
                MoveWorkspaceDividerPointerPuppetAction.Operation.HOVER, 0, 0));
    }
{% endif %}
{% if features.workspace_panels and features.workspace_dividers and features.pointer_modifier_ingress %}

    public void dragWorkspaceDividerIntersection(int deltaX, int deltaY) {
        add(new MoveWorkspaceDividerPointerPuppetAction(
                MoveWorkspaceDividerPointerPuppetAction.Operation.PRESS_AND_DRAG,
                deltaX,
                deltaY));
    }
{% endif %}
{% if features.workspace_panels and features.workspace_dividers and features.pointer_modifier_ingress %}

    public void releaseWorkspaceDividerIntersection() {
        add(new MoveWorkspaceDividerPointerPuppetAction(
                MoveWorkspaceDividerPointerPuppetAction.Operation.RELEASE, 0, 0));
    }
{% endif %}
{% if features.workspace_panels and features.workspace_dividers and features.workspace_panel_lookup %}

{% if features.workspace_panels and features.workspace_dividers and features.workspace_panel_lookup %}
    public void writeWorkspaceDividerEvidence(
            String artifactName,
            String stage,
            int paneCount
    ) {
        add(new WriteWorkspaceDividerEvidencePuppetAction(artifactName, stage, paneCount));
    }
{% endif %}
{% endif %}
{% if features.screen_diagnostics and features.workspace_panels %}

    public void openSizeDisplay(SFMSizeDisplayWorkspace.Allocation allocation) {
        add(new OpenSizeDisplayPuppetAction(Objects.requireNonNull(allocation)));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}

    public void openSizeDisplay(SFMSizeDisplayWorkspace.Allocation allocation) {
        add(new OpenSizeDisplayPuppetAction(Objects.requireNonNull(allocation)));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endcase %}
{% if features.timeline_panels %}

    public void openFalsifiedInventoryTimeline() {
        add(new OpenFalsifiedInventoryTimelinePuppetAction());
    }
{% endif %}
{% if features.timeline_panels %}

    public void seekFalsifiedInventoryTimeline(int timestep) {
        add(new SeekFalsifiedInventoryTimelinePuppetAction(timestep));
    }
{% endif %}
{% if features.timeline_panels %}

    public void seekFalsifiedInventoryKeyframePosition(double position) {
        add(new SeekFalsifiedInventoryKeyframePositionPuppetAction(position));
    }
{% endif %}
{% if features.timeline_panels %}

    public void seekFalsifiedInventoryElapsedTicks(double ticks) {
        add(new SeekFalsifiedInventoryElapsedTicksPuppetAction(ticks));
    }
{% endif %}
{% if features.timeline_panels %}

    public void jumpFalsifiedInventoryKeyframe(int direction) {
        add(new JumpFalsifiedInventoryKeyframePuppetAction(direction));
    }
{% endif %}
{% if features.timeline_panels %}

    public void dragFalsifiedInventoryTimeline(int fromTimestep, int toTimestep) {
        add(new DragFalsifiedInventoryTimelinePuppetAction(fromTimestep, toTimestep));
    }
{% endif %}
{% if features.workspace_panels %}

    public void openColorInput(boolean toSide) { add(new OpenColorInputPuppetAction(toSide)); }
{% endif %}
{% if features.workspace_panels %}
    public void setColorInputHueSaturation(double hue, double saturation) {
        add(new SetColorInputHueSaturationPuppetAction(hue, saturation));
    }
{% endif %}
{% if features.workspace_panels %}
    public void setColorInputValue(double value) { add(new SetColorInputValuePuppetAction(value)); }
{% endif %}
{% if features.workspace_panels %}
    public void adjustColorInputChannel(int channel, int direction, int clicks) {
        add(new AdjustColorInputChannelPuppetAction(channel, direction, clicks));
    }
{% endif %}
{% if features.workspace_panels %}
    public void selectColorInputRecent(int index) { add(new SelectColorInputRecentPuppetAction(index)); }
{% endif %}
{% if features.workspace_panels %}
    public void resetColorInput() { add(new ResetColorInputPuppetAction()); }
{% endif %}
{% if features.workspace_panels %}
    public void setColorInputHex(String hex, boolean rgbaOrder) { add(new SetColorInputHexPuppetAction(hex, rgbaOrder)); }
{% endif %}
{% if features.workspace_panels %}
    public void confirmColorInput() { add(new ConfirmColorInputPuppetAction()); }
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.legacy_repository_review and features.command_palette %}
    public void applyRepositoryReviewCommand(String command) { add(new ApplyRepositoryReviewPuppetAction(command)); }
{% endif %}
{% if features.legacy_repository_review %}
    public void prepareRepositoryReviewFixture() { add(new PrepareRepositoryReviewFixturePuppetAction()); }
{% endif %}

{% endcase %}
    /** Invokes the current screen's own close/back behavior. */
    public void closeScreenNaturally() {
        add(new CloseScreenNaturallyPuppetAction());
    }

    /** Executes a palette action whose success replaces the palette with a screen. */
{% if features.command_palette %}
    public void executeCommandPaletteAndWaitForScreen(
            String command,
            Class<?> expectedScreen
    ) {
        add(new ExecuteCommandPaletteAndWaitForScreenPuppetAction(command, expectedScreen));
    }
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.file_explorer %}

    public void pressFileExplorerKey(int keyCode) {
        add(new PressFileExplorerKeyPuppetAction(keyCode));
    }
{% endif %}
{% if features.workspace_panels and features.file_explorer %}

    public void setFileExplorerSnapshot(SFMFileExplorerSnapshot snapshot) {
        add(new SetFileExplorerSnapshotPuppetAction(snapshot));
    }
{% endif %}
{% if features.file_explorer and features.workspace_panels and features.icon_rules %}

    public void openItemIconGallery() {
        add(new OpenItemIconGalleryPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    public void pressFileExplorerKey(int keyCode) {
        add(new PressFileExplorerKeyPuppetAction(keyCode));
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    public void setFileExplorerSnapshot(SFMFileExplorerSnapshot snapshot) {
        add(new SetFileExplorerSnapshotPuppetAction(snapshot));
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    public void openFileExplorer(SFMFileExplorerSource source) {
        add(new OpenFileExplorerPuppetAction(source));
    }
{% endif %}
{% if features.workspace_panels and features.legacy_file_explorer %}

    public void openItemIconGallery() {
        add(new OpenItemIconGalleryPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% endcase %}
{% if features.item_picker and features.workspace_panels %}

    public void openItemPicker(boolean multiplexed) {
        add(new OpenItemPickerPuppetAction(multiplexed));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% if features.item_picker and features.workspace_panels %}

    public void configureItemPicker(ConfigureItemPickerPuppetAction.View view) {
        add(new ConfigureItemPickerPuppetAction(view));
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% if features.sfml_literal_globs %}

    public void showLiteralGlobDiagnostic() {
        add(new ShowLiteralGlobDiagnosticPuppetAction());
        add(new WaitTicksPuppetAction(RENDER_SETTLE_TICKS));
    }
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.file_explorer %}

    public void deliverFileExplorerDropFixture() {
        add(new DeliverFileExplorerDropFixturePuppetAction());
    }
{% endif %}
{% if features.workspace_panels and features.file_explorer and features.workspace_panel_lookup and features.workspace_panel_measurement and features.workspace_focus_tracking %}

    public void clickFileExplorerRow(int visibleRowIndex) {
        add(new ClickFileExplorerRowPuppetAction(visibleRowIndex));
    }
{% endif %}
{% if features.file_explorer and features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement and features.workspace_focus_tracking %}

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
{% endif %}
{% if features.file_explorer and features.workspace_panels and features.workspace_panel_lookup and features.workspace_focus_tracking and features.workspace_panel_measurement %}

    public void assertFileExplorerPreviewFocus(boolean previewFocused) {
        add(new AssertFileExplorerPreviewFocusPuppetAction(previewFocused));
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    public void deliverFileExplorerDropFixture() {
        add(new DeliverFileExplorerDropFixturePuppetAction());
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    public void clickFileExplorerRow(int visibleRowIndex) {
        add(new ClickFileExplorerRowPuppetAction(visibleRowIndex));
    }
{% endif %}
{% if features.workspace_panels and features.legacy_file_explorer %}

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
{% endif %}
{% endcase %}

    /**
     * Captures the currently rendered client frame with a numbered, styled caption.
     */
    public void capture(String captureName, Component caption) {
        add(new CapturePuppetAction(captureName, Objects.requireNonNull(caption, "caption").copy()));
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
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

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    public boolean isComplete() {
        return currentAction >= actions.size();
    }

    public String currentActionDescription() {
        return isComplete() ? "complete" : actions.get(currentAction).description();
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public void abortCurrentAction() {
        if (!isComplete()) actions.get(currentAction).abort();
    }

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /** Opt-in normal remote connection controlled by bounded request files. */
{% if features.multiplayer_packets and features.packet_values and features.packet_transport_private %}
    public void remoteMultiplayerPacketBoundary() {
        add(new RemoteMultiplayerPacketBoundaryPuppetAction());
    }
{% endif %}

    /** Opt-in real terminal pixels and gameplay press, sharing the ambient fixture's owned worker. */
{% if features.touch_display and features.touch_display_terminal_mount and features.client_frame_render and features.client_frame_language and features.client_manager %}
{% if features.touch_display and features.touch_display_terminal_mount and features.client_frame_render and features.client_manager and features.client_frame_language %}
    public void exploreTouchDisplayTerminalInteractively(
            ca.teamdman.sfm.gametest.tests.general.TouchDisplayTerminalVisualControl control) {
        add(new ExploreTouchDisplayTerminalPuppetAction(Objects.requireNonNull(control)));
    }
{% endif %}
{% endif %}

    /** Opt-in file-driven vanilla item rendering and actual hovered Alt+D acceptance. */
{% if features.packet_values and features.item_inspection and features.editor_documents and features.editor_document_panels and features.command_palette and features.workspace_panels %}
{% if features.packet_values and features.item_inspection and features.editor_documents and features.editor_document_panels and features.command_palette and features.workspace_panels and features.tooltip_mode_override %}
    public void explorePacketInspectionInteractively() {
        add(new ExplorePacketInspectionPuppetAction());
    }
{% endif %}
{% endif %}

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    private void add(SFMPuppetAction action) {
        if (currentAction != 0) {
            throw new IllegalStateException("Cannot add game puppet actions after execution has begun");
        }
        actions.add(action);
    }

}
