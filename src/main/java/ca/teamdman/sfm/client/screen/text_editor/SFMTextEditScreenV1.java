package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.client.ProgramTokenContextActions;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.SFMScreenRenderUtils;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_EDIT_SCREEN_CONFIG_BUTTON_TOOLTIP;
import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_EDIT_SCREEN_DONE_BUTTON_TOOLTIP;

@SuppressWarnings("NotNullFieldNotInitialized")
public class SFMTextEditScreenV1 extends GuiScreen implements ISFMTextEditScreen {
    private final ISFMTextEditScreenOpenContext openContext;
//    protected MyMultiLineEditBox textarea;
    protected String lastProgram = "";
    protected List<ITextComponent> lastProgramWithSyntaxHighlighting = new ArrayList<>();
//    protected PickList<IntellisenseAction> suggestedActions;
    private boolean scrolledOnFirstInit = false;

    public SFMTextEditScreenV1(
            ISFMTextEditScreenOpenContext openContext
    ) {
        super();
        // LocalizationKeys.TEXT_EDIT_SCREEN_TITLE.getComponent()
        this.openContext = openContext;
    }

//    public static ITextComponent substring(
//            ITextComponent component,
//            int start,
//            int end
//    ) {
//        var rtn = Component.empty();
//        AtomicInteger seen = new AtomicInteger(0);
//        component.visit(
//                (style, content) -> {
//                    int contentStart = Math.max(start - seen.get(), 0);
//                    int contentEnd = Math.min(end - seen.get(), content.length());
//
//                    if (contentStart < contentEnd) {
//                        rtn.append(Component.literal(content.substring(contentStart, contentEnd)).withStyle(style));
//                    }
//                    seen.addAndGet(content.length());
//                    return Optional.empty();
//                }, Style.EMPTY
//        );
//        return rtn;
//    }
//
    public void scrollToTop() {
//        this.textarea.scrollToTop();
    }
//
//    @Override
//    public boolean isPauseScreen() {
//        return false;
//    }
//
//    /**
//     * The user has indicated to save by hitting Shift+Enter or by pressing the Done button
//     */
//    public void saveAndClose() {
//        openContext.onSaveAndClose(textarea.getValue());
//    }
//
//    /**
//     * The user has tried to close the GUI without saving by hitting the Esc key
//     */
//    @Override
//    public void onClose() {
//        openContext.onTryClose(textarea.getValue(), SFMScreenChangeHelpers::popScreen);
//    }
//
    @Override
    public ISFMTextEditScreenOpenContext openContext() {
        return openContext;
    }

//    @Override
//    public void onPreferenceChanged() {
//        textarea.rebuildIntellisense();
//    }
//
//    @Override
//    public boolean keyReleased(
//            int pKeyCode,
//            int pScanCode,
//            int pModifiers
//    ) {
//        if (pKeyCode == GLFW.GLFW_KEY_LEFT_CONTROL || pKeyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
//            // if control released => update syntax highlighting
//            textarea.rebuild(Screen.hasControlDown());
//            return true;
//        }
//        return false;
//    }
//
//    @Override
//    public boolean charTyped(
//            char pCodePoint,
//            int pModifiers
//    ) {
//        if (Screen.hasControlDown() && pCodePoint == ' ') {
//            return true;
//        }
//        if (!suggestedActions.isEmpty() && pCodePoint == '\\') {
//            // prevent intellisense-accept hotkey from being typed
//            return true;
//        }
//        return super.charTyped(pCodePoint, pModifiers);
//    }
//
//    @Override
//    public boolean keyPressed(
//            int pKeyCode,
//            int pScanCode,
//            int pModifiers
//    ) {
//        // TODO: add separate keybindings for
//        // context action - hold to arm
//        // context action - execute
//        // indent - increase
//        // indent - decrease
//        // save and close - hold to arm
//        // save and close - execute
//        if ((pKeyCode == GLFW.GLFW_KEY_ENTER || pKeyCode == GLFW.GLFW_KEY_KP_ENTER) && Screen.hasShiftDown()) {
//            saveAndClose();
//            return true;
//        }
//        if (pKeyCode == GLFW.GLFW_KEY_TAB) {
//            // if tab pressed with no selection and not holding shift => insert 4 spaces
//            // if tab pressed with no selection and holding shift => de-indent current line
//            // if tab pressed with selection and not holding shift => de-indent lines containing selection 4 spaces
//            // if tab pressed with selection and holding shift => indent lines containing selection 4 spaces
//            String content = textarea.getValue();
//            int cursor = textarea.getCursorPosition();
//            int selectionCursor = textarea.getSelectionCursorPosition();
//            double scrollAmount = textarea.getScrollAmount();
//            ManipulationResult result;
//            if (Screen.hasShiftDown()) { // de-indent
//                result = ProgramStringManipulationUtils.deindent(content, cursor, selectionCursor);
//            } else { // indent
//                result = ProgramStringManipulationUtils.indent(content, cursor, selectionCursor);
//            }
//            textarea.setValue(result.content());
//            textarea.setCursorPosition(result.cursorPosition());
//            textarea.setSelectionCursorPosition(result.selectionCursorPosition());
//            textarea.setScrollAmount(scrollAmount);
//            return true;
//        }
//        if (pKeyCode == GLFW.GLFW_KEY_BACKSLASH && !suggestedActions.isEmpty()) {
//            IntellisenseAction action = suggestedActions.getSelected();
//            assert action != null;
//            ManipulationResult result = action.perform(
//                    new IntellisenseContext(
//                            ProgramBuilder.build(textarea.getValue()),
//                            textarea.getCursorPosition(),
//                            textarea.getSelectionCursorPosition(),
//                            openContext.labelPositionHolder(),
//                            SFMConfig.client.intellisenseLevel
//                    )
//            );
//            double scrollAmount = textarea.getScrollAmount();
//            textarea.setValue(result.content());
//            textarea.setSelectionCursorPosition(result.selectionCursorPosition());
//            textarea.setCursorPosition(result.cursorPosition());
//            textarea.setScrollAmount(scrollAmount);
//            return true;
//        }
//        if (pKeyCode == GLFW.GLFW_KEY_LEFT_CONTROL || pKeyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
//            // if control pressed => update syntax highlighting
//            textarea.rebuild(Screen.hasControlDown());
//            return true;
//        }
//        if (pKeyCode == GLFW.GLFW_KEY_SLASH && Screen.hasControlDown()) {
//            // toggle line comments for selected lines
//            String content = textarea.getValue();
//            int cursor = textarea.getCursorPosition();
//            int selectionCursor = textarea.getSelectionCursorPosition();
//            ManipulationResult result = ProgramStringManipulationUtils.toggleComments(content, cursor, selectionCursor);
//            textarea.setValue(result.content());
//            textarea.setCursorPosition(result.cursorPosition());
//            textarea.setSelectionCursorPosition(result.selectionCursorPosition());
//            return true;
//        }
//        if (pKeyCode == GLFW.GLFW_KEY_SPACE && Screen.hasControlDown()) {
//            ProgramTokenContextActions.getContextAction(
//                            textarea.getValue(),
//                            textarea.getCursorPosition()
//                    )
//                    .ifPresent(Runnable::run);
//
//            // disable the underline since it doesn't refresh when the context action closes
//            textarea.rebuild(false);
//            return true;
//        }
//        if (
//                (
//                        pKeyCode == GLFW.GLFW_KEY_UP
//                        || pKeyCode == GLFW.GLFW_KEY_DOWN
//                )
//                && !suggestedActions.getItems().isEmpty()
//        ) {
//            if (pKeyCode == GLFW.GLFW_KEY_UP) {
//                suggestedActions.selectPreviousWrapping();
//            } else {
//                suggestedActions.selectNextWrapping();
//            }
//            return true;
//        }
//        if (pKeyCode == GLFW.GLFW_KEY_ESCAPE && !suggestedActions.isEmpty()) {
//            suggestedActions.clear();
//            return true;
//        }
//        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
//    }
//
//    @Override
//    public void resize(
//            Minecraft mc,
//            int x,
//            int y
//    ) {
//        var prev = this.textarea.getValue();
//        init(mc, x, y);
//        super.resize(mc, x, y);
//        this.textarea.setValue(prev);
//    }
//
//    @Override
//    public void render(
//            PoseStack poseStack,
//            int mx,
//            int my,
//            float partialTicks
//    ) {
//        this.renderBackground(poseStack);
//        super.render(poseStack, mx, my, partialTicks);
//        this.renderTooltip(poseStack, mx, my);
//    }
//
//    private static boolean shouldShowLineNumbers() {
//        return SFMConfig.client.showLineNumbers;
//    }
//
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
//
//    @MCVersionDependentBehaviour
//    private void drawChildTooltips(
//            PoseStack pose,
//            int mx,
//            int my
//    ) {
//        // 1.19.2: manually render button tooltips
////        this.renderables
////                .stream()
////                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
////                .map(SFMExtendedButtonWithTooltip.class::cast)
////                .forEach(x -> x.renderToolTip(pose, mx, my));
//    }
//
//    @Override
//    protected void init() {
//        super.init();
//        SFMScreenRenderUtils.enableKeyRepeating();
//
//        this.textarea = this.addRenderableWidget(new MyMultiLineEditBox());
//
//        this.suggestedActions = this.addRenderableWidget(new PickList<>(
//                this.fontRenderer,
//                0,
//                0,
//                180,
//                this.fontRenderer.lineHeight * 6,
//                LocalizationKeys.INTELLISENSE_PICK_LIST_GUI_TITLE.getComponent(),
//                new ArrayList<>()
//        ));
//
//
//        this.addRenderableWidget(
//                new SFMButtonBuilder()
//                        .setPosition(this.width / 2 - 2 - 150, this.height / 2 - 100 + 195)
//                        .setSize(200, 20)
//                        .setText(CommonComponents.GUI_DONE)
//                        .setOnPress((button) -> this.saveAndClose())
//                        .setTooltip(this, font, PROGRAM_EDIT_SCREEN_DONE_BUTTON_TOOLTIP)
//                        .build()
//        );
//        this.addRenderableWidget(
//                new SFMButtonBuilder()
//                        .setPosition(this.width / 2 - 2 + 100, this.height / 2 - 100 + 195)
//                        .setSize(100, 20)
//                        .setText(CommonComponents.GUI_CANCEL)
//                        .setOnPress((button) -> this.onClose())
//                        .build()
//        );
//
//        textarea.setValue(openContext.initialValue());
//
//        // Scroll to top on first init to match previous behavior without needing external calls
//        if (!scrolledOnFirstInit) {
//            scrollToTop();
//            scrolledOnFirstInit = true;
//        }
//
//        this.setInitialFocus(textarea);
//    }
//
//    protected class MyMultiLineEditBox extends MultiLineEditBox {
//        public MyMultiLineEditBox() {
//            super(
//                    SFMTextEditScreenV1.this.font,
//                    SFMTextEditScreenV1.this.width / 2 - 200,
//                    SFMTextEditScreenV1.this.height / 2 - 110,
//                    400,
//                    200,
//                    Component.literal(""),
//                    Component.literal("")
//            );
//            this.textField.setValueListener(this::onValueOrCursorChanged);
//            this.textField.setCursorListener(() -> this.onValueOrCursorChanged(this.textField.value()));
//            this.rebuild(false);
//        }
//
//        public void scrollToTop() {
//            this.setScrollAmount(0);
//        }
//
//        public int getCursorPosition() {
//            return this.textField.cursor;
//        }
//
//        public void setCursorPosition(int cursor) {
//            this.textField.seekCursor(Whence.ABSOLUTE, cursor);
//        }
//
//        public int getLineNumberWidth() {
//            if (shouldShowLineNumbers()) {
//                return this.font.width("000");
//            } else {
//                return 0;
//            }
//        }
//
//        @Override
//        public int getScrollBarHeight() {
//            // Fix #307: divide by zero exception in AbstractScrollWidget.mouseDragged
//            int rtn = super.getScrollBarHeight();
//            if (rtn == this.height) {
//                return rtn - 1;
//            } else {
//                return rtn;
//            }
//        }
//
//        @MCVersionDependentBehaviour
//        @Override
//        public boolean mouseClicked(
//                double pMouseX,
//                double pMouseY,
//                int pButton
//        ) {
//            try {
//                // Accommodate line numbers
//                if (pMouseX >= this.getX() + 1 && pMouseX <= this.getX() + this.width - 1) {
//                    pMouseX -= getLineNumberWidth();
//                }
//
//                // we need to override the default behaviour because Mojang broke it
//                // if it's not scrolling, it should return false for cursor click movement
//                boolean rtn;
//                if (!this.visible) {
//                    rtn = false;
//                } else {
//                    //noinspection unused
//                    boolean flag = this.withinContentAreaPoint(pMouseX, pMouseY);
//                    boolean flag1 = this.scrollbarVisible()
//                                    && pMouseX >= (double) (this.getX() + this.width)
//                                    && pMouseX <= (double) (this.getX() + this.width + 8)
//                                    && pMouseY >= (double) this.getY()
//                                    && pMouseY < (double) (this.getY() + this.height);
//                    if (flag1 && pButton == 0) {
//                        this.scrolling = true;
//                        rtn = true;
//                    } else {
//                        //1.19.4 behaviour:
//                        //rtn=flag || flag1;
//                        // instead, we want to return false if we're not scrolling
//                        // (like how it was in 1.19.2)
//                        // https://bugs.mojang.com/browse/MC-262754
//                        rtn = false;
//                    }
//                }
//
//                if (rtn) {
//                    return true;
//                } else if (this.withinContentAreaPoint(pMouseX, pMouseY) && pButton == 0) {
//                    this.textField.setSelecting(Screen.hasShiftDown());
//                    this.seekCursorScreen(pMouseX, pMouseY);
//                    return true;
//                } else {
//                    return false;
//                }
//            } catch (Exception e) {
//                SFM.LOGGER.error("Error in SFMTextEditScreenV1.MyMultiLineEditBox.mouseClicked", e);
//                return false;
//            }
//        }
//
//        @Override
//        public int getInnerHeight() {
//            // parent method uses this.textField.getLineCount() which is split for text wrapping
//            // we don't use the wrapped text, so we need to calculate the height ourselves to avoid overshooting
//            return this.font.lineHeight * (lastProgramWithSyntaxHighlighting.size() + 2);
//        }
//
//        @Override
//        public boolean mouseDragged(
//                double mx,
//                double my,
//                int button,
//                double dx,
//                double dy
//        ) {
//            // if mouse in bounds, translate to accommodate line numbers
//            int thisX = SFMScreenRenderUtils.getX(this);
//            if (mx >= thisX + 1 && mx <= thisX + this.width - 1) {
//                mx -= getLineNumberWidth();
//            }
//
//            return super.mouseDragged(mx, my, button, dx, dy);
//        }
//
//        public int getSelectionCursorPosition() {
//            return this.textField.selectCursor;
//        }
//
//        public void setSelectionCursorPosition(int cursor) {
//            this.textField.selectCursor = cursor;
//        }
//
//        public double getScrollAmount() {
//            return this.scrollAmount();
//        }
//
//        @Override
//        public void setScrollAmount(double d) {
//            super.setScrollAmount(d);
//        }
//
//        @Override
//        protected int getMaxScrollAmount() {
//            return Math.max(1, super.getMaxScrollAmount()); // Fix #307: divide by zero exception
//        }
//
//        private void onValueOrCursorChanged(String programString) {
//            int cursorPosition = getCursorPosition();
//
//            // Build the program
//            ProgramBuildResult buildResult = ProgramBuilder.build(programString);
//
//            // Update the intellisense picklist
//            IntellisenseContext intellisenseContext = new IntellisenseContext(
//                    buildResult,
//                    cursorPosition,
//                    getSelectionCursorPosition(),
//                    openContext.labelPositionHolder(),
//                    SFMConfig.client.intellisenseLevel
//            );
//            List<IntellisenseAction> suggestions = SFMLIntellisense.getSuggestions(intellisenseContext);
//            SFMTextEditScreenV1.this.suggestedActions.setItems(suggestions);
//
//
//            // Update the intellisense picklist query used to sort the suggestions
//            String cursorWord = buildResult.getWordAtCursorPosition(cursorPosition);
//            SFMTextEditScreenV1.this.suggestedActions.setQuery(Component.literal(cursorWord));
//
//            boolean shouldPrint = false;
//            //noinspection ConstantValue
//            if (shouldPrint) {
//                String cursorPositionDisplay = SFMDisplayUtils.getCursorPositionDisplay(programString, cursorPosition);
//                String cursorTokenDisplay = SFMDisplayUtils.getCursorTokenDisplay(buildResult, cursorPosition);
//                String tokenHierarchyDisplay;
//                @Nullable Program program = buildResult.program();
//                if (program == null) {
//                    tokenHierarchyDisplay = "<INVALID PROGRAM>";
//                } else {
//                    tokenHierarchyDisplay = SFMDisplayUtils.getTokenHierarchyDisplay(program, cursorPosition);
//                }
//
//                String suggestionsDisplay = suggestedActions.getItems()
//                        .stream()
//                        .map(PickListItem::getComponent)
//                        .map(Component::getString)
//                        .collect(Collectors.joining(", "));
//
//                SFM.LOGGER.info(
//                        "PROGRAM OR CURSOR CHANGE! {}   {}   {}  |||  {} ||| {}",
//                        cursorPositionDisplay,
//                        cursorTokenDisplay,
//                        tokenHierarchyDisplay,
//                        cursorWord,
//                        suggestionsDisplay
//                );
//            }
//        }
//
//        private void rebuildIntellisense() {
//            onValueOrCursorChanged(getValue());
//        }
//
//        /**
//         * Rebuilds the syntax-highlighted program text.
//         * This runs more frequently than when the value is changed.
//         *
//         * @param showContextActionHints Should underline words that have context actions
//         */
//        private void rebuild(boolean showContextActionHints) {
//            lastProgram = this.textField.value();
//            lastProgramWithSyntaxHighlighting = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(
//                    lastProgram,
//                    showContextActionHints
//            );
//        }
//
//        @Override
//        protected void renderContents(
//                PoseStack poseStack,
//                int mx,
//                int my,
//                float partialTicks
//        ) {
//            Matrix4f matrix4f = poseStack.last().pose();
//            if (!lastProgram.equals(this.textField.value())) {
//                rebuild(Screen.hasControlDown());
//            }
//            List<MutableComponent> lines = lastProgramWithSyntaxHighlighting;
//            boolean isCursorVisible = this.isFocused() && this.frame / 6 % 2 == 0;
//            boolean isCursorAtEndOfLine = false;
//            int cursorIndex = textField.cursor();
//            int lineX = SFMScreenRenderUtils.getX(this) + this.innerPadding() + getLineNumberWidth();
//            int lineY = SFMScreenRenderUtils.getY(this) + this.innerPadding();
//            int charCount = 0;
//            int cursorX = 0;
//            int cursorY = 0;
//            MultilineTextField.StringView selectedRange = this.textField.getSelected();
//            int selectionStart = selectedRange.beginIndex();
//            int selectionEnd = selectedRange.endIndex();
//
//            for (int line = 0; line < lines.size(); ++line) {
//                var componentColoured = lines.get(line);
//                int lineLength = componentColoured.getString().length();
//                int lineHeight = this.font.lineHeight;
//                boolean cursorOnThisLine = isCursorVisible
//                                           && cursorIndex >= charCount
//                                           && cursorIndex <= charCount + lineLength;
//                var buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
//
//
//                if (shouldShowLineNumbers()) {
//                    // Draw line number
//                    String lineNumber = String.valueOf(line + 1);
//                    SFMFontUtils.drawInBatch(
//                            lineNumber,
//                            this.font,
//                            lineX - 2 - this.font.width(lineNumber),
//                            lineY,
//                            true,
//                            false
//                    );
//                }
//
//                if (cursorOnThisLine) {
//                    isCursorAtEndOfLine = cursorIndex == charCount + lineLength;
//                    cursorY = lineY;
//                    // draw text before cursor
//                    cursorX = SFMFontUtils.drawInBatch(
//                            substring(componentColoured, 0, cursorIndex - charCount),
//                            font,
//                            lineX,
//                            lineY,
//                            true,
//                            false
//                    ) - 1;
//                    SFMTextEditScreenV1.this.suggestedActions.setXY(cursorX + 10, cursorY);
//                    // draw text after cursor
//                    SFMFontUtils.drawInBatch(
//                            substring(componentColoured, cursorIndex - charCount, lineLength),
//                            font,
//                            cursorX,
//                            lineY,
//                            true,
//                            false
//                    );
//                } else {
//                    SFMFontUtils.drawInBatch(
//                            componentColoured,
//                            font,
//                            lineX,
//                            lineY,
//                            true,
//                            false
//                    );
//                }
//                buffer.endBatch();
//
//                // Check if the selection is within the current line
//                if (selectionStart <= charCount + lineLength && selectionEnd > charCount) {
//                    int lineSelectionStart = Math.max(selectionStart - charCount, 0);
//                    int lineSelectionEnd = Math.min(selectionEnd - charCount, lineLength);
//
//                    int highlightStartX = this.font.width(substring(componentColoured, 0, lineSelectionStart));
//                    int highlightEndX = this.font.width(substring(componentColoured, 0, lineSelectionEnd));
//
//                    SFMScreenRenderUtils.renderHighlight(
//                            poseStack,
//                            lineX + highlightStartX,
//                            lineY,
//                            lineX + highlightEndX,
//                            lineY + lineHeight
//                    );
//                }
//
//                lineY += lineHeight;
//                charCount += lineLength + 1;
//            }
//
//            if (isCursorAtEndOfLine) {
//                SFMFontUtils.draw(poseStack, this.font, "_", cursorX, cursorY, -1, true);
//            } else {
//                GuiComponent.fill(poseStack, cursorX, cursorY - 1, cursorX + 1, cursorY + 1 + 9, -1);
//            }
//        }
//    }
}
