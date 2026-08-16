package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextContributor;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
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
import ca.teamdman.sfm.client.symbol.SFMDefinitionLookupService;
import ca.teamdman.sfm.client.symbol.SFMDefinitionResult;
import ca.teamdman.sfm.client.symbol.SFMSymbolHoverIdentity;
import ca.teamdman.sfm.client.symbol.SFMSymbolHoverLookup;
import ca.teamdman.sfm.client.symbol.SFMSymbolHoverStateMachine;
import ca.teamdman.sfm.client.symbol.SFMSymbolNavigationRuntime;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.lwjgl.glfw.GLFW;

/**
 * Panel adapter for text editors.  Text Editor v3 uses the specialised
 * callback-aware screen below; legacy registrations still get a useful
 * lifecycle adapter while they are being migrated.
 */
public final class SFMTextEditorPanel implements SFMScreenPanel, SFMTextDocumentPanelState, SFMContextContributor {
    private static final String CONTEXT_CONTRIBUTOR_ID = "sfm:text-editor";
    private static final double DEFINITION_DRAG_THRESHOLD_PIXELS = 3.0D;
    private final SFMTextEditorPanelOpenContext openContext;
    private final Screen screen;
    private final SFMTextEditorHoverCaptureCache<Optional<HoverCapture>> hoverCaptureCache =
            new SFMTextEditorHoverCaptureCache<>();
    private SFMWorkspacePanelContext panelContext;
    private SFMSymbolHoverStateMachine symbolHover;
    private SFMEditorLinkCursorHost linkCursor;
    private SFMContextContribution hoverContribution;
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
    private SFMSymbolHoverIdentity.Modifiers hoverModifiers = SFMSymbolHoverIdentity.Modifiers.NONE;

    private SFMTextEditorPanel(
            SFMTextEditorPanelOpenContext openContext,
            Screen screen
    ) {
        this.openContext = openContext;
        this.screen = screen;
    }

    public static SFMTextEditorPanel textEditorV3(SFMTextEditorPanelOpenContext context) {
        SFMTextEditorPanel[] holder = new SFMTextEditorPanel[1];
        ISFMTextEditScreenOpenContext screenContext = screenContext(
                context,
                () -> {
                    if (holder[0] != null) holder[0].requestClose();
                }
        );
        PanelTextEditorScreen editor = new PanelTextEditorScreen(screenContext, () -> {
            if (holder[0] != null) holder[0].requestClose();
        });
        holder[0] = new SFMTextEditorPanel(context, editor);
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
                }
        );
        Screen screen = screenFactory.apply(screenContext).asScreen();
        holder[0] = new SFMTextEditorPanel(context, screen);
        return holder[0];
    }

    public String editorId() {
        return openContext.editorId();
    }

    /** Explorer previews are reusable only while their document is immutable. */
    public boolean isReadOnly() {
        return openContext.readOnly();
    }

    public Optional<SFMDrawCanvasScreen.SyntaxPresentationEvidence> syntaxPresentationEvidence() {
        if (!(screen instanceof SFMDrawCanvasScreen drawCanvas)) return Optional.empty();
        return drawCanvas.syntaxPresentationEvidence();
    }

    @Override
    public Optional<ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot> documentSnapshot() {
        return Optional.of(openContext.document());
    }

    @Override
    public boolean navigateToRange(ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange range) {
        if (!(screen instanceof SFMDrawCanvasScreen drawCanvas)) return false;
        range.validateAgainst(drawCanvas.captureContextProjection(
                openContext.editorId(), openContext.document(), isReadOnly()).currentText());
        drawCanvas.openAtTextRange(range);
        return true;
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
        if (screen instanceof SFMDrawCanvasScreen drawCanvas) {
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
        return SFMKeyboardUsageSituations.TEXT_EDITOR;
    }

    @Override
    public Component narration() {
        return title().copy().append(Component.literal(openContext.readOnly() ? " (read-only)" : ""));
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        this.panelContext = context;
        if (screen instanceof SFMDrawCanvasScreen) {
            this.symbolHover = new SFMSymbolHoverStateMachine(
                    this::submitHoverLookup,
                    SFMSymbolHoverStateMachine.DragThreshold.euclidean(DEFINITION_DRAG_THRESHOLD_PIXELS)
            );
            this.linkCursor = SFMEditorLinkCursorHost.live(minecraft.getWindow().getWindow());
        }
        init(minecraft, bounds);
        if (screen instanceof SFMDrawCanvasScreen drawCanvas) {
            observedDocumentGeneration = drawCanvas.documentGeneration();
        }
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        screen.resize(minecraft, Math.max(1, bounds.width()), Math.max(1, bounds.height()));
    }

    private void init(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        if (screen instanceof SFMDrawCanvasScreen drawCanvas) {
            drawCanvas.init(minecraft, Math.max(1, bounds.width()), Math.max(1, bounds.height()));
            openContext.document().targetRange().ifPresent(drawCanvas::openAtTextRange);
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
        hoverContribution = null;
        hoverHit = null;
        capturedHoverHit = null;
        capturedHoverClick = false;
        pointerInside = false;
        hoverFocused = false;
        hoverModifiers = SFMSymbolHoverIdentity.Modifiers.NONE;
        hoverCaptureCache.clear();
        screen.removed();
        panelContext = null;
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                       int mouseX, int mouseY, float partialTick, boolean focused) {
        if (symbolHover != null && screen instanceof SFMDrawCanvasScreen drawCanvas) {
            boolean inside = bounds.contains(mouseX, mouseY);
            if (focused != hoverFocused) {
                hoverFocused = focused;
                symbolHover.focusChanged();
                clearHoverTarget();
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

    @Override
    public void tick() {
        screen.tick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        updateHoverModifiers(keyCode, modifiers, true);
        boolean handled = screen.keyPressed(keyCode, scanCode, modifiers);
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
        boolean handled = screen.charTyped(character, modifiers);
        observeDocumentMutation();
        return handled;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        rememberPointer(mouseX, mouseY);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && screen instanceof SFMDrawCanvasScreen drawCanvas) {
            drawCanvas.focusContextAtScreen(mouseX, mouseY);
            if (hoverModifiers.requestsDefinitionNavigation()) refreshHoverTarget(mouseX, mouseY);
            return executeEditorAction("sfm:context/actions/open");
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && symbolHover != null
                && symbolHover.snapshot().phase() == SFMSymbolHoverStateMachine.Phase.ACTIONABLE) {
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
                return executeEditorAction("sfm:symbol/definition/open");
            }
            replaySuppressedPointerGesture(pressX, pressY, mouseX, mouseY, button,
                    decision.kind() == SFMSymbolHoverStateMachine.GestureKind.FALLBACK_DRAG);
            return true;
        }
        return screen.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
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
        Optional<SFMDrawCanvasScreen.SymbolHit> hit = drawCanvas.symbolHitAtScreen(mouseX, mouseY);
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
                new SFMSymbolHoverIdentity.PointerState(symbolHit.glyphOrdinal(), false)
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

    private boolean executeEditorAction(String command) {
        if (panelContext == null) return false;
        return SFMPanelActionExecution.execute(panelContext, Minecraft.getInstance(), command, ignored -> { });
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

    static ISFMTextEditScreenOpenContext screenContext(
            SFMTextEditorPanelOpenContext context,
            Runnable closePanel
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
                return context.saveHandler().save(value);
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
