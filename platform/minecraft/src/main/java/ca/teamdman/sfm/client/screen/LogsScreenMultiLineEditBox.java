package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.text_editor.SFMMultiLineTextRenderWidget;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorUtils;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Collections;
import java.util.List;

// TODO: enable scrolling without focus; respond to wheel events
class LogsScreenMultiLineEditBox extends MultiLineEditBox {

    private final LogsScreen logsScreen;

    public SFMMultiLineTextRenderWidget textRenderWidget;

    public List<MutableComponent> styledTextContentLines = Collections.emptyList();

    /// Used to debounce scrolling when click-dragging to select text.
    private boolean scrollingEnabled = true;

    private boolean scrollbarDragActive;

    public LogsScreenMultiLineEditBox(
            LogsScreen logsScreen,
            Font pFont,
            int pX,
            int pY,
            int pWidth,
            int pHeight,
            Component pPlaceholder,
            Component pMessage
    ) {

        super(
                pFont,
                pX,
                pY,
                pWidth,
                pHeight,
                pPlaceholder,
                pMessage,
                -2039584,
                true,
                -3092272,
                true,
                true
        );

        Rect2i textRenderWidgetArea = new Rect2i(
                SFMWidgetUtils.getX(this) + this.innerPadding(),
                SFMWidgetUtils.getY(this) + this.innerPadding(),
                this.width - this.totalInnerPadding(),
                this.height - this.totalInnerPadding()

        );
        this.textRenderWidget = new SFMMultiLineTextRenderWidget(pFont, textRenderWidgetArea);
        textRenderWidget.setStyledTextContentLines(styledTextContentLines);
        textRenderWidget.setTextContent(this.getValue());
        this.logsScreen = logsScreen;
    }

    public void scrollToBottom() {

        this.setScrollAmount(this.maxScrollAmount());
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
                if (styledTextContentLines.isEmpty()) {
                    return false;
                }

                // Focus the editor so the caret blinks and keys go here
                this.setFocused(true);

                boolean shiftDown = SFMWidgetUtils.hasShiftDown();

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

            return super.mouseClicked(event, doubleClick);
        } catch (Exception e) {
            SFM.LOGGER.error("Error in mouseClicked handler", e);
            return false;
        }
    }

    @MCVersionDependentBehaviour
    protected boolean withinContentAreaPoint(double x, double y) {
        return x >= (double)this.getX()
                && x < (double)(this.getX() + this.width)
                && y >= (double)this.getY()
                && y < (double)(this.getY() + this.height);
    }

    @Override
    public int getInnerHeight() {
        // parent method uses this.textField.getLineCount() which is split for text wrapping
        // we don't use the wrapped text, so we need to calculate the height ourselves to avoid overshooting
        return this.font.lineHeight * (styledTextContentLines.size() + 2);
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

    @Override
    public boolean mouseDragged(
            MouseButtonEvent event,
            double dx,
            double dy
    ) {
        double mx = event.x(),
                my = event.y();
        // We want to give the scrollbar priority, but we want to do our own selection logic.
        if (this.scrollbarDragActive && super.mouseDragged(event, dx, dy)) {
            return true;
        }

        try {
            if (event.button() == 0 && this.visible && this.withinContentAreaPoint(mx, my)) {
                if (styledTextContentLines.isEmpty()) {
                    return false;
                }
                // Keep selection active while dragging and update cursor
                this.textField.setSelecting(true);
                seekCursorFromPoint(event.x(), event.y());
                return true;
            }
        } catch (Exception e) {
            SFM.LOGGER.error("Error in mouseDragged handler", e);
            return false;
        }

        return false;
    }

    @Override
    public void setScrollAmount(double pScrollAmount) {

        if (!scrollingEnabled) return;
        super.setScrollAmount(pScrollAmount);
    }


    private void seekCursorFromPoint(
            double mx,
            double my
    ) {

        int lineCount = styledTextContentLines.size();
        double innerX = mx - (
                SFMWidgetUtils.getX(this)
                + this.innerPadding()
                + SFMTextEditorUtils.getLineNumberWidth(this.font, lineCount)
        );
        double innerY = my - (SFMWidgetUtils.getY(this) + this.innerPadding()) + this.scrollAmount();
        int cursorPosition = textRenderWidget.pointToCharacterIndex(innerX, innerY);

        this.scrollingEnabled = false;
        this.textField.seekCursor(Whence.ABSOLUTE, cursorPosition);
        this.scrollingEnabled = true;
    }

    @Override
    public int maxScrollAmount() {

        return Math.max(1, super.maxScrollAmount()); // Fix #307: divide by zero exception
    }

    @Override
    public void extractWidgetRenderState(
            GuiGraphicsExtractor pGuiGraphics,
            int mx,
            int my,
            float partialTicks
    ) {

        if (logsScreen.shouldRebuildText()) {
            logsScreen.rebuildText();
        }

        textRenderWidget.setCursorIndex(this.textField.cursor());
        textRenderWidget.setFocused(this.isFocused());
        textRenderWidget.setScrollAmount(this.scrollAmount());
        textRenderWidget.setSelected(this.textField.getSelected());
        textRenderWidget.extractRenderState(pGuiGraphics, mx, my, partialTicks);
    }

}
