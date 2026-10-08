package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.text_editor.SFMMultiLineTextRenderWidget;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorUtils;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import net.minecraft.client.gui.Font;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.gui.GuiGraphics;
{% when '26.1.2' %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Whence;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.gui.screens.Screen;
{% when '26.1.2' %}
import net.minecraft.client.input.MouseButtonEvent;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                pMessage
{% when '26.1.2' %}
                pMessage,
                -2039584,
                true,
                -3092272,
                true,
                true
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        this.setScrollAmount(this.getMaxScrollAmount());
{% when '26.1.2' %}
        this.setScrollAmount(this.maxScrollAmount());
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @Override
{% when '26.1.2' %}
    @MCVersionDependentBehaviour
{% endcase %}
    public int getScrollBarHeight() {
        // Fix #307: divide by zero exception in AbstractScrollWidget.mouseDragged
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        int rtn = super.getScrollBarHeight();
{% when '26.1.2' %}
        int rtn = this.getBottom() - this.getY();
{% endcase %}
        if (rtn == this.height) {
            return rtn - 1;
        } else {
            return rtn;
        }
    }

    @MCVersionDependentBehaviour
    @Override
    public boolean mouseClicked(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            double pMouseX,
            double pMouseY,
            int pButton
{% when '26.1.2' %}
            MouseButtonEvent event,
            boolean doubleClick
{% endcase %}
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}

{% when '26.1.2' %}
        int pButton = event.button();
        double pMouseX = event.x(),
                pMouseY = event.y();
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                boolean shiftDown = Screen.hasShiftDown();
{% when '26.1.2' %}
                boolean shiftDown = SFMWidgetUtils.hasShiftDown();
{% endcase %}

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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    && this.scrollbarVisible()
                    && pMouseX >= SFMWidgetUtils.getX(this) + this.width
                    && pMouseX <= SFMWidgetUtils.getX(this) + this.width + 8
                    && pMouseY >= SFMWidgetUtils.getY(this)
                    && pMouseY < SFMWidgetUtils.getY(this) + this.height;
{% when '26.1.2' %}
                    && this.isOverScrollbar(pMouseX, pMouseY);
{% endcase %}
            if (clickedScrollbar) {
                this.scrollbarDragActive = true;
            }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            return super.mouseClicked(pMouseX, pMouseY, pButton);
{% when '26.1.2' %}
            return super.mouseClicked(event, doubleClick);
{% endcase %}
        } catch (Exception e) {
            SFM.LOGGER.error("Error in mouseClicked handler", e);
            return false;
        }
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
    @MCVersionDependentBehaviour
    protected boolean withinContentAreaPoint(double x, double y) {
        return x >= (double)this.getX()
                && x < (double)(this.getX() + this.width)
                && y >= (double)this.getY()
                && y < (double)(this.getY() + this.height);
    }

{% endcase %}
    @Override
    public int getInnerHeight() {
        // parent method uses this.textField.getLineCount() which is split for text wrapping
        // we don't use the wrapped text, so we need to calculate the height ourselves to avoid overshooting
        return this.font.lineHeight * (styledTextContentLines.size() + 2);
    }

    @Override
    public boolean mouseReleased(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            double mx,
            double my,
            int button
{% when '26.1.2' %}
            MouseButtonEvent event
{% endcase %}
    ) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if (button == 0) {
{% when '26.1.2' %}
        if (event.button() == 0) {
{% endcase %}
            // Stop active selection on mouse up
            this.textField.setSelecting(false);
            this.scrollbarDragActive = false;
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return super.mouseReleased(mx, my, button);
{% when '26.1.2' %}
        return super.mouseReleased(event);
{% endcase %}
    }

    @Override
    public boolean mouseDragged(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            double mx,
            double my,
            int button,
{% when '26.1.2' %}
            MouseButtonEvent event,
{% endcase %}
            double dx,
            double dy
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
        double mx = event.x(),
                my = event.y();
{% endcase %}
        // We want to give the scrollbar priority, but we want to do our own selection logic.
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if (this.scrollbarDragActive && super.mouseDragged(mx, my, button, dx, dy)) {
{% when '26.1.2' %}
        if (this.scrollbarDragActive && super.mouseDragged(event, dx, dy)) {
{% endcase %}
            return true;
        }

        try {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            if (button == 0 && this.visible && this.withinContentAreaPoint(mx, my)) {
{% when '26.1.2' %}
            if (event.button() == 0 && this.visible && this.withinContentAreaPoint(mx, my)) {
{% endcase %}
                if (styledTextContentLines.isEmpty()) {
                    return false;
                }
                // Keep selection active while dragging and update cursor
                this.textField.setSelecting(true);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                seekCursorFromPoint(mx, my);
{% when '26.1.2' %}
                seekCursorFromPoint(event.x(), event.y());
{% endcase %}
                return true;
            }
        } catch (Exception e) {
            SFM.LOGGER.error("Error in mouseDragged handler", e);
            return false;
        }

        return false;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    protected void setScrollAmount(double pScrollAmount) {
{% when '26.1.2' %}
    public void setScrollAmount(double pScrollAmount) {
{% endcase %}

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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    protected int getMaxScrollAmount() {
{% when '26.1.2' %}
    public int maxScrollAmount() {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return Math.max(1, super.getMaxScrollAmount()); // Fix #307: divide by zero exception
{% when '26.1.2' %}
        return Math.max(1, super.maxScrollAmount()); // Fix #307: divide by zero exception
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
    protected void renderContents(
            PoseStack poseStack,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    protected void renderContents(
            GuiGraphics pGuiGraphics,
{% when '26.1.2' %}
    protected void extractContents(
            GuiGraphicsExtractor pGuiGraphics,
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        textRenderWidget.render(poseStack, mx, my, partialTicks);
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        textRenderWidget.render(pGuiGraphics, mx, my, partialTicks);
{% when '26.1.2' %}
        textRenderWidget.extractRenderState(pGuiGraphics, mx, my, partialTicks);
{% endcase %}
    }
}
