package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPreviewPlacement;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.symbol.SFMDefinitionLookupService;
import ca.teamdman.sfm.client.symbol.SFMDefinitionResult;
import ca.teamdman.sfm.client.symbol.SFMReferenceLookupService;
import ca.teamdman.sfm.client.symbol.SFMSymbolHoverStateMachine;
import ca.teamdman.sfm.client.symbol.SFMSymbolNavigationRuntime;
import ca.teamdman.sfm.client.symbol.SFMSymbolServerSupervisor;
import ca.teamdman.sfm.client.symbol.SFMUsageAtPositionResult;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetForegroundWindow;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetPointer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetRenderHarness;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;

/**
 * Drives and records the integrated C-11 source-navigation journey.
 *
 * <p>The action deliberately uses the real worker, dynamic keybinding engine,
 * panel pointer routes, constrained palette, reference resolver, and reveal
 * action. Reflection is confined to the adjacent test-only probe and only
 * observes presentation identities which production already owns.</p>
 */
public final class AssertSourceNavigationJourneyPuppetAction implements SFMPuppetAction {
    private static final int MAXIMUM_TICKS = 20 * 18 * 60;
    private static final int MAXIMUM_PHASE_TICKS = 20 * 3 * 60;
    private static final long FRAME_MEDIAN_BUDGET_NANOS = 16_700_000L;
    private static final long FRAME_P95_BUDGET_NANOS = 33_300_000L;
    private static final long FRAME_PAUSE_BUDGET_NANOS = 100_000_000L;
    private static final long INPUT_P95_BUDGET_NANOS = 50_000_000L;
    private static final int WARM_INTERACTION_KINDS = 6;
    private static final int WARM_MEASUREMENT_CYCLES = 5;
    private static final int WARM_INTERACTION_COUNT = WARM_INTERACTION_KINDS * WARM_MEASUREMENT_CYCLES;
    private static final String DEFINITION_COMMAND = "sfm action invoke sfm:symbol/definition/open";
    private static final String REFERENCES_COMMAND = "sfm action invoke sfm:symbol/references/open";

    private enum Phase {
        WAIT_SOURCE,
        PREPARE_HOVER,
        WAIT_HOVER_POINTER,
        WAIT_HOVER,
        CAPTURE_HOVER,
        ACTIVATE_HOVER,
        WAIT_HOVER_TARGET,
        PREPARE_DEFINITION,
        WAIT_DEFINITION_LOOKUP,
        INVOKE_F12,
        WAIT_DEFINITION_TARGET,
        CAPTURE_DEFINITION,
        PREPARE_ALT_ENTER,
        WAIT_ALT_ENTER,
        CLOSE_ALT_ENTER,
        INVOKE_RIGHT_CLICK,
        WAIT_RIGHT_CLICK,
        CAPTURE_CONTEXT_CHOICES,
        CLOSE_CONTEXT_CHOICES,
        PREPARE_REFERENCE_LOOKUP,
        WAIT_REFERENCE_LOOKUP,
        INVOKE_ALT_F7,
        WAIT_REFERENCE_EXPLORER,
        EXPAND_REFERENCE_EXPLORER,
        OPEN_REFERENCE,
        WAIT_REFERENCE_TARGET,
        CAPTURE_REFERENCES,
        PREPARE_REVEAL,
        WAIT_REVEAL,
        CAPTURE_REVEAL,
        FOCUS_PERFORMANCE_WINDOW,
        WARM_PERFORMANCE,
        FINALIZE,
        COMPLETE
    }

    private record Fixture(
            String id,
            String symbol,
            int occurrence,
            String expectedKind,
            String expectedResolver,
            String expectedRootRelativeSuffix
    ) {
        Fixture {
            requireNonBlank(id, "fixture id");
            requireNonBlank(symbol, "fixture symbol");
            if (occurrence < 0) throw new IllegalArgumentException("Fixture occurrence must be non-negative");
            requireNonBlank(expectedKind, "expected symbol kind");
            requireNonBlank(expectedResolver, "expected resolver");
            expectedRootRelativeSuffix = normalizeSuffix(expectedRootRelativeSuffix);
        }
    }

    private final Path sourceRoot;
    private final Path sourceFile;
    private final SFMPath sourceAddress;
    private final String artifactName;
    private final List<Fixture> fixtures = List.of(
            new Fixture("project-type", "ProgramContext", 0, "class", "workspace",
                    "ca/teamdman/sfm/common/program/ProgramContext.java"),
            new Fixture("jdk-string", "String", 0, "class", "jdk-source", "java/lang/String.java"),
            new Fixture("jdk-object", "Object", 0, "class", "jdk-source", "java/lang/Object.java"),
            new Fixture("jdk-string-builder", "StringBuilder", 0, "class", "jdk-source",
                    "java/lang/StringBuilder.java"),
            new Fixture("local-variable", "amountAvailableToMove", 1, "local-variable", "workspace",
                    "ca/teamdman/sfml/ast/OutputStatement.java"),
            new Fixture("member-field", "lastInputCapacity", 1, "field", "workspace",
                    "ca/teamdman/sfml/ast/OutputStatement.java"),
            new Fixture("member-method", "toStringPretty", 0, "method", "workspace",
                    "ca/teamdman/sfml/ast/OutputStatement.java"),
            new Fixture("annotation-use", "SFMLocalizationDatagen", 1, "annotation", "workspace",
                    "ca/teamdman/sfm/common/localization/SFMLocalizationDatagen.java"),
            new Fixture("import-subject", "SFMConfig", 0, "class", "workspace",
                    "ca/teamdman/sfm/common/config/SFMConfig.java")
    );
    private final JsonObject evidence = new JsonObject();
    private final JsonArray definitionEvidence = new JsonArray();
    private final JsonArray openedReferenceEvidence = new JsonArray();
    private Phase phase = Phase.WAIT_SOURCE;
    private int totalTicks;
    private int phaseTicks;
    private SFMWorkspacePanelId sourcePanelId;
    private SFMWorkspacePanelId sourceExplorerPanelId;
    private String sourceStackId;
    private int initialVisiblePanels;
    private SFMTextDocumentRange hoverRange;
    private C11SourceNavigationPuppetProbe.Pointer hoverPointer;
    private JsonObject hoverTopologyBefore;
    private int fixtureIndex;
    private Fixture currentFixture;
    private SFMTextDocumentRange currentSourceRange;
    private SFMDefinitionLookupService.Submission definitionSubmission;
    private SFMDefinitionLookupService.Lookup definitionLookup;
    private long lookupStartedNanos;
    private long navigationStartedNanos;
    private JsonObject navigationTopologyBefore;
    private int navigationVisibleBefore;
    private boolean methodDefinitionProven;
    private List<String> altEnterChoices = List.of();
    private C11SourceNavigationPuppetProbe.Pointer contextPointer;
    private SFMReferenceLookupService.Submission referenceSubmission;
    private SFMReferenceLookupService.Lookup referenceLookup;
    private long referenceLookupStartedNanos;
    private Set<SFMWorkspacePanelId> panelsBeforeReferences = Set.of();
    private SFMSourcePuppetProbe.ExplorerHandle referenceExplorer;
    private String referenceExplorerIdentity;
    private String referencePanelStackId;
    private List<SFMPath> referenceLeaves = List.of();
    private int referenceIndex;
    private SFMExplorerRuntime.ReferenceLeafNavigation pendingReferenceNavigation;
    private int performanceFocusRequests;
    private boolean performanceMeasurementStarted;
    private int performanceWarmupInteractionIndex;
    private int performanceInteractionIndex;
    private long completedInputToFrameSamples;
    private SFMGamePuppetRenderHarness.Ticket performanceRenderTicket;
    private final JsonArray performanceInputSamples = new JsonArray();

    public AssertSourceNavigationJourneyPuppetAction(
            Path sourceRoot,
            Path sourceFile,
            String artifactName
    ) {
        this.sourceRoot = Objects.requireNonNull(sourceRoot, "sourceRoot").toAbsolutePath().normalize();
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile").toAbsolutePath().normalize();
        if (!this.sourceFile.startsWith(this.sourceRoot)) {
            throw new IllegalArgumentException("The C-11 source file must lie under its source root");
        }
        sourceAddress = SFMPath.fromNative(this.sourceFile);
        this.artifactName = requireNonBlank(artifactName, "artifactName");
        evidence.addProperty("schema", "sfm.source-navigation-live-puppet/1");
        evidence.addProperty("source_root", SFMPath.fromNative(this.sourceRoot).canonical());
        evidence.addProperty("source_path", sourceAddress.canonical());
        evidence.addProperty("source_files_mutated", false);
        evidence.addProperty("raw_source_retained", false);
        String workerExecutable = System.getProperty(
                SFMSymbolServerSupervisor.EXECUTABLE_PROPERTY,
                SFMSymbolServerSupervisor.DEFAULT_EXECUTABLE
        ).strip();
        JsonObject worker = new JsonObject();
        worker.addProperty("executable", workerExecutable);
        Path workerPath = Path.of(workerExecutable).toAbsolutePath().normalize();
        worker.addProperty("exists", java.nio.file.Files.isRegularFile(workerPath));
        if (java.nio.file.Files.isRegularFile(workerPath)) {
            try {
                worker.addProperty("bytes", java.nio.file.Files.size(workerPath));
                worker.addProperty("last_modified_millis",
                        java.nio.file.Files.getLastModifiedTime(workerPath).toMillis());
            } catch (java.io.IOException failure) {
                worker.addProperty("metadata_failure", failure.getClass().getSimpleName());
            }
        }
        evidence.add("symbol_worker", worker);
        evidence.add("definitions", definitionEvidence);
    }

    @Override
    public String description() {
        return "prove the integrated C-11 OutputStatement source-navigation journey";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (++totalTicks > MAXIMUM_TICKS) fail("Whole C-11 journey timed out");
        if (++phaseTicks > MAXIMUM_PHASE_TICKS) fail("Phase timed out: " + phase);
        return switch (phase) {
            case WAIT_SOURCE -> waitSource();
            case PREPARE_HOVER -> prepareHover();
            case WAIT_HOVER_POINTER -> waitHoverPointer();
            case WAIT_HOVER -> waitHover();
            case CAPTURE_HOVER -> captureHover(runtime);
            case ACTIVATE_HOVER -> activateHover();
            case WAIT_HOVER_TARGET -> waitHoverTarget();
            case PREPARE_DEFINITION -> prepareDefinition();
            case WAIT_DEFINITION_LOOKUP -> waitDefinitionLookup();
            case INVOKE_F12 -> invokeF12(runtime);
            case WAIT_DEFINITION_TARGET -> waitDefinitionTarget();
            case CAPTURE_DEFINITION -> captureDefinition(runtime);
            case PREPARE_ALT_ENTER -> prepareAltEnter(runtime);
            case WAIT_ALT_ENTER -> waitAltEnter();
            case CLOSE_ALT_ENTER -> closeAltEnter();
            case INVOKE_RIGHT_CLICK -> invokeRightClick();
            case WAIT_RIGHT_CLICK -> waitRightClick();
            case CAPTURE_CONTEXT_CHOICES -> captureContextChoices(runtime);
            case CLOSE_CONTEXT_CHOICES -> closeContextChoices();
            case PREPARE_REFERENCE_LOOKUP -> prepareReferenceLookup();
            case WAIT_REFERENCE_LOOKUP -> waitReferenceLookup();
            case INVOKE_ALT_F7 -> invokeAltF7(runtime);
            case WAIT_REFERENCE_EXPLORER -> waitReferenceExplorer();
            case EXPAND_REFERENCE_EXPLORER -> expandReferenceExplorer();
            case OPEN_REFERENCE -> openReference();
            case WAIT_REFERENCE_TARGET -> waitReferenceTarget();
            case CAPTURE_REFERENCES -> captureReferences(runtime);
            case PREPARE_REVEAL -> prepareReveal();
            case WAIT_REVEAL -> waitReveal();
            case CAPTURE_REVEAL -> captureReveal(runtime);
            case FOCUS_PERFORMANCE_WINDOW -> focusPerformanceWindow();
            case WARM_PERFORMANCE -> warmPerformance(runtime);
            case FINALIZE -> finalizeEvidence(runtime);
            case COMPLETE -> true;
        };
    }

    private boolean waitSource() {
        SFMScreenMultiplexer workspace = workspaceOrNull();
        if (workspace == null) return false;
        SFMSourcePuppetProbe.EditorHandle source = sourceEditor(workspace).orElse(null);
        if (source == null || source.resolvedPanel().isEmpty()
                || source.state().documentSnapshot().filter(SFMTextDocumentSnapshot::ready).isEmpty()) return false;
        SFMTextDocumentSnapshot document = source.state().documentSnapshot().orElseThrow();
        require(source.state().isReadOnly(), "OutputStatement.java must be opened read-only");
        require(document.path().filter(sourceAddress::equals).isPresent(), "The opened document path is not OutputStatement.java");
        sourcePanelId = source.panelId();
        require(workspace.focusPanel(sourcePanelId), "The OutputStatement editor could not be focused");
        sourceStackId = workspace.panelStackId(sourcePanelId).map(Object::toString).orElseThrow();
        sourceExplorerPanelId = workspace.panelIds().stream()
                .filter(id -> workspace.panelInstance(id) instanceof SFMExplorerPanel)
                .filter(id -> {
                    SFMExplorerPanel explorer = (SFMExplorerPanel) workspace.panelInstance(id);
                    return explorer.sessionSnapshot().roots().stream().anyMatch(root -> contains(root, sourceAddress));
                })
                .min(Comparator.comparingLong(SFMWorkspacePanelId::value))
                .orElseThrow(() -> new IllegalStateException("The source explorer was not retained beside the editor"));
        initialVisiblePanels = workspace.visiblePanelEntries().size();
        require(initialVisiblePanels == 2, "Expected one explorer and one OutputStatement editor before navigation");
        JsonObject sourceJson = new JsonObject();
        sourceJson.addProperty("panel_id", sourcePanelId.toString());
        sourceJson.addProperty("stack_id", sourceStackId);
        sourceJson.addProperty("explorer_panel_id", sourceExplorerPanelId.toString());
        sourceJson.addProperty("sha256", document.sha256().orElse("missing"));
        sourceJson.addProperty("bytes", document.byteLength().orElse(-1L));
        sourceJson.add("initial_topology", C11SourceNavigationPuppetProbe.topology(workspace));
        evidence.add("source", sourceJson);
        advance(Phase.PREPARE_HOVER);
        return false;
    }

    private boolean prepareHover() {
        SFMScreenMultiplexer workspace = requireWorkspace();
        SFMSourcePuppetProbe.EditorHandle source = restoreSource(workspace);
        SFMTextDocumentSnapshot document = source.state().documentSnapshot().orElseThrow();
        hoverRange = SFMSourcePuppetProbe.symbolRange(document.text(), "ProgramContext", 0);
        hoverPointer = C11SourceNavigationPuppetProbe.pointer(workspace, source, hoverRange);
        hoverTopologyBefore = C11SourceNavigationPuppetProbe.topology(workspace);
        SFMGamePuppetPointer.move(workspace, hoverPointer.globalX(), hoverPointer.globalY());
        advance(Phase.WAIT_HOVER_POINTER);
        return false;
    }

    private boolean waitHoverPointer() {
        SFMScreenMultiplexer workspace = requireWorkspace();
        SFMGamePuppetPointer.Position current = SFMGamePuppetPointer.current();
        if (!current.isWithin(hoverPointer.globalX(), hoverPointer.globalY(), 1.0D)) {
            if (phaseTicks > 20) {
                fail("Native puppet pointer did not settle at the Ctrl-hover target: expected="
                        + hoverPointer.globalX() + "," + hoverPointer.globalY()
                        + " actual=" + current.logicalX() + "," + current.logicalY()
                        + " native=" + current.nativeX() + "," + current.nativeY());
            }
            return false;
        }
        workspace.mouseMoved(hoverPointer.globalX(), hoverPointer.globalY());
        workspace.keyPressed(GLFW.GLFW_KEY_LEFT_CONTROL, 0, GLFW.GLFW_MOD_CONTROL);
        advance(Phase.WAIT_HOVER);
        return false;
    }

    private boolean waitHover() {
        SFMScreenMultiplexer workspace = requireWorkspace();
        C11SourceNavigationPuppetProbe.Hover hover = C11SourceNavigationPuppetProbe.hover(sourceEditor(workspace).orElseThrow());
        if (hover.snapshot().phase() != SFMSymbolHoverStateMachine.Phase.ACTIONABLE
                || hover.renderedUnderline().isEmpty()
                || !hover.handCursorSelected()) {
            if (phaseTicks > 20
                    && hover.lastCancellationCause() != SFMSymbolHoverStateMachine.CancellationCause.NONE) {
                fail("Ctrl-hover lookup cancelled by " + hover.lastCancellationCause());
            }
            return false;
        }
        require(hover.renderedUnderline().orElseThrow().equals(hoverPointer.hit().range()),
                "Ctrl-hover underlined a range other than the exact ProgramContext symbol");
        JsonObject json = new JsonObject();
        json.addProperty("symbol", "ProgramContext");
        json.addProperty("phase", hover.snapshot().phase().name().toLowerCase(Locale.ROOT));
        json.addProperty("hand_cursor_selected", hover.handCursorSelected());
        json.addProperty("identity_present", hover.snapshot().identity().isPresent());
        json.add("source_range", C11SourceNavigationPuppetProbe.range(hoverRange));
        json.add("underline_range", hoverGlyphRange(hover.renderedUnderline().orElseThrow()));
        json.add("topology_before", hoverTopologyBefore);
        evidence.add("ctrl_hover", json);
        advance(Phase.CAPTURE_HOVER);
        return false;
    }

    private boolean captureHover(ISFMGamePuppetRuntime runtime) {
        if (!runtime.capture("output-statement-ctrl-hover", caption(
                "Ctrl-hover marks the exact ProgramContext token as a link before Ctrl+click navigation."
        ))) return false;
        advance(Phase.ACTIVATE_HOVER);
        return false;
    }

    private boolean activateHover() {
        SFMScreenMultiplexer workspace = requireWorkspace();
        navigationStartedNanos = System.nanoTime();
        require(workspace.mouseClicked(hoverPointer.globalX(), hoverPointer.globalY(), GLFW.GLFW_MOUSE_BUTTON_LEFT),
                "The workspace did not route the Ctrl+click press");
        workspace.mouseReleased(hoverPointer.globalX(), hoverPointer.globalY(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        workspace.keyReleased(GLFW.GLFW_KEY_LEFT_CONTROL, 0, 0);
        advance(Phase.WAIT_HOVER_TARGET);
        return false;
    }

    private boolean waitHoverTarget() {
        SFMScreenMultiplexer workspace = requireWorkspace();
        Optional<SFMSourcePuppetProbe.EditorHandle> target = focusedTarget(
                workspace,
                "ca/teamdman/sfm/common/program/ProgramContext.java",
                "ProgramContext",
                Optional.empty()
        );
        if (target.isEmpty()) {
            failOnNavigationToast(workspace, "Ctrl+click");
            return false;
        }
        assertNoSurpriseSplit(workspace, initialVisiblePanels, target.orElseThrow().panelId());
        JsonObject hover = evidence.getAsJsonObject("ctrl_hover");
        hover.addProperty("action", "sfm:symbol/definition/open");
        hover.addProperty("target_panel_id", target.orElseThrow().panelId().toString());
        hover.addProperty("duration_micros", microsSince(navigationStartedNanos));
        hover.add("topology_after", C11SourceNavigationPuppetProbe.topology(workspace));
        advance(Phase.PREPARE_DEFINITION);
        return false;
    }

    private boolean prepareDefinition() {
        if (fixtureIndex >= fixtures.size()) {
            advance(Phase.PREPARE_ALT_ENTER);
            return false;
        }
        SFMScreenMultiplexer workspace = requireWorkspace();
        SFMSourcePuppetProbe.EditorHandle source = restoreSource(workspace);
        currentFixture = fixtures.get(fixtureIndex);
        SFMTextDocumentSnapshot document = source.state().documentSnapshot().orElseThrow();
        currentSourceRange = SFMSourcePuppetProbe.symbolRange(
                document.text(), currentFixture.symbol(), currentFixture.occurrence());
        require(source.resolvedPanel().orElseThrow().navigateToRange(currentSourceRange),
                "Could not position " + currentFixture.id());
        SFMContextContribution contribution = C11SourceNavigationPuppetProbe.focusedDocument(workspace)
                .orElseThrow(() -> new IllegalStateException("The positioned source contribution is unavailable"));
        navigationTopologyBefore = C11SourceNavigationPuppetProbe.topology(workspace);
        navigationVisibleBefore = workspace.visiblePanelEntries().size();
        lookupStartedNanos = System.nanoTime();
        definitionSubmission = SFMSymbolNavigationRuntime.get().query(contribution);
        advance(Phase.WAIT_DEFINITION_LOOKUP);
        return false;
    }

    private boolean waitDefinitionLookup() {
        if (!definitionSubmission.result().isDone()) return false;
        try {
            definitionLookup = definitionSubmission.result().join();
        } catch (CompletionException failure) {
            throw new IllegalStateException("Definition lookup failed for " + currentFixture.id(), unwrap(failure));
        }
        SFMDefinitionResult result = definitionLookup.result();
        require(result.outcome() == SFMDefinitionResult.Outcome.SUCCESS,
                currentFixture.id() + " produced " + result.outcome());
        require(result.definitions().size() == 1,
                currentFixture.id() + " produced " + result.definitions().size() + " definitions");
        SFMDefinitionResult.Definition definition = result.definitions().get(0);
        require(definition.symbol().name().equals(currentFixture.symbol()),
                currentFixture.id() + " resolved the wrong symbol: " + definition.symbol().name());
        require(definition.symbol().kind().equals(currentFixture.expectedKind()),
                currentFixture.id() + " resolved kind " + definition.symbol().kind());
        require(definition.identifierSpan().resolverId().equals(currentFixture.expectedResolver()),
                currentFixture.id() + " resolved through " + definition.identifierSpan().resolverId());
        require(normalizeSuffix(definition.identifierSpan().rootRelativePath())
                        .endsWith(currentFixture.expectedRootRelativeSuffix()),
                currentFixture.id() + " resolved the wrong path: " + definition.identifierSpan().rootRelativePath());
        JsonObject fixture = definitionResult(
                currentFixture,
                currentSourceRange,
                definitionLookup,
                microsSince(lookupStartedNanos)
        );
        fixture.add("topology_before", navigationTopologyBefore);
        definitionEvidence.add(fixture);
        advance(Phase.INVOKE_F12);
        return false;
    }

    private boolean invokeF12(ISFMGamePuppetRuntime runtime) {
        navigationStartedNanos = System.nanoTime();
        runtime.pressScreenKey(GLFW.GLFW_KEY_F12, 0);
        advance(Phase.WAIT_DEFINITION_TARGET);
        return false;
    }

    private boolean waitDefinitionTarget() {
        SFMScreenMultiplexer workspace = requireWorkspace();
        SFMDefinitionResult.Definition definition = definitionLookup.result().definitions().get(0);
        SFMTextDocumentRange expectedRange = range(definition.identifierSpan());
        Optional<SFMSourcePuppetProbe.EditorHandle> target = focusedTarget(
                workspace,
                definition.identifierSpan().rootRelativePath(),
                definition.symbol().name(),
                Optional.of(expectedRange)
        );
        if (target.isEmpty()) {
            failOnNavigationToast(workspace, "F12 " + currentFixture.id());
            return false;
        }
        assertNoSurpriseSplit(workspace, navigationVisibleBefore, target.orElseThrow().panelId());
        JsonObject fixture = definitionEvidence.get(definitionEvidence.size() - 1).getAsJsonObject();
        JsonObject navigation = new JsonObject();
        navigation.addProperty("route", "F12");
        navigation.addProperty("target_panel_id", target.orElseThrow().panelId().toString());
        navigation.addProperty("target_stack_id", workspace.panelStackId(target.orElseThrow().panelId())
                .map(Object::toString).orElse("none"));
        navigation.addProperty("duration_micros", microsSince(navigationStartedNanos));
        navigation.addProperty("visible_panels_before", navigationVisibleBefore);
        navigation.addProperty("visible_panels_after", workspace.visiblePanelEntries().size());
        navigation.addProperty("no_unexpected_pane", true);
        navigation.add("topology_after", C11SourceNavigationPuppetProbe.topology(workspace));
        fixture.add("navigation", navigation);
        if (currentFixture.id().equals("member-method")) methodDefinitionProven = true;
        if (currentFixture.id().equals("jdk-string-builder")) {
            advance(Phase.CAPTURE_DEFINITION);
        } else {
            fixtureIndex++;
            definitionSubmission = null;
            definitionLookup = null;
            advance(Phase.PREPARE_DEFINITION);
        }
        return false;
    }

    private boolean captureDefinition(ISFMGamePuppetRuntime runtime) {
        if (!runtime.capture("output-statement-jdk-definition", caption(
                "F12 resolves java.lang.StringBuilder through the pinned JDK source root in the originating pane stack."
        ))) return false;
        fixtureIndex++;
        definitionSubmission = null;
        definitionLookup = null;
        advance(Phase.PREPARE_DEFINITION);
        return false;
    }

    private boolean prepareAltEnter(ISFMGamePuppetRuntime runtime) {
        SFMScreenMultiplexer workspace = requireWorkspace();
        SFMSourcePuppetProbe.EditorHandle source = restoreSource(workspace);
        SFMTextDocumentSnapshot document = source.state().documentSnapshot().orElseThrow();
        SFMTextDocumentRange range = SFMSourcePuppetProbe.symbolRange(document.text(), "ProgramContext", 0);
        contextPointer = C11SourceNavigationPuppetProbe.pointer(workspace, source, range);
        runtime.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_ALT);
        advance(Phase.WAIT_ALT_ENTER);
        return false;
    }

    private boolean waitAltEnter() {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) return false;
        altEnterChoices = List.copyOf(palette.choiceCommandsForAutomation());
        require(altEnterChoices.contains(DEFINITION_COMMAND), "Alt+Enter omitted Jump to Definition");
        require(altEnterChoices.contains(REFERENCES_COMMAND), "Alt+Enter omitted Find References");
        JsonObject context = new JsonObject();
        context.add("alt_enter", strings(altEnterChoices));
        evidence.add("contextual_actions", context);
        advance(Phase.CLOSE_ALT_ENTER);
        return false;
    }

    private boolean closeAltEnter() {
        SFMCommandPaletteScreen palette = requirePalette();
        palette.onClose();
        advance(Phase.INVOKE_RIGHT_CLICK);
        return false;
    }

    private boolean invokeRightClick() {
        SFMScreenMultiplexer workspace = requireWorkspace();
        restoreSource(workspace);
        SFMGamePuppetPointer.move(workspace, contextPointer.globalX(), contextPointer.globalY());
        require(workspace.mouseClicked(
                        contextPointer.globalX(), contextPointer.globalY(), GLFW.GLFW_MOUSE_BUTTON_RIGHT),
                "The editor did not route the contextual right-click");
        advance(Phase.WAIT_RIGHT_CLICK);
        return false;
    }

    private boolean waitRightClick() {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) return false;
        List<String> rightClick = List.copyOf(palette.choiceCommandsForAutomation());
        require(rightClick.equals(altEnterChoices),
                "Right-click choices differ from Alt+Enter: " + rightClick + " vs " + altEnterChoices);
        JsonObject context = evidence.getAsJsonObject("contextual_actions");
        context.add("right_click", strings(rightClick));
        context.addProperty("identical_order", true);
        advance(Phase.CAPTURE_CONTEXT_CHOICES);
        return false;
    }

    private boolean captureContextChoices(ISFMGamePuppetRuntime runtime) {
        if (!runtime.capture("output-statement-context-actions", caption(
                "Editor right-click and Alt+Enter expose the same constrained Definition/References action surface."
        ))) return false;
        advance(Phase.CLOSE_CONTEXT_CHOICES);
        return false;
    }

    private boolean closeContextChoices() {
        requirePalette().onClose();
        advance(Phase.PREPARE_REFERENCE_LOOKUP);
        return false;
    }

    private boolean prepareReferenceLookup() {
        SFMScreenMultiplexer workspace = requireWorkspace();
        SFMSourcePuppetProbe.EditorHandle source = restoreSource(workspace);
        SFMTextDocumentSnapshot document = source.state().documentSnapshot().orElseThrow();
        SFMTextDocumentRange range = SFMSourcePuppetProbe.symbolRange(document.text(), "amountAvailableToMove", 1);
        require(source.resolvedPanel().orElseThrow().navigateToRange(range),
                "Could not position the reference fixture");
        SFMContextContribution contribution = C11SourceNavigationPuppetProbe.focusedDocument(workspace)
                .orElseThrow(() -> new IllegalStateException("The reference contribution is unavailable"));
        referenceLookupStartedNanos = System.nanoTime();
        referenceSubmission = SFMSymbolNavigationRuntime.get().queryReferences(contribution);
        advance(Phase.WAIT_REFERENCE_LOOKUP);
        return false;
    }

    private boolean waitReferenceLookup() {
        if (!referenceSubmission.result().isDone()) return false;
        try {
            referenceLookup = referenceSubmission.result().join();
        } catch (CompletionException failure) {
            throw new IllegalStateException("Reference lookup failed", unwrap(failure));
        }
        SFMUsageAtPositionResult result = referenceLookup.result();
        require(result.outcome() == SFMDefinitionResult.Outcome.SUCCESS,
                "Reference lookup produced " + result.outcome());
        require(result.usages().size() >= 3,
                "The amountAvailableToMove fixture produced fewer than three references");
        JsonObject references = usageResult(result, referenceLookup, microsSince(referenceLookupStartedNanos));
        references.add("opened_rows", openedReferenceEvidence);
        evidence.add("references", references);
        advance(Phase.INVOKE_ALT_F7);
        return false;
    }

    private boolean invokeAltF7(ISFMGamePuppetRuntime runtime) {
        SFMScreenMultiplexer workspace = requireWorkspace();
        panelsBeforeReferences = Set.copyOf(workspace.panelIds());
        runtime.pressScreenKey(GLFW.GLFW_KEY_F7, GLFW.GLFW_MOD_ALT);
        advance(Phase.WAIT_REFERENCE_EXPLORER);
        return false;
    }

    private boolean waitReferenceExplorer() {
        SFMScreenMultiplexer workspace = requireWorkspace();
        referenceExplorer = C11SourceNavigationPuppetProbe.referenceExplorer(workspace, panelsBeforeReferences)
                .orElse(null);
        if (referenceExplorer == null) {
            failOnNavigationToast(workspace, "Alt+F7");
            return false;
        }
        referenceExplorerIdentity = referenceExplorer.panel().explorerId().value();
        referencePanelStackId = workspace.panelStackId(referenceExplorer.panelId())
                .map(Object::toString).orElseThrow();
        require(workspace.visiblePanelEntries().size() == initialVisiblePanels + 1,
                "Alt+F7 did not add exactly one explicit reference pane");
        JsonObject references = evidence.getAsJsonObject("references");
        references.addProperty("action", "sfm:symbol/references/open");
        references.addProperty("route", "Alt+F7");
        references.addProperty("explorer_id", referenceExplorerIdentity);
        references.addProperty("panel_id", referenceExplorer.panelId().toString());
        references.addProperty("stack_id", referencePanelStackId);
        references.addProperty("location", referenceExplorer.panel().sessionSnapshot().location().canonical());
        references.add("topology_opened", C11SourceNavigationPuppetProbe.topology(workspace));
        advance(Phase.EXPAND_REFERENCE_EXPLORER);
        return false;
    }

    private boolean expandReferenceExplorer() {
        SFMScreenMultiplexer workspace = requireWorkspace();
        assertReferenceIdentity(workspace);
        SFMExplorerPanel panel = referenceExplorer.panel();
        SFMScreenPanelBounds bounds = requireBounds(workspace, referenceExplorer.panelId());
        SFMExplorerProjection.Result projection = panel.model().state(bounds).projection();
        List<SFMPath> leaves = projection.rows().stream()
                .map(SFMExplorerProjection.Row::path)
                .filter(path -> SFMExplorerRuntime.get().referenceLeafNavigation(path).isPresent())
                .sorted(Comparator.comparing(SFMPath::canonical))
                .toList();
        if (leaves.size() >= 3) {
            referenceLeaves = leaves.subList(0, 3);
            referenceIndex = 0;
            advance(Phase.OPEN_REFERENCE);
            return false;
        }
        Optional<SFMExplorerProjection.Row> collapsed = projection.rows().stream()
                .filter(row -> row.entry().expandable() && !row.expanded())
                .findFirst();
        if (collapsed.isPresent()) {
            panel.model().select(collapsed.orElseThrow().path(), bounds);
            panel.model().emitExpandSelected(bounds);
        }
        return false;
    }

    private boolean openReference() {
        if (referenceIndex >= referenceLeaves.size()) {
            advance(Phase.CAPTURE_REFERENCES);
            return false;
        }
        SFMScreenMultiplexer workspace = requireWorkspace();
        assertReferenceIdentity(workspace);
        require(workspace.focusPanel(referenceExplorer.panelId()), "The reference explorer could not be refocused");
        SFMPath leafPath = referenceLeaves.get(referenceIndex);
        pendingReferenceNavigation = SFMExplorerRuntime.get().referenceLeafNavigation(leafPath)
                .orElseThrow(() -> new IllegalStateException("Reference leaf became stale: " + leafPath.canonical()));
        SFMScreenPanelBounds bounds = requireBounds(workspace, referenceExplorer.panelId());
        referenceExplorer.panel().model().select(leafPath, bounds);
        navigationStartedNanos = System.nanoTime();
        require(referenceExplorer.panel().model().emitOpenSelected(
                        bounds, SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW),
                "The reference leaf did not emit its open action");
        advance(Phase.WAIT_REFERENCE_TARGET);
        return false;
    }

    private boolean waitReferenceTarget() {
        SFMScreenMultiplexer workspace = requireWorkspace();
        SFMDefinitionResult.DefinitionSourceSpan span = pendingReferenceNavigation.leaf().sourceSpan();
        String symbol = pendingReferenceNavigation.leaf().usage().target().name();
        Optional<SFMSourcePuppetProbe.EditorHandle> target = focusedTarget(
                workspace,
                span.rootRelativePath(),
                symbol,
                Optional.of(range(span))
        );
        if (target.isEmpty()) {
            failOnNavigationToast(workspace, "reference row " + (referenceIndex + 1));
            return false;
        }
        assertReferenceIdentity(workspace);
        require(workspace.visiblePanelEntries().size() == initialVisiblePanels + 1,
                "Opening a reference row changed visible pane geometry");
        require(sameStack(workspace, sourcePanelId, target.orElseThrow().panelId()),
                "A reference row opened outside the originating source stack");
        JsonObject opened = new JsonObject();
        opened.addProperty("ordinal", referenceIndex + 1);
        opened.addProperty("leaf_path", referenceLeaves.get(referenceIndex).canonical());
        opened.addProperty("target_panel_id", target.orElseThrow().panelId().toString());
        opened.addProperty("duration_micros", microsSince(navigationStartedNanos));
        opened.add("source_span", span(span));
        opened.add("topology_after", C11SourceNavigationPuppetProbe.topology(workspace));
        openedReferenceEvidence.add(opened);
        referenceIndex++;
        advance(Phase.OPEN_REFERENCE);
        return false;
    }

    private boolean captureReferences(ISFMGamePuppetRuntime runtime) {
        SFMScreenMultiplexer workspace = requireWorkspace();
        assertReferenceIdentity(workspace);
        require(openedReferenceEvidence.size() >= 3, "Fewer than three reference rows were opened");
        require(workspace.focusPanel(referenceExplorer.panelId()), "The retained reference explorer could not be focused");
        evidence.getAsJsonObject("references").addProperty("identity_retained_after_three_opens", true);
        evidence.getAsJsonObject("references").add("topology_after_three_opens",
                C11SourceNavigationPuppetProbe.topology(workspace));
        if (!runtime.capture("output-statement-reference-explorer", caption(
                "The same persistent reference explorer remains after opening three exact source rows in the source stack."
        ))) return false;
        advance(Phase.PREPARE_REVEAL);
        return false;
    }

    private boolean prepareReveal() {
        SFMScreenMultiplexer workspace = requireWorkspace();
        restoreSource(workspace);
        int panelsBefore = workspace.panelIds().size();
        SFMClientActionContext context = new SFMClientActionContext(
                workspace,
                () -> Minecraft.getInstance().screen == workspace,
                sourcePanelId
        );
        try {
            int invoked = SFMClientActionExecutor.execute(
                    "sfm action invoke sfm:explorer/reveal",
                    context,
                    ignored -> { }
            );
            require(invoked == 1, "Reveal in Explorer was not invoked");
        } catch (CommandSyntaxException failure) {
            throw new IllegalStateException("Reveal in Explorer command failed", failure);
        }
        evidence.addProperty("panels_before_reveal", panelsBefore);
        advance(Phase.WAIT_REVEAL);
        return false;
    }

    private boolean waitReveal() {
        SFMScreenMultiplexer workspace = requireWorkspace();
        if (!workspace.focusedPanelId().equals(sourceExplorerPanelId)) return false;
        if (!(workspace.panelInstance(sourceExplorerPanelId) instanceof SFMExplorerPanel)) {
            fail("The source explorer disappeared before reveal completed");
        }
        SFMExplorerPanel explorer = (SFMExplorerPanel) workspace.panelInstance(sourceExplorerPanelId);
        SFMScreenPanelBounds bounds = requireBounds(workspace, sourceExplorerPanelId);
        Optional<SFMPath> selected = explorer.model().state(bounds).selectedPath();
        if (selected.filter(sourceAddress::equals).isEmpty()) return false;
        require(workspace.panelIds().size() == evidence.get("panels_before_reveal").getAsInt(),
                "Reveal in Explorer created an unnecessary panel");
        JsonObject reveal = new JsonObject();
        reveal.addProperty("action", "sfm:explorer/reveal");
        reveal.addProperty("document", sourceAddress.canonical());
        reveal.addProperty("explorer_panel_id", sourceExplorerPanelId.toString());
        reveal.addProperty("selected_path", selected.orElseThrow().canonical());
        reveal.addProperty("reused_compatible_explorer", true);
        reveal.add("topology", C11SourceNavigationPuppetProbe.topology(workspace));
        evidence.add("reveal", reveal);
        advance(Phase.CAPTURE_REVEAL);
        return false;
    }

    private boolean captureReveal(ISFMGamePuppetRuntime runtime) {
        if (!runtime.capture("output-statement-reveal-in-explorer", caption(
                "Reveal in Explorer reuses the compatible source explorer and selects exact OutputStatement.java."
        ))) return false;
        advance(Phase.FOCUS_PERFORMANCE_WINDOW);
        return false;
    }

    private boolean focusPerformanceWindow() {
        SFMGamePuppetForegroundWindow.Observation observation =
                SFMGamePuppetForegroundWindow.request(Minecraft.getInstance());
        performanceFocusRequests++;
        if (!observation.readyForVisibleLatency()) {
            if (phaseTicks > 40) {
                fail("The visible EditorV3 latency checkpoint could not make its preview window foreground: "
                        + observation);
            }
            return false;
        }
        // Give the compositor/driver half a second to leave any background
        // application frame policy before starting the warm sample window.
        if (phaseTicks < 10) return false;
        JsonObject focus = new JsonObject();
        focus.addProperty("required_for_visible_latency", true);
        focus.addProperty("glfw_focused", observation.glfwFocused());
        focus.addProperty("iconified", observation.iconified());
        focus.addProperty("platform_probe_available", observation.platformProbeAvailable());
        focus.addProperty("platform_foreground", observation.platformForeground());
        focus.addProperty("foreground_request_accepted", observation.foregroundRequestAccepted());
        focus.addProperty("thread_input_attached", observation.threadInputAttached());
        focus.addProperty("platform_window", Long.toUnsignedString(observation.platformWindow()));
        focus.addProperty("platform_foreground_window",
                Long.toUnsignedString(observation.platformForegroundWindow()));
        focus.addProperty("focus_requests", performanceFocusRequests);
        focus.addProperty("settle_ticks", phaseTicks);
        evidence.add("performance_window", focus);
        advance(Phase.WARM_PERFORMANCE);
        return false;
    }

    private boolean warmPerformance(ISFMGamePuppetRuntime runtime) {
        SFMScreenMultiplexer workspace = requireWorkspace();
        SFMSourcePuppetProbe.EditorHandle source = restoreSource(workspace);
        if (!performanceMeasurementStarted) {
            if (performanceRenderTicket != null) {
                if (!SFMGamePuppetRenderHarness.await(performanceRenderTicket)) return false;
                performanceRenderTicket = null;
                performanceWarmupInteractionIndex++;
            }
            if (performanceWarmupInteractionIndex < WARM_INTERACTION_KINDS) {
                int interaction = performanceWarmupInteractionIndex;
                performanceRenderTicket = SFMGamePuppetRenderHarness.beforeNextFrame(
                        workspace,
                        () -> runWarmInteraction(interaction, workspace)
                );
                return false;
            }
            C11SourceNavigationPuppetProbe.beginWarmPerformanceMeasurement(source);
            performanceMeasurementStarted = true;
            completedInputToFrameSamples = 0L;
            return false;
        }

        if (performanceRenderTicket != null) {
            if (!SFMGamePuppetRenderHarness.await(performanceRenderTicket)) return false;
            var completed = C11SourceNavigationPuppetProbe.performance(restoreSource(workspace));
            require(completed.inputToFrameSamples() == completedInputToFrameSamples + 1L,
                    "A warm interaction did not produce exactly one completed input-to-frame sample: before="
                            + completedInputToFrameSamples + " after=" + completed.inputToFrameSamples());
            JsonObject sample = new JsonObject();
            sample.addProperty("ordinal", performanceInteractionIndex);
            sample.addProperty("cycle", performanceInteractionIndex / WARM_INTERACTION_KINDS);
            sample.addProperty("interaction", warmInteractionName(
                    performanceInteractionIndex % WARM_INTERACTION_KINDS
            ));
            sample.addProperty("input_to_frame_nanos", completed.latestInputToFrameNanos());
            performanceInputSamples.add(sample);
            completedInputToFrameSamples = completed.inputToFrameSamples();
            performanceRenderTicket = null;
            performanceInteractionIndex++;
        }

        if (performanceInteractionIndex < WARM_INTERACTION_COUNT) {
            int interaction = performanceInteractionIndex;
            performanceRenderTicket = SFMGamePuppetRenderHarness.beforeNextFrame(
                    workspace,
                    () -> runWarmInteraction(interaction % WARM_INTERACTION_KINDS, workspace)
            );
            return false;
        }

        source = restoreSource(workspace);
        var performance = C11SourceNavigationPuppetProbe.performance(source);
        JsonArray warmScript = new JsonArray();
        warmScript.add("pointer-move");
        warmScript.add("scroll-down");
        warmScript.add("scroll-up");
        warmScript.add("selection-click");
        warmScript.add("f12-context-capture");
        warmScript.add("pointer-move-second-symbol");
        evidence.add("warm_interaction_script", warmScript);
        evidence.addProperty("warm_measurement_window_reset", true);
        evidence.addProperty("warm_measurement_dispatch", "screen-render-pre");
        evidence.addProperty("warmup_render_pre_interactions", WARM_INTERACTION_KINDS);
        evidence.addProperty("warm_measurement_cycles", WARM_MEASUREMENT_CYCLES);
        evidence.addProperty("warm_completed_input_to_frame_samples", completedInputToFrameSamples);
        evidence.add("warm_input_to_frame_samples", performanceInputSamples);
        evidence.add("performance", performance(performance));
        evidence.addProperty("definition_fixture_count", definitionEvidence.size());
        evidence.addProperty("reference_rows_opened", openedReferenceEvidence.size());
        evidence.addProperty("performance_checkpoint", true);
        runtime.writeArtifact(
                artifactName + "-performance-checkpoint",
                SFMGamePuppetArtifactFormat.JSON,
                new GsonBuilder().setPrettyPrinting().create().toJson(evidence)
        );
        require(performance.frames() > 0, "EditorV3 produced no live frame samples");
        require(performance.inputToFrameMaximumNanos() > 0,
                "EditorV3 produced no live input-to-frame sample");
        require(performance.inputToFrameSamples() == WARM_INTERACTION_COUNT,
                "EditorV3 did not complete every render-pre interaction sample: "
                        + performance.inputToFrameSamples());
        require(performance.contextCaptures() > 0,
                "EditorV3 produced no contextual-capture sample");
        require(performance.inputEvents() >= 7L * WARM_MEASUREMENT_CYCLES,
                "EditorV3 warm script produced too few input events: " + performance.inputEvents());
        require(performance.inputApplications() == performance.inputEvents(),
                "EditorV3 did not synchronously apply every warm input event");
        require(performance.frameMedianNanos() <= FRAME_MEDIAN_BUDGET_NANOS,
                "EditorV3 frame median exceeded 16.7 ms: " + performance.frameMedianNanos());
        require(performance.frameP95Nanos() <= FRAME_P95_BUDGET_NANOS,
                "EditorV3 frame p95 exceeded 33.3 ms: " + performance.frameP95Nanos());
        require(performance.frameMaximumNanos() < FRAME_PAUSE_BUDGET_NANOS,
                "EditorV3 recorded a >=100 ms editor-attributed frame: " + performance.frameMaximumNanos());
        require(performance.inputToFrameP95Nanos() <= INPUT_P95_BUDGET_NANOS,
                "EditorV3 input-to-frame p95 exceeded 50 ms: " + performance.inputToFrameP95Nanos());
        advance(Phase.FINALIZE);
        return false;
    }

    private void runWarmInteraction(int interaction, SFMScreenMultiplexer workspace) {
        SFMSourcePuppetProbe.EditorHandle source = restoreSource(workspace);
        C11SourceNavigationPuppetProbe.Pointer outputStatement = warmPointer(
                workspace,
                source,
                "OutputStatement"
        );
        switch (interaction) {
            case 0 -> SFMGamePuppetPointer.move(
                    workspace,
                    outputStatement.globalX(),
                    outputStatement.globalY()
            );
            case 1 -> workspace.mouseScrolled(
                    outputStatement.globalX(),
                    outputStatement.globalY(),
                    -1.0D
            );
            case 2 -> workspace.mouseScrolled(
                    outputStatement.globalX(),
                    outputStatement.globalY(),
                    1.0D
            );
            case 3 -> {
                require(workspace.mouseClicked(
                                outputStatement.globalX(),
                                outputStatement.globalY(),
                                GLFW.GLFW_MOUSE_BUTTON_LEFT
                        ),
                        "The warm selection sample was not routed to EditorV3");
                workspace.mouseReleased(
                        outputStatement.globalX(),
                        outputStatement.globalY(),
                        GLFW.GLFW_MOUSE_BUTTON_LEFT
                );
            }
            case 4 -> {
                source.resolvedPanel().orElseThrow().keyPressed(GLFW.GLFW_KEY_F12, 0, 0);
                require(C11SourceNavigationPuppetProbe.focusedDocument(workspace).isPresent(),
                        "The warm F12-context capture sample was unavailable");
                source.resolvedPanel().orElseThrow().keyReleased(GLFW.GLFW_KEY_F12, 0, 0);
            }
            case 5 -> {
                C11SourceNavigationPuppetProbe.Pointer programContext = warmPointer(
                        workspace,
                        source,
                        "ProgramContext"
                );
                SFMGamePuppetPointer.move(
                        workspace,
                        programContext.globalX(),
                        programContext.globalY()
                );
            }
            default -> throw new IllegalArgumentException("Unknown warm interaction index " + interaction);
        }
    }

    private static String warmInteractionName(int interaction) {
        return switch (interaction) {
            case 0 -> "pointer-move";
            case 1 -> "scroll-down";
            case 2 -> "scroll-up";
            case 3 -> "selection-click";
            case 4 -> "f12-context-capture";
            case 5 -> "pointer-move-second-symbol";
            default -> throw new IllegalArgumentException("Unknown warm interaction index " + interaction);
        };
    }

    private static C11SourceNavigationPuppetProbe.Pointer warmPointer(
            SFMScreenMultiplexer workspace,
            SFMSourcePuppetProbe.EditorHandle source,
            String symbol
    ) {
        SFMTextDocumentSnapshot document = source.state().documentSnapshot().orElseThrow();
        return C11SourceNavigationPuppetProbe.pointer(
                workspace,
                source,
                SFMSourcePuppetProbe.symbolRange(document.text(), symbol, 0)
        );
    }

    private boolean finalizeEvidence(ISFMGamePuppetRuntime runtime) {
        require(definitionEvidence.size() == fixtures.size(),
                "The C-11 journey did not exercise every definition fixture");
        require(methodDefinitionProven,
                "The C-11 journey did not prove method-symbol definition navigation");
        evidence.addProperty("definition_fixture_count", definitionEvidence.size());
        evidence.addProperty("reference_rows_opened", openedReferenceEvidence.size());
        evidence.addProperty("contextual_offer_parity", true);
        evidence.addProperty("no_unexpected_panes", true);
        evidence.addProperty("performance_checkpoint", false);
        evidence.addProperty("completed", true);
        runtime.writeArtifact(
                artifactName,
                SFMGamePuppetArtifactFormat.JSON,
                new GsonBuilder().setPrettyPrinting().create().toJson(evidence)
        );
        advance(Phase.COMPLETE);
        return true;
    }

    private SFMSourcePuppetProbe.EditorHandle restoreSource(SFMScreenMultiplexer workspace) {
        SFMSourcePuppetProbe.EditorHandle source = sourceEditor(workspace)
                .orElseThrow(() -> new IllegalStateException("OutputStatement.java editor disappeared"));
        require(workspace.focusPanel(source.panelId()), "OutputStatement.java editor could not be focused");
        sourcePanelId = source.panelId();
        return source;
    }

    private Optional<SFMSourcePuppetProbe.EditorHandle> sourceEditor(SFMScreenMultiplexer workspace) {
        return C11SourceNavigationPuppetProbe.editor(workspace, sourceFile);
    }

    private Optional<SFMSourcePuppetProbe.EditorHandle> focusedTarget(
            SFMScreenMultiplexer workspace,
            String rootRelativePath,
            String symbol,
            Optional<SFMTextDocumentRange> expectedRange
    ) {
        Optional<SFMSourcePuppetProbe.EditorHandle> focused = C11SourceNavigationPuppetProbe.focusedEditor(workspace);
        if (focused.isEmpty() || focused.orElseThrow().resolvedPanel().isEmpty()) return Optional.empty();
        SFMTextDocumentSnapshot document = focused.orElseThrow().state().documentSnapshot().orElse(null);
        if (document == null || !document.ready() || document.path().isEmpty()) return Optional.empty();
        String canonical = normalizeSuffix(document.path().orElseThrow().canonical());
        if (!canonical.toLowerCase(Locale.ROOT).endsWith(normalizeSuffix(rootRelativePath).toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        Optional<SFMTextDocumentRange> actualRange = C11SourceNavigationPuppetProbe.openTargetRange(focused.orElseThrow());
        if (actualRange.isEmpty()) return Optional.empty();
        if (expectedRange.isPresent() && !actualRange.orElseThrow().equals(expectedRange.orElseThrow())) {
            return Optional.empty();
        }
        if (!C11SourceNavigationPuppetProbe.textAtRange(document.text(), actualRange.orElseThrow()).equals(symbol)) {
            return Optional.empty();
        }
        return focused;
    }

    private void assertNoSurpriseSplit(
            SFMScreenMultiplexer workspace,
            int visibleBefore,
            SFMWorkspacePanelId targetPanelId
    ) {
        require(workspace.visiblePanelEntries().size() == visibleBefore,
                "Definition navigation created surprise split geometry");
        require(sameStack(workspace, sourcePanelId, targetPanelId),
                "Definition navigation escaped the originating source stack");
    }

    private static boolean sameStack(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId left,
            SFMWorkspacePanelId right
    ) {
        return workspace.panelStackId(left)
                .flatMap(leftStack -> workspace.panelStackId(right).map(leftStack::equals))
                .orElse(false);
    }

    private void assertReferenceIdentity(SFMScreenMultiplexer workspace) {
        require(referenceExplorer != null, "Reference explorer was not established");
        require(workspace.containsPanel(referenceExplorer.panelId()), "Reference explorer panel was discarded");
        require(workspace.panelInstance(referenceExplorer.panelId()) == referenceExplorer.panel(),
                "Reference explorer panel identity changed");
        require(referenceExplorer.panel().explorerId().value().equals(referenceExplorerIdentity),
                "Reference explorer session identity changed");
        require(workspace.panelStackId(referenceExplorer.panelId()).map(Object::toString)
                        .filter(referencePanelStackId::equals).isPresent(),
                "Reference explorer moved to another stack");
    }

    private static SFMScreenPanelBounds requireBounds(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId panelId
    ) {
        SFMScreenPanelBounds bounds = workspace.panelContentBounds(panelId);
        if (bounds == null) throw new IllegalStateException("Panel has no content bounds: " + panelId);
        return bounds;
    }

    private static JsonObject definitionResult(
            Fixture fixture,
            SFMTextDocumentRange sourceRange,
            SFMDefinitionLookupService.Lookup lookup,
            long durationMicros
    ) {
        SFMDefinitionResult result = lookup.result();
        JsonObject json = new JsonObject();
        json.addProperty("fixture", fixture.id());
        json.addProperty("symbol", fixture.symbol());
        json.addProperty("expected_kind", fixture.expectedKind());
        json.addProperty("expected_resolver", fixture.expectedResolver());
        json.addProperty("worker_protocol", lookup.hello().protocolSchema());
        json.addProperty("worker_version", lookup.hello().serverVersion());
        json.addProperty("request_id", result.requestId());
        json.addProperty("request_generation", result.requestGeneration());
        json.addProperty("workspace_generation", result.workspaceGeneration());
        json.addProperty("outcome", result.outcome().wireName());
        json.addProperty("completeness", result.completeness().wireName());
        json.addProperty("index_fingerprint", result.context().indexFingerprint());
        json.addProperty("classpath_fingerprint", result.context().classpathFingerprint());
        json.addProperty("lookup_duration_micros", durationMicros);
        json.add("source_range", C11SourceNavigationPuppetProbe.range(sourceRange));
        JsonArray definitions = new JsonArray();
        for (SFMDefinitionResult.Definition definition : result.definitions()) {
            JsonObject item = new JsonObject();
            item.addProperty("kind", definition.symbol().kind());
            item.addProperty("qualified_name", definition.symbol().qualifiedName());
            item.addProperty("confidence", definition.confidence());
            item.add("identifier_span", span(definition.identifierSpan()));
            item.add("declaration_span", span(definition.declarationSpan()));
            definitions.add(item);
        }
        json.add("definitions", definitions);
        json.add("dependency_index", dependencyIndex(result));
        json.add("diagnostics", diagnostics(result.diagnostics()));
        return json;
    }

    private static JsonObject usageResult(
            SFMUsageAtPositionResult result,
            SFMReferenceLookupService.Lookup lookup,
            long durationMicros
    ) {
        JsonObject json = new JsonObject();
        json.addProperty("worker_protocol", lookup.hello().protocolSchema());
        json.addProperty("worker_version", lookup.hello().serverVersion());
        json.addProperty("request_id", result.requestId());
        json.addProperty("request_generation", result.requestGeneration());
        json.addProperty("workspace_generation", result.workspaceGeneration());
        json.addProperty("outcome", result.outcome().wireName());
        json.addProperty("completeness", result.completeness().wireName());
        json.addProperty("usage_count", result.usages().size());
        json.addProperty("target_count", result.targets().size());
        json.addProperty("lookup_duration_micros", durationMicros);
        json.addProperty("index_fingerprint", result.context().indexFingerprint());
        json.add("dependency_index", dependencyIndex(result.dependencyIndex()));
        json.add("diagnostics", diagnostics(result.diagnostics()));
        JsonArray usages = new JsonArray();
        result.usages().forEach(usage -> {
            JsonObject item = new JsonObject();
            item.addProperty("kind", usage.kind().wireName());
            item.addProperty("target", usage.target().qualifiedName());
            item.addProperty("confidence", usage.confidence());
            item.add("span", span(usage.span()));
            usages.add(item);
        });
        json.add("usages", usages);
        return json;
    }

    private static JsonObject performance(
            ca.teamdman.sfm.client.screen.SFMDrawCanvasPerformanceTracker.Snapshot performance
    ) {
        JsonObject json = new JsonObject();
        json.addProperty("cold_load_nanos", performance.coldLoadNanos());
        json.addProperty("frames", performance.frames());
        json.addProperty("frame_median_nanos", performance.frameMedianNanos());
        json.addProperty("frame_p95_nanos", performance.frameP95Nanos());
        json.addProperty("frame_maximum_nanos", performance.frameMaximumNanos());
        json.addProperty("input_to_frame_median_nanos", performance.inputToFrameMedianNanos());
        json.addProperty("input_to_frame_p95_nanos", performance.inputToFrameP95Nanos());
        json.addProperty("input_to_frame_maximum_nanos", performance.inputToFrameMaximumNanos());
        json.addProperty("input_to_frame_samples", performance.inputToFrameSamples());
        json.addProperty("latest_input_to_frame_nanos", performance.latestInputToFrameNanos());
        json.addProperty("input_events", performance.inputEvents());
        json.addProperty("input_applications", performance.inputApplications());
        json.addProperty("input_apply_median_nanos", performance.inputApplyMedianNanos());
        json.addProperty("input_apply_p95_nanos", performance.inputApplyP95Nanos());
        json.addProperty("input_apply_maximum_nanos", performance.inputApplyMaximumNanos());
        json.addProperty("frame_allocation_measurement_available",
                performance.frameAllocationMeasurementAvailable());
        json.addProperty("frame_allocation_samples", performance.frameAllocationSamples());
        json.addProperty("frame_allocated_median_bytes", performance.frameAllocatedMedianBytes());
        json.addProperty("frame_allocated_p95_bytes", performance.frameAllocatedP95Bytes());
        json.addProperty("frame_allocated_maximum_bytes", performance.frameAllocatedMaximumBytes());
        json.addProperty("viewport_rebuilds", performance.viewportRebuilds());
        json.addProperty("viewport_cache_hits", performance.viewportCacheHits());
        json.addProperty("viewport_resolve_nanos", performance.viewportResolveNanos());
        json.addProperty("glyphs_visited", performance.glyphsVisited());
        json.addProperty("glyphs_drawn", performance.glyphsDrawn());
        json.addProperty("context_captures", performance.contextCaptures());
        json.addProperty("context_capture_nanos", performance.contextCaptureNanos());
        json.addProperty("syntax_worker_submissions", performance.syntaxWorkerSubmissions());
        json.addProperty("style_projection_rebuilds", performance.styleProjectionRebuilds());
        json.addProperty("style_projection_nanos", performance.styleProjectionNanos());
        json.addProperty("selection_geometry_rebuilds", performance.selectionGeometryRebuilds());
        json.addProperty("selection_geometry_cache_hits", performance.selectionGeometryCacheHits());
        json.addProperty("selection_geometry_nanos", performance.selectionGeometryNanos());
        json.addProperty("open_target_geometry_builds", performance.openTargetGeometryBuilds());
        json.addProperty("open_target_geometry_nanos", performance.openTargetGeometryNanos());
        json.addProperty("frame_median_budget_nanos", FRAME_MEDIAN_BUDGET_NANOS);
        json.addProperty("frame_p95_budget_nanos", FRAME_P95_BUDGET_NANOS);
        json.addProperty("frame_pause_budget_nanos", FRAME_PAUSE_BUDGET_NANOS);
        json.addProperty("input_p95_budget_nanos", INPUT_P95_BUDGET_NANOS);
        json.addProperty("input_sample_present", performance.inputToFrameMaximumNanos() > 0);
        json.addProperty("context_sample_present", performance.contextCaptures() > 0);
        json.addProperty("budgets_met", performance.frames() > 0
                && performance.inputToFrameMaximumNanos() > 0
                && performance.inputToFrameSamples() == WARM_INTERACTION_COUNT
                && performance.contextCaptures() > 0
                && performance.frameMedianNanos() <= FRAME_MEDIAN_BUDGET_NANOS
                && performance.frameP95Nanos() <= FRAME_P95_BUDGET_NANOS
                && performance.frameMaximumNanos() < FRAME_PAUSE_BUDGET_NANOS
                && performance.inputToFrameP95Nanos() <= INPUT_P95_BUDGET_NANOS);
        return json;
    }

    private static JsonObject span(SFMDefinitionResult.DefinitionSourceSpan span) {
        JsonObject json = new JsonObject();
        json.addProperty("address", span.address());
        json.addProperty("resolver_id", span.resolverId());
        json.addProperty("root_id", span.rootId());
        json.addProperty("root_relative_path", span.rootRelativePath());
        json.addProperty("report_path", span.reportPath());
        json.addProperty("source_set", span.sourceSet());
        json.addProperty("source_hash", span.sourceHash());
        json.addProperty("source_sha256", span.sourceSha256().orElse("missing"));
        json.addProperty("start_byte", span.startByte());
        json.addProperty("end_byte", span.endByte());
        json.addProperty("start_line", span.startLine());
        json.addProperty("start_column", span.startColumn());
        json.addProperty("end_line", span.endLine());
        json.addProperty("end_column", span.endColumn());
        return json;
    }

    private static JsonObject hoverGlyphRange(
            ca.teamdman.sfm.client.symbol.SFMSymbolHoverIdentity.TextGlyphRange range
    ) {
        JsonObject json = new JsonObject();
        json.addProperty("utf16_start", range.utf16Start());
        json.addProperty("utf16_end", range.utf16End());
        json.addProperty("utf8_start", range.utf8Start());
        json.addProperty("utf8_end", range.utf8End());
        json.addProperty("glyph_start", range.glyphStart());
        json.addProperty("glyph_end", range.glyphEnd());
        return json;
    }

    private static JsonArray diagnostics(List<SFMDefinitionResult.Diagnostic> diagnostics) {
        JsonArray array = new JsonArray();
        diagnostics.forEach(diagnostic -> {
            JsonObject item = new JsonObject();
            item.addProperty("code", diagnostic.code());
            item.addProperty("severity", diagnostic.severity());
            item.addProperty("message", diagnostic.message());
            array.add(item);
        });
        return array;
    }

    private static JsonObject dependencyIndex(SFMDefinitionResult result) {
        return dependencyIndex(result.dependencyIndex());
    }

    private static JsonObject dependencyIndex(Optional<SFMDefinitionResult.DependencyIndex> dependencyIndex) {
        JsonObject json = new JsonObject();
        if (dependencyIndex.isEmpty()) {
            json.addProperty("present", false);
            return json;
        }
        SFMDefinitionResult.DependencyIndex index = dependencyIndex.orElseThrow();
        json.addProperty("present", true);
        json.addProperty("status", index.status());
        json.addProperty("completeness", index.completeness().wireName());
        json.addProperty("expected_identity", index.expectedIdentity());
        json.addProperty("portable_path", index.portablePath());
        json.addProperty("reason", index.reason());
        return json;
    }

    private static SFMTextDocumentRange range(SFMDefinitionResult.DefinitionSourceSpan span) {
        return new SFMTextDocumentRange(
                new SFMTextDocumentPosition(
                        Math.toIntExact(span.startLine() - 1),
                        Math.toIntExact(span.startColumn() - 1),
                        Math.toIntExact(span.startByte())
                ),
                new SFMTextDocumentPosition(
                        Math.toIntExact(span.endLine() - 1),
                        Math.toIntExact(span.endColumn() - 1),
                        Math.toIntExact(span.endByte())
                )
        );
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private void failOnNavigationToast(SFMScreenMultiplexer workspace, String operation) {
        Optional<String> toast = SFMSourcePuppetProbe.workspaceToastText(workspace);
        if (toast.isPresent() && (toast.orElseThrow().contains("unavailable")
                || toast.orElseThrow().contains("No symbol")
                || toast.orElseThrow().contains("no worker source root"))) {
            fail(operation + " failed: " + toast.orElseThrow());
        }
    }

    private static boolean contains(SFMPath root, SFMPath path) {
        if (root.kind() != SFMPath.Kind.FILE || path.kind() != SFMPath.Kind.FILE) return false;
        return path.toNativePath().toAbsolutePath().normalize()
                .startsWith(root.toNativePath().toAbsolutePath().normalize());
    }

    private static String normalizeSuffix(String value) {
        return requireNonBlank(value, "path suffix").replace('\\', '/').strip();
    }

    private SFMScreenMultiplexer requireWorkspace() {
        SFMScreenMultiplexer workspace = workspaceOrNull();
        if (workspace == null) {
            throw new IllegalStateException("Expected the C-11 workspace but found "
                    + (Minecraft.getInstance().screen == null
                    ? "world" : Minecraft.getInstance().screen.getClass().getName()));
        }
        return workspace;
    }

    private static SFMScreenMultiplexer workspaceOrNull() {
        return Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace ? workspace : null;
    }

    private static SFMCommandPaletteScreen requirePalette() {
        if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette) return palette;
        throw new IllegalStateException("Expected a constrained command palette");
    }

    private void advance(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    private static long microsSince(long startedNanos) {
        return Math.max(0L, System.nanoTime() - startedNanos) / 1_000L;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable result = failure;
        while ((result instanceof CompletionException
                || result instanceof java.util.concurrent.ExecutionException)
                && result.getCause() != null) result = result.getCause();
        return result;
    }

    private static Component caption(String text) {
        return Component.literal("SFM Source Navigation — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }

    private static String requireNonBlank(String value, String label) {
        String result = Objects.requireNonNull(value, label).strip();
        if (result.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void fail(String message) {
        throw new IllegalStateException(message);
    }
}
