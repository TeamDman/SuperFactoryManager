package ca.teamdman.sfm.client.screen.text_editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import net.minecraft.client.Minecraft;
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
    protected List<ITextComponent> lastProgramWithSyntaxHighlighting = new ArrayList<>();
    protected PickList<IntellisenseAction> suggestedActions;
    private boolean scrolledOnFirstInit = false;

    public SFMTextEditScreenV1(
            ISFMTextEditScreenOpenContext openContext
    ) {
        super();
        // LocalizationKeys.TEXT_EDIT_SCREEN_TITLE.getComponent()
        this.openContext = openContext;
    }


    public static String substring(
            ITextComponent component,
            int start,
            int end
    ) {
        ITextComponent rtn = new TextComponentString("");
        AtomicInteger seen = new AtomicInteger(0);
        for (ITextComponent sibling : component.getSiblings()) {
            String content = sibling.getUnformattedText();
            int contentStart = Math.max(start - seen.get(), 0);
            int contentEnd = Math.min(end - seen.get(), content.length());

            if (contentStart < contentEnd) {
                rtn.appendSibling(new TextComponentString(content.substring(contentStart, contentEnd)).setStyle(sibling.getStyle()));
            }
            seen.addAndGet(content.length());
        }
        return rtn.getFormattedText();
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
        SFMScreenChangeHelpers.popScreen();
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
        if (!suggestedActions.isEmpty() && pCodePoint == '\\') {
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

        if ((pKeyCode == Keyboard.KEY_UP || pKeyCode == Keyboard.KEY_DOWN) && !suggestedActions.getItems().isEmpty()) {
            if (pKeyCode == Keyboard.KEY_UP) {
                suggestedActions.selectPreviousWrapping();
            } else {
                suggestedActions.selectNextWrapping();
            }
            return true;
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
//        else {
//            FocusNavigationEvent focusnavigationevent = (FocusNavigationEvent)(switch (pKeyCode) {
//                case Keyboard.KEY_TAB -> this.createTabEvent();
//                default -> null;
//                case Keyboard.KEY_RIGHT -> this.createArrowEvent(ScreenDirection.RIGHT);
//                case Keyboard.KEY_LEFT -> this.createArrowEvent(ScreenDirection.LEFT);
//                case Keyboard.KEY_DOWN -> this.createArrowEvent(ScreenDirection.DOWN);
//                case Keyboard.KEY_UP -> this.createArrowEvent(ScreenDirection.UP);
//            });
//            if (focusnavigationevent != null) {
//                ComponentPath componentpath = super.nextFocusPath(focusnavigationevent);
//                if (componentpath == null && focusnavigationevent instanceof FocusNavigationEvent.TabNavigation) {
//                    this.clearFocus();
//                    componentpath = super.nextFocusPath(focusnavigationevent);
//                }
//
//                if (componentpath != null) {
//                    this.changeFocus(componentpath);
//                }
//            }
//            return false;
//        }
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

//        this.renderBackground(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        for (Renderable renderable : this.renderables) {
            renderable.render(mx, my, partialTicks);
        }

        super.drawScreen(mx, my, partialTicks);
    }

    private static boolean shouldShowLineNumbers() {
        return SFMConfig.client.showLineNumbers;
    }

//    protected void renderTooltip(
//            PoseStack pose,
//            int mx,
//            int my
//    ) {
//        if (Minecraft.getInstance().screen != this) {
//            // this should fix the annoying Ctrl+E popup when editing
//            this.renderables
//                    .stream()
//                    .filter(AbstractWidget.class::isInstance)
//                    .map(AbstractWidget.class::cast)
//                    .forEach(w -> w.setFocused(false));
//            return;
//        }
//        drawChildTooltips(pose, mx, my);
//    }

//    private void drawChildTooltips(
//            PoseStack pose,
//            int mx,
//            int my
//    ) {

    /// / 1.19.2: manually render button tooltips
//        this.renderables
//                .stream()
//                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
//                .map(SFMExtendedButtonWithTooltip.class::cast)
//                .forEach(x -> x.renderToolTip(pose, mx, my));
//    }
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
            this.textarea = this.addRenderableWidget(new MyMultiLineEditBox());

            this.suggestedActions = this.addRenderableWidget(new PickList<>(
                    this.fontRenderer,
                    0,
                    0,
                    180,
                    this.fontRenderer.FONT_HEIGHT * 6,
                    LocalizationKeys.INTELLISENSE_PICK_LIST_GUI_TITLE.getComponent(),
                    new ArrayList<>()
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
            );this.addRenderableWidget(
                    new SFMButtonBuilder()
                            .setPosition(this.width / 2 - 2 + 100, this.height / 2 - 100 + 195)
                            .setSize(100, 20)
                            .setText(CommonComponents.GUI_CANCEL)
                            .setOnPress((button) -> this.onClose())
                            .build());

            textarea.setValue(openContext.initialValue());
            // this.setInitialFocus(textarea);
        }
        this.setFocused(this.textarea);
        this.textarea.setFocused(true
        );
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
        private int frame = 0;

        public MyMultiLineEditBox() {
            super(
                    SFMTextEditScreenV1.this.fontRenderer,
                    SFMTextEditScreenV1.this.width / 2 - 200,
                    SFMTextEditScreenV1.this.height / 2 - 110,
                    400,
                    200,
                    new TextComponentString(""),
                    new TextComponentString("")
            );
            this.textField.setValueListener(this::onValueOrCursorChanged);
            this.textField.setCursorListener(() -> this.onValueOrCursorChanged(this.textField.value()));
            this.setFocused(true);
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

                // Accommodate line numbers
                if (pMouseX >= this.getX() + 1 && pMouseX <= this.getX() + this.width - 1) {
                    pMouseX -= getLineNumberWidth();
                }

                // we need to override the default behaviour because Mojang broke it
                // if it's not scrolling, it should return false for cursor click movement
                boolean rtn;
                if (!this.visible) {
                    rtn = false;
                } else {
                    //noinspection unused
                    boolean flag = this.withinContentAreaPoint(pMouseX, pMouseY);
                    boolean flag1 = this.scrollbarVisible()
                            && pMouseX >= (double) (this.getX() + this.width)
                            && pMouseX <= (double) (this.getX() + this.width + 8)
                            && pMouseY >= (double) this.getY()
                            && pMouseY < (double) (this.getY() + this.height);
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
                    this.textField.setSelecting(GuiScreen.isShiftKeyDown());
                    this.seekCursorScreen(pMouseX, pMouseY);
                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                SFM.LOGGER.error("Error in SFMTextEditScreenV1.MyMultiLineEditBox.mouseClicked", 3);
                return false;
            }
        }

        @Override
        public int getInnerHeight() {
            // parent method uses this.textField.getLineCount() which is split for text wrapping
            // we don't use the wrapped text, so we need to calculate the height ourselves to avoid overshooting
            return this.font.FONT_HEIGHT * (lastProgramWithSyntaxHighlighting.size() + 2);
        }

        @Override
        public boolean mouseDragged(
                int mx,
                int my,
                int button,
                int dx,
                int dy
        ) {
            // if mouse in bounds, translate to accommodate line numbers
            int thisX = SFMWidgetUtils.getX(this);
            if (mx >= thisX + 1 && mx <= thisX + this.width - 1) {
                mx -= getLineNumberWidth();
            }
            return super.mouseDragged(mx, my, button, dx, dy);
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
        public void setScrollAmount(double d) {
            super.setScrollAmount(d);
        }

        protected List<IntellisenseAction> intellisenseCache;
        protected int lastIntellisenseTick;

        private void onValueOrCursorChanged(String programString) {
            int cursorPosition = getCursorPosition();

            // Build the program
            ProgramBuildResult buildResult = new ProgramBuilder(programString).build();

            if (this.textField.hasSelection()) {
                SFMTextEditScreenV1.this.suggestedActions.setItems(Collections.emptyList());
            }
            else if (intellisenseCache == null || Minecraft.getMinecraft().player.ticksExisted - lastIntellisenseTick > 5) {

                IntellisenseContext intellisenseContext = new IntellisenseContext(
                        buildResult,
                        cursorPosition,
                        getSelectionCursorPosition(),
                        openContext.labelPositionHolder(),
                        SFMConfig.client.intellisenseLevel
                );List<IntellisenseAction> suggestions = SFMLIntellisense.getSuggestions(intellisenseContext);
                SFMTextEditScreenV1.this.suggestedActions.setItems(suggestions);
                intellisenseCache = suggestions;
                lastIntellisenseTick = Minecraft.getMinecraft().player.ticksExisted;
            }
            // Update the intellisense picklist


            // Update the intellisense picklist query used to sort the suggestions
            String cursorWord = buildResult.getWordAtCursorPosition(cursorPosition);
            SFMTextEditScreenV1.this.suggestedActions.setQuery(new TextComponentString(cursorWord));

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
                        .map(ITextComponent::getUnformattedText)
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
         * Rebuilds the syntax-highlighted program text.
         * This runs more frequently than when the value is changed.
         *
         * @param showContextActionHints Should underline words that have context actions
         */
        private void rebuild(boolean showContextActionHints) {
            lastProgram = this.textField.value();
            lastProgramWithSyntaxHighlighting = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(
                    lastProgram,
                    showContextActionHints
            );
        }

        @Override
        protected void renderContents(int mx, int my, float partialTicks) {
            if (!lastProgram.equals(this.textField.value())) {
                rebuild(GuiScreen.isCtrlKeyDown());
            }
            List<ITextComponent> lines = lastProgramWithSyntaxHighlighting;
            boolean isCursorFrame = this.frame++ / 60 % 2 == 0;
            boolean isCursorAtEndOfLine = false;
            int cursorIndex = textField.cursor();
            int lineX = SFMWidgetUtils.getX(this) + this.innerPadding() + getLineNumberWidth();
            int lineY = SFMWidgetUtils.getY(this) + this.innerPadding();
            int charCount = 0;
            int cursorX = 0;
            int cursorY = 0;
            MultilineTextField.StringView selectedRange = this.textField.getSelected();
            int selectionStart = selectedRange.beginIndex();
            int selectionEnd = selectedRange.endIndex();

            for (int line = 0; line < lines.size(); ++line) {
                var componentColoured = lines.get(line);
                int lineLength = componentColoured.getUnformattedText().length();
                int lineHeight = this.font.FONT_HEIGHT;
                boolean cursorOnThisLine =
                        cursorIndex >= charCount &&
                                cursorIndex <= charCount + lineLength;

                if (shouldShowLineNumbers()) {
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
                    SFMTextEditScreenV1.this.suggestedActions.active = true;
                    isCursorAtEndOfLine = cursorIndex == charCount + lineLength;
                    cursorY = lineY;
                    // draw text before cursor
                    cursorX = SFMFontUtils.drawInBatch(
                            substring(componentColoured, 0, cursorIndex - charCount),
                            font,
                            lineX,
                            lineY,
                            true,
                            false
                    ) - 1;
                    SFMTextEditScreenV1.this.suggestedActions.setXY(cursorX + 10, cursorY);
                    // draw text after cursor
                    SFMFontUtils.drawInBatch(
                            substring(componentColoured, cursorIndex - charCount, lineLength),
                            font,
                            cursorX,
                            lineY,
                            true,
                            false
                    );
                } else {
                    SFMTextEditScreenV1.this.suggestedActions.active = false;
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
                if (selectionStart <= charCount + lineLength && selectionEnd > charCount) {
                    int lineSelectionStart = Math.max(selectionStart - charCount, 0);
                    int lineSelectionEnd = Math.min(selectionEnd - charCount, lineLength);

                    int highlightStartX = this.font.getStringWidth(substring(componentColoured, 0, lineSelectionStart));
                    int highlightEndX = this.font.getStringWidth(substring(componentColoured, 0, lineSelectionEnd));

                    SFMScreenRenderUtils.renderHighlight(
                            lineX + highlightStartX,
                            lineY,
                            lineX + highlightEndX,
                            lineY + lineHeight
                    );
                }

                lineY += lineHeight;
                charCount += lineLength + 1;
            }

            if (isCursorFrame) {
                if (isCursorAtEndOfLine) {
                    SFMFontUtils.draw(this.font, "_", cursorX, cursorY, -1, true);
                } else {
                    Gui.drawRect(cursorX - 1, cursorY - 1, cursorX, cursorY + 1 + 9, -1);
                }
            }
        }

    }
}
