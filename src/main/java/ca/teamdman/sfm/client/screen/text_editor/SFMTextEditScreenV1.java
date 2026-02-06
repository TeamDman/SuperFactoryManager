package ca.teamdman.sfm.client.screen.text_editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.util.Mth;
import ca.teamdman.sfm.common.util.SFMComponentUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import com.bbscn.*;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.ProgramTokenContextActions;
import ca.teamdman.sfm.client.screen.*;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.widget.PickList;
import ca.teamdman.sfm.client.widget.PickListItem;
import ca.teamdman.sfm.client.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDisplayUtils;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.intellisense.IntellisenseAction;
import ca.teamdman.sfml.intellisense.IntellisenseContext;
import ca.teamdman.sfml.intellisense.SFMLIntellisense;
import ca.teamdman.sfml.manipulation.ManipulationResult;
import ca.teamdman.sfml.manipulation.ProgramStringManipulationUtils;
import ca.teamdman.sfml.program_builder.ProgramBuildResult;
import ca.teamdman.sfml.program_builder.ProgramBuilder;

@SuppressWarnings("NotNullFieldNotInitialized")
public class SFMTextEditScreenV1 extends GuiScreenExtend implements ISFMTextEditScreen {
    private final ISFMTextEditScreenOpenContext openContext;
    protected MyMultiLineEditBox textarea;
    protected String lastProgram = "different";
    protected List<ITextComponent> content = new ArrayList<>();
//    protected List<ITextComponent> lastProgramWithSyntaxHighlighting = new ArrayList<>();
    protected PickList<IntellisenseAction> suggestedActions;
    private boolean scrolledOnFirstInit = false;

    public SFMTextEditScreenV1(
            ISFMTextEditScreenOpenContext openContext
    ) {
        super();
        // LocalizationKeys.TEXT_EDIT_SCREEN_TITLE.getComponent()
        this.openContext = openContext;
    }


    public void scrollToTop() {
        this.textarea.scrollToTop();
    }

    public ISFMTextEditScreenOpenContext openContext() {
        return openContext;
    }

    @Override
    public void onPreferenceChanged() {
        textarea.rebuildIntellisense();
    }


    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * The user has indicated to save by hitting Shift+Enter or by pressing the Done button
     */
    public void saveAndClose() {
        openContext.onSaveAndClose(textarea.getValue());
    }

    public void closeWithoutSaving() {
        SFMScreenChangeHelpers.popScreen();
    }

    public void onIntellisensePreferenceChanged() {
        textarea.rebuildIntellisense();
    }

    /**
     * The user has tried to close the GUI without saving by hitting the Esc key
     */
    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean charTyped(
            char pCodePoint,
            int pModifiers
    ) {
        if (!suggestedActions.isEmpty() && suggestedActions.isActive() && pCodePoint == '\\') {
            IntellisenseAction action = suggestedActions.getSelected();
            assert action != null;
            ManipulationResult result = action.perform(
                    new IntellisenseContext(
                            new ProgramBuilder(textarea.getValue()).build(),
                            textarea.getCursorPosition(),
                            textarea.getSelectionCursorPosition(),
                            openContext.labelPositionHolder(),
                            SFMConfig.client.intellisenseLevel));
            double scrollAmount = textarea.getScrollAmount();
            textarea.setValue(result.content());
            textarea.setSelectionCursorPosition(result.selectionCursorPosition());
            textarea.setCursorPosition(result.cursorPosition());
            textarea.setScrollAmount(scrollAmount);
            return true;
        }
        return super.charTyped(pCodePoint, pModifiers);
    }

    @Override
    public boolean keyPressed(
            int pKeyCode,
            int pScanCode,
            int pModifiers
    ) {
        if ((pKeyCode == Keyboard.KEY_RETURN || pKeyCode == Keyboard.KEY_NUMPADENTER) && GuiScreen.isShiftKeyDown()) {
            saveAndClose();
            return true;
        }

        if (pKeyCode == Keyboard.KEY_TAB) {
            String content = textarea.getValue();
            int cursor = textarea.getCursorPosition();
            int selectionCursor = textarea.getSelectionCursorPosition();
            double scrollAmount = textarea.getScrollAmount();
            ManipulationResult result;
            if (GuiScreen.isShiftKeyDown()) {
                result = ProgramStringManipulationUtils.deindent(content, cursor, selectionCursor);
            } else {
                result = ProgramStringManipulationUtils.indent(content, cursor, selectionCursor);
            }
            textarea.setValue(result.content());
            textarea.setCursorPosition(result.cursorPosition());
            textarea.setSelectionCursorPosition(result.selectionCursorPosition());
            textarea.setScrollAmount(scrollAmount);
            return true;
        }

        if (pKeyCode == Keyboard.KEY_LCONTROL || pKeyCode == Keyboard.KEY_RCONTROL) {
            textarea.rebuild(GuiScreen.isCtrlKeyDown());
            return true;
        }

        if (pKeyCode == Keyboard.KEY_SLASH && GuiScreen.isCtrlKeyDown()) {
            String content = textarea.getValue();
            int cursor = textarea.getCursorPosition();
            int selectionCursor = textarea.getSelectionCursorPosition();
            ManipulationResult result = ProgramStringManipulationUtils.toggleComments(content, cursor, selectionCursor);
            textarea.setValue(result.content());
            textarea.setCursorPosition(result.cursorPosition());
            textarea.setSelectionCursorPosition(result.selectionCursorPosition());
            return true;
        }

        if (pKeyCode == Keyboard.KEY_SPACE && GuiScreen.isCtrlKeyDown()) {
            ProgramTokenContextActions.getContextAction(
                            textarea.getValue(),
                            textarea.getCursorPosition()
                    )
                    .ifPresent(Runnable::run);

            textarea.rebuild(false);
            return true;
        }

        if (!suggestedActions.getSortedItems().isEmpty() && suggestedActions.isActive()) {
            if (pKeyCode == Keyboard.KEY_UP) {
                suggestedActions.selectPreviousWrapping();
                return true;
            } else if (pKeyCode == Keyboard.KEY_DOWN) {
                suggestedActions.selectNextWrapping();
                return true;
            }
        } else {
            if (pKeyCode == Keyboard.KEY_UP) {
                this.textarea.seekCursorLine(-1);
                return true;
            } else if (pKeyCode == Keyboard.KEY_DOWN) {
               this.textarea.seekCursorLine(1);
               return true;
            }
        }

        if (pKeyCode == Keyboard.KEY_ESCAPE && !suggestedActions.isEmpty()) {
            suggestedActions.clear();
            return true;
        }

        if (pKeyCode == Keyboard.KEY_ESCAPE && this.shouldCloseOnEsc()) {
            this.onClose();
            return true;
        } else if (this.getFocused() != null && this.getFocused().keyPressed(pKeyCode, pScanCode, pModifiers)) {
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        // If the content is different, ask to save
        if (!openContext.initialValue().equals(textarea.getValue())) {
            GuiYesNo exitWithoutSavingConfirmScreen = getExitWithoutSavingConfirmScreen();
            SFMScreenChangeHelpers.setOrPushScreen(exitWithoutSavingConfirmScreen);

        } else {
            super.onClose();
        }
    }


    @Override
    public void onResize(
            Minecraft mc,
            int x,
            int y
    ) {
        String prev = this.textarea.getValue();
        this.setWorldAndResolution(mc, width, height);
        super.onResize(mc, x, y);
        this.textarea.setValue(prev);
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        if (this.mc != null) {
            this.drawDefaultBackground();
        }


        super.drawScreen(mx, my, partialTicks);
    }

    private static boolean shouldShowLineNumbers() {
        return SFMConfig.client.showLineNumbers;
    }

    protected <T extends GuiEventListener & Renderable> T addRenderableWidget(T pWidget) {
        this.renderables.add(pWidget);
        return this.addWidget(pWidget);
    }

    protected <T extends GuiEventListener> T addWidget(T pListener) {
        this.children.add(pListener);
        return pListener;
    }

    @Override
    public void initGui() {
//        super.initGui();
        Keyboard.enableRepeatEvents(true);
        SFMScreenRenderUtils.enableKeyRepeating();

        if (this.textarea == null) {
            this.textarea = this.addRenderableWidget(new MyMultiLineEditBox(
                    this.fontRenderer,
                    SFMTextEditScreenV1.this.width / 2 - 200,
                    SFMTextEditScreenV1.this.height / 2 - 110,
                    400,
                    200,
                    new TextComponentString(""),
                    new TextComponentString("")
            ));

            // this.addRenderableWidget(
            // new SFMButtonBuilder()
            // .setPosition(this.width / 2 - 200, this.height / 2 - 100 + 195)
            // .setSize(16, 20)
            // .setText(new TextComponentString("#"))
            // .setOnPress((button) -> {
            // int cursorPos = textarea.getCursorPosition();
            // int selectionCursorPos = textarea.getSelectionCursorPosition();
            // SFMScreenChangeHelpers.setOrPushScreen(
            // new ProgramEditorConfigScreen(
            // this,
            // SFMConfig.CLIENT_PROGRAM_EDITOR,
            // () -> {
            //// this.setInitialFocus(textarea);
            // textarea.setCursorPosition(cursorPos);
            // textarea.setSelectionCursorPosition(selectionCursorPos);
            // }
            // )
            // );
            // })
            //// .setTooltip(this, font, PROGRAM_EDIT_SCREEN_CONFIG_BUTTON_TOOLTIP)
            // .build()
            // );
            this.addRenderableWidget(
                    new SFMButtonBuilder()
                            .setPosition(this.width / 2 - 2 - 150, this.height / 2 - 100 + 195)
                            .setSize(200, 20)
                            .setText(CommonComponents.GUI_DONE)
                            .setOnPress((button) -> this.saveAndClose())
                            // .setTooltip(this, font, PROGRAM_EDIT_SCREEN_DONE_BUTTON_TOOLTIP)
                            .build()
            );
            this.addRenderableWidget(
                    new SFMButtonBuilder()
                            .setPosition(this.width / 2 - 2 + 100, this.height / 2 - 100 + 195)
                            .setSize(100, 20)
                            .setText(CommonComponents.GUI_CANCEL)
                            .setOnPress((button) -> this.onClose())
                            .build());

            this.suggestedActions = this.addRenderableWidget(new PickList<>(
                    this.fontRenderer,
                     0,
                    0,
                    180,
                    this.fontRenderer.FONT_HEIGHT * 6,
                    LocalizationKeys.INTELLISENSE_PICK_LIST_GUI_TITLE.getComponent(),
                    new ArrayList<>()
            ));
            this.suggestedActions.setActive(false);
            textarea.setValue(openContext.initialValue());
            this.textarea.setCursorPosition(0);
            scrollToTop();
            // this.setInitialFocus(textarea);
        }
        this.setFocused(this.textarea);
        this.textarea.setFocused(true);
    }

    protected @NotNull GuiYesNo getExitWithoutSavingConfirmScreen() {
        var screen = new SFMConfirmationScreen(
                this::closeWithoutSaving,
                LocalizationKeys.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_TITLE.getComponent(),
                LocalizationKeys.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_MESSAGE.getComponent(),
                LocalizationKeys.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_YES_BUTTON.getComponent(),
                LocalizationKeys.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_NO_BUTTON.getComponent(),
                20);
        return screen;
    }

    protected class MyMultiLineEditBox extends MultiLineEditBox {
        private List<Integer> displayedLineStartOffsets = new ArrayList<>();
        private @Nullable ProgramBuildResult cachedBuildResult;
        private String cachedBuildProgram = "";
        private boolean scrollbarDragActive;
        private boolean scrollingEnabled = true;
        protected int lastIntellisenseTick;

        public MyMultiLineEditBox(
                FontRenderer pFont,
                int pX,
                int pY,
                int pWidth,
                int pHeight,
                ITextComponent pPlaceholder,
                ITextComponent pMessage
        ) {

            super(
                    pFont,
                    pX,
                    pY,
                    pWidth,
                    pHeight,
                    pPlaceholder,
                    pMessage
            );
            this.textField.setValueListener(this::onValueOrCursorChanged);
            this.textField.setCursorListener(() -> this.onValueOrCursorChanged(this.textField.value()));
            this.rebuild(false);
        }


        public void scrollToTop() {
            this.setScrollAmount(0);
        }

        public int getCursorPosition() {
            return this.textField.cursor;
        }

        public void setCursorPosition(int cursor) {
            this.textField.seekCursor(Whence.ABSOLUTE, cursor);
        }

        @Override
        public int getScrollBarHeight() {
            // Fix #307: divide by zero exception in AbstractScrollWidget.mouseDragged
            int rtn = super.getScrollBarHeight();
            if (rtn == this.height) {
                return rtn - 1;
            } else {
                return rtn;
            }
        }

        public int getLineNumberWidth() {
            if (shouldShowLineNumbers()) {
                return this.font.getStringWidth("000");
            } else {
                return 0;
            }
        }

        @MCVersionDependentBehaviour
        @Override
        public boolean mouseClicked(
                int pMouseX,
                int pMouseY,
                int pButton
        ) {

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

                    boolean shiftDown = GuiScreen.isShiftKeyDown();
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
                        && this.scrollbarVisible()
                        && pMouseX >= SFMWidgetUtils.getX(this) + this.width
                        && pMouseX <= SFMWidgetUtils.getX(this) + this.width + 8
                        && pMouseY >= SFMWidgetUtils.getY(this)
                        && pMouseY < SFMWidgetUtils.getY(this) + this.height;
                if (clickedScrollbar) {
                    this.scrollbarDragActive = true;
                }

                return super.mouseClicked(pMouseX, pMouseY, pButton);
            } catch (Exception e) {
                SFM.LOGGER.error("Error in SFMTextEditScreenV1.MyMultiLineEditBox.mouseClicked", e);
                return false;
            }
        }

        @Override
        public int getInnerHeight() {
            // parent method uses this.textField.getLineCount() which is split for text wrapping
            // we don't use the wrapped text, so we need to calculate the height ourselves to avoid overshooting
            return this.font.FONT_HEIGHT * (content.size() + 2);
        }


        @Override
        public boolean mouseDragged(
                int mx,
                int my,
                int button,
                int dx,
                int dy
        ) {
            // IMPORTANT: give the scrollbar drag priority.
            // If the drag started on the scrollbar, AbstractScrollWidget will
            // consume this, and we should not start a text selection.
            if (this.scrollbarDragActive && super.mouseDragged(mx, my, button, dx, dy)) {
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
                int mx,
                int my,
                int button
        ) {

            if (button == 0) {
                // Stop active selection on mouse up
                this.textField.setSelecting(false);
                this.scrollbarDragActive = false;
            }
            return super.mouseReleased(mx, my, button);
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
        protected void setScrollAmount(double pScrollAmount) {

            if (!scrollingEnabled) return;
            super.setScrollAmount(pScrollAmount);
        }

        private void seekCursorFromPoint(
                double mx,
                double my
        ) {
            suggestedActions.setActive(false);
            int lineCount = content.size();
            double innerX = mx - (
                    SFMWidgetUtils.getX(this)
                    + this.innerPadding()
                    + SFMTextEditorUtils.getLineNumberWidth(this.font, lineCount)
            );
            double innerY = my - (SFMWidgetUtils.getY(this) + this.innerPadding()) + this.scrollAmount();
            int lineIndex = Mth.clamp(
                    (int) Math.floor(innerY / Math.max(1, this.font.FONT_HEIGHT)),
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
            String plainLine = content.get(clampedLine).getUnformattedText();
            int clampedX = (int) Math.max(0, innerX);
            String trimmed = this.font.trimStringToWidth(plainLine, clampedX);
            int cursorOffsetInLine = trimmed.length();
            int widthBeforeCursor = this.font.getStringWidth(trimmed);
            if (cursorOffsetInLine < plainLine.length()) {
                int nextGlyphWidth = this.font.getStringWidth(plainLine.substring(cursorOffsetInLine, cursorOffsetInLine + 1));
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
        protected int getMaxScrollAmount() {

            return Math.max(1, super.getMaxScrollAmount()); // Fix #307: divide by zero exception
        }

        @Override
        public boolean charTyped(char pCodePoint, int pModifiers) {
            boolean success = super.charTyped(pCodePoint, pModifiers);
            if (success) {
                suggestedActions.setActive(true);
            }
            return success;
        }

        private void onValueOrCursorChanged(@Nullable String programString) {

            int cursorPosition = getCursorPosition();

            // Build the program only when text changed; reuse parse on cursor-only
            // moves
            ProgramBuildResult buildResult;
            if ((programString == null || programString.equals(cachedBuildProgram)) && cachedBuildResult != null) {
                buildResult = cachedBuildResult;
            } else {

                buildResult = new ProgramBuilder(programString).build();
                cachedBuildProgram = programString;
                cachedBuildResult = buildResult;
            }

            if (SFMTextEditScreenV1.this.suggestedActions.isEmpty() || Minecraft.getMinecraft().player.ticksExisted - lastIntellisenseTick > 5) {
                // Update the intellisense picklist
                IntellisenseContext intellisenseContext = new IntellisenseContext(
                        buildResult,
                        cursorPosition,
                        getSelectionCursorPosition(),
                        openContext.labelPositionHolder(),
                        SFMConfig.client.intellisenseLevel
                );
                List<IntellisenseAction> suggestions = SFMLIntellisense.getSuggestions(intellisenseContext);
                SFMTextEditScreenV1.this.suggestedActions.setItems(suggestions);
                lastIntellisenseTick =  Minecraft.getMinecraft().player.ticksExisted;
            }
            // Update the intellisense picklist query used to sort the suggestions
            String cursorWord = buildResult.getWordAtCursorPosition(cursorPosition);
            SFMTextEditScreenV1.this.suggestedActions.setQuery(cursorWord);

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
                suggestedActions.updateList();
                String suggestionsDisplay = suggestedActions.getItems()
                        .stream()
                        .map(PickListItem::getComponent)
                        .map(ITextComponent::getFormattedText)
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
//            displayedLineStartOffsets = StreamSupport.stream(this.textField.iterateLines().spliterator(), false).map((a) -> a.beginIndex()).collect(Collectors.toList());
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
        protected void renderContents(
                int mx,
                int my,
                float partialTicks
        ) {
            // rebuild the program if necessary
            if (!lastProgram.equals(this.textField.value())) {
                rebuild(GuiScreen.isCtrlKeyDown());
            }

            final List<ITextComponent> lines = content;
            if (lines.isEmpty()) {
                return;
            }

            final boolean isCursorFrame = this.isFocused() && Minecraft.getMinecraft().player.ticksExisted % 20 >= 10;
            final int cursorIndex = textField.cursor();

            final int lineHeight = Math.max(1, this.font.FONT_HEIGHT);
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

            final MultilineTextField.StringView selectedRange = this.textField.getSelected();
            final int selectionStart = selectedRange.beginIndex();
            final int selectionEnd = selectedRange.endIndex();

            // Collect selection highlights rects and draw them after the text
            List<int[]> highlightRects = new ArrayList<>();

            for (int line = viewLineIndexStart; line < viewLineIndexEnd; ++line) {
                var componentColoured = lines.get(line);
                String plainLine = componentColoured.getUnformattedText();
                int lineLength = plainLine.length();

                boolean cursorOnThisLine =
                        cursorIndex >= charCountAccum
                        && cursorIndex <= charCountAccum + lineLength;

                if (SFMTextEditorUtils.shouldShowLineNumbers()) {
                    // Draw line number
                    String lineNumber = String.valueOf(line + 1);
                    SFMFontUtils.drawInBatch(
                            lineNumber,
                            this.font,
                            lineX - 2 - this.font.getStringWidth(lineNumber),
                            lineY,
                            true,
                            false
                    );
                }

                if (cursorOnThisLine) {
                    isCursorAtEndOfLine = cursorIndex == charCountAccum + lineLength;
                    cursorY = lineY;
                    int relativeCursorIndex = cursorIndex - charCountAccum;
                    int drawnWidthBeforeCursor = this.font.getStringWidth(plainLine.substring(0, relativeCursorIndex));
                    cursorX = lineX + drawnWidthBeforeCursor;
                    // draw text before cursor
                    SFMFontUtils.drawInBatch(
                            SFMComponentUtils.substring(componentColoured, 0, relativeCursorIndex),
                            font,
                            lineX,
                            lineY,
                            true,
                            false
                    );
                    SFMTextEditScreenV1.this.suggestedActions.setXY(cursorX, (int)(cursorY - scroll + lineHeight));
                    // draw text after cursor
                    SFMFontUtils.drawInBatch(
                            SFMComponentUtils.substring(componentColoured, relativeCursorIndex, lineLength),
                            font,
                            cursorX,
                            lineY,
                            true,
                            false
                    );
                    drewCursorGlyph = true;
                } else {
                    SFMFontUtils.drawInBatch(
                            componentColoured,
                            font,
                            lineX,
                            lineY,
                            true,
                            false
                    );
                }

                // Check if the selection is within the current line
                if (selectionStart <= charCountAccum + lineLength && selectionEnd > charCountAccum) {
                    int lineSelectionStart = Math.max(selectionStart - charCountAccum, 0);
                    int lineSelectionEnd = Math.min(selectionEnd - charCountAccum, lineLength);

                    int highlightStartX = this.font.getStringWidth(plainLine.substring(0, lineSelectionStart));
                    int highlightEndX = this.font.getStringWidth(plainLine.substring(0, lineSelectionEnd));

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

            // Draw selection highlights after text
            for (int[] r : highlightRects) {
                SFMScreenRenderUtils.renderHighlight(
                        r[0],
                        r[1],
                        r[2],
                        r[3]
                );
            }

            if (isCursorFrame && drewCursorGlyph) {
                if (isCursorAtEndOfLine) {
                    SFMFontUtils.draw(
                            this.font,
                            "_",
                            cursorX,
                            cursorY,
                            -1,
                            true
                    );
                } else {
                    Gui.drawRect(cursorX - 1, cursorY - 1, cursorX, cursorY + 1 + 9, -1);
                }
            }
        }

        public void seekCursorLine(int offset) {
            final int cursorIndex = textField.cursor();
            int currentLine;
            for (currentLine = 0; currentLine < content.size() && currentLine < this.displayedLineStartOffsets.size() - 1 && this.displayedLineStartOffsets.get(currentLine + 1) <= cursorIndex; currentLine++) {}

            int currentX = this.font.getStringWidth(content.get(currentLine).getUnformattedText().substring(0,cursorIndex - this.displayedLineStartOffsets.get(currentLine)));

            if (offset < 0 && currentX < -offset) {
                this.textField.seekCursor(Whence.ABSOLUTE, 0);
            }

            int newLine = Math.min(content.size() - 1, currentLine + offset);

            int newLineIndex = this.pointToCursor(currentX, newLine);
            this.textField.seekCursor(Whence.ABSOLUTE, newLineIndex);

            scrollToCursor();
        }
    }
}
