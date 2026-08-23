package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.action.SFMContextActionsOpenAction;
import ca.teamdman.sfm.client.action.SFMJumpToDefinitionAction;
import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextContributor;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.context.SFMContextCursorProjection;
import ca.teamdman.sfm.client.context.SFMContextPosition;
import ca.teamdman.sfm.client.history.SFMDocumentHistoryHost;
import ca.teamdman.sfm.client.history.SFMDocumentHistoryHostController;
import ca.teamdman.sfm.client.history.SFMDocumentHistoryInputTarget;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistoryRuntime;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasScreen;
import ca.teamdman.sfm.client.screen.SFMTextEditorV3Screen;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelActionExecution;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.semantic.SFMCanvasSpatialCoverageSnapshot;
import ca.teamdman.sfm.client.semantic.SFMJavaInteractionMapSpatialAdapter;
import ca.teamdman.sfm.client.semantic.SFMSpatialCoverageService;
import ca.teamdman.sfm.client.semantic.SFMSpatialSemanticContract;
import ca.teamdman.sfm.client.symbol.SFMDefinitionLookupService;
import ca.teamdman.sfm.client.symbol.SFMDefinitionRequest;
import ca.teamdman.sfm.client.symbol.SFMDefinitionResult;
import ca.teamdman.sfm.client.symbol.SFMJavaInteractionMap;
import ca.teamdman.sfm.client.symbol.SFMJavaInteractionMapSession;
import ca.teamdman.sfm.client.symbol.SFMSymbolHoverIdentity;
import ca.teamdman.sfm.client.symbol.SFMSymbolHoverLookup;
import ca.teamdman.sfm.client.symbol.SFMSymbolHoverStateMachine;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionEvidenceSource;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionSnapshot;
import ca.teamdman.sfm.client.symbol.SFMSymbolNavigationRuntime;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.lwjgl.glfw.GLFW;

/**
 * Panel adapter for text editors.  Text Editor v3 uses the specialised
 * callback-aware screen below; legacy registrations still get a useful
 * lifecycle adapter while they are being migrated.
 */
public final class SFMTextEditorPanel implements SFMScreenPanel, SFMTextDocumentPanelState, SFMContextContributor,
        SFMSymbolInspectionEvidenceSource, SFMDocumentHistoryHost, SFMDocumentHistoryInputTarget {
    private static final String CONTEXT_CONTRIBUTOR_ID = "sfm:text-editor";
    private static final double DEFINITION_DRAG_THRESHOLD_PIXELS = 3.0D;
    private static final AtomicLong NEXT_HISTORY_SESSION = new AtomicLong();
    private final SFMTextEditorPanelOpenContext openContext;
    private final Screen screen;
    private final boolean independentDocumentHistory;
    private SFMTextDocumentSnapshot presentedDocument;
    private final SFMTextEditorHoverCaptureCache<Optional<HoverCapture>> hoverCaptureCache =
            new SFMTextEditorHoverCaptureCache<>();
    private SFMWorkspacePanelContext panelContext;
    private SFMSymbolHoverStateMachine symbolHover;
    private SFMEditorLinkCursorHost linkCursor;
    private SFMContextContribution hoverContribution;
    private SFMContextDocumentProjection contextualProjectionOverride;
    private SFMDrawCanvasScreen.SymbolHit hoverHit;
    private SFMDrawCanvasScreen.SymbolHit capturedHoverHit;
    private boolean capturedHoverClick;
    private double capturedPressX;
    private double capturedPressY;
    private double lastMouseX;
    private double lastMouseY;
    private boolean pointerInside;
    private boolean hoverFocused;
    private long observedDocumentGeneration = -1L;
    private String observedDocumentContentHash = "";
    private String observedSemanticFingerprint = "";
    private SFMJavaInteractionMapSession interactionMapSession;
    private SFMSymbolHoverIdentity.Modifiers hoverModifiers = SFMSymbolHoverIdentity.Modifiers.NONE;
    private final String historySessionId;
    private SFMDocumentHistoryHostController historyController;
    private SFMDocumentHistoryRuntime.Registration historyRegistration;
    private boolean historyFocused;

    private SFMTextEditorPanel(
            SFMTextEditorPanelOpenContext openContext,
            Screen screen,
            boolean independentDocumentHistory
    ) {
        this.openContext = openContext;
        this.screen = screen;
        this.independentDocumentHistory = independentDocumentHistory;
        this.presentedDocument = openContext.document();
        this.historySessionId = "sfm:document/text-editor/session-" + NEXT_HISTORY_SESSION.incrementAndGet();
    }

    public static SFMTextEditorPanel textEditorV3(SFMTextEditorPanelOpenContext context) {
        SFMTextEditorPanel[] holder = new SFMTextEditorPanel[1];
        ISFMTextEditScreenOpenContext screenContext = screenContext(
                context,
                () -> {
                    if (holder[0] != null) holder[0].requestClose();
                },
                value -> {
                    if (holder[0] != null) holder[0].documentSaved(value);
                }
        );
        PanelTextEditorScreen editor = new PanelTextEditorScreen(screenContext, () -> {
            if (holder[0] != null) holder[0].requestClose();
        });
        holder[0] = new SFMTextEditorPanel(context, editor, true);
        return holder[0];
    }

    /** Embeds the editor as the view of an already-authoritative temporal host. */
    public static SFMTextEditorPanel textEditorV3WithoutIndependentHistory(
            SFMTextEditorPanelOpenContext context
    ) {
        SFMTextEditorPanel[] holder = new SFMTextEditorPanel[1];
        ISFMTextEditScreenOpenContext screenContext = screenContext(
                context,
                () -> {
                    if (holder[0] != null) holder[0].requestClose();
                },
                value -> {
                    if (holder[0] != null) holder[0].documentSaved(value);
                }
        );
        PanelTextEditorScreen editor = new PanelTextEditorScreen(screenContext, () -> {
            if (holder[0] != null) holder[0].requestClose();
        });
        holder[0] = new SFMTextEditorPanel(context, editor, false);
        return holder[0];
    }

    public static SFMTextEditorPanel legacy(
            SFMTextEditorPanelOpenContext context,
            Function<ISFMTextEditScreenOpenContext, ISFMTextEditScreen> screenFactory
    ) {
        SFMTextEditorPanel[] holder = new SFMTextEditorPanel[1];
        ISFMTextEditScreenOpenContext screenContext = screenContext(
                context,
                () -> {
                    if (holder[0] != null) holder[0].requestClose();
                },
                value -> {
                    if (holder[0] != null) holder[0].documentSaved(value);
                }
        );
        Screen screen = screenFactory.apply(screenContext).asScreen();
        holder[0] = new SFMTextEditorPanel(context, screen, false);
        return holder[0];
    }

    public String editorId() {
        return openContext.editorId();
    }

    private void documentSaved(String value) {
        presentedDocument = presentedDocument.withSavedText(value);
    }

    /** Explorer previews are reusable only while their document is immutable. */
    public boolean isReadOnly() {
        return openContext.readOnly();
    }

    @Override
    public String documentHistorySessionId() {
        return historySessionId;
    }

    @Override
    public boolean documentHistoryAvailable() {
        return historyController != null;
    }

    @Override
    public SFMDocumentHistorySession documentHistorySession() {
        if (historyController == null) {
            throw new IllegalStateException("This editor does not own a writable document history");
        }
        return historyController.session();
    }

    @Override
    public SFMDocumentHistoryHostController documentHistoryController() {
        if (historyController == null) {
            throw new IllegalStateException("This editor does not own a writable document history");
        }
        return historyController;
    }

    @Override
    public void recordDocumentRawInput(
            long clientTick,
            SFMDocumentHistoryContract.RawEventKind kind,
            String source,
            String code,
            Optional<String> text,
            int modifiers,
            boolean consumed,
            boolean delivered
    ) {
        if (historyController == null) return;
        historyController.recordRawInput(
                clientTick, kind, source, code, text, modifiers, consumed, delivered);
    }

    private void updateDocumentHistoryFocus(boolean focused) {
        if (historyFocused == focused) return;
        if (focused && historyRegistration != null) {
            historyRegistration.focus();
        }
        if (historyController != null) {
            historyController.recordRawInput(
                    SFMKeyBindingService.INSTANCE.currentTick(),
                    SFMDocumentHistoryContract.RawEventKind.FOCUS,
                    "text-editor-focus",
                    focused ? "gain" : "loss",
                    Optional.empty(),
                    0,
                    false,
                    true
            );
        }
        historyFocused = focused;
    }

    public Optional<SFMDocumentHistorySession> optionalDocumentHistorySession() {
        return historyController == null ? Optional.empty() : Optional.of(historyController.session());
    }

    public Optional<SFMDrawCanvasScreen.SyntaxPresentationEvidence> syntaxPresentationEvidence() {
        if (!(screen instanceof SFMDrawCanvasScreen drawCanvas)) return Optional.empty();
        return drawCanvas.syntaxPresentationEvidence();
    }

    /** Exact current projection used by controller-backed editable overlays. */
    public String currentText() {
        if (!(screen instanceof SFMDrawCanvasScreen drawCanvas)) {
            return presentedDocument.displayText();
        }
        return drawCanvas.currentDocumentText();
    }

    /** Programmatic revision checkout; callers must suppress their own feedback loop. */
    public void checkoutDocument(
            String text,
            List<SFMTextDocumentRange> selectionRanges
    ) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(selectionRanges, "selectionRanges");
        if (!(screen instanceof SFMDrawCanvasScreen drawCanvas)) {
            throw new UnsupportedOperationException("This editor does not expose a document checkout surface");
        }
        drawCanvas.checkoutDocument(text, selectionRanges);
        presentedDocument = SFMTextDocumentSnapshot.literal(text);
    }

    void checkoutDocumentHistoryState(SFMDocumentHistoryContract.DocumentState state) {
        if (!(screen instanceof SFMDrawCanvasScreen drawCanvas)) {
            throw new UnsupportedOperationException("This editor does not expose a document checkout surface");
        }
        ArrayList<SFMDocumentHistoryContract.LogicalSelection> ordered = new ArrayList<>(state.selections());
        state.primarySelectionId().ifPresent(primary -> ordered.sort((left, right) -> {
            if (left.id().equals(primary)) return right.id().equals(primary) ? 0 : -1;
            if (right.id().equals(primary)) return 1;
            return left.id().compareTo(right.id());
        }));
        ArrayList<SFMTextDocumentSelection> selections = new ArrayList<>();
        for (SFMDocumentHistoryContract.LogicalSelection selection : ordered) {
            int anchorUtf16 = state.text().offsetByCodePoints(0, selection.anchor().codePointOffset());
            int activeUtf16 = state.text().offsetByCodePoints(0, selection.active().codePointOffset());
            selections.add(new SFMTextDocumentSelection(
                    selection.id(),
                    SFMContextTextCoordinates.atUtf16Offset(state.text(), anchorUtf16),
                    SFMContextTextCoordinates.atUtf16Offset(state.text(), activeUtf16),
                    state.primarySelectionId().filter(selection.id()::equals).isPresent()
            ));
        }
        drawCanvas.checkoutDocumentSelections(state.text(), selections);
    }

    SFMDocumentHistoryContract.DocumentState captureDocumentHistoryState(
            SFMDrawCanvasScreen drawCanvas
    ) {
        SFMContextDocumentProjection projection = drawCanvas.captureContextProjection(
                openContext.editorId(), openContext.document(), isReadOnly());
        String text = projection.currentText();
        Optional<List<SFMTextDocumentSelection>> exact = drawCanvas.exactDocumentSelections();
        if (exact.isPresent()) {
            ArrayList<SFMDocumentHistoryContract.LogicalSelection> selections = new ArrayList<>();
            String primary = null;
            for (SFMTextDocumentSelection selection : exact.orElseThrow()) {
                int anchorUtf16 = SFMContextTextCoordinates.utf16OffsetAtUtf8Byte(
                        text, selection.anchor().byteOffset());
                int activeUtf16 = SFMContextTextCoordinates.utf16OffsetAtUtf8Byte(
                        text, selection.active().byteOffset());
                selections.add(new SFMDocumentHistoryContract.LogicalSelection(
                        selection.id(),
                        SFMDocumentHistoryContract.LogicalPoint.at(
                                text, text.codePointCount(0, anchorUtf16)),
                        SFMDocumentHistoryContract.LogicalPoint.at(
                                text, text.codePointCount(0, activeUtf16))
                ));
                if (selection.primary()) primary = selection.id();
            }
            if (selections.isEmpty()) return SFMDocumentHistoryContract.DocumentState.withoutSelection(text);
            return new SFMDocumentHistoryContract.DocumentState(
                    text,
                    selections,
                    Optional.of(Objects.requireNonNull(primary, "primary selection"))
            );
        }
        ArrayList<SFMDocumentHistoryContract.LogicalSelection> selections = new ArrayList<>();
        String primary = null;
        for (SFMContextCursorProjection cursor : projection.cursors()) {
            Optional<ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition> hit = Optional.empty();
            if (cursor.position() instanceof SFMContextPosition.Text textPosition) {
                hit = Optional.of(textPosition.position());
            } else if (cursor.position() instanceof SFMContextPosition.Canvas canvasPosition) {
                hit = canvasPosition.textHit();
            }
            int codePointOffset = hit.map(position -> {
                int utf16 = SFMContextTextCoordinates.utf16OffsetAtUtf8Byte(text, position.byteOffset());
                return text.codePointCount(0, utf16);
            }).orElseGet(() -> SFMDocumentHistoryContract.codePointLength(text));
            SFMDocumentHistoryContract.LogicalPoint point =
                    SFMDocumentHistoryContract.LogicalPoint.at(text, codePointOffset);
            selections.add(new SFMDocumentHistoryContract.LogicalSelection(
                    cursor.id(), point, point));
            if (cursor.primary()) primary = cursor.id();
        }
        if (selections.isEmpty()) {
            return SFMDocumentHistoryContract.DocumentState.withCaret(
                    text, SFMDocumentHistoryContract.codePointLength(text));
        }
        return new SFMDocumentHistoryContract.DocumentState(
                text,
                selections,
                Optional.of(Objects.requireNonNull(primary, "primary cursor"))
        );
    }

    private void observeHistoryMutation(
            SFMDocumentHistoryContract.DocumentState before,
            SFMDocumentHistoryContract.MutationKind kind,
            SFMDocumentHistoryContract.EditDirection direction,
            Optional<String> changedText,
            String cause
    ) {
        if (historyController == null || historyController.checkoutInProgress()
                || !(screen instanceof SFMDrawCanvasScreen drawCanvas)) return;
        if (!historyController.session().currentState().equals(before)) return;
        SFMDocumentHistoryContract.DocumentState after = captureDocumentHistoryState(drawCanvas);
        if (after.equals(before)) return;
        historyController.observeMutation(
                kind,
                direction,
                before,
                after,
                changedText,
                SFMKeyBindingService.INSTANCE.currentTick(),
                cause
        );
    }

    private Optional<SFMDocumentHistoryContract.DocumentState> beforeHistoryMutation() {
        if (historyController == null || !(screen instanceof SFMDrawCanvasScreen drawCanvas)) {
            return Optional.empty();
        }
        return Optional.of(captureDocumentHistoryState(drawCanvas));
    }

    @Override
    public Optional<SFMTextDocumentSnapshot> documentSnapshot() {
        return Optional.of(presentedDocument);
    }

    @Override
    public boolean navigateToRange(SFMTextDocumentRange range) {
        if (!(screen instanceof SFMDrawCanvasScreen drawCanvas)) return false;
        range.validateAgainst(drawCanvas.captureContextProjection(
                openContext.editorId(), openContext.document(), isReadOnly()).currentText());
        drawCanvas.openAtTextRange(range);
        presentedDocument = withTargetRange(presentedDocument, range);
        return true;
    }

    private static SFMTextDocumentSnapshot withTargetRange(
            SFMTextDocumentSnapshot document,
            SFMTextDocumentRange range
    ) {
        return new SFMTextDocumentSnapshot(
                document.state(),
                document.text(),
                document.mutationCapability(),
                document.path(),
                document.authorizedRoot(),
                document.sha256(),
                document.byteLength(),
                document.lastModified(),
                document.lineEndingKind(),
                Optional.of(range),
                document.diagnostics(),
                document.sourceRootIdentity(),
                document.analysisIdentity()
        );
    }

    @Override
    public String id() {
        return CONTEXT_CONTRIBUTOR_ID;
    }

    @Override
    public Optional<SFMContextOriginId> focusedOriginId() {
        return panelContext == null ? Optional.empty() : Optional.of(contextOrigin());
    }

    @Override
    public java.util.List<SFMContextContribution> capture(SFMContextCaptureRequest request) {
        if (panelContext == null) return java.util.List.of();
        SFMContextDocumentProjection projection;
        long generation;
        if (contextualProjectionOverride != null) {
            projection = contextualProjectionOverride;
            generation = screen instanceof SFMDrawCanvasScreen drawCanvas ? drawCanvas.contextGeneration() : 0;
        } else if (screen instanceof SFMDrawCanvasScreen drawCanvas) {
            projection = drawCanvas.captureContextProjection(
                    openContext.editorId(),
                    openContext.document(),
                    isReadOnly()
            );
            generation = drawCanvas.contextGeneration();
        } else {
            projection = SFMContextDocumentProjection.capture(
                    openContext.editorId(),
                    openContext.document(),
                    openContext.document().text(),
                    false,
                    isReadOnly(),
                    java.util.List.of(),
                    java.util.List.of()
            );
            generation = 0;
        }
        return java.util.List.of(new SFMContextContribution(
                contextOrigin(),
                new SFMContextGenerationEvidence(generation, generation, generation, 0),
                projection
        ));
    }

    private SFMContextOriginId contextOrigin() {
        return new SFMContextOriginId(
                CONTEXT_CONTRIBUTOR_ID,
                "panel-" + panelContext.panelId().value(),
                "document"
        );
    }

    @Override
    public Component title() {
        return Component.literal(openContext.title());
    }

    @Override
    public ResourceLocation keyboardUsageSituationId() {
        return isReadOnly()
                ? SFMKeyboardUsageSituations.TEXT_EDITOR
                : SFMKeyboardUsageSituations.TEMPORAL_DOCUMENT;
    }

    @Override
    public Component narration() {
        return title().copy().append(Component.literal(openContext.readOnly() ? " (read-only)" : ""));
    }

    /** Captures one exact, generation-consistent canvas plus Rust semantic map. */
    public Optional<SpatialCoverageCapture> captureSpatialCoverage() {
        if (!(screen instanceof SFMDrawCanvasScreen drawCanvas)) return Optional.empty();
        SFMJavaInteractionMap.Result map = currentInteractionMap(drawCanvas).orElse(null);
        if (map == null) return Optional.empty();
        SFMContextDocumentProjection projection = drawCanvas.captureContextProjection(
                openContext.editorId(), openContext.document(), isReadOnly());
        if (!SFMDefinitionRequest.sha256(projection.currentText()).equals(map.document().contentHash())
                || map.documentGeneration() != drawCanvas.documentGeneration()) {
            return Optional.empty();
        }
        SFMDrawCanvasScreen.SpatialLayoutSnapshot layout = drawCanvas.captureSpatialLayout();
        SFMSpatialCoverageService.Document document = SFMCanvasSpatialCoverageSnapshot.capture(
                map.document().address(),
                map.document().sourceSet(),
                map.document().contentHash(),
                layout.index(),
                layout.lineHeight(),
                map.workspaceGeneration(),
                map.documentGeneration(),
                layout.generation(),
                new SFMJavaInteractionMapSpatialAdapter(projection.currentText(), map)
        );
        SFMSpatialSemanticContract.SnapshotIdentity identity =
                new SFMSpatialSemanticContract.SnapshotIdentity(
                        map.workspaceFingerprint(),
                        map.workspaceGeneration(),
                        map.document().address(),
                        map.document().contentHash(),
                        map.documentGeneration(),
                        map.semanticFingerprint(),
                        map.semanticGeneration(),
                        layout.fingerprint(),
                        layout.generation()
                );
        return Optional.of(new SpatialCoverageCapture(identity, document, map.files()));
    }

    /** Current exact Rust semantic publication for context-aware review selector proposals. */
    public Optional<SFMJavaInteractionMap.Result> currentJavaInteractionMap() {
        if (!(screen instanceof SFMDrawCanvasScreen drawCanvas)) return Optional.empty();
        return currentInteractionMap(drawCanvas);
    }

    @Override
    public Optional<SFMSymbolInspectionSnapshot.SemanticEvidence> captureSymbolInspectionEvidence(
            SFMContextDocumentProjection document,
            SFMSymbolInspectionSnapshot.CapturedPoint point
    ) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(point, "point");
        if (!(screen instanceof SFMDrawCanvasScreen drawCanvas)) return Optional.empty();
        SFMJavaInteractionMap.Result map = currentInteractionMap(drawCanvas).orElse(null);
        if (map == null) {
            return Optional.of(lexicalInspectionEvidence(
                    drawCanvas,
                    document,
                    point,
                    SFMSymbolInspectionSnapshot.DocumentEvidence.empty(),
                    List.of("java.semantic-map-unavailable: no current semantic publication matched the editor document")
            ));
        }
        if (!map.document().contentHash().equals(SFMDefinitionRequest.sha256(document.currentText()))) {
            return Optional.of(lexicalInspectionEvidence(
                    drawCanvas,
                    document,
                    point,
                    interactionMapDocumentEvidence(map),
                    List.of("java.semantic-map-stale: the semantic publication content hash did not match the captured editor text")
            ));
        }
        long probe = SFMSymbolInspectionSnapshot.semanticProbeByte(document.currentText(), point.position());
        SFMJavaInteractionMap.Region target = map.mostSpecificRegionAtByte(probe).orElse(null);
        if (target == null) {
            ArrayList<String> diagnostics = new ArrayList<>();
            diagnostics.add("java.semantic-region-unavailable: the current semantic map contains no exact region at byte "
                    + probe);
            diagnostics.addAll(interactionMapDiagnosticsAtByte(map.diagnostics(), probe));
            return Optional.of(lexicalInspectionEvidence(
                    drawCanvas,
                    document,
                    point,
                    interactionMapDocumentEvidence(map),
                    diagnostics
            ));
        }

        ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange textRange;
        try {
            List<Integer> offsets = SFMContextTextCoordinates.utf16OffsetsAtUtf8Bytes(
                    document.currentText(),
                    List.of(Math.toIntExact(target.startByte()), Math.toIntExact(target.endByte()))
            );
            textRange = SFMContextTextCoordinates.rangeAtUtf16Offsets(
                    document.currentText(), offsets.get(0), offsets.get(1));
        } catch (ArithmeticException | IllegalArgumentException invalidRange) {
            return Optional.empty();
        }

        List<SFMJavaInteractionMap.Outlink> touching = map.outlinks().stream()
                .filter(value -> value.sourceRegionId().equals(target.id())
                        || value.destinationRegionId().equals(target.id()))
                .sorted(Comparator.comparing(SFMJavaInteractionMap.Outlink::id))
                .toList();
        List<String> selectors = touching.stream()
                .flatMap(value -> value.actionDrafts().stream())
                .filter(value -> value.actionId().equals("sfm:symbol/definition/open")
                        || value.actionId().equals("sfm:symbol/references/open"))
                .filter(value -> !value.arguments().isEmpty())
                .map(value -> value.arguments().get(0))
                .distinct()
                .sorted()
                .toList();
        List<SFMSymbolInspectionSnapshot.Outlink> outlinks = touching.stream()
                .map(SFMTextEditorPanel::inspectionOutlink)
                .toList();

        ArrayList<String> diagnostics = new ArrayList<>();
        for (SFMDefinitionResult.Diagnostic diagnostic : map.diagnostics()) {
            diagnostics.add(diagnostic.code() + " [" + diagnostic.severity() + "]: " + diagnostic.message());
        }
        map.classification(target.id()).flatMap(SFMJavaInteractionMap.Classification::reasonCode)
                .ifPresent(value -> diagnostics.add("classification: " + value));
        map.exceptions().stream()
                .filter(value -> value.regionId().equals(target.id()))
                .forEach(value -> diagnostics.add(value.code() + ": " + value.reason()
                        + " (" + value.effect().wireName() + "; " + value.witness() + ")"));

        List<String> logicalPath = map.regions().stream()
                .filter(value -> value.startByte() <= target.startByte()
                        && target.endByte() <= value.endByte())
                .sorted(Comparator.comparingLong(SFMJavaInteractionMap.Region::byteLength).reversed()
                        .thenComparing(SFMJavaInteractionMap.Region::id))
                .map(value -> value.semanticKind() + "[" + value.id() + "]")
                .toList();
        SFMSymbolInspectionSnapshot.DocumentEvidence documentEvidence = interactionMapDocumentEvidence(map);

        SFMDrawCanvasScreen.SpatialLayoutSnapshot layout = drawCanvas.captureSpatialLayout();
        InspectionGeometry geometry = inspectionGeometry(layout, document.currentText(), textRange);
        return Optional.of(new SFMSymbolInspectionSnapshot.SemanticEvidence(
                document.currentSha256(),
                point.position(),
                documentEvidence,
                textRange,
                Optional.of(target.id()),
                target.semanticKind(),
                logicalPath,
                geometry.glyphs(),
                selectors,
                summarize(touching, SFMJavaInteractionMap.Outlink::confidence, "unresolved"),
                summarize(touching, SFMJavaInteractionMap.Outlink::completeness, "incomplete"),
                outlinks,
                diagnostics,
                geometry.canvasBounds(),
                geometry.localScreenBounds(),
                geometry.globalScreenBounds(),
                geometry.physicalPixelBounds(),
                Optional.of(new SFMSymbolInspectionSnapshot.Rectangle(
                        0, 0, layout.viewportWidth(), layout.viewportHeight()))
        ));
    }

    private SFMSymbolInspectionSnapshot.SemanticEvidence lexicalInspectionEvidence(
            SFMDrawCanvasScreen drawCanvas,
            SFMContextDocumentProjection document,
            SFMSymbolInspectionSnapshot.CapturedPoint point,
            SFMSymbolInspectionSnapshot.DocumentEvidence documentEvidence,
            List<String> diagnostics
    ) {
        Objects.requireNonNull(documentEvidence, "documentEvidence");
        diagnostics = List.copyOf(diagnostics);
        SFMDrawCanvasScreen.SpatialLayoutSnapshot layout = drawCanvas.captureSpatialLayout();
        InspectionGeometry geometry = inspectionGeometry(layout, document.currentText(), point.localRange());
        return new SFMSymbolInspectionSnapshot.SemanticEvidence(
                document.currentSha256(),
                point.position(),
                documentEvidence,
                point.localRange(),
                Optional.empty(),
                point.localKind(),
                List.of(),
                geometry.glyphs(),
                List.of(),
                "unresolved",
                "incomplete",
                List.of(),
                diagnostics,
                geometry.canvasBounds(),
                geometry.localScreenBounds(),
                geometry.globalScreenBounds(),
                geometry.physicalPixelBounds(),
                Optional.of(new SFMSymbolInspectionSnapshot.Rectangle(
                        0, 0, layout.viewportWidth(), layout.viewportHeight()))
        );
    }

    private static SFMSymbolInspectionSnapshot.DocumentEvidence interactionMapDocumentEvidence(
            SFMJavaInteractionMap.Result map
    ) {
        SFMJavaInteractionMap.FileRow sourceFile = map.files().stream()
                .filter(value -> value.address().equals(map.document().address())
                        && value.rootId().equals(map.document().rootId()))
                .findFirst()
                .orElse(null);
        return new SFMSymbolInspectionSnapshot.DocumentEvidence(
                Optional.of(map.document().address()),
                sourceFile == null ? Optional.empty() : Optional.of(sourceFile.resolverId()),
                Optional.of(map.document().rootId()),
                Optional.of(map.document().rootRelativePath()),
                Optional.of(map.document().reportPath()),
                Optional.of(map.document().sourceSet())
        );
    }

    static List<String> interactionMapDiagnosticsAtByte(
            List<SFMDefinitionResult.Diagnostic> diagnostics,
            long byteOffset
    ) {
        return diagnostics.stream()
                .filter(diagnostic -> diagnostic.span()
                        .filter(span -> span.startByte() <= byteOffset && byteOffset < span.endByte())
                        .isPresent())
                .map(diagnostic -> diagnostic.code() + " [" + diagnostic.severity() + "]: "
                        + diagnostic.message())
                .distinct()
                .sorted()
                .toList();
    }

    private static SFMSymbolInspectionSnapshot.Outlink inspectionOutlink(SFMJavaInteractionMap.Outlink value) {
        return new SFMSymbolInspectionSnapshot.Outlink(
                value.id(),
                value.relationKind(),
                value.destinationQuery(),
                value.providerId(),
                value.providerGeneration(),
                value.confidence(),
                value.completeness(),
                value.recommendedProjection(),
                value.reason(),
                value.provenance()
        );
    }

    private static String summarize(
            List<SFMJavaInteractionMap.Outlink> outlinks,
            java.util.function.Function<SFMJavaInteractionMap.Outlink, String> projection,
            String fallback
    ) {
        List<String> values = outlinks.stream().map(projection).distinct().sorted().toList();
        return values.isEmpty() ? fallback : String.join("|", values);
    }

    private InspectionGeometry inspectionGeometry(
            SFMDrawCanvasScreen.SpatialLayoutSnapshot layout,
            String expectedText,
            ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange textRange
    ) {
        var projection = layout.index().projection();
        if (!inspectionProjectionMatches(expectedText, projection.text())) return InspectionGeometry.EMPTY;
        List<Integer> offsets = SFMContextTextCoordinates.utf16OffsetsAtUtf8Bytes(
                expectedText,
                List.of(textRange.start().byteOffset(), textRange.end().byteOffset())
        );
        int start = offsets.get(0);
        int end = offsets.get(1);
        if (end > projection.text().length()
                || !expectedText.substring(start, end).equals(projection.text().substring(start, end))) {
            return InspectionGeometry.EMPTY;
        }
        LinkedHashSet<SFMDrawCanvasModel.CanvasGlyph> glyphs = new LinkedHashSet<>();
        for (int index = start; index < end && index < projection.glyphsByCharIndex().size(); index++) {
            SFMDrawCanvasModel.CanvasGlyph glyph = projection.glyphsByCharIndex().get(index);
            if (glyph != null) glyphs.add(glyph);
        }
        ArrayList<SFMSymbolInspectionSnapshot.Rectangle> canvas = new ArrayList<>();
        ArrayList<SFMSymbolInspectionSnapshot.Glyph> selectedGlyphs = new ArrayList<>();
        for (SFMDrawCanvasModel.CanvasGlyph glyph : glyphs) {
            SFMSymbolInspectionSnapshot.Rectangle glyphBounds = new SFMSymbolInspectionSnapshot.Rectangle(
                    glyph.x(), glyph.y(), glyph.x() + glyph.width(), glyph.y() + layout.lineHeight());
            addOrMergeRow(canvas, glyphBounds);
            int ordinal = layout.index().glyphOrdinalOf(glyph)
                    .orElseThrow(() -> new IllegalStateException("Selected glyph is absent from its document index"));
            int utf16Offset = layout.index().utf16OffsetOf(glyph)
                    .orElseThrow(() -> new IllegalStateException("Selected glyph has no UTF-16 projection"));
            selectedGlyphs.add(new SFMSymbolInspectionSnapshot.Glyph(
                    ordinal, utf16Offset, glyph.text(), glyphBounds));
        }
        ArrayList<SFMSymbolInspectionSnapshot.Rectangle> screen = new ArrayList<>();
        for (SFMSymbolInspectionSnapshot.Rectangle rectangle : canvas) {
            screen.add(new SFMSymbolInspectionSnapshot.Rectangle(
                    canvasToScreen(rectangle.left(), layout.camera().x(), layout.camera().zoom(),
                            layout.viewportWidth()),
                    canvasToScreen(rectangle.top(), layout.camera().y(), layout.camera().zoom(),
                            layout.viewportHeight()),
                    canvasToScreen(rectangle.right(), layout.camera().x(), layout.camera().zoom(),
                            layout.viewportWidth()),
                    canvasToScreen(rectangle.bottom(), layout.camera().y(), layout.camera().zoom(),
                            layout.viewportHeight())
            ));
        }
        ArrayList<SFMSymbolInspectionSnapshot.Rectangle> globalScreen = new ArrayList<>();
        ArrayList<SFMSymbolInspectionSnapshot.Rectangle> physicalPixels = new ArrayList<>();
        if (panelContext != null) {
            for (SFMSymbolInspectionSnapshot.Rectangle rectangle : screen) {
                SFMScreenPanelBounds logical = new SFMScreenPanelBounds(
                        (int) Math.floor(rectangle.left()),
                        (int) Math.floor(rectangle.top()),
                        Math.max(1, (int) Math.ceil(rectangle.right()) - (int) Math.floor(rectangle.left())),
                        Math.max(1, (int) Math.ceil(rectangle.bottom()) - (int) Math.floor(rectangle.top()))
                );
                panelContext.measure(logical).ifPresent(metrics -> {
                    globalScreen.add(inspectionRectangle(metrics.globalGuiLogicalBounds()));
                    physicalPixels.add(inspectionRectangle(metrics.physicalPixelBounds()));
                });
            }
        }
        return new InspectionGeometry(selectedGlyphs, canvas, screen, globalScreen, physicalPixels);
    }

    static boolean inspectionProjectionMatches(String documentText, String projectedText) {
        Objects.requireNonNull(documentText, "documentText");
        Objects.requireNonNull(projectedText, "projectedText");
        if (documentText.equals(projectedText)) return true;
        return trimTrailingLineEndings(documentText).equals(trimTrailingLineEndings(projectedText));
    }

    private static String trimTrailingLineEndings(String value) {
        int end = value.length();
        while (end > 0) {
            char character = value.charAt(end - 1);
            if (character != '\r' && character != '\n') break;
            end--;
        }
        return value.substring(0, end);
    }

    private static void addOrMergeRow(
            List<SFMSymbolInspectionSnapshot.Rectangle> values,
            SFMSymbolInspectionSnapshot.Rectangle next
    ) {
        if (!values.isEmpty()) {
            SFMSymbolInspectionSnapshot.Rectangle previous = values.get(values.size() - 1);
            if (Double.compare(previous.top(), next.top()) == 0
                    && Double.compare(previous.bottom(), next.bottom()) == 0
                    && next.left() <= previous.right()) {
                values.set(values.size() - 1, new SFMSymbolInspectionSnapshot.Rectangle(
                        previous.left(), previous.top(), Math.max(previous.right(), next.right()), previous.bottom()));
                return;
            }
        }
        values.add(next);
    }

    private static double canvasToScreen(double value, double camera, double zoom, int viewportSize) {
        return (value - camera) * zoom + viewportSize / 2.0D;
    }

    private static SFMSymbolInspectionSnapshot.Rectangle inspectionRectangle(SFMScreenPanelBounds value) {
        return new SFMSymbolInspectionSnapshot.Rectangle(
                value.x(), value.y(), value.x() + value.width(), value.y() + value.height());
    }

    private record InspectionGeometry(
            List<SFMSymbolInspectionSnapshot.Glyph> glyphs,
            List<SFMSymbolInspectionSnapshot.Rectangle> canvasBounds,
            List<SFMSymbolInspectionSnapshot.Rectangle> localScreenBounds,
            List<SFMSymbolInspectionSnapshot.Rectangle> globalScreenBounds,
            List<SFMSymbolInspectionSnapshot.Rectangle> physicalPixelBounds
    ) {
        private static final InspectionGeometry EMPTY = new InspectionGeometry(
                List.of(), List.of(), List.of(), List.of(), List.of());

        private InspectionGeometry {
            glyphs = List.copyOf(glyphs);
            canvasBounds = List.copyOf(canvasBounds);
            localScreenBounds = List.copyOf(localScreenBounds);
            globalScreenBounds = List.copyOf(globalScreenBounds);
            physicalPixelBounds = List.copyOf(physicalPixelBounds);
        }
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        this.panelContext = context;
        if (screen instanceof SFMDrawCanvasScreen) {
            this.symbolHover = new SFMSymbolHoverStateMachine(
                    this::submitHoverLookup,
                    SFMSymbolHoverStateMachine.DragThreshold.euclidean(DEFINITION_DRAG_THRESHOLD_PIXELS)
            );
            this.interactionMapSession = new SFMJavaInteractionMapSession(SFMSymbolNavigationRuntime.get());
            this.linkCursor = SFMEditorLinkCursorHost.live(minecraft.getWindow().getWindow());
        }
        init(minecraft, bounds);
        if (screen instanceof SFMDrawCanvasScreen drawCanvas) {
            if (independentDocumentHistory && !isReadOnly() && historyController == null) {
                SFMDocumentHistoryContract.DocumentState initialState = captureDocumentHistoryState(drawCanvas);
                Optional<String> sourceAddress = openContext.document().path().map(path -> path.canonical());
                SFMDocumentHistorySession session = SFMDocumentHistorySession.create(
                        new SFMDocumentHistoryContract.SessionIdentity(
                                historySessionId,
                                historySessionId + "/document",
                                sourceAddress
                        ),
                        initialState
                );
                historyController = new SFMDocumentHistoryHostController(
                        session,
                        "sfm:text_editor_v3",
                        historySessionId + "/canvas",
                        this::checkoutDocumentHistoryState
                );
                historyRegistration = SFMDocumentHistoryRuntime.get().registerFocused(historyController);
                updateDocumentHistoryFocus(true);
            }
            observedDocumentGeneration = drawCanvas.documentGeneration();
            refreshInteractionMap(drawCanvas);
        }
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        screen.resize(minecraft, Math.max(1, bounds.width()), Math.max(1, bounds.height()));
    }

    private void init(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        if (screen instanceof SFMDrawCanvasScreen drawCanvas) {
            drawCanvas.init(minecraft, Math.max(1, bounds.width()), Math.max(1, bounds.height()));
            presentedDocument.targetRange().ifPresent(drawCanvas::openAtTextRange);
        } else {
            screen.init(minecraft, Math.max(1, bounds.width()), Math.max(1, bounds.height()));
        }
    }

    @Override
    public void closed() {
        if (symbolHover != null) {
            symbolHover.close();
            symbolHover = null;
        }
        if (linkCursor != null) {
            linkCursor.close();
            linkCursor = null;
        }
        if (interactionMapSession != null) {
            interactionMapSession.close();
            interactionMapSession = null;
        }
        hoverContribution = null;
        hoverHit = null;
        capturedHoverHit = null;
        capturedHoverClick = false;
        pointerInside = false;
        hoverFocused = false;
        observedDocumentGeneration = -1L;
        observedDocumentContentHash = "";
        observedSemanticFingerprint = "";
        hoverModifiers = SFMSymbolHoverIdentity.Modifiers.NONE;
        hoverCaptureCache.clear();
        if (historyController != null) {
            updateDocumentHistoryFocus(false);
            if (historyRegistration != null) {
                historyRegistration.close();
                historyRegistration = null;
            }
            historyController.close();
            historyController = null;
        }
        historyFocused = false;
        screen.removed();
        panelContext = null;
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                       int mouseX, int mouseY, float partialTick, boolean focused) {
        updateDocumentHistoryFocus(focused);
        if (symbolHover != null && screen instanceof SFMDrawCanvasScreen drawCanvas) {
            boolean inside = bounds.contains(mouseX, mouseY);
            if (focused != hoverFocused) {
                hoverFocused = focused;
                reconcileHoverFocusTransition(
                        focused,
                        inside,
                        hoverModifiers,
                        () -> {
                            symbolHover.focusChanged();
                            clearHoverTarget();
                        },
                        () -> refreshHoverTarget(mouseX, mouseY)
                );
            }
            if (!inside && pointerInside) {
                pointerInside = false;
                symbolHover.pointerExited();
                clearHoverTarget();
            }
            long documentGeneration = drawCanvas.documentGeneration();
            if (documentGeneration != observedDocumentGeneration) {
                observedDocumentGeneration = documentGeneration;
                symbolHover.documentChanged();
                clearHoverTarget();
                refreshInteractionMap(drawCanvas);
            }
            String semanticFingerprint = currentInteractionMap(drawCanvas)
                    .map(SFMJavaInteractionMap.Result::semanticFingerprint)
                    .orElse("");
            if (!semanticFingerprint.equals(observedSemanticFingerprint)) {
                observedSemanticFingerprint = semanticFingerprint;
                clearHoverTarget();
                if (focused && inside && hoverModifiers.requestsDefinitionNavigation()) {
                    refreshHoverTarget(mouseX, mouseY);
                }
            }
            SFMSymbolHoverStateMachine.Snapshot hover = symbolHover.snapshot();
            drawCanvas.setSymbolHoverUnderline(focused && inside
                    ? hover.underlineRange()
                    : Optional.empty());
            if (linkCursor != null) {
                linkCursor.setLink(focused && inside && hover.ownsLinkCursor());
            }
        }
        screen.render(poseStack, mouseX, mouseY, partialTick);
    }

    static void reconcileHoverFocusTransition(
            boolean focused,
            boolean pointerInside,
            SFMSymbolHoverIdentity.Modifiers modifiers,
            Runnable reset,
            Runnable refresh
    ) {
        Objects.requireNonNull(modifiers, "modifiers");
        if (!focused) {
            Objects.requireNonNull(reset, "reset").run();
        } else if (pointerInside && modifiers.requestsDefinitionNavigation()) {
            Objects.requireNonNull(refresh, "refresh").run();
        }
    }

    @Override
    public void tick() {
        screen.tick();
        observeDocumentMutation();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Optional<SFMDocumentHistoryContract.DocumentState> before = beforeHistoryMutation();
        updateHoverModifiers(keyCode, modifiers, true);
        boolean handled = screen.keyPressed(keyCode, scanCode, modifiers);
        before.ifPresent(state -> observeHistoryMutation(
                state,
                mutationKindForKey(keyCode, modifiers),
                editDirectionForKey(keyCode),
                Optional.empty(),
                "key-" + keyCode
        ));
        observeDocumentMutation();
        return handled;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        updateHoverModifiers(keyCode, modifiers, false);
        boolean handled = screen.keyReleased(keyCode, scanCode, modifiers);
        observeDocumentMutation();
        return handled;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        Optional<SFMDocumentHistoryContract.DocumentState> before = beforeHistoryMutation();
        boolean handled = screen.charTyped(character, modifiers);
        before.ifPresent(state -> observeHistoryMutation(
                state,
                SFMDocumentHistoryContract.MutationKind.TYPE,
                SFMDocumentHistoryContract.EditDirection.FORWARD,
                Optional.of(Character.toString(character)),
                "character"
        ));
        observeDocumentMutation();
        return handled;
    }

    private static SFMDocumentHistoryContract.MutationKind mutationKindForKey(
            int keyCode,
            int modifiers
    ) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            return SFMDocumentHistoryContract.MutationKind.DELETE_BACKWARD;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            return SFMDocumentHistoryContract.MutationKind.DELETE_FORWARD;
        }
        if (Screen.isPaste(keyCode)) return SFMDocumentHistoryContract.MutationKind.PASTE;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return SFMDocumentHistoryContract.MutationKind.TYPE;
        }
        if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            return SFMDocumentHistoryContract.MutationKind.SELECTION_CHANGE;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END
                || keyCode == GLFW.GLFW_KEY_F1 || keyCode == GLFW.GLFW_KEY_F4) {
            return SFMDocumentHistoryContract.MutationKind.CARET_CHANGE;
        }
        return SFMDocumentHistoryContract.MutationKind.OTHER;
    }

    private static SFMDocumentHistoryContract.EditDirection editDirectionForKey(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) return SFMDocumentHistoryContract.EditDirection.BACKWARD;
        if (keyCode == GLFW.GLFW_KEY_DELETE) return SFMDocumentHistoryContract.EditDirection.FORWARD;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return SFMDocumentHistoryContract.EditDirection.FORWARD;
        }
        return SFMDocumentHistoryContract.EditDirection.NONE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return observePointerMutation(
                "pointer-press-" + button,
                () -> mouseClickedWithoutHistory(mouseX, mouseY, button));
    }

    private boolean mouseClickedWithoutHistory(double mouseX, double mouseY, int button) {
        rememberPointer(mouseX, mouseY);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && screen instanceof SFMDrawCanvasScreen drawCanvas) {
            Optional<SFMDrawCanvasScreen.SymbolHit> hit = drawCanvas.symbolHitAtScreen(
                    mouseX, mouseY, currentInteractionMap(drawCanvas));
            hit.ifPresentOrElse(drawCanvas::focusSymbolHit, () -> drawCanvas.focusContextAtScreen(mouseX, mouseY));
            contextualProjectionOverride = hit
                    .map(value -> drawCanvas.captureContextProjectionAt(
                            openContext.editorId(), openContext.document(), isReadOnly(), value))
                    .orElseGet(() -> drawCanvas.captureContextProjection(
                            openContext.editorId(), openContext.document(), isReadOnly()));
            if (hoverModifiers.requestsDefinitionNavigation()) refreshHoverTarget(mouseX, mouseY);
            try {
                return executeEditorAction(SFMContextActionsOpenAction.ID);
            } finally {
                contextualProjectionOverride = null;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && hoverModifiers.requestsDefinitionNavigation()
                && symbolHover != null) {
            // A pointer press may arrive before the render/move loop has observed this exact
            // location. Resolve the canvas hit synchronously so a fast Ctrl+click cannot leak
            // through as a multi-cursor edit while the asynchronous definition query is pending.
            refreshHoverTarget(mouseX, mouseY);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && hoverModifiers.requestsDefinitionNavigation()
                && symbolHover != null
                && symbolHover.snapshot().ownsLinkCursor()) {
            symbolHover.primaryPressed(mouseX, mouseY);
            capturedHoverClick = true;
            capturedHoverHit = hoverHit;
            capturedPressX = mouseX;
            capturedPressY = mouseY;
            return true;
        }
        return screen.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        rememberPointer(mouseX, mouseY);
        screen.mouseMoved(mouseX, mouseY);
        if (hoverModifiers.requestsDefinitionNavigation()) refreshHoverTarget(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return observePointerMutation(
                "pointer-release-" + button,
                () -> mouseReleasedWithoutHistory(mouseX, mouseY, button));
    }

    private boolean mouseReleasedWithoutHistory(double mouseX, double mouseY, int button) {
        rememberPointer(mouseX, mouseY);
        if (capturedHoverClick && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && symbolHover != null) {
            SFMSymbolHoverStateMachine.GestureDecision decision = symbolHover.primaryReleased(mouseX, mouseY);
            SFMDrawCanvasScreen.SymbolHit capturedHit = capturedHoverHit;
            double pressX = capturedPressX;
            double pressY = capturedPressY;
            capturedHoverClick = false;
            capturedHoverHit = null;
            if (decision.kind() == SFMSymbolHoverStateMachine.GestureKind.ACTIVATE_DEFINITION
                    && capturedHit != null
                    && screen instanceof SFMDrawCanvasScreen drawCanvas) {
                drawCanvas.focusSymbolHit(capturedHit);
                refreshHoverTarget(mouseX, mouseY);
                return executeEditorAction(SFMJumpToDefinitionAction.ID);
            }
            replaySuppressedPointerGesture(pressX, pressY, mouseX, mouseY, button,
                    decision.kind() == SFMSymbolHoverStateMachine.GestureKind.FALLBACK_DRAG);
            return true;
        }
        return screen.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return observePointerMutation(
                "pointer-drag-" + button,
                () -> mouseDraggedWithoutHistory(mouseX, mouseY, button, dragX, dragY));
    }

    private boolean mouseDraggedWithoutHistory(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        rememberPointer(mouseX, mouseY);
        if (capturedHoverClick && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && symbolHover != null) {
            double dx = mouseX - capturedPressX;
            double dy = mouseY - capturedPressY;
            if (dx * dx + dy * dy > DEFINITION_DRAG_THRESHOLD_PIXELS * DEFINITION_DRAG_THRESHOLD_PIXELS) {
                SFMSymbolHoverStateMachine.GestureDecision decision = symbolHover.primaryReleased(mouseX, mouseY);
                double pressX = capturedPressX;
                double pressY = capturedPressY;
                capturedHoverClick = false;
                capturedHoverHit = null;
                if (decision.kind() == SFMSymbolHoverStateMachine.GestureKind.FALLBACK_DRAG) {
                    screen.mouseClicked(pressX, pressY, button);
                    return screen.mouseDragged(mouseX, mouseY, button, dragX, dragY);
                }
            }
            return true;
        }
        return screen.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean observePointerMutation(String code, BooleanSupplier dispatch) {
        Optional<SFMDocumentHistoryContract.DocumentState> before = beforeHistoryMutation();
        if (historyController != null) {
            historyController.recordRawInput(
                    SFMKeyBindingService.INSTANCE.currentTick(),
                    SFMDocumentHistoryContract.RawEventKind.POINTER,
                    "text-editor-pointer",
                    code,
                    Optional.empty(),
                    0,
                    false,
                    true
            );
        }
        boolean handled = dispatch.getAsBoolean();
        before.ifPresent(state -> observeHistoryMutation(
                state,
                SFMDocumentHistoryContract.MutationKind.SELECTION_CHANGE,
                SFMDocumentHistoryContract.EditDirection.NONE,
                Optional.empty(),
                code
        ));
        observeDocumentMutation();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return screen.mouseScrolled(mouseX, mouseY, delta);
    }

    private void requestClose() {
        if (panelContext != null) {
            panelContext.submit(new ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent.Close());
        }
    }

    private SFMSymbolHoverLookup.Query submitHoverLookup(SFMSymbolHoverIdentity identity) {
        SFMContextContribution contribution = hoverContribution;
        if (contribution == null
                || !(contribution.projection() instanceof SFMContextDocumentProjection document)
                || !document.currentSha256().equals(identity.document().contentHash())) {
            return new SFMSymbolHoverLookup.Query(
                    CompletableFuture.completedFuture(SFMSymbolHoverLookup.Resolution.UNAVAILABLE),
                () -> { }
            );
        }
        if (screen instanceof SFMDrawCanvasScreen drawCanvas
                && hoverHit != null
                && hoverHit.semanticRegionId().isPresent()) {
            Optional<SFMJavaInteractionMap.Result> currentMap = currentInteractionMap(drawCanvas);
            if (currentMap.isPresent()
                    && currentMap.orElseThrow().semanticGeneration() == hoverHit.semanticGeneration()) {
                SFMJavaInteractionMap.Result map = currentMap.orElseThrow();
                SFMJavaInteractionMap.Classification classification = map
                        .classification(hoverHit.semanticRegionId().orElseThrow())
                        .orElse(null);
                int navigationTargets = classification == null
                        ? 0
                        : map.navigationOutlinks(classification).size();
                SFMSymbolHoverLookup.Resolution resolution = navigationTargets > 1
                        ? SFMSymbolHoverLookup.Resolution.AMBIGUOUS
                        : navigationTargets == 1
                                ? SFMSymbolHoverLookup.Resolution.ACTIONABLE
                                : SFMSymbolHoverLookup.Resolution.UNRESOLVED;
                return new SFMSymbolHoverLookup.Query(
                        CompletableFuture.completedFuture(resolution),
                        () -> { }
                );
            }
        }
        SFMDefinitionLookupService.Submission submission = SFMSymbolNavigationRuntime.get().query(contribution);
        return new SFMSymbolHoverLookup.Query(
                submission.result().handle((lookup, failure) -> {
                    if (failure != null || lookup == null) return SFMSymbolHoverLookup.Resolution.UNAVAILABLE;
                    SFMDefinitionResult result = lookup.result();
                    if (result.outcome() == SFMDefinitionResult.Outcome.AMBIGUOUS
                            || result.definitions().size() > 1) {
                        return SFMSymbolHoverLookup.Resolution.AMBIGUOUS;
                    }
                    if (result.outcome() == SFMDefinitionResult.Outcome.SUCCESS
                            && result.definitions().size() == 1) {
                        return SFMSymbolHoverLookup.Resolution.ACTIONABLE;
                    }
                    if (result.outcome() == SFMDefinitionResult.Outcome.NO_SYMBOL
                            || result.outcome() == SFMDefinitionResult.Outcome.NO_DEFINITION) {
                        return SFMSymbolHoverLookup.Resolution.UNRESOLVED;
                    }
                    return SFMSymbolHoverLookup.Resolution.UNAVAILABLE;
                }),
                submission::cancel
        );
    }

    private void refreshHoverTarget(double mouseX, double mouseY) {
        if (symbolHover == null || panelContext == null || !(screen instanceof SFMDrawCanvasScreen drawCanvas)) {
            return;
        }
        if (!hoverModifiers.requestsDefinitionNavigation()) return;
        SFMSymbolHoverIdentity.EditorOrigin editorOrigin = hoverEditorOrigin();
        long documentGeneration = drawCanvas.documentGeneration();
        SFMTextEditorHoverCaptureCache.Key key = new SFMTextEditorHoverCaptureCache.Key(
                editorOrigin,
                documentGeneration,
                mouseX,
                mouseY
        );
        Optional<HoverCapture> resolved = hoverCaptureCache.resolve(true, key, () -> captureHoverAt(
                drawCanvas, mouseX, mouseY, editorOrigin, documentGeneration
        )).orElseThrow();
        if (resolved.isEmpty()) {
            clearHoverPresentation();
            symbolHover.observe(Optional.empty());
            return;
        }
        HoverCapture capture = resolved.orElseThrow();
        hoverHit = capture.hit();
        hoverContribution = capture.contribution();
        symbolHover.observe(Optional.of(capture.target()));
    }

    private Optional<HoverCapture> captureHoverAt(
            SFMDrawCanvasScreen drawCanvas,
            double mouseX,
            double mouseY,
            SFMSymbolHoverIdentity.EditorOrigin editorOrigin,
            long documentGeneration
    ) {
        Optional<SFMDrawCanvasScreen.SymbolHit> hit = drawCanvas.symbolHitAtScreen(
                mouseX,
                mouseY,
                currentInteractionMap(drawCanvas)
        );
        if (hit.isEmpty()) return Optional.empty();
        SFMDrawCanvasScreen.SymbolHit symbolHit = hit.orElseThrow();
        SFMContextDocumentProjection projection = drawCanvas.captureContextProjectionAt(
                openContext.editorId(), openContext.document(), isReadOnly(), symbolHit);
        long generation = drawCanvas.contextGeneration();
        SFMContextContribution contribution = new SFMContextContribution(
                contextOrigin(),
                new SFMContextGenerationEvidence(generation, generation, generation, 0),
                projection
        );
        SFMSymbolHoverStateMachine.Target target = new SFMSymbolHoverStateMachine.Target(
                editorOrigin,
                new SFMSymbolHoverIdentity.DocumentVersion(
                        openContext.document().path().map(path -> path.canonical())
                                .orElse("editor://" + openContext.editorId()),
                        projection.currentSha256(),
                        documentGeneration
                ),
                symbolHit.range(),
                new SFMSymbolHoverIdentity.SemanticContext(
                        symbolHit.semanticKind(),
                        symbolHit.navigationUtf16Offset(),
                        symbolHit.semanticRegionId(),
                        symbolHit.semanticFingerprint(),
                        symbolHit.semanticGeneration()
                )
        );
        return Optional.of(new HoverCapture(contribution, symbolHit, target));
    }

    private SFMSymbolHoverIdentity.EditorOrigin hoverEditorOrigin() {
        String panelId = Long.toString(panelContext.panelId().value());
        String workspaceId = panelContext.host().getClass().getName() + "@"
                + Integer.toUnsignedString(System.identityHashCode(panelContext.host()));
        String stackId = "panel-" + panelId;
        long focusGeneration = 0;
        if (panelContext.host() instanceof SFMScreenMultiplexer multiplexer) {
            stackId = multiplexer.panelStackId(panelContext.panelId())
                    .map(Object::toString)
                    .orElse(stackId);
            focusGeneration = multiplexer.keyboardFocusGeneration();
        }
        return new SFMSymbolHoverIdentity.EditorOrigin(
                "sfm:workspace",
                workspaceId,
                stackId,
                panelId,
                openContext.editorId(),
                focusGeneration
        );
    }

    private void updateHoverModifiers(int keyCode, int modifiers, boolean pressed) {
        if (symbolHover == null) return;
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean superKey = (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
        if (keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) control = pressed;
        if (keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT) alt = pressed;
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) shift = pressed;
        if (keyCode == GLFW.GLFW_KEY_LEFT_SUPER || keyCode == GLFW.GLFW_KEY_RIGHT_SUPER) superKey = pressed;
        hoverModifiers = new SFMSymbolHoverIdentity.Modifiers(control, alt, shift, superKey);
        if (hoverModifiers.requestsDefinitionNavigation() && pointerInside) {
            refreshHoverTarget(lastMouseX, lastMouseY);
        }
        symbolHover.modifiersChanged(hoverModifiers);
    }

    private void observeDocumentMutation() {
        if (symbolHover == null || !(screen instanceof SFMDrawCanvasScreen drawCanvas)) return;
        long generation = drawCanvas.documentGeneration();
        if (generation == observedDocumentGeneration) return;
        observedDocumentGeneration = generation;
        symbolHover.documentChanged();
        clearHoverTarget();
        refreshInteractionMap(drawCanvas);
    }

    private void refreshInteractionMap(SFMDrawCanvasScreen drawCanvas) {
        if (interactionMapSession == null || panelContext == null) return;
        SFMContextDocumentProjection projection = drawCanvas.captureContextProjection(
                openContext.editorId(),
                openContext.document(),
                isReadOnly()
        );
        SFMContextDocumentProjection semanticProjection = projection.baseline().semanticAnalysisSnapshot()
                .map(baseline -> new SFMContextDocumentProjection(
                        projection.editorId(), baseline, projection.currentText(), projection.currentSha256(),
                        projection.dirty(), projection.readOnly(), projection.cursors(), projection.selections()))
                .orElse(projection);
        long documentGeneration = drawCanvas.documentGeneration();
        long contributorGeneration = drawCanvas.contextGeneration();
        observedDocumentContentHash = SFMDefinitionRequest.sha256(projection.currentText());
        observedSemanticFingerprint = "";
        interactionMapSession.refresh(
                new SFMContextContribution(
                        contextOrigin(),
                        new SFMContextGenerationEvidence(
                                contributorGeneration,
                                documentGeneration,
                                contributorGeneration,
                                0
                        ),
                        semanticProjection
                ),
                documentGeneration,
                observedDocumentContentHash
        );
    }

    private Optional<SFMJavaInteractionMap.Result> currentInteractionMap(SFMDrawCanvasScreen drawCanvas) {
        if (interactionMapSession == null || observedDocumentContentHash.isBlank()) return Optional.empty();
        return interactionMapSession.current(drawCanvas.documentGeneration(), observedDocumentContentHash);
    }

    private void rememberPointer(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        pointerInside = true;
    }

    private void clearHoverTarget() {
        hoverCaptureCache.clear();
        clearHoverPresentation();
    }

    private void clearHoverPresentation() {
        hoverContribution = null;
        hoverHit = null;
        if (screen instanceof SFMDrawCanvasScreen drawCanvas) {
            drawCanvas.setSymbolHoverUnderline(Optional.empty());
        }
        if (linkCursor != null) linkCursor.setLink(false);
    }

    private boolean executeEditorAction(ResourceLocation actionId) {
        if (panelContext == null) return false;
        return SFMPanelActionExecution.executeAction(
                panelContext,
                Minecraft.getInstance(),
                actionId,
                ignored -> { }
        );
    }

    private void replaySuppressedPointerGesture(
            double pressX,
            double pressY,
            double releaseX,
            double releaseY,
            int button,
            boolean dragged
    ) {
        screen.mouseClicked(pressX, pressY, button);
        if (dragged) screen.mouseDragged(releaseX, releaseY, button, releaseX - pressX, releaseY - pressY);
        screen.mouseReleased(releaseX, releaseY, button);
    }

    private record HoverCapture(
            SFMContextContribution contribution,
            SFMDrawCanvasScreen.SymbolHit hit,
            SFMSymbolHoverStateMachine.Target target
    ) {
    }

    public record SpatialCoverageCapture(
            SFMSpatialSemanticContract.SnapshotIdentity snapshot,
            SFMSpatialCoverageService.Document document,
            java.util.List<SFMJavaInteractionMap.FileRow> workspaceInventory
    ) {
        public SpatialCoverageCapture {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(document, "document");
            workspaceInventory = java.util.List.copyOf(workspaceInventory);
        }
    }

    static ISFMTextEditScreenOpenContext screenContext(
            SFMTextEditorPanelOpenContext context,
            Runnable closePanel
    ) {
        return screenContext(context, closePanel, ignored -> { });
    }

    static ISFMTextEditScreenOpenContext screenContext(
            SFMTextEditorPanelOpenContext context,
            Runnable closePanel,
            java.util.function.Consumer<String> savedDocument
    ) {
        return new ISFMTextEditScreenOpenContext() {
            @Override public String initialValue() { return context.initialValue(); }
            @Override public boolean readOnly() { return context.readOnly(); }
            @Override public Optional<ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot> documentSnapshot() {
                return Optional.of(context.document());
            }
            @Override public java.util.function.Consumer<String> saveWriter() {
                return value -> context.saveHandler().save(value);
            }
            @Override public ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult saveDocument(
                    String value
            ) {
                ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult result =
                        context.saveHandler().save(value);
                if (result.saved()) savedDocument.accept(value);
                return result;
            }
            @Override public ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult trySaveAndClose(
                    String value
            ) {
                ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult result = saveDocument(value);
                if (result.saved()) closePanel.run();
                return result;
            }
            @Override public void onTryClose(String latestContent, Runnable ignoredFullScreenClose) {
                ISFMTextEditScreenOpenContext.super.onTryClose(latestContent, closePanel);
            }
            @Override public LabelPositionHolder labelPositionHolder() { return LabelPositionHolder.empty(); }
        };
    }

    private static final class PanelTextEditorScreen extends SFMTextEditorV3Screen {
        private final Runnable close;

        private PanelTextEditorScreen(ISFMTextEditScreenOpenContext context, Runnable close) {
            super(context, null, false);
            this.close = close;
        }

        @Override
        protected void finishClose() {
            close.run();
        }
    }
}
