package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.ProgramTokenContextActions;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.*;
import ca.teamdman.sfm.client.screen.widget.PickList;
import ca.teamdman.sfm.client.screen.widget.PickListItem;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMComponentUtils;
import ca.teamdman.sfm.common.util.SFMDisplayUtils;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.intellisense.IntellisenseAction;
import ca.teamdman.sfml.intellisense.IntellisenseContext;
import ca.teamdman.sfml.intellisense.SFMLIntellisense;
import ca.teamdman.sfml.manipulation.ManipulationResult;
import ca.teamdman.sfml.manipulation.ProgramStringManipulationUtils;
import ca.teamdman.sfml.program_builder.ProgramBuildResult;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField.StringView;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("NotNullFieldNotInitialized")
public class SFMTextEditScreenV1 extends Screen implements ISFMTextEditScreen {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TEXT_EDIT_SCREEN_V1_TITLE = new LocalizationEntry(
            "gui.sfm.text_editor.v1.title",
            "Text Editor"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry INTELLISENSE_PICK_LIST_GUI_TITLE = new LocalizationEntry(
            "gui.sfm.title.intellisense_pick_list",
            "Intellisense Pick List"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_EDIT_SCREEN_DONE_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.text_editor.done_button.tooltip",
            "Shift+Enter to submit"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_EDIT_SCREEN_CONFIG_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.text_editor.config_button.tooltip",
            "Open editor config"
    );

    private final ISFMTextEditScreenOpenContext openContext;

    protected MyMultiLineEditBox textarea;

    protected String lastProgram = "";

    protected List<MutableComponent> content = new ArrayList<>();

    protected PickList<IntellisenseAction> suggestedActions;

    private boolean scrolledOnFirstInit = false;

    private boolean suppressNextCharTypedForIntellisenseAccept = false;

    public SFMTextEditScreenV1(
            ISFMTextEditScreenOpenContext openContext
    ) {

        super(TEXT_EDIT_SCREEN_V1_TITLE.getComponent());
        this.openContext = openContext;
    }

    public void scrollToTop() {

        this.textarea.scrollToTop();
    }

    @Override
    public boolean isPauseScreen() {

        return false;
    }

    /**
     * The user has indicated to save by hitting Shift+Enter or by pressing the Done button
     */
    public void saveAndClose() {

        openContext.onSaveAndClose(textarea.getValue());
    }

    /**
     * The user has tried to close the GUI without saving by hitting the Esc key
     */
    @Override
    public void onClose() {

        openContext.onTryClose(textarea.getValue(), SFMScreenChangeHelpers::popScreen);
    }

    @Override
    public ISFMTextEditScreenOpenContext openContext() {

        return openContext;
    }

    @Override
    public void onPreferenceChanged() {

        textarea.rebuildIntellisense();
    }

    @Override
    public boolean keyReleased(
            KeyEvent event
    ) {

        boolean handled = false;
        if (event.hasControlDown()) {
            // if control released => update syntax highlighting
            textarea.rebuild(event.hasControlDown());
            handled = true;
        }
        if (suppressNextCharTypedForIntellisenseAccept && isIntellisenseAcceptKey(event)) {
            suppressNextCharTypedForIntellisenseAccept = false;
            handled = true;
        }
        return handled;
    }

    @Override
    public boolean charTyped(
            CharacterEvent event
    ) {

        if (suppressNextCharTypedForIntellisenseAccept) {
            suppressNextCharTypedForIntellisenseAccept = false;
            return true;
        }
        if (SFMWidgetUtils.hasCtrlDown() && event.codepoint() == ' ') {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(
            KeyEvent event
    ) {
        // TODO: add separate keybindings for
        // context action - hold to arm
        // context action - execute
        // indent - increase
        // indent - decrease
        // save and close - hold to arm
        // save and close - execute
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && event.hasShiftDown()) {
            saveAndClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            // if tab pressed with no selection and not holding shift => insert 4 spaces
            // if tab pressed with no selection and holding shift => de-indent current line
            // if tab pressed with selection and not holding shift => de-indent lines containing selection 4 spaces
            // if tab pressed with selection and holding shift => indent lines containing selection 4 spaces
            String content = textarea.getValue();
            int cursor = textarea.getCursorPosition();
            int selectionCursor = textarea.getSelectionCursorPosition();
            double scrollAmount = textarea.getScrollAmount();
            ManipulationResult result;
            if (event.hasShiftDown()) { // de-indent
                result = ProgramStringManipulationUtils.deindent(content, cursor, selectionCursor);
            } else { // indent
                result = ProgramStringManipulationUtils.indent(content, cursor, selectionCursor);
            }
            textarea.setValue(result.content());
            textarea.setCursorPosition(result.cursorPosition());
            textarea.setSelectionCursorPosition(result.selectionCursorPosition());
            textarea.setScrollAmount(scrollAmount);
            return true;
        }
        if (isIntellisenseAcceptKey(event) && acceptSelectedIntellisenseAction()) {
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_LEFT_CONTROL || event.key() == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            // if control pressed => update syntax highlighting
            textarea.rebuild(event.hasControlDown());
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_SLASH && event.hasControlDown()) {
            // toggle line comments for selected lines
            String content = textarea.getValue();
            int cursor = textarea.getCursorPosition();
            int selectionCursor = textarea.getSelectionCursorPosition();
            ManipulationResult result = ProgramStringManipulationUtils.toggleComments(content, cursor, selectionCursor);
            textarea.setValue(result.content());
            textarea.setCursorPosition(result.cursorPosition());
            textarea.setSelectionCursorPosition(result.selectionCursorPosition());
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_SPACE && event.hasControlDown()) {
            ProgramTokenContextActions.getContextAction(
                            textarea.getValue(),
                            textarea.getCursorPosition()
                    )
                    .ifPresent(Runnable::run);

            // disable the underline since it doesn't refresh when the context action closes
            textarea.rebuild(false);
            return true;
        }
        if (
                (
                        event.key() == GLFW.GLFW_KEY_UP
                        || event.key() == GLFW.GLFW_KEY_DOWN
                )
                && !suggestedActions.getItems().isEmpty()
        ) {
            if (event.key() == GLFW.GLFW_KEY_UP) {
                suggestedActions.selectPreviousWrapping();
            } else {
                suggestedActions.selectNextWrapping();
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && !suggestedActions.isEmpty()) {
            suggestedActions.clear();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void resize(
            int x,
            int y
    ) {

        var prev = this.textarea.getValue();
        init(x, y);
        super.resize(x, y);
        this.textarea.setValue(prev);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mx,
            int my,
            float partialTicks
    ) {

        // render background
        this.extractTransparentBackground(graphics);

        // render widgets
        super.extractRenderState(graphics, mx, my, partialTicks);

        // render tooltips
        SFMWidgetUtils.hideTooltipsWhenNotFocused(this, this.renderables);
        SFMWidgetUtils.renderChildTooltips(graphics.pose(), mx, my, this.renderables);
    }

    @Override
    public void tick() {

        this.textarea.tick();
    }

    private boolean isIntellisenseAcceptKey(
            KeyEvent event
    ) {

        return SFMKeyMappings.TEXT_EDITOR_ACCEPT_INTELLISENSE_KEY.get().matches(event);
    }

    private boolean acceptSelectedIntellisenseAction() {

        if (suggestedActions.isEmpty()) {
            return false;
        }
        IntellisenseAction action = suggestedActions.getSelected();
        assert action != null;

        ManipulationResult result = action.perform(
                new IntellisenseContext(
                        new ProgramBuilder(textarea.getValue()).build(),
                        textarea.getCursorPosition(),
                        textarea.getSelectionCursorPosition(),
                        openContext.labelPositionHolder(),
                        SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.intellisenseLevel.get()
                )
        );
        double scrollAmount = textarea.getScrollAmount();
        textarea.setValue(result.content());
        textarea.setSelectionCursorPosition(result.selectionCursorPosition());
        textarea.setCursorPosition(result.cursorPosition());
        textarea.setScrollAmount(scrollAmount);
        suppressNextCharTypedForIntellisenseAccept = true;
        return true;
    }

    @Override
    protected void init() {

        super.init();
        SFMScreenRenderUtils.enableKeyRepeating();

        this.textarea = this.addRenderableWidget(new MyMultiLineEditBox(
                SFMTextEditScreenV1.this.font,
                SFMTextEditScreenV1.this.width / 2 - 200,
                SFMTextEditScreenV1.this.height / 2 - 110,
                400,
                200,
                Component.literal(""),
                Component.literal("")
        ));

        this.suggestedActions = this.addRenderableWidget(new PickList<>(
                this.font,
                0,
                0,
                180,
                this.font.lineHeight * 6,
                INTELLISENSE_PICK_LIST_GUI_TITLE.getComponent(),
                new ArrayList<>()
        ));

        this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(this.width / 2 - 200, this.height / 2 - 100 + 195)
                        .setSize(16, 20)
                        .setText(Component.literal("#"))
                        .setOnPress((button) -> {
                            int cursorPos = textarea.getCursorPosition();
                            int selectionCursorPos = textarea.getSelectionCursorPosition();
                            SFMScreenChangeHelpers.setOrPushScreen(
                                    new SFMTextEditorConfigScreen(
                                            this,
                                            SFMConfig.CLIENT_TEXT_EDITOR_CONFIG,
                                            () -> {
                                                this.setInitialFocus(textarea);
                                                textarea.setCursorPosition(cursorPos);
                                                textarea.setSelectionCursorPosition(selectionCursorPos);
                                            }
                                    )
                            );
                        })
                        .setTooltip(this, font, PROGRAM_EDIT_SCREEN_CONFIG_BUTTON_TOOLTIP)
                        .build()
        );
        this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                this.width / 2 - 2 - 150,
                                this.height / 2 - 100 + 195
                        )
                        .setSize(200, 20)
                        .setText(CommonComponents.GUI_DONE)
                        .setOnPress((button) -> this.saveAndClose())
                        .setTooltip(this, font, PROGRAM_EDIT_SCREEN_DONE_BUTTON_TOOLTIP)
                        .build()
        );
        this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                this.width / 2 - 2 + 100,
                                this.height / 2 - 100 + 195
                        )
                        .setSize(100, 20)
                        .setText(CommonComponents.GUI_CANCEL)
                        .setOnPress((button) -> this.onClose())
                        .build()
        );

        textarea.setValue(openContext.initialValue());

        // Scroll to top on first init to match previous behavior without needing external calls
        if (!scrolledOnFirstInit) {
            scrollToTop();
            scrolledOnFirstInit = true;
        }

        this.setInitialFocus(textarea);
    }

    // TODO: enable scrolling without focus; respond to wheel events
    protected class MyMultiLineEditBox extends MultiLineEditBox {
        // Precomputed line start offsets for fast mapping; kept in sync in rebuild()
        private final List<Integer> displayedLineStartOffsets = new ArrayList<>();

        // Cache to avoid reparsing on cursor-only moves
        private @Nullable ProgramBuildResult cachedBuildResult;

        private String cachedBuildProgram = "";

        private boolean scrollbarDragActive;

        /// Used to debounce scrolling when click-dragging to select text.
        private boolean scrollingEnabled = true;

        private int cursorBlinkTick = 0;

        protected MyMultiLineEditBox(
                Font pFont,
                int pX,
                int pY,
                int pWidth,
                int pHeight,
                Component pPlaceholder,
                Component pMessage
        ) {
            super(pFont, pX, pY, pWidth, pHeight, pPlaceholder, pMessage, -2039584, true, -3092272, true, true);

            this.textField.setValueListener(this::onValueOrCursorChanged);
            this.textField.setCursorListener(() -> this.onValueOrCursorChanged(this.textField.value()));
            this.rebuild(false);
        }

        public void scrollToTop() {

            this.setScrollAmount(0);
        }

        @Override
        public void setFocused(boolean focused) {

            super.setFocused(focused);
//            if (!focused) {
//                this.scrollbarDragActive = false;
//            }
        }

        public int getCursorPosition() {

            return this.textField.cursor;
        }

        public void setCursorPosition(int cursor) {

            this.textField.seekCursor(Whence.ABSOLUTE, cursor);
        }

        @MCVersionDependentBehaviour
        public int getScrollBarHeight() {
            // Fix #307: divide by zero exception in AbstractScrollWidget.mouseDragged
            int rtn = this.getBottom() - this.getY();
            if (rtn == this.height) {
                return rtn - 1;
            } else {
                return rtn;
            }
        }

        @MCVersionDependentBehaviour
        protected boolean withinContentAreaPoint(double x, double y) {
            return x >= (double)this.getX()
                    && x < (double)(this.getX() + this.width)
                    && y >= (double)this.getY()
                    && y < (double)(this.getY() + this.height);
        }

        @MCVersionDependentBehaviour
        @Override
        public boolean mouseClicked(
                MouseButtonEvent event,
                boolean doubleClick
        ) {
            int pButton = event.button();
            double pMouseX = event.x(),
                    pMouseY = event.y();
            try {
                if (pButton == 0) {
                    this.scrollbarDragActive = false;
                }
                if (pButton == 0 && this.visible && this.withinContentAreaPoint(pMouseX, pMouseY)) {
                    if (content.isEmpty()) {
                        return false;
                    }
                    // Focus the editor so the caret blinks and keys go here
                    this.setFocused(true);

                    boolean shiftDown = event.hasShiftDown();
                    // Move cursor to the click position
                    seekCursorFromPoint(pMouseX, pMouseY);
                    // If not extending with Shift, start a new selection anchor at the click
                    if (!shiftDown) {
                        this.textField.selectCursor = this.textField.cursor;
                    }
                    // Enable selection so dragging extends from the anchor
                    this.textField.setSelecting(true);
                    return true;
                }
                boolean clickedScrollbar =
                        pButton == 0
                        && this.visible
                        && this.isOverScrollbar(pMouseX, pMouseY);
                if (clickedScrollbar) {
                    this.scrollbarDragActive = true;
                }

//                return super.mouseClicked(pMouseX, pMouseY, pButton);
                // we need to override the default behaviour because Mojang broke it
                // if it's not scrolling, it should return false for cursor click movement
                boolean rtn;
                if (!this.visible) {
                    rtn = false;
                } else {
                    //noinspection unused
                    boolean flag = this.withinContentAreaPoint(pMouseX, pMouseY);
                    boolean flag1 = this.isOverScrollbar(pMouseX, pMouseY);
                    if (flag1 && pButton == 0) {
                        this.scrolling = true;
                        rtn = true;
                    } else {
                        //1.19.4 behaviour:
                        //rtn=flag || flag1;
                        // instead, we want to return false if we're not scrolling
                        // (like how it was in 1.19.2)
                        // https://bugs.mojang.com/browse/MC-262754
                        rtn = false;
                    }
                }

                if (rtn) {
                    return true;
                } else if (this.withinContentAreaPoint(pMouseX, pMouseY) && pButton == 0) {
                    this.textField.setSelecting(event.hasShiftDown());
                    this.seekCursorScreen(pMouseX, pMouseY);
                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                SFM.LOGGER.error("Error in SFMTextEditScreenV1.MyMultiLineEditBox.mouseClicked", e);
                return false;
            }
        }

        @Override
        public int getInnerHeight() {
            // parent method uses this.textField.getLineCount() which is split for text wrapping
            // we don't use the wrapped text, so we need to calculate the height ourselves to avoid overshooting
            return this.font.lineHeight * (content.size() + 2);
        }


        @Override
        public boolean mouseDragged(
                MouseButtonEvent event, double dx, double dy
        ) {
            int button = event.button();
            double mx = event.x(),
                    my = event.y();
            // IMPORTANT: give the scrollbar drag priority.
            // If the drag started on the scrollbar, AbstractScrollWidget will
            // consume this, and we should not start a text selection.
            if (this.scrollbarDragActive && super.mouseDragged(event, dx, dy)) {
                return true;
            }

            try {
                if (button == 0 && this.visible && this.withinContentAreaPoint(mx, my)) {
                    if (content.isEmpty()) {
                        return false;
                    }
                    // Keep selection active while dragging and update cursor
                    this.textField.setSelecting(true);
                    seekCursorFromPoint(mx, my);
                    return true;
                }
            } catch (Exception e) {
                SFM.LOGGER.error("Error in SFMTextEditScreenV1.MyMultiLineEditBox.mouseDragged", e);
                return false;
            }

            return false;
        }

        @Override
        public boolean mouseReleased(
                MouseButtonEvent event
        ) {

            if (event.button() == 0) {
                // Stop active selection on mouse up
                this.textField.setSelecting(false);
                this.scrollbarDragActive = false;
            }
            return super.mouseReleased(event);
        }

        public int getSelectionCursorPosition() {

            return this.textField.selectCursor;
        }

        public void setSelectionCursorPosition(int cursor) {

            this.textField.selectCursor = cursor;
        }

        public double getScrollAmount() {

            return this.scrollAmount();
        }

        @Override
        public void setScrollAmount(double pScrollAmount) {

            if (!scrollingEnabled) return;
            super.setScrollAmount(pScrollAmount);
        }

        public void tick() {

            this.cursorBlinkTick++;
        }

        private void seekCursorFromPoint(
                double mx,
                double my
        ) {

            int lineCount = content.size();
            double innerX = mx - (
                    SFMWidgetUtils.getX(this)
                    + this.innerPadding()
                    + SFMTextEditorUtils.getLineNumberWidth(this.font, lineCount)
            );
            double innerY = my - (SFMWidgetUtils.getY(this) + this.innerPadding()) + this.scrollAmount();
            int lineIndex = Mth.clamp(
                    (int) Math.floor(innerY / Math.max(1, this.font.lineHeight)),
                    0,
                    Math.max(0, lineCount - 1)
            );
            int cursorPosition = pointToCursor(innerX, lineIndex);

            this.scrollingEnabled = false;
            this.textField.seekCursor(Whence.ABSOLUTE, cursorPosition);
            this.scrollingEnabled = true;
        }

        private int getLineStartIndex(int lineIndex) {

            if (displayedLineStartOffsets.isEmpty()) return 0;
            int clamped = Mth.clamp(
                    lineIndex,
                    0,
                    Math.max(0, displayedLineStartOffsets.size() - 1)
            );
            return displayedLineStartOffsets.get(clamped);
        }

        private int pointToCursor(
                double innerX,
                int lineIndex
        ) {

            int lineStartIndex = getLineStartIndex(lineIndex);
            if (content.isEmpty()) {
                return lineStartIndex;
            }
            int clampedLine = Mth.clamp(lineIndex, 0, Math.max(0, content.size() - 1));
            String plainLine = content.get(clampedLine).getString();
            int clampedX = (int) Math.max(0, innerX);
            int cursorOffsetInLine = this.font.plainSubstrByWidth(plainLine, clampedX).length();
            int widthBeforeCursor = this.font.width(plainLine.substring(0, cursorOffsetInLine));
            if (cursorOffsetInLine < plainLine.length()) {
                int nextGlyphWidth = this.font.width(plainLine.substring(cursorOffsetInLine, cursorOffsetInLine + 1));
                if ((double) (clampedX - widthBeforeCursor) >= nextGlyphWidth / 2.0D) {
                    cursorOffsetInLine = Math.min(plainLine.length(), cursorOffsetInLine + 1);
                }
            }
            return Mth.clamp(
                    lineStartIndex + cursorOffsetInLine,
                    0,
                    this.textField.value().length()
            );
        }

        @Override
        public int maxScrollAmount() {

            return Math.max(1, super.maxScrollAmount()); // Fix #307: divide by zero exception
        }

        private void onValueOrCursorChanged(String programString) {

            int cursorPosition = getCursorPosition();

            // Build the program only when text changed; reuse parse on cursor-only
            // moves
            ProgramBuildResult buildResult;
            if (programString.equals(cachedBuildProgram) && cachedBuildResult != null) {
                buildResult = cachedBuildResult;
            } else {

                buildResult = new ProgramBuilder(programString).build();
                cachedBuildProgram = programString;
                cachedBuildResult = buildResult;
            }

            // Update the intellisense picklist
            IntellisenseContext intellisenseContext = new IntellisenseContext(
                    buildResult,
                    cursorPosition,
                    getSelectionCursorPosition(),
                    openContext.labelPositionHolder(),
                    SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.intellisenseLevel.get()
            );
            List<IntellisenseAction> suggestions = SFMLIntellisense.getSuggestions(intellisenseContext);
            SFMTextEditScreenV1.this.suggestedActions.setItems(suggestions);

            // Update the intellisense picklist query used to sort the suggestions
            String cursorWord = buildResult.getWordAtCursorPosition(cursorPosition);
            SFMTextEditScreenV1.this.suggestedActions.setQuery(Component.literal(cursorWord));

            boolean shouldPrint = false;
            //noinspection ConstantValue
            if (shouldPrint) {
                String cursorPositionDisplay = SFMDisplayUtils.getCursorPositionDisplay(programString, cursorPosition);
                String cursorTokenDisplay = SFMDisplayUtils.getCursorTokenDisplay(buildResult, cursorPosition);
                String tokenHierarchyDisplay;
                @Nullable Program program = buildResult.program();
                if (program == null) {
                    tokenHierarchyDisplay = "<INVALID PROGRAM>";
                } else {
                    tokenHierarchyDisplay = SFMDisplayUtils.getTokenHierarchyDisplay(program, cursorPosition);
                }

                String suggestionsDisplay = suggestedActions.getItems()
                        .stream()
                        .map(PickListItem::getComponent)
                        .map(Component::getString)
                        .collect(Collectors.joining(", "));

                SFM.LOGGER.info(
                        "PROGRAM OR CURSOR CHANGE! {}   {}   {}  |||  {} ||| {}",
                        cursorPositionDisplay,
                        cursorTokenDisplay,
                        tokenHierarchyDisplay,
                        cursorWord,
                        suggestionsDisplay
                );
            }
        }

        private void rebuildIntellisense() {

            onValueOrCursorChanged(getValue());
        }

        /**
         * Rebuilds the syntax-highlighted program text. This runs more frequently than
         * when the value is changed.
         *
         * @param showContextActionHints Should underline words that have context
         *                               actions
         */
        private void rebuild(boolean showContextActionHints) {

            lastProgram = this.textField.value();
            content = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(
                    lastProgram,
                    showContextActionHints
            );

            rebuildDisplayCache();
        }

        private void rebuildDisplayCache() {
            // Rebuild displayed line-start offsets to match the raw text and
            // rendered lines
            displayedLineStartOffsets.clear();
            displayedLineStartOffsets.add(0);
            for (int i = 0; i < lastProgram.length(); i++) {
                if (lastProgram.charAt(i) == '\n') {
                    displayedLineStartOffsets.add(i + 1);
                }
            }
            // Ensure the list size matches the number of rendered lines
            int lines = content.size();
            while (displayedLineStartOffsets.size() > lines) {
                displayedLineStartOffsets.remove(displayedLineStartOffsets.size() - 1);
            }
            while (displayedLineStartOffsets.size() < lines) {
                displayedLineStartOffsets.add(lastProgram.length());
            }
        }

        @Override
        protected void extractContents(
                GuiGraphicsExtractor graphics,
                int mx,
                int my,
                float partialTicks
        ) {

            Matrix3x2fStack matrixStack = graphics.pose();

            // rebuild the program if necessary
            if (!lastProgram.equals(this.textField.value())) {
                rebuild(SFMWidgetUtils.hasCtrlDown());
            }

            final List<MutableComponent> lines = content;
            if (lines.isEmpty()) {
                return;
            }

            final boolean isCursorVisible = this.isFocused() && this.cursorBlinkTick % 20 >= 10;
            final int cursorIndex = textField.cursor();

            final int lineHeight = Math.max(1, this.font.lineHeight);
            final int availableHeight = this.height - this.innerPadding() * 2;
            final double scroll = this.scrollAmount();

            // Determine which logical line is at the top
            final int viewLineIndexStart = Mth.clamp(
                    (int) Math.floor(scroll / lineHeight),
                    0,
                    Math.max(0, lines.size() - 1)
            );
            // Render a small overscan
            final int numVisibleLines = Math.max(1, availableHeight / lineHeight + 2);
            final int viewLineIndexEnd = Math.min(lines.size(), viewLineIndexStart + numVisibleLines);

            final int lineX =
                    SFMWidgetUtils.getX(this) + this.innerPadding()
                    + SFMTextEditorUtils.getLineNumberWidth(this.font, content.size());

            boolean isCursorAtEndOfLine = false;
            boolean drewCursorGlyph = false;

            // IMPORTANT: do not subtract (scroll % lineHeight) here.
            // The parent has already translated by -scrollAmount.
            // Draw at content-space Y positions as if there was no scrolling:
            final int contentTopY = SFMWidgetUtils.getY(this) + this.innerPadding();
            int lineY = contentTopY + viewLineIndexStart * lineHeight;
            int charCountAccum = getLineStartIndex(viewLineIndexStart);

            int cursorX = 0;
            int cursorY = 0;

            final StringView selectedRange = this.textField.getSelected();
            final int selectionStart = selectedRange.beginIndex();
            final int selectionEnd = selectedRange.endIndex();

            // One buffer for the entire text pass
            MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

            // Collect selection highlights rects and draw them after the text
            List<int[]> highlightRects = new ArrayList<>();

            for (int line = viewLineIndexStart; line < viewLineIndexEnd; ++line) {
                var componentColoured = lines.get(line);
                String plainLine = componentColoured.getString();
                int lineLength = plainLine.length();

                boolean isCursorOnThisLine =
                        cursorIndex >= charCountAccum
                        && cursorIndex <= charCountAccum + lineLength;

                if (SFMTextEditorUtils.shouldShowLineNumbers()) {
                    // Draw line number
                    String lineNumber = String.valueOf(line + 1);
                    SFMFontUtils.drawInBatch(
                            lineNumber,
                            this.font,
                            lineX - 2 - this.font.width(lineNumber),
                            lineY,
                            true,
                            false,
                            matrixStack,
                            buffer
                    );
                }

                if (isCursorOnThisLine) {
                    isCursorAtEndOfLine = cursorIndex == charCountAccum + lineLength;
                    cursorY = lineY;
                    int relativeCursorIndex = cursorIndex - charCountAccum;
                    int drawnWidthBeforeCursor = this.font.width(plainLine.substring(0, relativeCursorIndex));
                    cursorX = lineX + drawnWidthBeforeCursor;
                    // draw text before cursor
                    SFMFontUtils.drawInBatch(
                            SFMComponentUtils.substring(componentColoured, 0, relativeCursorIndex),
                            font,
                            lineX,
                            lineY,
                            true,
                            false,
                            matrixStack,
                            buffer
                    );
                    SFMTextEditScreenV1.this.suggestedActions.setXY(cursorX + 10, cursorY);
                    // draw text after cursor
                    SFMFontUtils.drawInBatch(
                            SFMComponentUtils.substring(componentColoured, relativeCursorIndex, lineLength),
                            font,
                            cursorX,
                            lineY,
                            true,
                            false,
                            matrixStack,
                            buffer
                    );
                    drewCursorGlyph = isCursorVisible;
                } else {
                    SFMFontUtils.drawInBatch(
                            componentColoured,
                            font,
                            lineX,
                            lineY,
                            true,
                            false,
                            matrixStack,
                            buffer
                    );
                }

                // Check if the selection is within the current line
                if (selectionStart <= charCountAccum + lineLength && selectionEnd > charCountAccum) {
                    int lineSelectionStart = Math.max(selectionStart - charCountAccum, 0);
                    int lineSelectionEnd = Math.min(selectionEnd - charCountAccum, lineLength);

                    int highlightStartX = this.font.width(plainLine.substring(0, lineSelectionStart));
                    int highlightEndX = this.font.width(plainLine.substring(0, lineSelectionEnd));

                    highlightRects.add(new int[]{
                            lineX + highlightStartX,
                            lineY,
                            lineX + highlightEndX,
                            lineY + lineHeight
                    });
                }

                lineY += lineHeight;
                charCountAccum += lineLength + 1;
            }

            // Flush the text batch once
            buffer.endBatch();

            // Draw selection highlights after text
            for (int[] r : highlightRects) {
                SFMScreenRenderUtils.renderHighlight(
                        graphics,
                        r[0],
                        r[1],
                        r[2],
                        r[3]
                );
            }

            if (drewCursorGlyph) {
                if (isCursorAtEndOfLine) {
                    SFMFontUtils.draw(
                            graphics,
                            this.font,
                            "_",
                            cursorX,
                            cursorY,
                            -1,
                            true
                    );
                } else {
                    graphics.fill(
                            cursorX, cursorY - 1, cursorX + 1, cursorY + 1 + 9, -1);
                }
            }
        }

    }

}
