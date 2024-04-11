package ca.teamdman.sfm.client.gui.screen;

import ca.teamdman.sfm.client.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.client.ProgramTokenContextActions;
import ca.teamdman.sfm.client.gui.EditorUtils;
import ca.teamdman.sfm.common.Constants;
import com.mojang.blaze3d.vertex.PoseStack;
import ca.teamdman.sfm.common.item.DiskItem;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static ca.teamdman.sfm.common.Constants.LocalizationKeys.PROGRAM_EDIT_SCREEN_DONE_BUTTON_TOOLTIP;

public class ProgramEditScreen extends Screen {
    private final String INITIAL_CONTENT;
    private final Consumer<String> CALLBACK;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private MyMultiLineEditBox textarea;
    private String lastProgram = "";
    private List<MutableComponent> lastProgramWithSyntaxHighlighting = Collections.emptyList();

    public ProgramEditScreen(String initialContent, Consumer<String> callback) {
        super(Constants.LocalizationKeys.PROGRAM_EDIT_SCREEN_TITLE.getComponent());
        this.INITIAL_CONTENT = initialContent;
        this.CALLBACK = callback;
    }

    public static MutableComponent substring(MutableComponent component, int start, int end) {
        var rtn = Component.empty();
        AtomicInteger seen = new AtomicInteger(0);
        component.visit((style, content) -> {
            int contentStart = Math.max(start - seen.get(), 0);
            int contentEnd = Math.min(end - seen.get(), content.length());

            if (contentStart < contentEnd) {
                rtn.append(Component.literal(content.substring(contentStart, contentEnd)).withStyle(style));
            }
            seen.addAndGet(content.length());
            return Optional.empty();
        }, Style.EMPTY);
        return rtn;
    }

    public void scrollToTop() {
        this.textarea.scrollToTop();
    }

    @Override
    protected void init() {
        super.init();
        assert this.minecraft != null;
        this.textarea = this.addRenderableWidget(new MyMultiLineEditBox());
        textarea.setValue(INITIAL_CONTENT);
        this.setInitialFocus(textarea);

        this.addRenderableWidget(new ExtendedButtonWithTooltip(
                this.width / 2 - 2 - 150,
                this.height / 2 - 100 + 195,
                300,
                20,
                CommonComponents.GUI_DONE,
                (p_97691_) -> this.onClosePerformCallback(),
                Tooltip.create(PROGRAM_EDIT_SCREEN_DONE_BUTTON_TOOLTIP.getComponent())
        ));
    }

    public void onClosePerformCallback() {
        CALLBACK.accept(textarea.getValue());

        assert this.minecraft != null;
        this.minecraft.popGuiLayer();
    }

    @Override
    public void onClose() {
        if (!INITIAL_CONTENT.equals(textarea.getValue())) {
            // if content changed => ask to save
            assert this.minecraft != null;
            // push confirm screen
            this.minecraft.pushGuiLayer(new ConfirmScreen(
                    doSave -> {
                        this.minecraft.popGuiLayer();
                        if (doSave) {
                            onClosePerformCallback();
                        } else {
                            this.minecraft.popGuiLayer();
                        }
                    },
                    Constants.LocalizationKeys.SAVE_CHANGES_CONFIRM_SCREEN_TITLE.getComponent(),
                    Constants.LocalizationKeys.SAVE_CHANGES_CONFIRM_SCREEN_MESSAGE.getComponent(),
                    Constants.LocalizationKeys.SAVE_CHANGES_CONFIRM_SCREEN_YES_BUTTON.getComponent(),
                    Constants.LocalizationKeys.SAVE_CHANGES_CONFIRM_SCREEN_NO_BUTTON.getComponent()
            ));
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean keyReleased(int pKeyCode, int pScanCode, int pModifiers) {
        if (pKeyCode == GLFW.GLFW_KEY_LEFT_CONTROL || pKeyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            // if control released => update syntax highlighting
            textarea.rebuild(Screen.hasControlDown());
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char pCodePoint, int pModifiers) {
        if (Screen.hasControlDown() && pCodePoint == ' ') {
            return true;
        }
        return super.charTyped(pCodePoint, pModifiers);
    }

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        if ((pKeyCode == GLFW.GLFW_KEY_ENTER || pKeyCode == GLFW.GLFW_KEY_KP_ENTER) && Screen.hasShiftDown()) {
            onClosePerformCallback();
            return true;
        }
        if (pKeyCode == GLFW.GLFW_KEY_TAB) {
            // if tab pressed with no selection and not holding shift => insert 4 spaces
            // if tab pressed with no selection and holding shift => de-indent current line
            // if tab pressed with selection and not holding shift => de-indent lines containing selection 4 spaces
            // if tab pressed with selection and holding shift => indent lines containing selection 4 spaces
            String content = textarea.getValue();
            int cursor = textarea.getCursorPosition();
            int selectionCursor = textarea.getSelectionCursorPosition();
            EditorUtils.ManipulationResult result;
            if (Screen.hasShiftDown()) { // de-indent
                result = EditorUtils.deindent(content, cursor, selectionCursor);
            } else { // indent
                result = EditorUtils.indent(content, cursor, selectionCursor);
            }
            textarea.setValue(result.content());
            textarea.setCursorPosition(result.cursorPosition());
            textarea.setSelectionCursorPosition(result.selectionCursorPosition());
            return true;
        }
        if (pKeyCode == GLFW.GLFW_KEY_LEFT_CONTROL || pKeyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            // if control pressed => update syntax highlighting
            textarea.rebuild(Screen.hasControlDown());
            return true;
        }
        if (pKeyCode == GLFW.GLFW_KEY_SLASH && Screen.hasControlDown()) {
            // toggle line comments for selected lines
            String content = textarea.getValue();
            int cursor = textarea.getCursorPosition();
            int selectionCursor = textarea.getSelectionCursorPosition();
            EditorUtils.ManipulationResult result = EditorUtils.toggleComments(content, cursor, selectionCursor);
            textarea.setValue(result.content());
            textarea.setCursorPosition(result.cursorPosition());
            textarea.setSelectionCursorPosition(result.selectionCursorPosition());
            return true;
        }
        if (pKeyCode == GLFW.GLFW_KEY_SPACE && Screen.hasControlDown()) {
            ProgramTokenContextActions.getContextAction(
                            textarea.getValue(),
                            textarea.getCursorPosition()
                    )
                    .ifPresent(Runnable::run);

            // disable the underline since it doesn't refresh when the context action closes
            textarea.rebuild(false);
            return true;
        }
        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
    }

    @Override
    public void resize(Minecraft mc, int x, int y) {
        var prev = this.textarea.getValue();
        init(mc, x, y);
        super.resize(mc, x, y);
        this.textarea.setValue(prev);
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float partialTicks) {
        this.renderTransparentBackground(graphics);
        super.render(graphics, mx, my, partialTicks);
    }

    private class MyMultiLineEditBox extends MultiLineEditBox {
        private int frame = 0;
        public MyMultiLineEditBox() {
            super(
                    ProgramEditScreen.this.font,
                    ProgramEditScreen.this.width / 2 - 200,
                    ProgramEditScreen.this.height / 2 - 110,
                    400,
                    200,
                    Component.literal(""),
                    Component.literal("")
            );
        }

        public void scrollToTop() {
            this.setScrollAmount(0);
        }

        public int getCursorPosition() {
            return this.textField.cursor;
        }

        public void setCursorPosition(int cursor) {
            this.textField.cursor = cursor;
        }

        @Override
        public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
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
                this.textField.setSelecting(Screen.hasShiftDown());
                this.seekCursorScreen(pMouseX, pMouseY);
                return true;
            } else {
                return false;
            }
        }

        public int getSelectionCursorPosition() {
            return this.textField.selectCursor;
        }

        public void setSelectionCursorPosition(int cursor) {
            this.textField.selectCursor = cursor;
        }

        private void rebuild(boolean showContextActionHints) {
            lastProgram = this.textField.value();
            lastProgramWithSyntaxHighlighting = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(
                    lastProgram,
                    showContextActionHints
            );
        }

        @Override
        protected void renderContents(GuiGraphics graphics, int mx, int my, float partialTicks) {
            Matrix4f matrix4f = graphics.pose().last().pose();
            if (!lastProgram.equals(this.textField.value())) {
                rebuild(Screen.hasControlDown());
            }
            List<MutableComponent> lines = lastProgramWithSyntaxHighlighting;
            boolean isCursorVisible = this.isFocused() && this.frame++ / 60 % 2 == 0;
            boolean isCursorAtEndOfLine = false;
            int cursorIndex = textField.cursor();
            int lineX = this.getX() + this.innerPadding();
            int lineY = this.getY() + this.innerPadding();
            int charCount = 0;
            int cursorX = 0;
            int cursorY = 0;
            MultilineTextField.StringView selectedRange = this.textField.getSelected();
            int selectionStart = selectedRange.beginIndex();
            int selectionEnd = selectedRange.endIndex();

            for (int line = 0; line < lines.size(); ++line) {
                var componentColoured = lines.get(line);
                int lineLength = componentColoured.getString().length();
                int lineHeight = this.font.lineHeight + (line == 0 ? 2 : 0);
                boolean cursorOnThisLine = isCursorVisible
                                           && cursorIndex >= charCount
                                           && cursorIndex <= charCount + lineLength;
                var buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());

                if (cursorOnThisLine) {
                    isCursorAtEndOfLine = cursorIndex == charCount + lineLength;
                    cursorY = lineY;
                    // we draw the raw before coloured in case of token recognition errors
                    // draw before cursor
                    cursorX = this.font.drawInBatch(
                            substring(componentColoured, 0, cursorIndex - charCount),
                            lineX,
                            lineY,
                            -1,
                            true,
                            matrix4f,
                            buffer,
                            Font.DisplayMode.NORMAL,
                            0,
                            LightTexture.FULL_BRIGHT
                    ) - 1;
                    this.font.drawInBatch(
                            substring(componentColoured, cursorIndex - charCount, lineLength),
                            cursorX,
                            lineY,
                            -1,
                            true,
                            matrix4f,
                            buffer,
                            Font.DisplayMode.NORMAL,
                            0,
                            LightTexture.FULL_BRIGHT
                    );
                } else {
                    this.font.drawInBatch(
                            componentColoured,
                            lineX,
                            lineY,
                            -1,
                            true,
                            matrix4f,
                            buffer,
                            Font.DisplayMode.NORMAL,
                            0,
                            LightTexture.FULL_BRIGHT
                    );
                }
                buffer.endBatch();

                // Check if the selection is within the current line
                if (selectionStart <= charCount + lineLength && selectionEnd > charCount) {
                    int lineSelectionStart = Math.max(selectionStart - charCount, 0);
                    int lineSelectionEnd = Math.min(selectionEnd - charCount, lineLength);

                    int highlightStartX = this.font.width(substring(componentColoured, 0, lineSelectionStart));
                    int highlightEndX = this.font.width(substring(componentColoured, 0, lineSelectionEnd));

                    this.renderHighlight(
                            graphics,
                            lineX + highlightStartX,
                            lineY,
                            lineX + highlightEndX,
                            lineY + lineHeight
                    );
                }

                lineY += lineHeight;
                charCount += lineLength + 1;
            }

            if (isCursorAtEndOfLine) {
                graphics.drawString(this.font, "_", cursorX, cursorY, -1);
            } else {
                graphics.fill(cursorX, cursorY - 1, cursorX + 1, cursorY + 1 + 9, -1);
            }
        }
    }
}

