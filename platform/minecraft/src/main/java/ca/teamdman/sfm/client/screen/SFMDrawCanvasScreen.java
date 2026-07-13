package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SFMDrawCanvasScreen extends Screen implements ISFMTextEditScreen {
    @SFMLocalizationDatagen
    public static final LocalizationEntry DRAW_CANVAS_READ_ONLY_DOCUMENT = new LocalizationEntry(
            "gui.sfm.draw_canvas.read_only",
            "Read-only document"
    );
    @SFMLocalizationDatagen
    public static final LocalizationEntry DRAW_CANVAS_SFML_BUTTON_TOOLTIP_PREFIX = new LocalizationEntry(
            "gui.sfm.draw_canvas.sfml_button.tooltip.prefix",
            "Drag onto the canvas to insert "
    );
    @SFMLocalizationDatagen
    public static final LocalizationEntry DRAW_CANVAS_DONE_BUTTON_TOOLTIP_PREFIX = new LocalizationEntry(
            "gui.sfm.draw_canvas.done_button.tooltip.prefix",
            "Press "
    );
    @SFMLocalizationDatagen
    public static final LocalizationEntry DRAW_CANVAS_DONE_BUTTON_TOOLTIP_SUFFIX = new LocalizationEntry(
            "gui.sfm.draw_canvas.done_button.tooltip.suffix",
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
    private static final int FOCUS_BORDER = 0xFF60A5FA;
    private static final int PANEL_BACKGROUND = 0xF01A2028;
    private static final int PANEL_TAB_BACKGROUND = 0xF0283340;
    private static final int EMBEDDED_DOCUMENT_BACKGROUND = 0xE81A2028;
    private static final int EMBEDDED_DOCUMENT_BORDER = 0xFF7C8A9B;
    private static final int EMBEDDED_DOCUMENT_HANDLE = 0xFFE6EDF3;
    private static final int INSERT_DRAG_LINE = 0xFF60A5FA;
    private static final ResourceLocation SFML_GRAMMAR_RESOURCE = sfmlGrammarResource();

    @MCVersionDependentBehaviour
    private static ResourceLocation sfmlGrammarResource() {
        return ResourceLocation.fromNamespaceAndPath(SFM.MOD_ID, "grammar/sfml/sfml.g4");
    }

    private final Screen previousScreen;
    private final ISFMTextEditScreenOpenContext openContext;
    private SFMDrawCanvasModel model = new SFMDrawCanvasModel();
    private final List<CanvasPoint> cursorTrail = new ArrayList<>();
    private final List<String> inputEvents = new ArrayList<>();
    private final List<Button> diagnosticButtons = new ArrayList<>();
    private final List<EmbeddedDocument> embeddedDocuments = new ArrayList<>();
    private Button canvasFocusTarget;
    private Button sfmlButton;
    private double cameraX;
    private double cameraY;
    private double zoom = 1.0D;
    private boolean cameraInitialized;
    private boolean diagnosticControlsVisible = false;
    private boolean showGrid = false;
    private boolean showCrosshairCoordinates = false;
    private boolean showGlyphBoundingBoxes = false;
    private boolean showCursorTrail = false;
    private boolean hideSelection = false;
    private boolean panning;
    private boolean suppressNextNumpadPanChar;
    private boolean initialContentLoaded;
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
    private double grammarInsertStartX;
    private double grammarInsertStartY;
    private EmbeddedDocument resizingEmbeddedDocument;
    private ResizeCorner resizingCorner;
    private double resizeAnchorX;
    private double resizeAnchorY;

    public SFMDrawCanvasScreen(Screen previousScreen) {
        super(Component.literal("SFM Draw Canvas"));
        this.previousScreen = previousScreen;
        this.openContext = null;
    }

    public SFMDrawCanvasScreen(
            ISFMTextEditScreenOpenContext openContext,
            Screen previousScreen
    ) {
        super(Component.literal("SFM Draw Canvas"));
        this.previousScreen = previousScreen;
        this.openContext = openContext;
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
        openContext.onTryClose(getCurrentText(), () -> Minecraft.getInstance().setScreen(previousScreen));
    }

    @Override
    public ISFMTextEditScreenOpenContext openContext() {
        return openContext;
    }

    @Override
    public OpenBehaviour openBehaviour() {
        return OpenBehaviour.Replace;
    }

    @Override
    protected void init() {
        super.init();
        SFMScreenRenderUtils.enableKeyRepeating();
        loadInitialContent();
        initializeCamera();
        canvasFocusTarget = new CanvasFocusTarget(1, 1, Math.max(1, this.width - 2), Math.max(1, this.height - 26), true);
        this.addRenderableWidget(canvasFocusTarget);
        this.setInitialFocus(canvasFocusTarget);
        this.setFocused(canvasFocusTarget);
        canvasFocusTarget.setFocused(true);
        grammarFocusTarget = new CanvasFocusTarget(grammarPanelLeft(), grammarPanelTop(), grammarPanelWidth(), grammarPanelHeight(), false);
        grammarFocusTarget.visible = grammarPanelVisible;
        grammarFocusTarget.active = grammarPanelVisible;
        this.addRenderableWidget(grammarFocusTarget);
        diagnosticButtons.clear();
        addDiagnosticButton(8, 8, () -> showCrosshairCoordinates, value -> showCrosshairCoordinates = value, "Coords");
        addDiagnosticButton(8, 32, () -> showGlyphBoundingBoxes, value -> showGlyphBoundingBoxes = value, "Glyph Bounds");
        addDiagnosticButton(8, 56, () -> showCursorTrail, value -> showCursorTrail = value, "Cursor Trail");
        addDiagnosticButton(8, 80, () -> showGrid, value -> showGrid = value, "Grid");
        addDiagnosticButton(8, 104, () -> hideSelection, value -> hideSelection = value, "Hide Selection");
        if (openContext != null) {
            this.addRenderableWidget(new SFMButtonBuilder()
                    .setPosition(4, this.height - 24)
                    .setSize(16, 20)
                    .setText(Component.literal("#"))
                    .setOnPress(button -> SFMScreenChangeHelpers.setOrPushScreen(new SFMTextEditorConfigScreen(
                            this,
                            SFMConfig.CLIENT_TEXT_EDITOR_CONFIG,
                            () -> { }
                    )))
                    .build());
        }
        sfmlButton = new SFMButtonBuilder()
                .setPosition(this.width - 140, this.height - 24)
                .setSize(48, 20)
                .setText(Component.literal("SFML"))
                .setOnPress(button -> { })
                .setTooltip(this, font, sfmlButtonTooltip())
                .build();
        this.addRenderableWidget(sfmlButton);
        this.addRenderableWidget(new SFMButtonBuilder()
                .setPosition(this.width - 88, this.height - 24)
                .setSize(80, 20)
                .setText(CommonComponents.GUI_DONE)
                .setOnPress(button -> this.saveAndClose())
                .setTooltip(this, font, doneButtonTooltip())
                .build());
        refreshDiagnosticControls();
    }

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        guiGraphics.fill( 0, 0, this.width, this.height, BACKGROUND);
        if (showGrid) {
            renderGrid(guiGraphics);
        }
        if (showCursorTrail) {
            renderCursorTrail(guiGraphics);
        }
        renderEmbeddedDocuments(guiGraphics);
        renderGlyphs(guiGraphics);
        if (!hideSelection) {
            renderGlyphSelectionHighlights(guiGraphics);
        }
        if (showGlyphBoundingBoxes) {
            renderGlyphBoundingBoxes(guiGraphics);
        }
        renderCanvasCursor(guiGraphics);
        if (showCrosshairCoordinates) {
            renderHud(guiGraphics);
        }
        if (diagnosticControlsVisible) {
            renderInputDiagnostics(guiGraphics);
        }
        if (grammarPanelVisible) {
            renderGrammarPanel(guiGraphics);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (draggingGrammarInsert) {
            renderGrammarInsertDrag(guiGraphics, mouseX, mouseY);
        }
        if (grammarPanelVisible && isGrammarPanelFocused()) {
            renderReadOnlyMessage(guiGraphics);
        }
    }

    @Override
    public void mouseMoved(
            double mouseX,
            double mouseY
    ) {
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && sfmlButton != null && sfmlButton.isMouseOver(mouseX, mouseY)) {
            beginGrammarInsertDrag(mouseX, mouseY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && beginEmbeddedDocumentResize(mouseX, mouseY)) {
            focusMainCanvas();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (grammarPanelVisible && isInGrammarPanel(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                beginGrammarPan(mouseX, mouseY);
                focusGrammarPanel();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                grammarModel.setActiveCursors(grammarScreenToCanvasX(mouseX), grammarScreenToCanvasY(mouseY));
                focusGrammarPanel();
                return true;
            }
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
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (panning && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            cameraX = panAnchorCameraX - (mouseX - panAnchorMouseX) / zoom;
            cameraY = panAnchorCameraY - (mouseY - panAnchorMouseY) / zoom;
            model().setCursor(screenToCanvasX(mouseX), screenToCanvasY(mouseY));
            rememberCursorPosition();
            return true;
        }
        if (resizingEmbeddedDocument != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            resizeEmbeddedDocument(mouseX, mouseY);
            return true;
        }
        if (grammarPanning && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            grammarCameraX = grammarPanAnchorCameraX - (mouseX - grammarPanAnchorMouseX) / grammarZoom;
            grammarCameraY = grammarPanAnchorCameraY - (mouseY - grammarPanAnchorMouseY) / grammarZoom;
            grammarModel.setCursor(grammarScreenToCanvasX(mouseX), grammarScreenToCanvasY(mouseY));
            return true;
        }
        if (draggingGrammarInsert && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (grammarPanelVisible && isInGrammarPanel(mouseX, mouseY)) {
                grammarModel.setActiveCursors(grammarScreenToCanvasX(mouseX), grammarScreenToCanvasY(mouseY));
                focusGrammarPanel();
                return true;
            }
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
    }

    @Override
    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && panning) {
            panning = false;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && grammarPanning) {
            grammarPanning = false;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && resizingEmbeddedDocument != null) {
            resizingEmbeddedDocument.fitContent(this.font.lineHeight);
            resizingEmbeddedDocument = null;
            resizingCorner = null;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingGrammarInsert) {
            finishGrammarInsertDrag(mouseX, mouseY);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    @MCVersionDependentBehaviour
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double deltaX,
            double deltaY
    ) {
        if (deltaY == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        }
        if (grammarPanelVisible && isInGrammarPanel(mouseX, mouseY)) {
            double focusX = grammarScreenToCanvasX(mouseX);
            double focusY = grammarScreenToCanvasY(mouseY);
            double scaleFactor = Math.pow(ZOOM_STEP, deltaY);
            grammarZoom = Mth.clamp(grammarZoom * scaleFactor, MIN_ZOOM, MAX_ZOOM);
            grammarCameraX = focusX - (mouseX - grammarPanelCenterX()) / grammarZoom;
            grammarCameraY = focusY - (mouseY - grammarPanelCenterY()) / grammarZoom;
            grammarModel.setCursor(focusX, focusY);
            focusGrammarPanel();
            return true;
        }
        double focusX = screenToCanvasX(mouseX);
        double focusY = screenToCanvasY(mouseY);
        double scaleFactor = Math.pow(ZOOM_STEP, deltaY);
        zoom = Mth.clamp(zoom * scaleFactor, MIN_ZOOM, MAX_ZOOM);
        cameraX = focusX - (mouseX - this.width / 2.0D) / zoom;
        cameraY = focusY - (mouseY - this.height / 2.0D) / zoom;
        model().setCursor(focusX, focusY);
        rememberCursorPosition();
        return true;
    }

    @Override
    public boolean charTyped(
            char codePoint,
            int modifiers
    ) {
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
        if (isGrammarPanelFocused()) {
            return true;
        }
        String text = Character.toString(codePoint);
        rememberInputEvent(String.format("charTyped '%s' U+%04X modifiers=%s", text, (int) codePoint, modifierText(modifiers)));
        model().typeGlyph(text, this.font.width(text), this.font.lineHeight);
        rememberCursorPosition();
        return true;
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        rememberInputEvent(String.format("keyPressed key=%d scan=%d modifiers=%s", keyCode, scanCode, modifierText(modifiers)));
        if (keyCode == GLFW.GLFW_KEY_F3) {
            diagnosticControlsVisible = !diagnosticControlsVisible;
            refreshDiagnosticControls();
            return true;
        }
        if (isGrammarPanelFocused()) {
            return handleGrammarPanelKeyPressed(keyCode, modifiers);
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
            model().deleteLeftWord(this.font.lineHeight);
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            model().deleteRightWord(this.font.lineHeight);
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            model().deleteLeft(this.font.lineHeight);
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            model().deleteNearestAndMoveRight(this.font.lineHeight);
            rememberCursorPosition();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                saveAndClose();
            } else {
                insertLineBreak();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        rememberInputEvent(String.format("keyReleased key=%d scan=%d modifiers=%s", keyCode, scanCode, modifierText(modifiers)));
        if (isNumpadPanKey(keyCode)) {
            suppressNextNumpadPanChar = false;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private boolean handleGrammarPanelKeyPressed(
            int keyCode,
            int modifiers
    ) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                saveAndClose();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F1) {
            grammarModel.focusPreviousCursor((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F4) {
            grammarModel.focusNextCursor((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        if (handleGrammarCameraShortcut(keyCode, modifiers)) {
            return true;
        }
        if (Screen.isCopy(keyCode)) {
            copyGrammarTextToClipboard();
            return true;
        }
        if (Screen.isPaste(keyCode)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            grammarModel.ensureCursorClosestToEachGlyph();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_L && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            grammarModel.ensureCursorClosestToEachGlyphOnActiveCursorLines(this.font.lineHeight);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_COMMA && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            grammarModel.discardCursorsNotClosestToAnyGlyph();
            return true;
        }
        if (handleGrammarArrowAddCursorShortcut(keyCode, modifiers)) {
            return true;
        }
        if (handleGrammarNumpadCameraPan(keyCode)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                grammarModel.moveCursorLeftWord(this.font.lineHeight, this.font.width(" "));
            } else {
                grammarModel.moveCursorLeft(this.font.lineHeight, this.font.width(" "));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                grammarModel.moveCursorRightWord(this.font.lineHeight, this.font.width(" "));
            } else {
                grammarModel.moveCursorRight(this.font.width(" "));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                grammarModel.moveCursorUpToGlyph(this.font.lineHeight);
            } else {
                grammarModel.moveCursorUp(this.font.lineHeight);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                grammarModel.moveCursorDownToGlyph(this.font.lineHeight);
            } else {
                grammarModel.moveCursorDown(this.font.lineHeight);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                grammarModel.moveCursorToDocumentStart();
            } else {
                grammarModel.moveCursorToLineStart();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                grammarModel.moveCursorToDocumentEnd();
            } else {
                grammarModel.moveCursorToLineEnd();
            }
            return true;
        }
        return keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE;
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

    private boolean handleGrammarNumpadCameraPan(int keyCode) {
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
        grammarPanCamera(x * KEYBOARD_PAN_SCREEN_PIXELS, y * KEYBOARD_PAN_SCREEN_PIXELS);
        suppressNextNumpadPanChar = true;
        return true;
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

    private boolean handleGrammarCameraShortcut(
            int keyCode,
            int modifiers
    ) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            return false;
        }
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && (keyCode == GLFW.GLFW_KEY_9 || keyCode == GLFW.GLFW_KEY_KP_9)) {
            fitGrammarContentToPanel();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_0 || keyCode == GLFW.GLFW_KEY_KP_0) {
            grammarZoom = 1.0D;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            grammarZoomAtPanelCenter(ZOOM_STEP);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            grammarZoomAtPanelCenter(1.0D / ZOOM_STEP);
            return true;
        }
        return false;
    }

    private boolean handleGrammarArrowAddCursorShortcut(
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
                grammarModel.addCursorLeftWord(lineHeight, spaceWidth);
            } else {
                grammarModel.addCursor(grammarModel.cursorCanvasX() - spaceWidth, grammarModel.cursorCanvasY());
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (wordTarget) {
                grammarModel.addCursorRightWord(lineHeight, spaceWidth);
            } else {
                grammarModel.addCursor(grammarModel.cursorCanvasX() + spaceWidth, grammarModel.cursorCanvasY());
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (wordTarget) {
                grammarModel.addCursorUpToGlyph(lineHeight);
            } else {
                grammarModel.addCursor(grammarModel.cursorCanvasX(), grammarModel.cursorCanvasY() - lineHeight);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (wordTarget) {
                grammarModel.addCursorDownToGlyph(lineHeight);
            } else {
                grammarModel.addCursor(grammarModel.cursorCanvasX(), grammarModel.cursorCanvasY() + lineHeight);
            }
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
        if (model().glyphs().isEmpty()) {
            resetZoomLevel();
            return;
        }

        double left = Double.POSITIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (SFMDrawCanvasModel.CanvasGlyph glyph : model().glyphs()) {
            left = Math.min(left, glyph.x());
            top = Math.min(top, glyph.y());
            right = Math.max(right, glyph.x() + glyph.width());
            bottom = Math.max(bottom, glyph.y() + this.font.lineHeight);
        }

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
        model.typeText(openContext.initialValue(), this.font::width, this.font.lineHeight);
        model.moveCursorToDocumentStart();
    }

    private void saveAndClose() {
        if (openContext == null) {
            Minecraft.getInstance().setScreen(previousScreen);
            return;
        }
        openContext.saveWriter().accept(getCurrentText());
        Minecraft.getInstance().setScreen(previousScreen);
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
        return SFMDrawCanvasSyntaxHighlightingHelper
                .projectCanvasDocument(model().glyphs(), this.font.width(" "), this.font.lineHeight)
                .text();
    }

    private void initializeCamera() {
        if (cameraInitialized) {
            return;
        }
        cameraX = (this.width / 2.0D - DEFAULT_ORIGIN_MARGIN) / zoom;
        cameraY = (this.height / 2.0D - DEFAULT_ORIGIN_MARGIN) / zoom;
        cameraInitialized = true;
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

    private void renderGrid(GuiGraphics guiGraphics) {
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
            guiGraphics.fill( screenX, 0, screenX + 1, this.height, color);
        }

        int firstHorizontal = Mth.floor(topCanvas / step);
        int lastHorizontal = Mth.ceil(bottomCanvas / step);
        for (int gridY = firstHorizontal; gridY <= lastHorizontal; gridY++) {
            double canvasY = gridY * step;
            int screenY = (int) Math.round(canvasToScreenY(canvasY));
            int color = gridLineColor(gridY);
            guiGraphics.fill( 0, screenY, this.width, screenY + 1, color);
        }

        int axisX = (int) Math.round(canvasToScreenX(0.0D));
        if (axisX >= 0 && axisX < this.width) {
            guiGraphics.fill( axisX, 0, axisX + 2, this.height, AXIS_Y);
        }
        int axisY = (int) Math.round(canvasToScreenY(0.0D));
        if (axisY >= 0 && axisY < this.height) {
            guiGraphics.fill( 0, axisY, this.width, axisY + 2, AXIS_X);
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

    private void renderGlyphs(GuiGraphics guiGraphics) {
        Map<SFMDrawCanvasModel.CanvasGlyph, Integer> glyphColours = SFMDrawCanvasSyntaxHighlightingHelper.buildSyntaxHighlightColours(
                model().glyphs(),
                this.font.width(" "),
                this.font.lineHeight,
                GLYPH
        );
        for (SFMDrawCanvasModel.CanvasGlyph glyph : model().glyphs()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(canvasToScreenX(glyph.x()), canvasToScreenY(glyph.y()), 0.0D);
            guiGraphics.pose().scale((float) zoom, (float) zoom, 1.0F);
            guiGraphics.drawString( this.font, glyph.text(), 0, 0, glyphColours.getOrDefault(glyph, GLYPH));
            guiGraphics.pose().popPose();
        }
    }

    private void renderGlyphBoundingBoxes(GuiGraphics guiGraphics) {
        for (SFMDrawCanvasModel.CanvasGlyph glyph : model().glyphs()) {
            int left = (int) Math.floor(canvasToScreenX(glyph.x()));
            int top = (int) Math.floor(canvasToScreenY(glyph.y()));
            int right = (int) Math.ceil(left + this.font.width(glyph.text()) * zoom);
            int bottom = (int) Math.ceil(top + this.font.lineHeight * zoom);
            drawRectOutline(guiGraphics, left, top, Math.max(left + 1, right), Math.max(top + 1, bottom), GLYPH_BOUNDS);
        }
    }

    @MCVersionDependentBehaviour
    private void renderGlyphSelectionHighlights(GuiGraphics guiGraphics) {
        List<CanvasRect> mask = new ArrayList<>();
        for (SFMDrawCanvasModel.CanvasGlyph glyph : model().glyphs()) {
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
        for (CanvasRect rect : unionRects(mask)) {
            SFMScreenRenderUtils.renderHighlight(
                    guiGraphics,
                    Mth.floor(rect.left()),
                    Mth.floor(rect.top()),
                    Mth.ceil(Math.max(rect.left() + 1.0D, rect.right())),
                    Mth.ceil(Math.max(rect.top() + 1.0D, rect.bottom()))
            );
        }
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

    private void renderCursorTrail(GuiGraphics guiGraphics) {
        int count = cursorTrail.size();
        for (int i = 0; i < count; i++) {
            CanvasPoint point = cursorTrail.get(i);
            double age = count <= 1 ? 1.0D : (double) i / (double) (count - 1);
            int alpha = 32 + (int) Math.round(age * 176.0D);
            int color = (alpha << 24) | (CURSOR_TRAIL & 0x00FFFFFF);
            int screenX = (int) Math.round(canvasToScreenX(point.x()));
            int screenY = (int) Math.round(canvasToScreenY(point.y()));
            int size = Math.max(1, (int) Math.round(2.0D * zoom));
            guiGraphics.fill( screenX - size, screenY - size, screenX + size + 1, screenY + size + 1, color);
        }
    }

    private void renderEmbeddedDocuments(GuiGraphics guiGraphics) {
        for (EmbeddedDocument document : embeddedDocuments) {
            renderEmbeddedDocument(guiGraphics, document);
        }
    }

    private void renderEmbeddedDocument(
            GuiGraphics guiGraphics,
            EmbeddedDocument document
    ) {
        int left = (int) Math.floor(canvasToScreenX(document.canvasX));
        int top = (int) Math.floor(canvasToScreenY(document.canvasY));
        int right = (int) Math.ceil(canvasToScreenX(document.canvasX + document.canvasWidth));
        int bottom = (int) Math.ceil(canvasToScreenY(document.canvasY + document.canvasHeight));
        if (right < 0 || bottom < 0 || left > this.width || top > this.height) {
            return;
        }

        guiGraphics.fill( left, top, right, bottom, EMBEDDED_DOCUMENT_BACKGROUND);
        drawRectOutline(guiGraphics, left, top, right, bottom, EMBEDDED_DOCUMENT_BORDER);
        int titleWidth = this.font.width(document.title);
        guiGraphics.fill( left, top - 14, Math.min(right, left + titleWidth + 12), top, PANEL_TAB_BACKGROUND);
        guiGraphics.drawString( this.font, Component.literal(document.title), left + 6, top - 11, HUD_TEXT);
        renderEmbeddedDocumentGlyphs(guiGraphics, document, left, top, right, bottom);
        renderEmbeddedDocumentHandles(guiGraphics, left, top, right, bottom);
    }

    private void renderEmbeddedDocumentGlyphs(
            GuiGraphics guiGraphics,
            EmbeddedDocument document,
            int left,
            int top,
            int right,
            int bottom
    ) {
        Map<SFMDrawCanvasModel.CanvasGlyph, Integer> glyphColours = SFMDrawCanvasSyntaxHighlightingHelper.buildAntlrGrammarHighlightColours(
                document.model.glyphs(),
                this.font.width(" "),
                this.font.lineHeight,
                GLYPH
        );
        double scale = document.contentZoom * zoom;
        for (SFMDrawCanvasModel.CanvasGlyph glyph : document.model.glyphs()) {
            double canvasX = document.canvasX + document.canvasWidth / 2.0D + (glyph.x() - document.cameraX) * document.contentZoom;
            double canvasY = document.canvasY + document.canvasHeight / 2.0D + (glyph.y() - document.cameraY) * document.contentZoom;
            double screenX = canvasToScreenX(canvasX);
            double screenY = canvasToScreenY(canvasY);
            if (screenX > right || screenX + glyph.width() * scale < left || screenY > bottom || screenY + this.font.lineHeight * scale < top) {
                continue;
            }
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(screenX, screenY, 0.0D);
            guiGraphics.pose().scale((float) scale, (float) scale, 1.0F);
            guiGraphics.drawString( this.font, glyph.text(), 0, 0, glyphColours.getOrDefault(glyph, GLYPH));
            guiGraphics.pose().popPose();
        }
    }

    private void renderEmbeddedDocumentHandles(
            GuiGraphics guiGraphics,
            int left,
            int top,
            int right,
            int bottom
    ) {
        int handle = 5;
        guiGraphics.fill( left - handle, top - handle, left + handle, top + handle, EMBEDDED_DOCUMENT_HANDLE);
        guiGraphics.fill( right - handle, top - handle, right + handle, top + handle, EMBEDDED_DOCUMENT_HANDLE);
        guiGraphics.fill( left - handle, bottom - handle, left + handle, bottom + handle, EMBEDDED_DOCUMENT_HANDLE);
        guiGraphics.fill( right - handle, bottom - handle, right + handle, bottom + handle, EMBEDDED_DOCUMENT_HANDLE);
    }

    private void renderGrammarInsertDrag(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY
    ) {
        drawDashedLine(
                guiGraphics,
                (int) Math.round(grammarInsertStartX),
                (int) Math.round(grammarInsertStartY),
                mouseX,
                mouseY,
                INSERT_DRAG_LINE
        );
        int previewWidth = Math.max(180, (int) Math.round(260.0D * zoom));
        int previewHeight = Math.max(100, (int) Math.round(160.0D * zoom));
        drawRectOutline(
                guiGraphics,
                mouseX,
                mouseY,
                mouseX + previewWidth,
                mouseY + previewHeight,
                INSERT_DRAG_LINE
        );
    }

    private void drawDashedLine(
            GuiGraphics guiGraphics,
            int startX,
            int startY,
            int endX,
            int endY,
            int color
    ) {
        double dx = endX - startX;
        double dy = endY - startY;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0D) {
            return;
        }
        int segments = Math.max(1, (int) (length / 6.0D));
        for (int i = 0; i <= segments; i += 2) {
            double t = (double) i / (double) segments;
            int x = (int) Math.round(startX + dx * t);
            int y = (int) Math.round(startY + dy * t);
            guiGraphics.fill( x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private void drawRectOutline(
            GuiGraphics guiGraphics,
            int left,
            int top,
            int right,
            int bottom,
            int color
    ) {
        guiGraphics.fill( left, top, right, top + 1, color);
        guiGraphics.fill( left, bottom - 1, right, bottom, color);
        guiGraphics.fill( left, top, left + 1, bottom, color);
        guiGraphics.fill( right - 1, top, right, bottom, color);
    }

    private void renderCanvasCursor(GuiGraphics guiGraphics) {
        for (int i = 0; i < model().cursors().size(); i++) {
            SFMDrawCanvasModel.CanvasCursor cursor = model().cursors().get(i);
            if (!hideSelection && isUniqueCursorInAnyGlyphBounds(cursor)) {
                continue;
            }
            renderCanvasCursor(guiGraphics, cursor, i == model().focusedCursorIndex());
        }
    }

    private void renderCanvasCursor(
            GuiGraphics guiGraphics,
            SFMDrawCanvasModel.CanvasCursor cursor,
            boolean focused
    ) {
        int mouseX = (int) Math.round(canvasToScreenX(cursor.x()));
        int mouseY = (int) Math.round(canvasToScreenY(cursor.y()));
        int size = panning ? 8 : 6;
        int cursorSize = cursor.active() ? size + 2 : size;
        if (focused) {
            drawCrosshair(guiGraphics, mouseX, mouseY, cursorSize + 2, focusedCursorOutlineColor(cursor.color()));
        }
        drawCrosshair(guiGraphics, mouseX, mouseY, cursorSize, cursor.active() ? cursor.color() : inactiveCursorColor(cursor.color()));
    }

    private void drawCrosshair(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            int size,
            int color
    ) {
        guiGraphics.fill( mouseX - size, mouseY, mouseX - 2, mouseY + 1, color);
        guiGraphics.fill( mouseX + 3, mouseY, mouseX + size + 1, mouseY + 1, color);
        guiGraphics.fill( mouseX, mouseY - size, mouseX + 1, mouseY - 2, color);
        guiGraphics.fill( mouseX, mouseY + 3, mouseX + 1, mouseY + size + 1, color);
        guiGraphics.fill( mouseX, mouseY, mouseX + 1, mouseY + 1, color);
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
        for (SFMDrawCanvasModel.CanvasGlyph glyph : sourceModel.glyphs()) {
            if (uniqueCursorInGlyphBounds(sourceModel, glyph) == cursor) {
                return true;
            }
        }
        return false;
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

    private void insertLineBreak() {
        model().insertLineBreak(this.font.lineHeight);
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
        rememberCursorPosition();
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

    private void renderHud(GuiGraphics guiGraphics) {
        int left = 8;
        int top = diagnosticControlsVisible ? 104 : 8;
        int right = 226;
        int bottom = 48;
        guiGraphics.fill( left, top, right, bottom, HUD_BACKGROUND);
        guiGraphics.fill( left, top, right, top + 1, HUD_BORDER);
        guiGraphics.fill( left, bottom - 1, right, bottom, HUD_BORDER);
        guiGraphics.fill( left, top, left + 1, bottom, HUD_BORDER);
        guiGraphics.fill( right - 1, top, right, bottom, HUD_BORDER);

        guiGraphics.drawString( this.font, this.title, left + 8, top + 7, HUD_TEXT);
        guiGraphics.drawString(
                this.font,
                String.format(
                        "cursor %.1f, %.1f  zoom %.2fx",
                        model().cursorCanvasX(),
                        model().cursorCanvasY(),
                        zoom
                ),
                left + 8,
                top + 22,
                HUD_MUTED
        );
    }

    private void renderInputDiagnostics(GuiGraphics guiGraphics) {
        if (inputEvents.isEmpty()) {
            return;
        }
        int left = 8;
        int lineHeight = this.font.lineHeight + 2;
        int height = inputEvents.size() * lineHeight + 12;
        int top = Math.max(112, this.height - height - 8);
        int right = Math.min(this.width - 8, 420);
        int bottom = top + height;
        guiGraphics.fill( left, top, right, bottom, HUD_BACKGROUND);
        guiGraphics.fill( left, top, right, top + 1, HUD_BORDER);
        guiGraphics.fill( left, bottom - 1, right, bottom, HUD_BORDER);
        guiGraphics.fill( left, top, left + 1, bottom, HUD_BORDER);
        guiGraphics.fill( right - 1, top, right, bottom, HUD_BORDER);
        int y = top + 6;
        for (String event : inputEvents) {
            guiGraphics.drawString( this.font, event, left + 6, y, HUD_MUTED);
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

    private MutableComponent sfmlButtonTooltip() {
        return DRAW_CANVAS_SFML_BUTTON_TOOLTIP_PREFIX.getComponent()
                .append(Component.literal("SFML.g4").withStyle(ChatFormatting.AQUA));
    }

    private MutableComponent doneButtonTooltip() {
        return DRAW_CANVAS_DONE_BUTTON_TOOLTIP_PREFIX.getComponent()
                .append(Component.literal("Shift+Enter").withStyle(ChatFormatting.AQUA))
                .append(DRAW_CANVAS_DONE_BUTTON_TOOLTIP_SUFFIX.getComponent());
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
        if (sfmlButton != null && sfmlButton.isMouseOver(mouseX, mouseY)) {
            return;
        }
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

    private void renderGrammarPanel(GuiGraphics guiGraphics) {
        loadGrammarContent();
        initializeGrammarCamera();
        int left = grammarPanelLeft();
        int top = grammarPanelTop();
        int right = left + grammarPanelWidth();
        int bottom = top + grammarPanelHeight();
        guiGraphics.fill( left, top, right, bottom, PANEL_BACKGROUND);

        int tabWidth = 70;
        int tabHeight = 16;
        guiGraphics.fill( left + 8, top - tabHeight, left + 8 + tabWidth, top, PANEL_TAB_BACKGROUND);
        drawRectOutline(guiGraphics, left + 8, top - tabHeight, left + 8 + tabWidth, top + 1, HUD_BORDER);
        guiGraphics.drawString( this.font, Component.literal("SFML.g4"), left + 14, top - tabHeight + 4, HUD_TEXT);

        renderGrammarGlyphs(guiGraphics);
        if (!hideSelection) {
            renderGrammarGlyphSelectionHighlights(guiGraphics);
        }
        renderGrammarCanvasCursor(guiGraphics);

        drawRectOutline(guiGraphics, left, top, right, bottom, isGrammarPanelFocused() ? FOCUS_BORDER : HUD_BORDER);
    }

    private void renderGrammarGlyphs(GuiGraphics guiGraphics) {
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
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(screenX, screenY, 0.0D);
            guiGraphics.pose().scale((float) grammarZoom, (float) grammarZoom, 1.0F);
            guiGraphics.drawString( this.font, glyph.text(), 0, 0, glyphColours.getOrDefault(glyph, GLYPH));
            guiGraphics.pose().popPose();
        }
    }

    @MCVersionDependentBehaviour
    private void renderGrammarGlyphSelectionHighlights(GuiGraphics guiGraphics) {
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
                    guiGraphics,
                    Mth.floor(rect.left()),
                    Mth.floor(rect.top()),
                    Mth.ceil(Math.max(rect.left() + 1.0D, rect.right())),
                    Mth.ceil(Math.max(rect.top() + 1.0D, rect.bottom()))
            );
        }
    }

    private void renderGrammarCanvasCursor(GuiGraphics guiGraphics) {
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
                drawCrosshair(guiGraphics, screenX, screenY, cursorSize + 2, focusedCursorOutlineColor(cursor.color()));
            }
            drawCrosshair(guiGraphics, screenX, screenY, cursorSize, cursor.active() ? cursor.color() : inactiveCursorColor(cursor.color()));
        }
    }

    private void renderReadOnlyMessage(GuiGraphics guiGraphics) {
        Component message = DRAW_CANVAS_READ_ONLY_DOCUMENT.getComponent();
        int width = this.font.width(message);
        int left = (this.width - width) / 2 - 8;
        int top = this.height - 48;
        int right = left + width + 16;
        int bottom = top + this.font.lineHeight + 10;
        guiGraphics.fill( left, top, right, bottom, HUD_BACKGROUND);
        drawRectOutline(guiGraphics, left, top, right, bottom, HUD_BORDER);
        guiGraphics.drawString( this.font, message, left + 8, top + 5, HUD_TEXT);
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

        @MCVersionDependentBehaviour
        public CanvasFocusTarget(
                int x,
                int y,
                int width,
                int height,
                boolean showWhenFocused
        ) {
            super(x, y, width, height, Component.empty(), button -> { }, Button.DEFAULT_NARRATION);
            this.showWhenFocused = showWhenFocused;
        }

        @Override
        @MCVersionDependentBehaviour
        public void renderWidget(
                GuiGraphics guiGraphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            if (showWhenFocused && isFocused()) {
                guiGraphics.fill( this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, FOCUS_BORDER);
                guiGraphics.fill( this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, FOCUS_BORDER);
                guiGraphics.fill( this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, FOCUS_BORDER);
                guiGraphics.fill( this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, FOCUS_BORDER);
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
