package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.context.SFMContextCursorProjection;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextPosition;
import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMDocumentActionTarget;
import ca.teamdman.sfm.client.semantic.SFMNavigationFramingPolicy;
import ca.teamdman.sfm.client.semantic.SFMJavaCanvasInteractionRegions;
import ca.teamdman.sfm.client.semantic.SFMSpatialSemanticContract;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightResult;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightRuntime;
import ca.teamdman.sfm.client.syntax.SFMTextEditorSyntaxSession;
import ca.teamdman.sfm.client.symbol.SFMSymbolHoverIdentity;
import ca.teamdman.sfm.client.symbol.SFMJavaInteractionMap;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class SFMDrawCanvasScreen extends Screen implements ISFMTextEditScreen, SFMDocumentActionTarget {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TEXT_EDITOR_V3_READ_ONLY_DOCUMENT = new LocalizationEntry(
            "gui.sfm.text_editor_v3.read_only",
            "Read-only"
    );
    @SFMLocalizationDatagen
    public static final LocalizationEntry TEXT_EDITOR_V3_DONE_BUTTON_TOOLTIP_PREFIX = new LocalizationEntry(
            "gui.sfm.text_editor_v3.done_button.tooltip.prefix",
            "Press "
    );
    @SFMLocalizationDatagen
    public static final LocalizationEntry TEXT_EDITOR_V3_DONE_BUTTON_TOOLTIP_SUFFIX = new LocalizationEntry(
            "gui.sfm.text_editor_v3.done_button.tooltip.suffix",
            " to save"
    );

    private static final int BACKGROUND = 0xFF15191E;
    private static final int MINOR_GRID = 0xFF252C34;
    private static final int MAJOR_GRID = 0xFF343D47;
    private static final int AXIS_X = 0xFF9A6B6B;
    private static final int AXIS_Y = 0xFF6D9075;
    private static final int CURSOR_TRAIL = 0xFFFF8A8A;
    private static final int GLYPH = 0xFFE6EDF3;
    private static final int GLYPH_BOUNDS = 0xFFFF5CCD;
    private static final int HUD_BACKGROUND = 0xC0181D23;
    private static final int HUD_BORDER = 0xFF4B5563;
    private static final int HUD_TEXT = 0xFFE6EDF3;
    private static final int HUD_MUTED = 0xFF9CA3AF;
    private static final int READ_ONLY_CHROME_BACKGROUND = 0xFF181D23;
    private static final int INPUT_LOG_LIMIT = 8;
    private static final double MIN_ZOOM = 0.05D;
    private static final double MAX_ZOOM = 8.0D;
    private static final double ZOOM_STEP = 1.15D;
    private static final double BASE_GRID_STEP = 32.0D;
    private static final double MAJOR_GRID_INTERVAL = 5.0D;
    private static final double MIN_GRID_PIXEL_STEP = 12.0D;
    private static final int CURSOR_TRAIL_LIMIT = 48;
    private static final double CURSOR_TRAIL_MIN_DISTANCE = 2.0D;
    private static final int DEFAULT_ORIGIN_MARGIN = 32;
    private static final double KEYBOARD_PAN_SCREEN_PIXELS = 64.0D;
    private static final int FIT_CONTENT_MARGIN = 32;
    private static final AtomicLong NEXT_SYNTAX_ORIGIN = new AtomicLong();
    private static final int FOCUS_BORDER = 0xFF60A5FA;
    private static final int PANEL_BACKGROUND = 0xF01A2028;
    private static final int PANEL_TAB_BACKGROUND = 0xF0283340;
    private static final int EMBEDDED_DOCUMENT_BACKGROUND = 0xE81A2028;
    private static final int EMBEDDED_DOCUMENT_BORDER = 0xFF7C8A9B;
    private static final int EMBEDDED_DOCUMENT_HANDLE = 0xFFE6EDF3;
    private static final int INSERT_DRAG_LINE = 0xFF60A5FA;
    private static final ResourceLocation SFML_GRAMMAR_RESOURCE = new ResourceLocation(SFM.MOD_ID, "grammar/sfml/sfml.g4");

    private final Screen previousScreen;
    private final ISFMTextEditScreenOpenContext openContext;
    private final boolean pushed;
    private SFMDrawCanvasModel model = new SFMDrawCanvasModel();
    private final List<CanvasPoint> cursorTrail = new ArrayList<>();
    private final List<String> inputEvents = new ArrayList<>();
    private final List<Button> diagnosticButtons = new ArrayList<>();
    private final List<EmbeddedDocument> embeddedDocuments = new ArrayList<>();
    private Button canvasFocusTarget;
    private SFMTextEditorReadOnlyChrome.Rect configButtonBounds;
    private SFMTextEditorReadOnlyChrome.Rect doneButtonBounds;
    private double cameraX;
    private double cameraY;
    private double zoom = 1.0D;
    private boolean cameraInitialized;
    private int cameraViewportWidth;
    private int cameraViewportHeight;
    private boolean diagnosticControlsVisible = false;
    private boolean showGrid = false;
    private boolean showCrosshairCoordinates = false;
    private boolean showGlyphBoundingBoxes = false;
    private boolean showCursorTrail = false;
    private boolean hideSelection = false;
    private boolean panning;
    private boolean suppressNextNumpadPanChar;
    private boolean initialContentLoaded;
    private String initialCanvasProjectionText = "";
    private long contextGeneration;
    private double panAnchorMouseX;
    private double panAnchorMouseY;
    private double panAnchorCameraX;
    private double panAnchorCameraY;
    private boolean grammarPanelVisible;
    private SFMDrawCanvasModel grammarModel = new SFMDrawCanvasModel();
    private Button grammarFocusTarget;
    private double grammarCameraX;
    private double grammarCameraY;
    private double grammarZoom = 1.0D;
    private boolean grammarCameraInitialized;
    private boolean grammarContentLoaded;
    private boolean grammarPanning;
    private double grammarPanAnchorMouseX;
    private double grammarPanAnchorMouseY;
    private double grammarPanAnchorCameraX;
    private double grammarPanAnchorCameraY;
    private boolean draggingGrammarInsert;
    private Optional<Component> saveDiagnostic = Optional.empty();
    private Optional<SFMTextDocumentRange> openTargetRange = Optional.empty();
    private Optional<SFMSpatialSemanticContract.FramingObservation> navigationFramingObservation = Optional.empty();
    private long javaInteractionRegionRevision = -1L;
    private SFMJavaCanvasInteractionRegions.Index javaInteractionRegionIndex;
    private Map<SFMDrawCanvasModel.CanvasGlyph, Integer> localSyntaxColours = Map.of();
    private Map<SFMDrawCanvasModel.CanvasGlyph, Component> remoteStyledGlyphs = Map.of();
    private long documentGeneration;
    private final String syntaxOriginId = "sfm:text-editor:" + NEXT_SYNTAX_ORIGIN.incrementAndGet();
    private SFMTextEditorSyntaxSession syntaxSession;
    private long lastRequestedSyntaxGeneration;
    private long syntaxRequestStartedNanos;
    private Optional<Component> syntaxDiagnostic = Optional.empty();
    private Optional<SyntaxPresentationEvidence> syntaxPresentationEvidence = Optional.empty();
    private double grammarInsertStartX;
    private double grammarInsertStartY;
    private EmbeddedDocument resizingEmbeddedDocument;
    private ResizeCorner resizingCorner;
    private double resizeAnchorX;
    private double resizeAnchorY;
    private final SFMDrawCanvasPerformanceTracker performanceTracker = new SFMDrawCanvasPerformanceTracker();
    private ViewportGlyphCache viewportGlyphCache;
    private SelectionHighlightCache selectionHighlightCache;
    private Optional<SFMSymbolHoverIdentity.TextGlyphRange> symbolHoverUnderline = Optional.empty();

    public SFMDrawCanvasScreen(Screen previousScreen) {
        this(previousScreen, false);
    }

    public SFMDrawCanvasScreen(Screen previousScreen, boolean pushed) {
        super(Component.literal("Text Editor v3"));
        this.previousScreen = previousScreen;
        this.openContext = null;
        this.pushed = pushed;
    }

    public SFMDrawCanvasScreen(
            ISFMTextEditScreenOpenContext openContext,
            Screen previousScreen
    ) {
        this(openContext, previousScreen, false);
    }

    public SFMDrawCanvasScreen(
            ISFMTextEditScreenOpenContext openContext,
            Screen previousScreen,
            boolean pushed
    ) {
        super(Component.literal("Text Editor v3"));
        this.previousScreen = previousScreen;
        this.openContext = openContext;
        this.pushed = pushed;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (model().cursors().size() > 1) {
            model().collapseToFocusedCursor();
            rememberCursorPosition();
            return;
        }
        if (openContext == null) {
            onTryCloseStandalone();
            return;
        }
        openContext.onTryClose(getCurrentText(), this::finishClose);
    }

    @Override
    public ISFMTextEditScreenOpenContext openContext() {
        return openContext;
    }

    @Override
    public OpenBehaviour openBehaviour() {
        return pushed ? OpenBehaviour.Push : OpenBehaviour.Replace;
    }

    @Override
    protected void init() {
        super.init();
        SFMScreenRenderUtils.enableKeyRepeating();
        loadInitialContent();
        ensureRemoteSyntaxHighlighting();
        initializeCamera();
        canvasFocusTarget = new CanvasFocusTarget(1, 1, Math.max(1, this.width - 2), Math.max(1, this.height - 26), true);
        this.addRenderableWidget(canvasFocusTarget);
        this.setInitialFocus(canvasFocusTarget);
        this.setFocused(canvasFocusTarget);
        canvasFocusTarget.setFocused(true);
        diagnosticButtons.clear();
        addDiagnosticButton(8, 8, () -> showCrosshairCoordinates, value -> showCrosshairCoordinates = value, "Coords");
        addDiagnosticButton(8, 32, () -> showGlyphBoundingBoxes, value -> showGlyphBoundingBoxes = value, "Glyph Bounds");
        addDiagnosticButton(8, 56, () -> showCursorTrail, value -> showCursorTrail = value, "Cursor Trail");
        addDiagnosticButton(8, 80, () -> showGrid, value -> showGrid = value, "Grid");
        addDiagnosticButton(8, 104, () -> hideSelection, value -> hideSelection = value, "Hide Selection");
        configButtonBounds = null;
        if (openContext != null) {
            configButtonBounds = new SFMTextEditorReadOnlyChrome.Rect(4, this.height - 24, 16, 20);
            this.addRenderableWidget(new SFMButtonBuilder()
                    .setPosition(configButtonBounds.x(), configButtonBounds.y())
                    .setSize(configButtonBounds.width(), configButtonBounds.height())
                    .setText(Component.literal("#"))
                    .setOnPress(button -> SFMScreenChangeHelpers.setOrPushScreen(new SFMTextEditorConfigScreen(
                            this,
                            SFMConfig.CLIENT_TEXT_EDITOR_CONFIG,
                            () -> { }
                    )))
                    .build());
        }
        doneButtonBounds = new SFMTextEditorReadOnlyChrome.Rect(this.width - 88, this.height - 24, 80, 20);
        this.addRenderableWidget(new SFMButtonBuilder()
                .setPosition(doneButtonBounds.x(), doneButtonBounds.y())
                .setSize(doneButtonBounds.width(), doneButtonBounds.height())
                .setText(CommonComponents.GUI_DONE)
                .setOnPress(button -> this.saveDocumentAndClose())
                .setTooltip(this, font, doneButtonTooltip())
                .build());
        refreshDiagnosticControls();
    }

    @Override
    public void render(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        long frameStartedNanos = System.nanoTime();
        long frameAllocatedBefore = performanceTracker.allocationCheckpoint();
        ResolvedViewportGlyphs viewport = resolveViewportGlyphs();
        List<SFMDrawCanvasModel.CanvasGlyph> visibleGlyphs = viewport.slice().glyphs();
        fill(poseStack, 0, 0, this.width, this.height, BACKGROUND);
        if (showGrid) {
            renderGrid(poseStack);
        }
        if (showCursorTrail) {
            renderCursorTrail(poseStack);
        }
        renderOpenTargetRange(poseStack);
        renderGlyphs(poseStack, visibleGlyphs);
        renderSymbolHoverUnderline(poseStack, visibleGlyphs);
        if (!hideSelection) {
            renderGlyphSelectionHighlights(poseStack, visibleGlyphs);
        }
        if (showGlyphBoundingBoxes) {
            renderGlyphBoundingBoxes(poseStack, visibleGlyphs);
        }
        renderCanvasCursor(poseStack);
        if (showCrosshairCoordinates) {
            renderHud(poseStack);
        }
        if (diagnosticControlsVisible) {
            renderInputDiagnostics(poseStack);
        }
        renderSaveDiagnostic(poseStack);
        renderSyntaxDiagnostic(poseStack);
        renderReadOnlyChrome(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        performanceTracker.frame(
                frameStartedNanos,
                System.nanoTime(),
                viewport.slice(),
                viewport.rebuilt(),
                frameAllocatedBefore
        );
    }

    @Override
    public void mouseMoved(
            double mouseX,
            double mouseY
    ) {
        performanceTracker.inputReceived(System.nanoTime());
        try {
            super.mouseMoved(mouseX, mouseY);
        } finally {
            performanceTracker.inputApplied(System.nanoTime());
        }
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        performanceTracker.inputReceived(System.nanoTime());
        try {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                beginPan(mouseX, mouseY);
                focusMainCanvas();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (hasAltDown()) {
                    model().addCursor(screenToCanvasX(mouseX), screenToCanvasY(mouseY));
                } else if (hasControlDown()) {
                    model().setAllCursors(screenToCanvasX(mouseX), screenToCanvasY(mouseY));
                } else {
                    model().setActiveCursors(screenToCanvasX(mouseX), screenToCanvasY(mouseY));
                }
                focusMainCanvas();
                rememberCursorPosition();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        } finally {
            performanceTracker.inputApplied(System.nanoTime());
        }
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        performanceTracker.inputReceived(System.nanoTime());
        try {
            if (panning && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                cameraX = panAnchorCameraX - (mouseX - panAnchorMouseX) / zoom;
                cameraY = panAnchorCameraY - (mouseY - panAnchorMouseY) / zoom;
                model().setCursor(screenToCanvasX(mouseX), screenToCanvasY(mouseY));
                rememberCursorPosition();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (hasAltDown()) {
                    model().addCursorAvoidingCrowding(
                            screenToCanvasX(mouseX),
                            screenToCanvasY(mouseY),
                            this.font.width("W"),
                            this.font.lineHeight
                    );
                } else {
                    model().setActiveCursors(screenToCanvasX(mouseX), screenToCanvasY(mouseY));
                }
                focusMainCanvas();
                rememberCursorPosition();
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        } finally {
            performanceTracker.inputApplied(System.nanoTime());
        }
    }

    @Override
    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        performanceTracker.inputReceived(System.nanoTime());
        try {
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && panning) {
                panning = false;
                return true;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        } finally {
            performanceTracker.inputApplied(System.nanoTime());
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        performanceTracker.inputReceived(System.nanoTime());
        try {
            if (delta == 0.0D) {
                return super.mouseScrolled(mouseX, mouseY, delta);
            }
            double focusX = screenToCanvasX(mouseX);
            double focusY = screenToCanvasY(mouseY);
            double scaleFactor = Math.pow(ZOOM_STEP, delta);
            zoom = Mth.clamp(zoom * scaleFactor, MIN_ZOOM, MAX_ZOOM);
            cameraX = focusX - (mouseX - this.width / 2.0D) / zoom;
            cameraY = focusY - (mouseY - this.height / 2.0D) / zoom;
            model().setCursor(focusX, focusY);
            rememberCursorPosition();
            return true;
        } finally {
            performanceTracker.inputApplied(System.nanoTime());
        }
    }

    @Override
    public boolean charTyped(
            char codePoint,
            int modifiers
    ) {
        performanceTracker.inputReceived(System.nanoTime());
        try {
        if (suppressNextNumpadPanChar) {
            suppressNextNumpadPanChar = false;
            if (codePoint >= '0' && codePoint <= '9') {
                rememberInputEvent(String.format("charTyped suppressed numpad pan '%s'", Character.toString(codePoint)));
                return true;
            }
        }
        if (Character.isISOControl(codePoint)) {
            return super.charTyped(codePoint, modifiers);
        }
        if (openContext != null && openContext.readOnly()) {
            return true;
        }
        String text = Character.toString(codePoint);
        rememberInputEvent(String.format("charTyped '%s' U+%04X modifiers=%s", text, (int) codePoint, modifierText(modifiers)));
        model().typeGlyph(text, this.font.width(text), this.font.lineHeight);
        documentChanged();
        rememberCursorPosition();
        return true;
        } finally {
            performanceTracker.inputApplied(System.nanoTime());
        }
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        performanceTracker.inputReceived(System.nanoTime());
        try {
        rememberInputEvent(String.format("keyPressed key=%d scan=%d modifiers=%s", keyCode, scanCode, modifierText(modifiers)));
        if (keyCode == GLFW.GLFW_KEY_F3) {
            diagnosticControlsVisible = !diagnosticControlsVisible;
            refreshDiagnosticControls();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F1) {
            model().focusPreviousCursor((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F4) {
            model().focusNextCursor((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            rememberCursorPosition();
            return true;
        }
        if (handleCameraShortcut(keyCode, modifiers)) {
            return true;
        }
        if (Screen.isCopy(keyCode)) {
            copyCanvasTextToClipboard();
            return true;
        }
        if (Screen.isPaste(keyCode)) {
            if (openContext != null && openContext.readOnly()) {
                return true;
            }
            pasteClipboardText();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            model().ensureCursorClosestToEachGlyph();
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_L && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            model().ensureCursorClosestToEachGlyphOnActiveCursorLines(this.font.lineHeight);
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_COMMA && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            model().discardCursorsNotClosestToAnyGlyph();
            rememberCursorPosition();
            return true;
        }
        if (handleArrowAddCursorShortcut(keyCode, modifiers)) {
            return true;
        }
        if (handleNumpadCameraPan(keyCode)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                model().moveCursorLeftWord(this.font.lineHeight, this.font.width(" "));
            } else {
                model().moveCursorLeft(this.font.lineHeight, this.font.width(" "));
            }
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                model().moveCursorRightWord(this.font.lineHeight, this.font.width(" "));
            } else {
                model().moveCursorRight(this.font.width(" "));
            }
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                model().moveCursorUpToGlyph(this.font.lineHeight);
            } else {
                model().moveCursorUp(this.font.lineHeight);
            }
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                model().moveCursorDownToGlyph(this.font.lineHeight);
            } else {
                model().moveCursorDown(this.font.lineHeight);
            }
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                model().moveCursorToDocumentStart();
            } else {
                model().moveCursorToLineStart();
            }
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                model().moveCursorToDocumentEnd();
            } else {
                model().moveCursorToLineEnd();
            }
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (openContext != null && openContext.readOnly()) return true;
            model().deleteLeftWord(this.font.lineHeight);
            documentChanged();
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (openContext != null && openContext.readOnly()) return true;
            model().deleteRightWord(this.font.lineHeight);
            documentChanged();
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (openContext != null && openContext.readOnly()) return true;
            model().deleteLeft(this.font.lineHeight);
            documentChanged();
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (openContext != null && openContext.readOnly()) return true;
            model().deleteNearestAndMoveRight(this.font.lineHeight);
            documentChanged();
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (openContext != null && openContext.readOnly()) return true;
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                saveDocumentAndClose();
            } else {
                insertLineBreak();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
        } finally {
            performanceTracker.inputApplied(System.nanoTime());
        }
    }

    @Override
    public boolean keyReleased(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        performanceTracker.inputReceived(System.nanoTime());
        try {
        rememberInputEvent(String.format("keyReleased key=%d scan=%d modifiers=%s", keyCode, scanCode, modifierText(modifiers)));
        if (isNumpadPanKey(keyCode)) {
            suppressNextNumpadPanChar = false;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
        } finally {
            performanceTracker.inputApplied(System.nanoTime());
        }
    }

    private boolean handleNumpadCameraPan(int keyCode) {
        double x = 0.0D;
        double y = 0.0D;
        switch (keyCode) {
            case GLFW.GLFW_KEY_KP_7 -> {
                x = -1.0D;
                y = -1.0D;
            }
            case GLFW.GLFW_KEY_KP_8 -> y = -1.0D;
            case GLFW.GLFW_KEY_KP_9 -> {
                x = 1.0D;
                y = -1.0D;
            }
            case GLFW.GLFW_KEY_KP_4 -> x = -1.0D;
            case GLFW.GLFW_KEY_KP_6 -> x = 1.0D;
            case GLFW.GLFW_KEY_KP_1 -> {
                x = -1.0D;
                y = 1.0D;
            }
            case GLFW.GLFW_KEY_KP_2 -> y = 1.0D;
            case GLFW.GLFW_KEY_KP_3 -> {
                x = 1.0D;
                y = 1.0D;
            }
            default -> {
                return false;
            }
        }
        panCamera(x * KEYBOARD_PAN_SCREEN_PIXELS, y * KEYBOARD_PAN_SCREEN_PIXELS);
        suppressNextNumpadPanChar = true;
        return true;
    }

    private boolean isNumpadPanKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_KP_1
               || keyCode == GLFW.GLFW_KEY_KP_2
               || keyCode == GLFW.GLFW_KEY_KP_3
               || keyCode == GLFW.GLFW_KEY_KP_4
               || keyCode == GLFW.GLFW_KEY_KP_6
               || keyCode == GLFW.GLFW_KEY_KP_7
               || keyCode == GLFW.GLFW_KEY_KP_8
               || keyCode == GLFW.GLFW_KEY_KP_9;
    }

    private boolean handleCameraShortcut(
            int keyCode,
            int modifiers
    ) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            return false;
        }
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && (keyCode == GLFW.GLFW_KEY_9 || keyCode == GLFW.GLFW_KEY_KP_9)) {
            fitCanvasContentToScreen();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_0 || keyCode == GLFW.GLFW_KEY_KP_0) {
            resetZoomLevel();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            zoomAtScreenCenter(ZOOM_STEP);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoomAtScreenCenter(1.0D / ZOOM_STEP);
            return true;
        }
        return false;
    }

    private boolean handleArrowAddCursorShortcut(
            int keyCode,
            int modifiers
    ) {
        if ((modifiers & GLFW.GLFW_MOD_ALT) == 0) {
            return false;
        }
        boolean wordTarget = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        int lineHeight = this.font.lineHeight;
        int spaceWidth = this.font.width(" ");
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (wordTarget) {
                model().addCursorLeftWord(lineHeight, spaceWidth);
            } else {
                model().addCursor(model().cursorCanvasX() - spaceWidth, model().cursorCanvasY());
            }
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (wordTarget) {
                model().addCursorRightWord(lineHeight, spaceWidth);
            } else {
                model().addCursor(model().cursorCanvasX() + spaceWidth, model().cursorCanvasY());
            }
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (wordTarget) {
                model().addCursorUpToGlyph(lineHeight);
            } else {
                model().addCursor(model().cursorCanvasX(), model().cursorCanvasY() - lineHeight);
            }
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (wordTarget) {
                model().addCursorDownToGlyph(lineHeight);
            } else {
                model().addCursor(model().cursorCanvasX(), model().cursorCanvasY() + lineHeight);
            }
            rememberCursorPosition();
            return true;
        }
        return false;
    }

    private void zoomAtScreenCenter(double scaleFactor) {
        double focusX = screenToCanvasX(this.width / 2.0D);
        double focusY = screenToCanvasY(this.height / 2.0D);
        zoom = Mth.clamp(zoom * scaleFactor, MIN_ZOOM, MAX_ZOOM);
        cameraX = focusX;
        cameraY = focusY;
    }

    private void resetZoomLevel() {
        zoom = 1.0D;
    }

    private void panCamera(
            double screenDeltaX,
            double screenDeltaY
    ) {
        cameraX += screenDeltaX / zoom;
        cameraY += screenDeltaY / zoom;
    }

    private void fitCanvasContentToScreen() {
        Optional<SFMDrawCanvasDocumentIndex.ContentBounds> indexedBounds = model()
                .documentIndex(this.font.width(" "), this.font.lineHeight)
                .bounds();
        if (indexedBounds.isEmpty()) {
            resetZoomLevel();
            return;
        }
        SFMDrawCanvasDocumentIndex.ContentBounds contentBounds = indexedBounds.orElseThrow();
        double left = contentBounds.left();
        double top = contentBounds.top();
        double right = contentBounds.right();
        double bottom = contentBounds.bottom();

        double contentWidth = Math.max(1.0D, right - left);
        double contentHeight = Math.max(1.0D, bottom - top);
        double availableWidth = Math.max(1.0D, this.width - FIT_CONTENT_MARGIN * 2.0D);
        double availableHeight = Math.max(1.0D, this.height - FIT_CONTENT_MARGIN * 2.0D);
        zoom = Mth.clamp(Math.min(availableWidth / contentWidth, availableHeight / contentHeight), MIN_ZOOM, MAX_ZOOM);
        cameraX = (left + right) / 2.0D;
        cameraY = (top + bottom) / 2.0D;
    }

    private void addDiagnosticButton(
            int x,
            int y,
            ToggleReader reader,
            ToggleWriter writer,
            String label
    ) {
        Button button = new SFMButtonBuilder()
                .setPosition(x, y)
                .setSize(104, 20)
                .setText(diagnosticButtonLabel(label, reader.get()))
                .setOnPress(pressed -> {
                    writer.set(!reader.get());
                    refreshDiagnosticControls();
                })
                .build();
        diagnosticButtons.add(button);
        this.addRenderableWidget(button);
    }

    private void refreshDiagnosticControls() {
        for (Button button : diagnosticButtons) {
            button.visible = diagnosticControlsVisible;
            button.active = diagnosticControlsVisible;
        }
        if (diagnosticButtons.size() >= 2) {
            diagnosticButtons.get(0).setMessage(diagnosticButtonLabel("Coords", showCrosshairCoordinates));
            diagnosticButtons.get(1).setMessage(diagnosticButtonLabel("Glyph Bounds", showGlyphBoundingBoxes));
        }
        if (diagnosticButtons.size() >= 3) {
            diagnosticButtons.get(2).setMessage(diagnosticButtonLabel("Cursor Trail", showCursorTrail));
        }
        if (diagnosticButtons.size() >= 4) {
            diagnosticButtons.get(3).setMessage(diagnosticButtonLabel("Grid", showGrid));
        }
        if (showCursorTrail && cursorTrail.isEmpty()) {
            rememberCursorPosition();
        }
        if (diagnosticButtons.size() >= 5) {
            diagnosticButtons.get(4).setMessage(diagnosticButtonLabel("Hide Selection", hideSelection));
        }
    }

    private Component diagnosticButtonLabel(
            String label,
            boolean enabled
    ) {
        return Component.literal((enabled ? "[x] " : "[ ] ") + label);
    }

    private SFMDrawCanvasModel model() {
        if (model == null) {
            model = new SFMDrawCanvasModel();
        }
        return model;
    }

    private void loadInitialContent() {
        if (initialContentLoaded || openContext == null) {
            return;
        }
        initialContentLoaded = true;
        model = new SFMDrawCanvasModel();
        long loadStartedNanos = System.nanoTime();
        model.replaceText(openContext.initialValue(), this.font::width, this.font.lineHeight);
        performanceTracker.coldLoad(System.nanoTime() - loadStartedNanos);
        initialCanvasProjectionText = model.projectedText(this.font.width(" "), this.font.lineHeight);
        model.moveCursorToDocumentStart();
        documentGeneration = incrementGeneration(documentGeneration);
        refreshLocalSyntaxColours();
        contextGeneration = incrementGeneration(contextGeneration);
    }

    /** Positions and visibly marks an exact UTF-8 witnessed source range. */
    public void openAtTextRange(SFMTextDocumentRange range) {
        Objects.requireNonNull(range, "range");
        if (openContext == null) throw new IllegalStateException("A standalone canvas has no text document");
        loadInitialContent();
        range.validateAgainst(openContext.initialValue());
        openTargetRange = Optional.of(range);
        long geometryStartedNanos = System.nanoTime();
        CanvasTextPoint point = canvasPoint(openContext.initialValue(), range.start());
        model().setCursor(point.x(), point.y());
        SFMNavigationFramingPolicy.Result framing = frameDestination(openContext.initialValue(), range);
        cameraX = framing.camera().x();
        cameraY = framing.camera().y();
        navigationFramingObservation = Optional.of(framing.observation());
        cameraInitialized = true;
        cameraViewportWidth = this.width;
        cameraViewportHeight = this.height;
        performanceTracker.openTargetGeometry(System.nanoTime() - geometryStartedNanos);
        rememberCursorPosition();
    }

    /** Latest machine-readable decision made while revealing a navigation destination. */
    public Optional<SFMSpatialSemanticContract.FramingObservation> navigationFramingObservation() {
        return navigationFramingObservation;
    }

    private SFMNavigationFramingPolicy.Result frameDestination(String text, SFMTextDocumentRange range) {
        SFMDrawCanvasDocumentIndex index = model().documentIndex(this.font.width(" "), this.font.lineHeight);
        int lineCount = documentLineCount(text);
        double documentRight = index.bounds().map(SFMDrawCanvasDocumentIndex.ContentBounds::right).orElse(0.0D);
        var documentBounds = new SFMSpatialSemanticContract.Rectangle(
                0.0D,
                0.0D,
                Math.max(0.0D, documentRight),
                Math.max(this.font.lineHeight, (double) lineCount * this.font.lineHeight)
        );
        String targetLine = lineText(text, range.start().line());
        var lineBounds = new SFMSpatialSemanticContract.Rectangle(
                0.0D,
                (double) range.start().line() * this.font.lineHeight,
                Math.max(0.0D, this.font.width(targetLine)),
                (double) (range.start().line() + 1) * this.font.lineHeight
        );
        var destinationBounds = canvasBounds(text, range);
        SFMTextDocumentSnapshot snapshot = openContext.documentSnapshot()
                .orElseGet(() -> SFMTextDocumentSnapshot.literal(text));
        String address = snapshot.path().map(path -> path.canonical()).orElse("editor://text-editor-v3");
        String hash = snapshot.sha256().orElseGet(() -> SFMContextTextCoordinates.sha256(text));
        return SFMNavigationFramingPolicy.choose(new SFMNavigationFramingPolicy.Request(
                "sfm:text-editor-v3",
                address,
                hash,
                "utf8:" + range.start().byteOffset() + ".." + range.end().byteOffset(),
                "start",
                documentBounds,
                lineBounds,
                destinationBounds,
                Math.max(1, this.width),
                Math.max(1, this.height),
                DEFAULT_ORIGIN_MARGIN,
                new SFMSpatialSemanticContract.Camera(cameraX, cameraY, zoom)
        ));
    }

    private SFMSpatialSemanticContract.Rectangle canvasBounds(String text, SFMTextDocumentRange range) {
        double left = Double.POSITIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (int line = range.start().line(); line <= range.end().line(); line++) {
            String value = lineText(text, line);
            int codePoints = value.codePointCount(0, value.length());
            int startColumn = line == range.start().line() ? range.start().column() : 0;
            int endColumn = line == range.end().line() ? range.end().column() : codePoints;
            int startIndex = value.offsetByCodePoints(0, Math.min(startColumn, codePoints));
            int endIndex = value.offsetByCodePoints(0, Math.min(endColumn, codePoints));
            double rowLeft = this.font.width(value.substring(0, startIndex));
            double rowRight = this.font.width(value.substring(0, endIndex));
            double rowTop = (double) line * this.font.lineHeight;
            left = Math.min(left, rowLeft);
            top = Math.min(top, rowTop);
            right = Math.max(right, Math.max(rowLeft, rowRight));
            bottom = Math.max(bottom, rowTop + this.font.lineHeight);
        }
        if (!Double.isFinite(left)) {
            CanvasTextPoint point = canvasPoint(text, range.start());
            left = point.x();
            top = point.y();
            right = point.x();
            bottom = point.y() + this.font.lineHeight;
        }
        return new SFMSpatialSemanticContract.Rectangle(left, top, right, bottom);
    }

    private static int documentLineCount(String text) {
        int lines = 1;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '\r') {
                if (index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                lines++;
            } else if (value == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /** Captures exact current text plus immutable 2D cursor projections for one panel origin. */
    public SFMContextDocumentProjection captureContextProjection(
            String editorId,
            SFMTextDocumentSnapshot baseline,
            boolean readOnly
    ) {
        long captureStartedNanos = System.nanoTime();
        Objects.requireNonNull(baseline, "baseline");
        loadInitialContent();
        SFMDrawCanvasDocumentIndex documentIndex = model().documentIndex(this.font.width(" "), this.font.lineHeight);
        SFMDrawCanvasSyntaxHighlightingHelper.CanvasDocumentProjection canvas = documentIndex.projection();
        String projectedText = canvas.text();
        boolean dirty = !projectedText.equals(initialCanvasProjectionText);
        String currentText = !dirty && baseline.ready() ? baseline.text() : projectedText;
        List<SFMContextCursorProjection> cursors = new ArrayList<>();
        List<SFMDrawCanvasModel.CanvasCursor> currentCursors = List.copyOf(model().cursors());
        for (int index = 0; index < currentCursors.size(); index++) {
            SFMDrawCanvasModel.CanvasCursor cursor = currentCursors.get(index);
            cursors.add(new SFMContextCursorProjection(
                    "cursor-" + index,
                    new SFMContextPosition.Canvas(
                            cursor.x(),
                            cursor.y(),
                            contextTextPosition(cursor, documentIndex, currentText)
                    ),
                    index == model().focusedCursorIndex(),
                    cursor.active()
            ));
        }
        SFMContextDocumentProjection captured = SFMContextDocumentProjection.capture(
                editorId,
                baseline,
                currentText,
                dirty,
                readOnly,
                cursors,
                List.of()
        );
        performanceTracker.contextCapture(System.nanoTime() - captureStartedNanos);
        return captured;
    }

    public long contextGeneration() {
        return contextGeneration;
    }

    public long documentGeneration() {
        return documentGeneration;
    }

    /** Immutable evidence for one exact painted canvas/layout generation. */
    public SpatialLayoutSnapshot captureSpatialLayout() {
        loadInitialContent();
        SFMDrawCanvasDocumentIndex index = model().documentIndex(this.font.width(" "), this.font.lineHeight);
        long generation = model().contentRevision();
        StringBuilder fingerprintInput = new StringBuilder(index.projection().text())
                .append('\u0000').append(generation)
                .append('\u0000').append(this.font.lineHeight)
                .append('\u0000').append(this.width).append('x').append(this.height)
                .append('\u0000').append(cameraX).append(',').append(cameraY).append(',').append(zoom);
        for (SFMDrawCanvasModel.CanvasGlyph glyph : index.orderedGlyphs()) {
            fingerprintInput.append('\u0000')
                    .append(glyph.x()).append(',')
                    .append(glyph.y()).append(',')
                    .append(glyph.width()).append(':')
                    .append(glyph.text());
        }
        return new SpatialLayoutSnapshot(
                index,
                this.font.lineHeight,
                generation,
                ca.teamdman.sfm.client.symbol.SFMDefinitionRequest.sha256(fingerprintInput.toString()),
                this.width,
                this.height,
                new SFMSpatialSemanticContract.Camera(cameraX, cameraY, zoom)
        );
    }

    public SFMContextDocumentProjection captureContextProjectionAt(
            String editorId,
            SFMTextDocumentSnapshot baseline,
            boolean readOnly,
            SymbolHit hit
    ) {
        long captureStartedNanos = System.nanoTime();
        Objects.requireNonNull(hit, "hit");
        Objects.requireNonNull(baseline, "baseline");
        loadInitialContent();
        SFMDrawCanvasDocumentIndex index = model().documentIndex(this.font.width(" "), this.font.lineHeight);
        SFMDrawCanvasSyntaxHighlightingHelper.CanvasDocumentProjection canvas = index.projection();
        String projectedText = canvas.text();
        boolean dirty = !projectedText.equals(initialCanvasProjectionText);
        String currentText = !dirty && baseline.ready() ? baseline.text() : projectedText;
        SFMDrawCanvasModel.CanvasGlyph glyph = index.orderedGlyphs().get(hit.glyphOrdinal());
        int capturedOffset = index.utf16OffsetOf(glyph)
                .orElse(Math.min(hit.navigationUtf16Offset(), currentText.length()));
        SFMTextDocumentPosition position = SFMContextTextCoordinates.atUtf16Offset(
                currentText,
                capturedOffset
        );
        SFMContextDocumentProjection captured = SFMContextDocumentProjection.capture(
                editorId,
                baseline,
                currentText,
                dirty,
                readOnly,
                List.of(new SFMContextCursorProjection(
                        "hover-cursor",
                        new SFMContextPosition.Canvas(glyph.x(), glyph.y(), Optional.of(position)),
                        true,
                        true
                )),
                List.of()
        );
        performanceTracker.contextCapture(System.nanoTime() - captureStartedNanos);
        return captured;
    }

    public void focusSymbolHit(SymbolHit hit) {
        Objects.requireNonNull(hit, "hit");
        SFMDrawCanvasModel.CanvasGlyph glyph = model()
                .documentIndex(this.font.width(" "), this.font.lineHeight)
                .orderedGlyphs()
                .get(hit.range().glyphStart());
        model().setActiveCursors(glyph.x(), glyph.y());
        focusMainCanvas();
        rememberCursorPosition();
    }

    public void focusContextAtScreen(double mouseX, double mouseY) {
        model().setActiveCursors(screenToCanvasX(mouseX), screenToCanvasY(mouseY));
        focusMainCanvas();
        rememberCursorPosition();
    }

    public Optional<SymbolHit> symbolHitAtScreen(double mouseX, double mouseY) {
        return symbolHitAtScreen(mouseX, mouseY, Optional.empty());
    }

    public Optional<SymbolHit> symbolHitAtScreen(
            double mouseX,
            double mouseY,
            Optional<SFMJavaInteractionMap.Result> interactionMap
    ) {
        if (openContext == null) return Optional.empty();
        SFMDrawCanvasDocumentIndex index = model().documentIndex(this.font.width(" "), this.font.lineHeight);
        SFMDrawCanvasModel.CanvasGlyph glyph = index.glyphAt(
                screenToCanvasX(mouseX),
                screenToCanvasY(mouseY)
        ).orElse(null);
        if (glyph == null) return Optional.empty();
        int offset = index.utf16OffsetOf(glyph).orElse(-1);
        int glyphOrdinal = index.glyphOrdinalOf(glyph).orElse(-1);
        if (offset < 0 || glyphOrdinal < 0) return Optional.empty();
        String text = index.projection().text();
        Optional<SymbolHit> semantic = interactionMap.flatMap(map ->
                interactionMapHit(index, glyphOrdinal, offset, text, map));
        if (semantic.isPresent()) return semantic;
        SFMJavaCanvasInteractionRegions.Region region = javaInteractionRegions(text)
                .atUtf16(offset)
                .orElse(null);
        if (region == null) return Optional.empty();
        int start = region.utf16Start();
        int end = region.utf16End();
        SFMDrawCanvasModel.CanvasGlyph first = firstGlyph(index, start, end);
        SFMDrawCanvasModel.CanvasGlyph last = lastGlyph(index, start, end);
        if (first == null || last == null) return Optional.empty();
        int firstOrdinal = index.glyphOrdinalOf(first).orElse(-1);
        int finalOrdinal = index.glyphOrdinalOf(last).orElse(-1);
        if (firstOrdinal < 0 || finalOrdinal < firstOrdinal) return Optional.empty();
        return Optional.of(new SymbolHit(
                SFMSymbolHoverIdentity.TextGlyphRange.fromUtf16(
                        text,
                        start,
                        end,
                        firstOrdinal,
                        finalOrdinal + 1
                ),
                glyphOrdinal,
                region.kind().name().toLowerCase(java.util.Locale.ROOT),
                region.navigationUtf16Offset(),
                Optional.empty(),
                0
        ));
    }

    private Optional<SymbolHit> interactionMapHit(
            SFMDrawCanvasDocumentIndex index,
            int glyphOrdinal,
            int utf16Offset,
            String text,
            SFMJavaInteractionMap.Result map
    ) {
        if (map.outcome() != SFMJavaInteractionMap.Outcome.SUCCESS
                || map.documentGeneration() != documentGeneration
                || !map.document().contentHash().equals(ca.teamdman.sfm.client.symbol.SFMDefinitionRequest.sha256(text))) {
            return Optional.empty();
        }
        long byteOffset = SFMContextTextCoordinates.atUtf16Offset(text, utf16Offset).byteOffset();
        SFMJavaInteractionMap.Region region = map.mostSpecificRegionAtByte(byteOffset).orElse(null);
        if (region == null || map.classification(region.id()).isEmpty()) return Optional.empty();
        int start;
        int end;
        try {
            List<Integer> offsets = SFMContextTextCoordinates.utf16OffsetsAtUtf8Bytes(
                    text,
                    List.of(Math.toIntExact(region.startByte()), Math.toIntExact(region.endByte()))
            );
            start = offsets.get(0);
            end = offsets.get(1);
        } catch (ArithmeticException | IllegalArgumentException invalidProjection) {
            return Optional.empty();
        }
        SFMDrawCanvasModel.CanvasGlyph first = firstGlyph(index, start, end);
        SFMDrawCanvasModel.CanvasGlyph last = lastGlyph(index, start, end);
        if (first == null || last == null) return Optional.empty();
        int firstOrdinal = index.glyphOrdinalOf(first).orElse(-1);
        int finalOrdinal = index.glyphOrdinalOf(last).orElse(-1);
        if (firstOrdinal < 0 || finalOrdinal < firstOrdinal) return Optional.empty();
        return Optional.of(new SymbolHit(
                SFMSymbolHoverIdentity.TextGlyphRange.fromUtf16(
                        text, start, end, firstOrdinal, finalOrdinal + 1),
                glyphOrdinal,
                region.semanticKind(),
                start,
                Optional.of(region.id()),
                map.semanticGeneration()
        ));
    }

    private SFMJavaCanvasInteractionRegions.Index javaInteractionRegions(String text) {
        long revision = model().contentRevision();
        if (javaInteractionRegionIndex == null
                || javaInteractionRegionRevision != revision
                || !javaInteractionRegionIndex.text().equals(text)) {
            javaInteractionRegionIndex = SFMJavaCanvasInteractionRegions.index(text);
            javaInteractionRegionRevision = revision;
        }
        return javaInteractionRegionIndex;
    }

    private static SFMDrawCanvasModel.CanvasGlyph firstGlyph(
            SFMDrawCanvasDocumentIndex index,
            int start,
            int end
    ) {
        List<SFMDrawCanvasModel.CanvasGlyph> byChar = index.projection().glyphsByCharIndex();
        for (int offset = start; offset < end; offset++) {
            SFMDrawCanvasModel.CanvasGlyph glyph = byChar.get(offset);
            if (glyph != null) return glyph;
        }
        return null;
    }

    private static SFMDrawCanvasModel.CanvasGlyph lastGlyph(
            SFMDrawCanvasDocumentIndex index,
            int start,
            int end
    ) {
        List<SFMDrawCanvasModel.CanvasGlyph> byChar = index.projection().glyphsByCharIndex();
        for (int offset = end - 1; offset >= start; offset--) {
            SFMDrawCanvasModel.CanvasGlyph glyph = byChar.get(offset);
            if (glyph != null) return glyph;
        }
        return null;
    }

    public void setSymbolHoverUnderline(Optional<SFMSymbolHoverIdentity.TextGlyphRange> range) {
        symbolHoverUnderline = Objects.requireNonNull(range, "range");
    }

    private Optional<SFMTextDocumentPosition> contextTextPosition(
            SFMDrawCanvasModel.CanvasCursor cursor,
            SFMDrawCanvasDocumentIndex index,
            String currentText
    ) {
        SFMDrawCanvasSyntaxHighlightingHelper.CanvasDocumentProjection canvas = index.projection();
        if (canvas.text().isEmpty()) {
            return currentText.isEmpty()
                    ? Optional.of(SFMContextTextCoordinates.atUtf16Offset(currentText, 0))
                    : Optional.empty();
        }
        SFMDrawCanvasModel.CanvasGlyph hit = index.glyphAt(cursor.x(), cursor.y()).orElse(null);

        int projectedOffset = -1;
        if (hit != null) {
            projectedOffset = index.utf16OffsetOf(hit).orElse(-1);
        } else {
            SFMDrawCanvasModel.CanvasGlyph finalGlyph = index.finalGlyphOnVisualRow(cursor.y()).orElse(null);
            if (finalGlyph != null && cursor.x() >= finalGlyph.x() + finalGlyph.width()) {
                int start = index.utf16OffsetOf(finalGlyph).orElse(-1);
                if (start >= 0) projectedOffset = start + finalGlyph.text().length();
            }
        }
        if (projectedOffset < 0) return Optional.empty();
        SFMTextDocumentPosition projected = SFMContextTextCoordinates.atUtf16Offset(
                canvas.text(),
                projectedOffset
        );
        try {
            return Optional.of(SFMContextTextCoordinates.atLineColumn(
                    currentText,
                    projected.line(),
                    projected.column()
            ));
        } catch (IllegalArgumentException incompatibleBaseline) {
            return Optional.empty();
        }
    }

    private void renderOpenTargetRange(PoseStack poseStack) {
        if (openTargetRange.isEmpty() || openContext == null) return;
        SFMTextDocumentRange range = openTargetRange.orElseThrow();
        String text = openContext.initialValue();
        for (int line = range.start().line(); line <= range.end().line(); line++) {
            String lineText = lineText(text, line);
            int lineCodePoints = lineText.codePointCount(0, lineText.length());
            int startColumn = line == range.start().line() ? range.start().column() : 0;
            int endColumn = line == range.end().line() ? range.end().column() : lineCodePoints;
            if (startColumn >= endColumn) continue;
            int startIndex = lineText.offsetByCodePoints(0, Math.min(startColumn, lineCodePoints));
            int endIndex = lineText.offsetByCodePoints(0, Math.min(endColumn, lineCodePoints));
            double canvasX = font.width(lineText.substring(0, startIndex));
            double canvasY = (double) line * font.lineHeight;
            double canvasWidth = Math.max(1, font.width(lineText.substring(startIndex, endIndex)));
            int left = (int) Math.floor(canvasToScreenX(canvasX));
            int top = (int) Math.floor(canvasToScreenY(canvasY));
            int right = (int) Math.ceil(canvasToScreenX(canvasX + canvasWidth));
            int bottom = (int) Math.ceil(canvasToScreenY(canvasY + font.lineHeight));
            GuiComponent.fill(poseStack, left, top, right, bottom, 0x8042647A);
        }
    }

    private CanvasTextPoint canvasPoint(String text, SFMTextDocumentPosition position) {
        String line = lineText(text, position.line());
        int codePoints = line.codePointCount(0, line.length());
        if (position.column() > codePoints) {
            throw new IllegalArgumentException("Text column lies beyond line " + position.line());
        }
        int end = line.offsetByCodePoints(0, position.column());
        return new CanvasTextPoint(font.width(line.substring(0, end)), (double) position.line() * font.lineHeight);
    }

    private static String lineText(String text, int requestedLine) {
        int line = 0;
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value != '\r' && value != '\n') continue;
            if (line == requestedLine) return text.substring(start, index);
            if (value == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
            line++;
            start = index + 1;
        }
        if (line == requestedLine) return text.substring(start);
        throw new IllegalArgumentException("Text line lies beyond the document: " + requestedLine);
    }

    private record CanvasTextPoint(double x, double y) {
    }

    @Override
    public boolean canSaveDocument() {
        return openContext != null;
    }

    @Override
    public void saveDocumentAndClose() {
        if (openContext == null) {
            finishClose();
            return;
        }
        if (saveDocumentInternal().saved()) finishClose();
    }

    @Override
    public void saveDocument() {
        saveDocumentInternal();
    }

    private SFMTextDocumentSaveResult saveDocumentInternal() {
        if (openContext == null || openContext.readOnly()) {
            saveDiagnostic = Optional.empty();
            return SFMTextDocumentSaveResult.success();
        }
        SFMTextDocumentSaveResult result = openContext.saveDocument(getCurrentText());
        saveDiagnostic = result.diagnostic();
        return result;
    }

    private void renderSaveDiagnostic(PoseStack poseStack) {
        if (saveDiagnostic.isEmpty()) return;
        String text = saveDiagnostic.orElseThrow().getString();
        int maximumWidth = Math.max(0, width - 124);
        if (maximumWidth <= 0) return;
        String rendered = font.plainSubstrByWidth(text, maximumWidth);
        SFMFontUtils.draw(
                poseStack,
                font,
                rendered,
                24,
                Math.max(2, height - 18),
                0xFFFF7777,
                true
        );
    }

    @Override
    public void closeDocumentWithoutSaving() {
        onClose();
    }

    protected void finishClose() {
        if (pushed) {
            SFMScreenChangeHelpers.popScreen();
        } else {
            Minecraft.getInstance().setScreen(previousScreen);
        }
    }

    private void onTryCloseStandalone() {
        if (model().glyphs().isEmpty()) {
            Minecraft.getInstance().setScreen(previousScreen);
            return;
        }
        ConfirmScreen exitWithoutSavingConfirmScreen = new ConfirmScreen(
                doClose -> {
                    SFMScreenChangeHelpers.popScreen();
                    if (doClose) {
                        Minecraft.getInstance().setScreen(previousScreen);
                    }
                },
                ISFMTextEditScreenOpenContext.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_TITLE.getComponent(),
                ISFMTextEditScreenOpenContext.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_MESSAGE.getComponent(),
                ISFMTextEditScreenOpenContext.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_YES_BUTTON.getComponent(),
                ISFMTextEditScreenOpenContext.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_NO_BUTTON.getComponent()
        );
        SFMScreenChangeHelpers.setOrPushScreen(exitWithoutSavingConfirmScreen);
        exitWithoutSavingConfirmScreen.setDelay(20);
    }

    private String getCurrentText() {
        return model().documentIndex(this.font.width(" "), this.font.lineHeight).projection().text();
    }

    public SFMDrawCanvasPerformanceTracker.Snapshot performanceEvidence() {
        return performanceTracker.snapshot();
    }

    public void beginWarmPerformanceMeasurement() {
        performanceTracker.beginWarmMeasurement();
    }

    private ResolvedViewportGlyphs resolveViewportGlyphs() {
        long startedNanos = System.nanoTime();
        int spaceWidth = this.font.width(" ");
        int lineHeight = this.font.lineHeight;
        double firstCanvasX = screenToCanvasX(0);
        double secondCanvasX = screenToCanvasX(this.width);
        double firstCanvasY = screenToCanvasY(0);
        double secondCanvasY = screenToCanvasY(this.height);
        double minimumX = Math.min(firstCanvasX, secondCanvasX);
        double minimumY = Math.min(firstCanvasY, secondCanvasY);
        double maximumX = Math.max(firstCanvasX, secondCanvasX);
        double maximumY = Math.max(firstCanvasY, secondCanvasY);
        long contentRevision = model().contentRevision();
        if (viewportGlyphCache != null && viewportGlyphCache.matches(
                contentRevision,
                spaceWidth,
                lineHeight,
                minimumX,
                minimumY,
                maximumX,
                maximumY,
                this.width,
                this.height
        )) {
            performanceTracker.viewportResolved(System.nanoTime() - startedNanos);
            return viewportGlyphCache.cacheHit();
        }
        SFMDrawCanvasDocumentIndex.Viewport viewport = new SFMDrawCanvasDocumentIndex.Viewport(
                minimumX, minimumY, maximumX, maximumY
        );
        SFMDrawCanvasDocumentIndex.VisibleSlice slice = model()
                .documentIndex(spaceWidth, lineHeight)
                .visible(viewport);
        viewportGlyphCache = new ViewportGlyphCache(
                contentRevision,
                spaceWidth,
                lineHeight,
                minimumX,
                minimumY,
                maximumX,
                maximumY,
                this.width,
                this.height,
                slice,
                new ResolvedViewportGlyphs(slice, false)
        );
        performanceTracker.viewportResolved(System.nanoTime() - startedNanos);
        return new ResolvedViewportGlyphs(slice, true);
    }

    private void initializeCamera() {
        if (!cameraInitialized) {
            cameraX = (this.width / 2.0D - DEFAULT_ORIGIN_MARGIN) / zoom;
            cameraY = (this.height / 2.0D - DEFAULT_ORIGIN_MARGIN) / zoom;
            cameraInitialized = true;
        } else {
            cameraX = resizeCameraAxis(cameraX, zoom, cameraViewportWidth, this.width);
            cameraY = resizeCameraAxis(cameraY, zoom, cameraViewportHeight, this.height);
        }
        cameraViewportWidth = this.width;
        cameraViewportHeight = this.height;
    }

    static double resizeCameraAxis(
            double camera,
            double zoom,
            int previousViewportExtent,
            int nextViewportExtent
    ) {
        if (previousViewportExtent <= 0 || previousViewportExtent == nextViewportExtent) return camera;
        return camera + (nextViewportExtent - previousViewportExtent) / (2.0D * zoom);
    }

    private void beginPan(
            double mouseX,
            double mouseY
    ) {
        panning = true;
        panAnchorMouseX = mouseX;
        panAnchorMouseY = mouseY;
        panAnchorCameraX = cameraX;
        panAnchorCameraY = cameraY;
    }

    private void renderGrid(PoseStack poseStack) {
        double step = visibleGridStep();
        double leftCanvas = screenToCanvasX(0);
        double rightCanvas = screenToCanvasX(this.width);
        double topCanvas = screenToCanvasY(0);
        double bottomCanvas = screenToCanvasY(this.height);

        int firstVertical = Mth.floor(leftCanvas / step);
        int lastVertical = Mth.ceil(rightCanvas / step);
        for (int gridX = firstVertical; gridX <= lastVertical; gridX++) {
            double canvasX = gridX * step;
            int screenX = (int) Math.round(canvasToScreenX(canvasX));
            int color = gridLineColor(gridX);
            fill(poseStack, screenX, 0, screenX + 1, this.height, color);
        }

        int firstHorizontal = Mth.floor(topCanvas / step);
        int lastHorizontal = Mth.ceil(bottomCanvas / step);
        for (int gridY = firstHorizontal; gridY <= lastHorizontal; gridY++) {
            double canvasY = gridY * step;
            int screenY = (int) Math.round(canvasToScreenY(canvasY));
            int color = gridLineColor(gridY);
            fill(poseStack, 0, screenY, this.width, screenY + 1, color);
        }

        int axisX = (int) Math.round(canvasToScreenX(0.0D));
        if (axisX >= 0 && axisX < this.width) {
            fill(poseStack, axisX, 0, axisX + 2, this.height, AXIS_Y);
        }
        int axisY = (int) Math.round(canvasToScreenY(0.0D));
        if (axisY >= 0 && axisY < this.height) {
            fill(poseStack, 0, axisY, this.width, axisY + 2, AXIS_X);
        }
    }

    private int gridLineColor(int gridIndex) {
        return Math.floorMod(gridIndex, (int) MAJOR_GRID_INTERVAL) == 0 ? MAJOR_GRID : MINOR_GRID;
    }

    private double visibleGridStep() {
        double step = BASE_GRID_STEP;
        while (step * zoom < MIN_GRID_PIXEL_STEP) {
            step *= 2.0D;
        }
        while (step * zoom >= MIN_GRID_PIXEL_STEP * 4.0D) {
            step /= 2.0D;
        }
        return step;
    }

    private void renderGlyphs(
            PoseStack poseStack,
            List<SFMDrawCanvasModel.CanvasGlyph> visibleGlyphs
    ) {
        poseStack.pushPose();
        poseStack.translate(canvasToScreenX(0), canvasToScreenY(0), 0.0D);
        poseStack.scale((float) zoom, (float) zoom, 1.0F);
        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(
                Tesselator.getInstance().getBuilder()
        );
        var matrix = poseStack.last().pose();
        for (SFMDrawCanvasModel.CanvasGlyph glyph : visibleGlyphs) {
            Component styled = remoteStyledGlyphs.get(glyph);
            if (styled != null) {
                SFMFontUtils.drawInBatch(
                        styled,
                        this.font,
                        (int) glyph.x(),
                        (int) glyph.y(),
                        GLYPH,
                        true,
                        false,
                        matrix,
                        buffer
                );
            } else {
                SFMFontUtils.drawInBatch(
                        glyph.text(),
                        this.font,
                        (int) glyph.x(),
                        (int) glyph.y(),
                        localSyntaxColours.getOrDefault(glyph, GLYPH),
                        true,
                        false,
                        matrix,
                        buffer
                );
            }
        }
        buffer.endBatch();
        poseStack.popPose();
    }

    private void renderSymbolHoverUnderline(
            PoseStack poseStack,
            List<SFMDrawCanvasModel.CanvasGlyph> visibleGlyphs
    ) {
        if (symbolHoverUnderline.isEmpty()) return;
        SFMSymbolHoverIdentity.TextGlyphRange range = symbolHoverUnderline.orElseThrow();
        SFMDrawCanvasDocumentIndex index = model()
                .documentIndex(this.font.width(" "), this.font.lineHeight);
        int start = Math.max(0, Math.min(range.glyphStart(), index.orderedGlyphs().size()));
        int end = Math.max(start, Math.min(range.glyphEnd(), index.orderedGlyphs().size()));
        for (SFMDrawCanvasModel.CanvasGlyph glyph : visibleGlyphs) {
            int ordinal = index.glyphOrdinalOrMinusOne(glyph);
            if (ordinal < start || ordinal >= end) continue;
            int left = (int) Math.floor(canvasToScreenX(glyph.x()));
            int right = (int) Math.ceil(canvasToScreenX(glyph.x() + glyph.width()));
            int bottom = (int) Math.ceil(canvasToScreenY(glyph.y() + this.font.lineHeight));
            fill(poseStack, left, bottom - 1, Math.max(left + 1, right), bottom, 0xFF60A5FA);
        }
    }

    private void renderGlyphBoundingBoxes(
            PoseStack poseStack,
            List<SFMDrawCanvasModel.CanvasGlyph> visibleGlyphs
    ) {
        for (SFMDrawCanvasModel.CanvasGlyph glyph : visibleGlyphs) {
            int left = (int) Math.floor(canvasToScreenX(glyph.x()));
            int top = (int) Math.floor(canvasToScreenY(glyph.y()));
            int right = (int) Math.ceil(left + this.font.width(glyph.text()) * zoom);
            int bottom = (int) Math.ceil(top + this.font.lineHeight * zoom);
            drawRectOutline(poseStack, left, top, Math.max(left + 1, right), Math.max(top + 1, bottom), GLYPH_BOUNDS);
        }
    }

    private void renderGlyphSelectionHighlights(
            PoseStack poseStack,
            List<SFMDrawCanvasModel.CanvasGlyph> visibleGlyphs
    ) {
        long startedNanos = System.nanoTime();
        long cursorFingerprint = cursorFingerprint();
        SelectionHighlightCache cached = selectionHighlightCache;
        List<CanvasRect> highlights;
        if (cached != null && cached.matches(
                model().contentRevision(),
                cursorFingerprint,
                visibleGlyphs,
                cameraX,
                cameraY,
                zoom,
                this.font.lineHeight
        )) {
            highlights = cached.highlights();
            performanceTracker.selectionGeometry(false, System.nanoTime() - startedNanos);
        } else {
            List<CanvasRect> mask = new ArrayList<>();
            for (SFMDrawCanvasModel.CanvasGlyph glyph : visibleGlyphs) {
                if (uniqueCursorInGlyphBounds(glyph) == null) {
                    continue;
                }
                mask.add(new CanvasRect(
                        canvasToScreenX(glyph.x()),
                        canvasToScreenY(glyph.y()),
                        canvasToScreenX(glyph.x() + glyph.width()),
                        canvasToScreenY(glyph.y() + this.font.lineHeight)
                ));
            }
            highlights = List.copyOf(unionRects(mask));
            selectionHighlightCache = new SelectionHighlightCache(
                    model().contentRevision(),
                    cursorFingerprint,
                    visibleGlyphs,
                    cameraX,
                    cameraY,
                    zoom,
                    this.font.lineHeight,
                    highlights
            );
            performanceTracker.selectionGeometry(true, System.nanoTime() - startedNanos);
        }
        for (CanvasRect rect : highlights) {
            SFMScreenRenderUtils.renderHighlight(
                    poseStack,
                    rect.left(),
                    rect.top(),
                    Math.max(rect.left() + 1.0D, rect.right()),
                    Math.max(rect.top() + 1.0D, rect.bottom())
            );
        }
    }

    private long cursorFingerprint() {
        long value = 0xCBF29CE484222325L;
        for (SFMDrawCanvasModel.CanvasCursor cursor : model().cursors()) {
            value = mixFingerprint(value, Double.doubleToLongBits(cursor.x()));
            value = mixFingerprint(value, Double.doubleToLongBits(cursor.y()));
            value = mixFingerprint(value, cursor.color());
            value = mixFingerprint(value, cursor.active() ? 1L : 0L);
        }
        return mixFingerprint(value, model().focusedCursorIndex());
    }

    private static long mixFingerprint(long current, long value) {
        return (current ^ value) * 0x100000001B3L;
    }

    static List<CanvasRect> unionRects(List<CanvasRect> sourceRects) {
        List<CanvasRect> rects = sourceRects
                .stream()
                .filter(rect -> !rect.isEmpty())
                .toList();
        if (rects.isEmpty()) {
            return List.of();
        }

        List<Double> xs = sortedDistinctEdges(rects, true);
        List<Double> ys = sortedDistinctEdges(rects, false);
        boolean[][] covered = new boolean[ys.size() - 1][xs.size() - 1];

        for (int yIndex = 0; yIndex < ys.size() - 1; yIndex++) {
            double top = ys.get(yIndex);
            double bottom = ys.get(yIndex + 1);
            for (int xIndex = 0; xIndex < xs.size() - 1; xIndex++) {
                double left = xs.get(xIndex);
                double right = xs.get(xIndex + 1);
                for (CanvasRect rect : rects) {
                    if (rect.covers(left, top, right, bottom)) {
                        covered[yIndex][xIndex] = true;
                        break;
                    }
                }
            }
        }

        // Partition by all source edges so the result covers exactly the union without overlapping highlights.
        List<CanvasRect> horizontalStrips = new ArrayList<>();
        for (int yIndex = 0; yIndex < ys.size() - 1; yIndex++) {
            int runStart = -1;
            for (int xIndex = 0; xIndex <= xs.size() - 1; xIndex++) {
                boolean cellCovered = xIndex < xs.size() - 1 && covered[yIndex][xIndex];
                if (cellCovered && runStart == -1) {
                    runStart = xIndex;
                } else if (!cellCovered && runStart != -1) {
                    horizontalStrips.add(new CanvasRect(
                            xs.get(runStart),
                            ys.get(yIndex),
                            xs.get(xIndex),
                            ys.get(yIndex + 1)
                    ));
                    runStart = -1;
                }
            }
        }
        return mergeVerticalStrips(horizontalStrips);
    }

    private static List<Double> sortedDistinctEdges(
            List<CanvasRect> rects,
            boolean horizontal
    ) {
        List<Double> edges = new ArrayList<>();
        for (CanvasRect rect : rects) {
            edges.add(horizontal ? rect.left() : rect.top());
            edges.add(horizontal ? rect.right() : rect.bottom());
        }
        return edges.stream().distinct().sorted().toList();
    }

    private static List<CanvasRect> mergeVerticalStrips(List<CanvasRect> strips) {
        List<CanvasRect> merged = new ArrayList<>();
        for (CanvasRect strip : strips) {
            boolean mergedIntoExisting = false;
            for (int i = 0; i < merged.size(); i++) {
                CanvasRect existing = merged.get(i);
                if (existing.left() == strip.left()
                    && existing.right() == strip.right()
                    && existing.bottom() == strip.top()) {
                    merged.set(i, new CanvasRect(existing.left(), existing.top(), existing.right(), strip.bottom()));
                    mergedIntoExisting = true;
                    break;
                }
            }
            if (!mergedIntoExisting) {
                merged.add(strip);
            }
        }
        return merged;
    }

    private void renderCursorTrail(PoseStack poseStack) {
        int count = cursorTrail.size();
        for (int i = 0; i < count; i++) {
            CanvasPoint point = cursorTrail.get(i);
            double age = count <= 1 ? 1.0D : (double) i / (double) (count - 1);
            int alpha = 32 + (int) Math.round(age * 176.0D);
            int color = (alpha << 24) | (CURSOR_TRAIL & 0x00FFFFFF);
            int screenX = (int) Math.round(canvasToScreenX(point.x()));
            int screenY = (int) Math.round(canvasToScreenY(point.y()));
            int size = Math.max(1, (int) Math.round(2.0D * zoom));
            fill(poseStack, screenX - size, screenY - size, screenX + size + 1, screenY + size + 1, color);
        }
    }

    private void drawRectOutline(
            PoseStack poseStack,
            int left,
            int top,
            int right,
            int bottom,
            int color
    ) {
        fill(poseStack, left, top, right, top + 1, color);
        fill(poseStack, left, bottom - 1, right, bottom, color);
        fill(poseStack, left, top, left + 1, bottom, color);
        fill(poseStack, right - 1, top, right, bottom, color);
    }

    private void renderCanvasCursor(PoseStack poseStack) {
        for (int i = 0; i < model().cursors().size(); i++) {
            SFMDrawCanvasModel.CanvasCursor cursor = model().cursors().get(i);
            if (!hideSelection && isUniqueCursorInAnyGlyphBounds(cursor)) {
                continue;
            }
            renderCanvasCursor(poseStack, cursor, i == model().focusedCursorIndex());
        }
    }

    private void renderCanvasCursor(
            PoseStack poseStack,
            SFMDrawCanvasModel.CanvasCursor cursor,
            boolean focused
    ) {
        int mouseX = (int) Math.round(canvasToScreenX(cursor.x()));
        int mouseY = (int) Math.round(canvasToScreenY(cursor.y()));
        int size = panning ? 8 : 6;
        int cursorSize = cursor.active() ? size + 2 : size;
        if (focused) {
            SFMGuiCrosshair.draw(poseStack, mouseX, mouseY, cursorSize + 2, focusedCursorOutlineColor(cursor.color()));
        }
        SFMGuiCrosshair.draw(poseStack, mouseX, mouseY, cursorSize, cursor.active() ? cursor.color() : inactiveCursorColor(cursor.color()));
    }

    private int inactiveCursorColor(int color) {
        return 0x88000000 | (color & 0x00FFFFFF);
    }

    private int focusedCursorOutlineColor(int color) {
        int red = Math.min(255, ((color >> 16) & 0xFF) + 56);
        int green = Math.min(255, ((color >> 8) & 0xFF) + 56);
        int blue = Math.min(255, (color & 0xFF) + 56);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private SFMDrawCanvasModel.CanvasCursor uniqueCursorInGlyphBounds(SFMDrawCanvasModel.CanvasGlyph glyph) {
        return uniqueCursorInGlyphBounds(model(), glyph);
    }

    private SFMDrawCanvasModel.CanvasCursor uniqueCursorInGlyphBounds(
            SFMDrawCanvasModel sourceModel,
            SFMDrawCanvasModel.CanvasGlyph glyph
    ) {
        SFMDrawCanvasModel.CanvasCursor selected = null;
        for (SFMDrawCanvasModel.CanvasCursor cursor : sourceModel.cursors()) {
            if (!cursorInGlyphBounds(cursor, glyph)) {
                continue;
            }
            if (selected != null) {
                return null;
            }
            selected = cursor;
        }
        return selected;
    }

    private boolean isUniqueCursorInAnyGlyphBounds(SFMDrawCanvasModel.CanvasCursor cursor) {
        return isUniqueCursorInAnyGlyphBounds(model(), cursor);
    }

    private boolean isUniqueCursorInAnyGlyphBounds(
            SFMDrawCanvasModel sourceModel,
            SFMDrawCanvasModel.CanvasCursor cursor
    ) {
        return sourceModel.documentIndex(this.font.width(" "), this.font.lineHeight)
                .glyphAt(cursor.x(), cursor.y())
                .map(glyph -> uniqueCursorInGlyphBounds(sourceModel, glyph) == cursor)
                .orElse(false);
    }

    private boolean cursorInGlyphBounds(
            SFMDrawCanvasModel.CanvasCursor cursor,
            SFMDrawCanvasModel.CanvasGlyph glyph
    ) {
        return cursor.x() >= glyph.x()
               && cursor.x() < glyph.x() + glyph.width()
               && cursor.y() >= glyph.y()
               && cursor.y() < glyph.y() + this.font.lineHeight;
    }

    private void rememberCursorPosition() {
        contextGeneration = incrementGeneration(contextGeneration);
        if (!showCursorTrail && cursorTrail.isEmpty()) {
            return;
        }
        if (!cursorTrail.isEmpty()) {
            CanvasPoint previous = cursorTrail.get(cursorTrail.size() - 1);
            double dx = model().cursorCanvasX() - previous.x();
            double dy = model().cursorCanvasY() - previous.y();
            if (dx * dx + dy * dy < CURSOR_TRAIL_MIN_DISTANCE * CURSOR_TRAIL_MIN_DISTANCE) {
                return;
            }
        }
        cursorTrail.add(new CanvasPoint(model().cursorCanvasX(), model().cursorCanvasY()));
        while (cursorTrail.size() > CURSOR_TRAIL_LIMIT) {
            cursorTrail.remove(0);
        }
    }

    private static long incrementGeneration(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }

    private void insertLineBreak() {
        model().insertLineBreak(this.font.lineHeight);
        documentChanged();
        rememberCursorPosition();
    }

    private void pasteClipboardText() {
        String clipboardContents;
        try {
            clipboardContents = Minecraft.getInstance().keyboardHandler.getClipboard();
        } catch (Throwable ignored) {
            return;
        }
        if (clipboardContents.isEmpty()) {
            return;
        }
        model().pasteText(clipboardContents, text -> this.font.width(text), this.font.lineHeight);
        documentChanged();
        rememberCursorPosition();
    }

    private void documentChanged() {
        documentGeneration = incrementGeneration(documentGeneration);
        clearRemoteSyntaxStyles();
        syntaxPresentationEvidence = Optional.empty();
        refreshLocalSyntaxColours();
        requestRemoteSyntaxStyles();
    }

    private void refreshLocalSyntaxColours() {
        long startedNanos = System.nanoTime();
        try {
            if (isConcreteJavaDocument()) {
                localSyntaxColours = Map.of();
                return;
            }
            localSyntaxColours = SFMDrawCanvasSyntaxHighlightingHelper.buildSyntaxHighlightColours(
                    model().glyphs(),
                    this.font.width(" "),
                    this.font.lineHeight,
                    GLYPH
            );
        } finally {
            performanceTracker.styleProjection(System.nanoTime() - startedNanos);
        }
    }

    private boolean isConcreteJavaDocument() {
        return openContext != null
                && openContext.documentSnapshot()
                        .flatMap(SFMTextDocumentSnapshot::path)
                        .map(path -> path.extension().equalsIgnoreCase("java"))
                        .orElse(false);
    }

    private void ensureRemoteSyntaxHighlighting() {
        if (!isConcreteJavaDocument()) return;
        if (syntaxSession == null) {
            syntaxSession = new SFMTextEditorSyntaxSession(
                    syntaxOriginId,
                    SFMSyntaxHighlightRuntime.get(),
                    runnable -> Minecraft.getInstance().execute(runnable),
                    this::publishRemoteSyntaxStyles,
                    this::publishRemoteSyntaxFailure
            );
        }
        requestRemoteSyntaxStyles();
    }

    private void requestRemoteSyntaxStyles() {
        if (!isConcreteJavaDocument() || syntaxSession == null
                || lastRequestedSyntaxGeneration == documentGeneration) return;
        String source = getCurrentText();
        syntaxRequestStartedNanos = System.nanoTime();
        syntaxDiagnostic = Optional.empty();
        try {
            syntaxSession.request(documentGeneration, "java", source);
            performanceTracker.syntaxWorkerSubmitted();
            lastRequestedSyntaxGeneration = documentGeneration;
        } catch (RuntimeException failure) {
            publishRemoteSyntaxFailure(failure);
        }
    }

    private void publishRemoteSyntaxStyles(SFMTextEditorSyntaxSession.Publication publication) {
        if (syntaxSession == null
                || publication.request().originGeneration() != documentGeneration
                || !publication.request().source().equals(getCurrentText())) return;
        SFMSyntaxHighlightResult result = publication.result();
        if (result.outcome() != SFMSyntaxHighlightResult.Outcome.HIGHLIGHTED) {
            clearRemoteSyntaxStyles();
            syntaxDiagnostic = Optional.of(Component.literal(
                    "Java syntax highlighting unavailable: " + result.outcome().wireName()
            ));
            return;
        }
        try {
            List<SFMDrawCanvasRemoteSyntaxStyles.FormattingSpan> spans = result.spans().stream()
                    .map(span -> new SFMDrawCanvasRemoteSyntaxStyles.FormattingSpan(
                            Math.toIntExact(span.startByte()),
                            Math.toIntExact(span.endByte()),
                            SFMDrawCanvasRemoteSyntaxStyles.parseFormattingNames(span.chatFormatting())
                    ))
                    .toList();
            long projectionStartedNanos = System.nanoTime();
            try {
                setRemoteSyntaxStyles(SFMDrawCanvasRemoteSyntaxStyles.project(
                        model().glyphs(),
                        this.font.width(" "),
                        this.font.lineHeight,
                        publication.request().source(),
                        spans
                ));
            } finally {
                performanceTracker.styleProjection(System.nanoTime() - projectionStartedNanos);
            }
            syntaxDiagnostic = Optional.empty();
            Set<String> tags = result.spans().stream()
                    .map(SFMSyntaxHighlightResult.Span::arboriumTag)
                    .collect(Collectors.toUnmodifiableSet());
            Set<String> formatting = result.spans().stream()
                    .flatMap(span -> span.chatFormatting().stream())
                    .collect(Collectors.toUnmodifiableSet());
            syntaxPresentationEvidence = Optional.of(new SyntaxPresentationEvidence(
                    publication.request().requestId(),
                    publication.request().requestGeneration(),
                    publication.request().originGeneration(),
                    publication.request().sourceSha256(),
                    result.parserFingerprint(),
                    result.formattingSchema(),
                    result.spans().size(),
                    tags.size(),
                    formatting.size(),
                    result.cache().status().wireName(),
                    result.elapsedMicros(),
                    Math.max(0L, (System.nanoTime() - syntaxRequestStartedNanos) / 1_000L)
            ));
        } catch (RuntimeException invalid) {
            clearRemoteSyntaxStyles();
            syntaxPresentationEvidence = Optional.empty();
            publishRemoteSyntaxFailure(invalid);
        }
    }

    private void publishRemoteSyntaxFailure(Throwable failure) {
        clearRemoteSyntaxStyles();
        syntaxPresentationEvidence = Optional.empty();
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        String detail = current.getMessage();
        if (detail == null || detail.isBlank()) detail = current.getClass().getSimpleName();
        if (detail.length() > 160) detail = detail.substring(0, 160);
        syntaxDiagnostic = Optional.of(Component.literal("Java syntax highlighting unavailable: " + detail));
    }

    private void setRemoteSyntaxStyles(
            Map<SFMDrawCanvasModel.CanvasGlyph, List<ChatFormatting>> styles
    ) {
        remoteStyledGlyphs = SFMDrawCanvasRemoteSyntaxStyles.styledGlyphs(styles);
    }

    private void clearRemoteSyntaxStyles() {
        remoteStyledGlyphs = Map.of();
    }

    private void renderSyntaxDiagnostic(PoseStack poseStack) {
        if (syntaxDiagnostic.isEmpty()) return;
        int maximumWidth = Math.max(0, this.width - 16);
        if (maximumWidth <= 0) return;
        String rendered = this.font.plainSubstrByWidth(syntaxDiagnostic.orElseThrow().getString(), maximumWidth);
        SFMFontUtils.draw(
                poseStack,
                this.font,
                rendered,
                8,
                Math.max(2, this.height - 38),
                0xFFFFAA00,
                true
        );
    }

    public Optional<SyntaxPresentationEvidence> syntaxPresentationEvidence() {
        return syntaxPresentationEvidence;
    }

    public record SyntaxPresentationEvidence(
            long requestId,
            long requestGeneration,
            long originGeneration,
            String sourceSha256,
            String parserFingerprint,
            String formattingSchema,
            int spanCount,
            int distinctTagCount,
            int distinctFormattingCount,
            String cacheStatus,
            long rustElapsedMicros,
            long queryToVisibleMicros
    ) {
    }

    @Override
    public void removed() {
        if (syntaxSession != null) {
            syntaxSession.close();
            syntaxSession = null;
            lastRequestedSyntaxGeneration = 0;
        }
        super.removed();
    }

    private void copyCanvasTextToClipboard() {
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(model().copyableText(this.font.width(" "), this.font.lineHeight));
        } catch (Throwable ignored) {
        }
    }

    private void rememberInputEvent(String event) {
        inputEvents.add(event);
        while (inputEvents.size() > INPUT_LOG_LIMIT) {
            inputEvents.remove(0);
        }
    }

    private void renderHud(PoseStack poseStack) {
        int left = 8;
        int top = diagnosticControlsVisible ? 104 : 8;
        int right = 226;
        int bottom = 48;
        fill(poseStack, left, top, right, bottom, HUD_BACKGROUND);
        fill(poseStack, left, top, right, top + 1, HUD_BORDER);
        fill(poseStack, left, bottom - 1, right, bottom, HUD_BORDER);
        fill(poseStack, left, top, left + 1, bottom, HUD_BORDER);
        fill(poseStack, right - 1, top, right, bottom, HUD_BORDER);

        SFMFontUtils.draw(poseStack, this.font, this.title, left + 8, top + 7, HUD_TEXT, true);
        SFMFontUtils.draw(
                poseStack,
                this.font,
                String.format(
                        "cursor %.1f, %.1f  zoom %.2fx",
                        model().cursorCanvasX(),
                        model().cursorCanvasY(),
                        zoom
                ),
                left + 8,
                top + 22,
                HUD_MUTED,
                true
        );
    }

    private void renderInputDiagnostics(PoseStack poseStack) {
        if (inputEvents.isEmpty()) {
            return;
        }
        int left = 8;
        int lineHeight = this.font.lineHeight + 2;
        int height = inputEvents.size() * lineHeight + 12;
        int top = Math.max(112, this.height - height - 8);
        int right = Math.min(this.width - 8, 420);
        int bottom = top + height;
        fill(poseStack, left, top, right, bottom, HUD_BACKGROUND);
        fill(poseStack, left, top, right, top + 1, HUD_BORDER);
        fill(poseStack, left, bottom - 1, right, bottom, HUD_BORDER);
        fill(poseStack, left, top, left + 1, bottom, HUD_BORDER);
        fill(poseStack, right - 1, top, right, bottom, HUD_BORDER);
        int y = top + 6;
        for (String event : inputEvents) {
            SFMFontUtils.draw(poseStack, this.font, event, left + 6, y, HUD_MUTED, true);
            y += lineHeight;
        }
    }

    private String modifierText(int modifiers) {
        List<String> parts = new ArrayList<>();
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            parts.add("ctrl");
        }
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            parts.add("shift");
        }
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) {
            parts.add("alt");
        }
        if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) {
            parts.add("super");
        }
        return parts.isEmpty() ? "none" : String.join("+", parts);
    }

    private double canvasToScreenX(double canvasX) {
        return (canvasX - cameraX) * zoom + this.width / 2.0D;
    }

    private double canvasToScreenY(double canvasY) {
        return (canvasY - cameraY) * zoom + this.height / 2.0D;
    }

    private double screenToCanvasX(double screenX) {
        return (screenX - this.width / 2.0D) / zoom + cameraX;
    }

    private double screenToCanvasY(double screenY) {
        return (screenY - this.height / 2.0D) / zoom + cameraY;
    }

    private MutableComponent doneButtonTooltip() {
        return TEXT_EDITOR_V3_DONE_BUTTON_TOOLTIP_PREFIX.getComponent()
                .append(Component.literal("Shift+Enter").withStyle(ChatFormatting.AQUA))
                .append(TEXT_EDITOR_V3_DONE_BUTTON_TOOLTIP_SUFFIX.getComponent());
    }

    private void beginGrammarInsertDrag(
            double mouseX,
            double mouseY
    ) {
        draggingGrammarInsert = true;
        grammarInsertStartX = mouseX;
        grammarInsertStartY = mouseY;
        loadGrammarContent();
        focusMainCanvas();
    }

    private void finishGrammarInsertDrag(
            double mouseX,
            double mouseY
    ) {
        draggingGrammarInsert = false;
        double canvasX = screenToCanvasX(mouseX);
        double canvasY = screenToCanvasY(mouseY);
        EmbeddedDocument document = EmbeddedDocument.create(
                "SFML.g4",
                copyModel(grammarModel),
                canvasX,
                canvasY,
                Math.max(260.0D, 360.0D / zoom),
                Math.max(160.0D, 220.0D / zoom),
                this.font.lineHeight
        );
        embeddedDocuments.add(document);
        focusMainCanvas();
    }

    private SFMDrawCanvasModel copyModel(SFMDrawCanvasModel source) {
        SFMDrawCanvasModel copy = new SFMDrawCanvasModel();
        for (SFMDrawCanvasModel.CanvasGlyph glyph : source.glyphs()) {
            copy.glyphs().add(new SFMDrawCanvasModel.CanvasGlyph(glyph.text(), glyph.x(), glyph.y(), glyph.width()));
        }
        copy.moveCursorToDocumentStart();
        return copy;
    }

    private boolean beginEmbeddedDocumentResize(
            double mouseX,
            double mouseY
    ) {
        for (int i = embeddedDocuments.size() - 1; i >= 0; i--) {
            EmbeddedDocument document = embeddedDocuments.get(i);
            ResizeCorner corner = embeddedDocumentResizeCornerAt(document, mouseX, mouseY);
            if (corner == null) {
                continue;
            }
            resizingEmbeddedDocument = document;
            resizingCorner = corner;
            switch (corner) {
                case TOP_LEFT -> {
                    resizeAnchorX = document.canvasX + document.canvasWidth;
                    resizeAnchorY = document.canvasY + document.canvasHeight;
                }
                case TOP_RIGHT -> {
                    resizeAnchorX = document.canvasX;
                    resizeAnchorY = document.canvasY + document.canvasHeight;
                }
                case BOTTOM_LEFT -> {
                    resizeAnchorX = document.canvasX + document.canvasWidth;
                    resizeAnchorY = document.canvasY;
                }
                case BOTTOM_RIGHT -> {
                    resizeAnchorX = document.canvasX;
                    resizeAnchorY = document.canvasY;
                }
            }
            return true;
        }
        return false;
    }

    private void resizeEmbeddedDocument(
            double mouseX,
            double mouseY
    ) {
        if (resizingEmbeddedDocument == null || resizingCorner == null) {
            return;
        }
        double canvasX = screenToCanvasX(mouseX);
        double canvasY = screenToCanvasY(mouseY);
        resizingEmbeddedDocument.resizeFromCorner(resizingCorner, resizeAnchorX, resizeAnchorY, canvasX, canvasY);
        resizingEmbeddedDocument.fitContent(this.font.lineHeight);
    }

    private ResizeCorner embeddedDocumentResizeCornerAt(
            EmbeddedDocument document,
            double mouseX,
            double mouseY
    ) {
        double left = canvasToScreenX(document.canvasX);
        double top = canvasToScreenY(document.canvasY);
        double right = canvasToScreenX(document.canvasX + document.canvasWidth);
        double bottom = canvasToScreenY(document.canvasY + document.canvasHeight);
        if (isNear(mouseX, mouseY, left, top)) {
            return ResizeCorner.TOP_LEFT;
        }
        if (isNear(mouseX, mouseY, right, top)) {
            return ResizeCorner.TOP_RIGHT;
        }
        if (isNear(mouseX, mouseY, left, bottom)) {
            return ResizeCorner.BOTTOM_LEFT;
        }
        if (isNear(mouseX, mouseY, right, bottom)) {
            return ResizeCorner.BOTTOM_RIGHT;
        }
        return null;
    }

    private boolean isNear(
            double mouseX,
            double mouseY,
            double targetX,
            double targetY
    ) {
        return Math.abs(mouseX - targetX) <= 8.0D && Math.abs(mouseY - targetY) <= 8.0D;
    }

    private void toggleGrammarPanel() {
        grammarPanelVisible = !grammarPanelVisible;
        if (grammarFocusTarget != null) {
            grammarFocusTarget.visible = grammarPanelVisible;
            grammarFocusTarget.active = grammarPanelVisible;
        }
        if (grammarPanelVisible) {
            loadGrammarContent();
            initializeGrammarCamera();
            focusGrammarPanel();
        } else {
            focusMainCanvas();
        }
    }

    private void loadGrammarContent() {
        if (grammarContentLoaded) {
            return;
        }
        grammarContentLoaded = true;
        grammarModel = new SFMDrawCanvasModel();
        grammarModel.typeText(readGrammarResource(), this.font::width, this.font.lineHeight);
        grammarModel.moveCursorToDocumentStart();
    }

    private String readGrammarResource() {
        Map<ResourceLocation, Resource> resources = Minecraft.getInstance()
                .getResourceManager()
                .listResources("grammar/sfml", location -> location.equals(SFML_GRAMMAR_RESOURCE));
        Resource resource = resources.get(SFML_GRAMMAR_RESOURCE);
        if (resource == null) {
            return "// Missing runtime grammar resource: " + SFML_GRAMMAR_RESOURCE;
        }
        try (BufferedReader reader = resource.openAsReader()) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "// Failed to read runtime grammar resource: " + SFML_GRAMMAR_RESOURCE + "\n// " + e.getMessage();
        }
    }

    private void initializeGrammarCamera() {
        if (grammarCameraInitialized) {
            return;
        }
        grammarCameraX = (grammarPanelWidth() / 2.0D - DEFAULT_ORIGIN_MARGIN) / grammarZoom;
        grammarCameraY = (grammarPanelHeight() / 2.0D - DEFAULT_ORIGIN_MARGIN) / grammarZoom;
        grammarCameraInitialized = true;
    }

    private void focusMainCanvas() {
        this.setFocused(canvasFocusTarget);
        if (canvasFocusTarget != null) {
            canvasFocusTarget.setFocused(true);
        }
        if (grammarFocusTarget != null) {
            grammarFocusTarget.setFocused(false);
        }
    }

    private void focusGrammarPanel() {
        if (grammarFocusTarget == null) {
            return;
        }
        this.setFocused(grammarFocusTarget);
        grammarFocusTarget.setFocused(true);
        if (canvasFocusTarget != null) {
            canvasFocusTarget.setFocused(false);
        }
    }

    private boolean isGrammarPanelFocused() {
        return grammarPanelVisible && grammarFocusTarget != null && grammarFocusTarget.isFocused();
    }

    private int grammarPanelWidth() {
        return Math.max(220, Math.min(this.width - 32, 540));
    }

    private int grammarPanelHeight() {
        return Math.max(120, this.height - 84);
    }

    private int grammarPanelLeft() {
        return this.width - grammarPanelWidth() - 16;
    }

    private int grammarPanelTop() {
        return 28;
    }

    private double grammarPanelCenterX() {
        return grammarPanelLeft() + grammarPanelWidth() / 2.0D;
    }

    private double grammarPanelCenterY() {
        return grammarPanelTop() + grammarPanelHeight() / 2.0D;
    }

    private boolean isInGrammarPanel(
            double mouseX,
            double mouseY
    ) {
        return mouseX >= grammarPanelLeft()
               && mouseX < grammarPanelLeft() + grammarPanelWidth()
               && mouseY >= grammarPanelTop()
               && mouseY < grammarPanelTop() + grammarPanelHeight();
    }

    private void beginGrammarPan(
            double mouseX,
            double mouseY
    ) {
        grammarPanning = true;
        grammarPanAnchorMouseX = mouseX;
        grammarPanAnchorMouseY = mouseY;
        grammarPanAnchorCameraX = grammarCameraX;
        grammarPanAnchorCameraY = grammarCameraY;
    }

    private void grammarPanCamera(
            double screenDeltaX,
            double screenDeltaY
    ) {
        grammarCameraX += screenDeltaX / grammarZoom;
        grammarCameraY += screenDeltaY / grammarZoom;
    }

    private void grammarZoomAtPanelCenter(double scaleFactor) {
        double focusX = grammarScreenToCanvasX(grammarPanelCenterX());
        double focusY = grammarScreenToCanvasY(grammarPanelCenterY());
        grammarZoom = Mth.clamp(grammarZoom * scaleFactor, MIN_ZOOM, MAX_ZOOM);
        grammarCameraX = focusX;
        grammarCameraY = focusY;
    }

    private void fitGrammarContentToPanel() {
        if (grammarModel.glyphs().isEmpty()) {
            grammarZoom = 1.0D;
            initializeGrammarCamera();
            return;
        }

        double left = Double.POSITIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (SFMDrawCanvasModel.CanvasGlyph glyph : grammarModel.glyphs()) {
            left = Math.min(left, glyph.x());
            top = Math.min(top, glyph.y());
            right = Math.max(right, glyph.x() + glyph.width());
            bottom = Math.max(bottom, glyph.y() + this.font.lineHeight);
        }

        double contentWidth = Math.max(1.0D, right - left);
        double contentHeight = Math.max(1.0D, bottom - top);
        double availableWidth = Math.max(1.0D, grammarPanelWidth() - FIT_CONTENT_MARGIN * 2.0D);
        double availableHeight = Math.max(1.0D, grammarPanelHeight() - FIT_CONTENT_MARGIN * 2.0D);
        grammarZoom = Mth.clamp(Math.min(availableWidth / contentWidth, availableHeight / contentHeight), MIN_ZOOM, MAX_ZOOM);
        grammarCameraX = (left + right) / 2.0D;
        grammarCameraY = (top + bottom) / 2.0D;
    }

    private double grammarCanvasToScreenX(double canvasX) {
        return (canvasX - grammarCameraX) * grammarZoom + grammarPanelCenterX();
    }

    private double grammarCanvasToScreenY(double canvasY) {
        return (canvasY - grammarCameraY) * grammarZoom + grammarPanelCenterY();
    }

    private double grammarScreenToCanvasX(double screenX) {
        return (screenX - grammarPanelCenterX()) / grammarZoom + grammarCameraX;
    }

    private double grammarScreenToCanvasY(double screenY) {
        return (screenY - grammarPanelCenterY()) / grammarZoom + grammarCameraY;
    }

    private void renderGrammarPanel(PoseStack poseStack) {
        loadGrammarContent();
        initializeGrammarCamera();
        int left = grammarPanelLeft();
        int top = grammarPanelTop();
        int right = left + grammarPanelWidth();
        int bottom = top + grammarPanelHeight();
        fill(poseStack, left, top, right, bottom, PANEL_BACKGROUND);

        int tabWidth = 70;
        int tabHeight = 16;
        fill(poseStack, left + 8, top - tabHeight, left + 8 + tabWidth, top, PANEL_TAB_BACKGROUND);
        drawRectOutline(poseStack, left + 8, top - tabHeight, left + 8 + tabWidth, top + 1, HUD_BORDER);
        SFMFontUtils.draw(poseStack, this.font, Component.literal("SFML.g4"), left + 14, top - tabHeight + 4, HUD_TEXT, true);

        renderGrammarGlyphs(poseStack);
        if (!hideSelection) {
            renderGrammarGlyphSelectionHighlights(poseStack);
        }
        renderGrammarCanvasCursor(poseStack);

        drawRectOutline(poseStack, left, top, right, bottom, isGrammarPanelFocused() ? FOCUS_BORDER : HUD_BORDER);
    }

    private void renderGrammarGlyphs(PoseStack poseStack) {
        Map<SFMDrawCanvasModel.CanvasGlyph, Integer> glyphColours = SFMDrawCanvasSyntaxHighlightingHelper.buildAntlrGrammarHighlightColours(
                grammarModel.glyphs(),
                this.font.width(" "),
                this.font.lineHeight,
                GLYPH
        );
        int left = grammarPanelLeft();
        int top = grammarPanelTop();
        int right = left + grammarPanelWidth();
        int bottom = top + grammarPanelHeight();
        for (SFMDrawCanvasModel.CanvasGlyph glyph : grammarModel.glyphs()) {
            double screenX = grammarCanvasToScreenX(glyph.x());
            double screenY = grammarCanvasToScreenY(glyph.y());
            if (screenX > right || screenX + glyph.width() * grammarZoom < left || screenY > bottom || screenY + this.font.lineHeight * grammarZoom < top) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(screenX, screenY, 0.0D);
            poseStack.scale((float) grammarZoom, (float) grammarZoom, 1.0F);
            SFMFontUtils.draw(poseStack, this.font, glyph.text(), 0, 0, glyphColours.getOrDefault(glyph, GLYPH), true);
            poseStack.popPose();
        }
    }

    private void renderGrammarGlyphSelectionHighlights(PoseStack poseStack) {
        List<CanvasRect> mask = new ArrayList<>();
        int left = grammarPanelLeft();
        int top = grammarPanelTop();
        int right = left + grammarPanelWidth();
        int bottom = top + grammarPanelHeight();
        for (SFMDrawCanvasModel.CanvasGlyph glyph : grammarModel.glyphs()) {
            if (uniqueCursorInGlyphBounds(grammarModel, glyph) == null) {
                continue;
            }
            mask.add(new CanvasRect(
                    Mth.clamp(grammarCanvasToScreenX(glyph.x()), left, right),
                    Mth.clamp(grammarCanvasToScreenY(glyph.y()), top, bottom),
                    Mth.clamp(grammarCanvasToScreenX(glyph.x() + glyph.width()), left, right),
                    Mth.clamp(grammarCanvasToScreenY(glyph.y() + this.font.lineHeight), top, bottom)
            ));
        }
        for (CanvasRect rect : unionRects(mask)) {
            SFMScreenRenderUtils.renderHighlight(
                    poseStack,
                    rect.left(),
                    rect.top(),
                    Math.max(rect.left() + 1.0D, rect.right()),
                    Math.max(rect.top() + 1.0D, rect.bottom())
            );
        }
    }

    private void renderGrammarCanvasCursor(PoseStack poseStack) {
        for (int i = 0; i < grammarModel.cursors().size(); i++) {
            SFMDrawCanvasModel.CanvasCursor cursor = grammarModel.cursors().get(i);
            if (!hideSelection && isUniqueCursorInAnyGlyphBounds(grammarModel, cursor)) {
                continue;
            }
            int screenX = (int) Math.round(grammarCanvasToScreenX(cursor.x()));
            int screenY = (int) Math.round(grammarCanvasToScreenY(cursor.y()));
            if (!isInGrammarPanel(screenX, screenY)) {
                continue;
            }
            int size = grammarPanning ? 8 : 6;
            int cursorSize = cursor.active() ? size + 2 : size;
            if (i == grammarModel.focusedCursorIndex()) {
                SFMGuiCrosshair.draw(poseStack, screenX, screenY, cursorSize + 2, focusedCursorOutlineColor(cursor.color()));
            }
            SFMGuiCrosshair.draw(poseStack, screenX, screenY, cursorSize, cursor.active() ? cursor.color() : inactiveCursorColor(cursor.color()));
        }
    }

    private void renderReadOnlyChrome(PoseStack poseStack) {
        if (openContext == null || !openContext.readOnly() || configButtonBounds == null || doneButtonBounds == null) {
            return;
        }
        Component message = TEXT_EDITOR_V3_READ_ONLY_DOCUMENT.getComponent();
        SFMTextEditorReadOnlyChrome.layout(
                true,
                configButtonBounds,
                doneButtonBounds,
                this.font.width(message),
                this.font.lineHeight
        ).ifPresent(layout -> {
            SFMTextEditorReadOnlyChrome.Rect background = layout.background();
            fill(
                    poseStack,
                    background.x(),
                    background.y(),
                    background.right(),
                    background.bottom(),
                    READ_ONLY_CHROME_BACKGROUND
            );
            if (background.width() >= 3 && background.height() >= 3) {
                drawRectOutline(
                        poseStack,
                        background.x(),
                        background.y(),
                        background.right(),
                        background.bottom(),
                        HUD_BORDER
                );
            }

            SFMTextEditorReadOnlyChrome.Rect textArea = layout.textArea();
            String rendered = this.font.plainSubstrByWidth(message.getString(), textArea.width());
            if (rendered.isEmpty()) return;
            int textX = textArea.x() + (textArea.width() - this.font.width(rendered)) / 2;
            SFMFontUtils.draw(poseStack, this.font, rendered, textX, textArea.y(), HUD_TEXT, true);
        });
    }

    private void copyGrammarTextToClipboard() {
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(grammarModel.copyableText(this.font.width(" "), this.font.lineHeight));
        } catch (Throwable ignored) {
        }
    }

    private record CanvasPoint(
            double x,
            double y
    ) {
    }

    private static class EmbeddedDocument {
        private static final double MIN_WIDTH = 120.0D;
        private static final double MIN_HEIGHT = 80.0D;
        private final String title;
        private final SFMDrawCanvasModel model;
        private double canvasX;
        private double canvasY;
        private double canvasWidth;
        private double canvasHeight;
        private double cameraX;
        private double cameraY;
        private double contentZoom = 1.0D;

        private EmbeddedDocument(
                String title,
                SFMDrawCanvasModel model,
                double canvasX,
                double canvasY,
                double canvasWidth,
                double canvasHeight
        ) {
            this.title = title;
            this.model = model;
            this.canvasX = canvasX;
            this.canvasY = canvasY;
            this.canvasWidth = canvasWidth;
            this.canvasHeight = canvasHeight;
        }

        private static EmbeddedDocument create(
                String title,
                SFMDrawCanvasModel model,
                double canvasX,
                double canvasY,
                double canvasWidth,
                double canvasHeight,
                int lineHeight
        ) {
            EmbeddedDocument document = new EmbeddedDocument(title, model, canvasX, canvasY, canvasWidth, canvasHeight);
            document.fitContent(lineHeight);
            return document;
        }

        private void resizeFromCorner(
                ResizeCorner corner,
                double anchorX,
                double anchorY,
                double movingX,
                double movingY
        ) {
            double left = Math.min(anchorX, movingX);
            double right = Math.max(anchorX, movingX);
            double top = Math.min(anchorY, movingY);
            double bottom = Math.max(anchorY, movingY);
            if (right - left < MIN_WIDTH) {
                if (corner == ResizeCorner.TOP_LEFT || corner == ResizeCorner.BOTTOM_LEFT) {
                    left = right - MIN_WIDTH;
                } else {
                    right = left + MIN_WIDTH;
                }
            }
            if (bottom - top < MIN_HEIGHT) {
                if (corner == ResizeCorner.TOP_LEFT || corner == ResizeCorner.TOP_RIGHT) {
                    top = bottom - MIN_HEIGHT;
                } else {
                    bottom = top + MIN_HEIGHT;
                }
            }
            canvasX = left;
            canvasY = top;
            canvasWidth = right - left;
            canvasHeight = bottom - top;
        }

        private void fitContent(int lineHeight) {
            if (model.glyphs().isEmpty()) {
                cameraX = 0.0D;
                cameraY = 0.0D;
                contentZoom = 1.0D;
                return;
            }
            double left = Double.POSITIVE_INFINITY;
            double top = Double.POSITIVE_INFINITY;
            double right = Double.NEGATIVE_INFINITY;
            double bottom = Double.NEGATIVE_INFINITY;
            for (SFMDrawCanvasModel.CanvasGlyph glyph : model.glyphs()) {
                left = Math.min(left, glyph.x());
                top = Math.min(top, glyph.y());
                right = Math.max(right, glyph.x() + glyph.width());
                bottom = Math.max(bottom, glyph.y() + lineHeight);
            }
            double contentWidth = Math.max(1.0D, right - left);
            double contentHeight = Math.max(1.0D, bottom - top);
            double availableWidth = Math.max(1.0D, canvasWidth - FIT_CONTENT_MARGIN);
            double availableHeight = Math.max(1.0D, canvasHeight - FIT_CONTENT_MARGIN);
            contentZoom = Mth.clamp(Math.min(availableWidth / contentWidth, availableHeight / contentHeight), MIN_ZOOM, MAX_ZOOM);
            cameraX = (left + right) / 2.0D;
            cameraY = (top + bottom) / 2.0D;
        }
    }

    private enum ResizeCorner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    private record ViewportGlyphCache(
            long contentRevision,
            int spaceWidth,
            int lineHeight,
            double minimumX,
            double minimumY,
            double maximumX,
            double maximumY,
            int screenWidth,
            int screenHeight,
            SFMDrawCanvasDocumentIndex.VisibleSlice slice,
            ResolvedViewportGlyphs cacheHit
    ) {
        private boolean matches(
                long contentRevision,
                int spaceWidth,
                int lineHeight,
                double minimumX,
                double minimumY,
                double maximumX,
                double maximumY,
                int screenWidth,
                int screenHeight
        ) {
            return this.contentRevision == contentRevision
                    && this.spaceWidth == spaceWidth
                    && this.lineHeight == lineHeight
                    && Double.compare(this.minimumX, minimumX) == 0
                    && Double.compare(this.minimumY, minimumY) == 0
                    && Double.compare(this.maximumX, maximumX) == 0
                    && Double.compare(this.maximumY, maximumY) == 0
                    && this.screenWidth == screenWidth
                    && this.screenHeight == screenHeight;
        }
    }

    public record SymbolHit(
            SFMSymbolHoverIdentity.TextGlyphRange range,
            int glyphOrdinal,
            String semanticKind,
            int navigationUtf16Offset,
            Optional<String> semanticRegionId,
            long semanticGeneration
    ) {
        public SymbolHit {
            Objects.requireNonNull(range, "range");
            if (glyphOrdinal < 0) throw new IllegalArgumentException("glyphOrdinal must not be negative");
            if (semanticKind == null || semanticKind.isBlank()) {
                throw new IllegalArgumentException("semanticKind must not be blank");
            }
            if (navigationUtf16Offset < 0) {
                throw new IllegalArgumentException("navigationUtf16Offset must not be negative");
            }
            semanticRegionId = Objects.requireNonNull(semanticRegionId, "semanticRegionId");
            semanticRegionId.ifPresent(value -> {
                if (value.isBlank()) throw new IllegalArgumentException("semanticRegionId must not be blank");
            });
            if (semanticGeneration < 0) {
                throw new IllegalArgumentException("semanticGeneration must not be negative");
            }
        }

        public SymbolHit(SFMSymbolHoverIdentity.TextGlyphRange range, int glyphOrdinal) {
            this(range, glyphOrdinal, "identifier", range.utf16Start(), Optional.empty(), 0);
        }

        public SymbolHit(
                SFMSymbolHoverIdentity.TextGlyphRange range,
                int glyphOrdinal,
                String semanticKind,
                int navigationUtf16Offset
        ) {
            this(range, glyphOrdinal, semanticKind, navigationUtf16Offset, Optional.empty(), 0);
        }
    }

    public record SpatialLayoutSnapshot(
            SFMDrawCanvasDocumentIndex index,
            int lineHeight,
            long generation,
            String fingerprint,
            int viewportWidth,
            int viewportHeight,
            SFMSpatialSemanticContract.Camera camera
    ) {
        public SpatialLayoutSnapshot {
            Objects.requireNonNull(index, "index");
            if (lineHeight <= 0) throw new IllegalArgumentException("lineHeight must be positive");
            if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
            if (fingerprint == null || fingerprint.isBlank()) {
                throw new IllegalArgumentException("fingerprint must not be blank");
            }
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                throw new IllegalArgumentException("viewport must be positive");
            }
            Objects.requireNonNull(camera, "camera");
        }
    }

    private record ResolvedViewportGlyphs(
            SFMDrawCanvasDocumentIndex.VisibleSlice slice,
            boolean rebuilt
    ) {
    }

    private record SelectionHighlightCache(
            long contentRevision,
            long cursorFingerprint,
            List<SFMDrawCanvasModel.CanvasGlyph> visibleGlyphs,
            double cameraX,
            double cameraY,
            double zoom,
            int lineHeight,
            List<CanvasRect> highlights
    ) {
        private SelectionHighlightCache {
            Objects.requireNonNull(visibleGlyphs, "visibleGlyphs");
            highlights = List.copyOf(highlights);
        }

        private boolean matches(
                long contentRevision,
                long cursorFingerprint,
                List<SFMDrawCanvasModel.CanvasGlyph> visibleGlyphs,
                double cameraX,
                double cameraY,
                double zoom,
                int lineHeight
        ) {
            return this.contentRevision == contentRevision
                    && this.cursorFingerprint == cursorFingerprint
                    && this.visibleGlyphs == visibleGlyphs
                    && Double.compare(this.cameraX, cameraX) == 0
                    && Double.compare(this.cameraY, cameraY) == 0
                    && Double.compare(this.zoom, zoom) == 0
                    && this.lineHeight == lineHeight;
        }
    }

    record CanvasRect(
            double left,
            double top,
            double right,
            double bottom
    ) {
        public boolean isEmpty() {
            return left >= right || top >= bottom;
        }

        public boolean covers(
                double left,
                double top,
                double right,
                double bottom
        ) {
            return this.left <= left
                   && this.top <= top
                   && this.right >= right
                   && this.bottom >= bottom;
        }
    }

    private interface ToggleReader {
        boolean get();
    }

    private interface ToggleWriter {
        void set(boolean value);
    }

    private static class CanvasFocusTarget extends Button {
        private final boolean showWhenFocused;

        public CanvasFocusTarget(
                int x,
                int y,
                int width,
                int height,
                boolean showWhenFocused
        ) {
            super(x, y, width, height, Component.empty(), button -> { });
            this.showWhenFocused = showWhenFocused;
        }

        @Override
        public void renderButton(
                PoseStack poseStack,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            if (showWhenFocused && isFocused()) {
                fill(poseStack, this.x, this.y, this.x + this.width, this.y + 1, FOCUS_BORDER);
                fill(poseStack, this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, FOCUS_BORDER);
                fill(poseStack, this.x, this.y, this.x + 1, this.y + this.height, FOCUS_BORDER);
                fill(poseStack, this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, FOCUS_BORDER);
            }
        }

        @Override
        public boolean mouseClicked(
                double mouseX,
                double mouseY,
                int button
        ) {
            return false;
        }

        @Override
        public boolean keyPressed(
                int keyCode,
                int scanCode,
                int modifiers
        ) {
            return keyCode == GLFW.GLFW_KEY_SPACE
                   || keyCode == GLFW.GLFW_KEY_ENTER
                   || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        }
    }
}
