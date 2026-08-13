package ca.teamdman.sfm.client.screen.widget;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScissorStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Matrix4f;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A read-only, scissor-clipped console viewport.
 *
 * <p>This deliberately does not extend one of Minecraft's scroll widgets. The
 * supported versions disagree about the details of their scroll geometry and
 * event handling, so the viewport owns its scroll amount, scrollbar and mouse
 * interaction.</p>
 */
public final class SFMConsoleWidget {
    private static final int PADDING = 4;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_GAP = 3;
    private static final int SCROLLBAR_MIN_THUMB_HEIGHT = 12;
    private static final int MAX_LINES = 2_000;

    private final Font font;
    private int x;
    private int y;
    private int width;
    private int height;
    private final List<Component> lines = new ArrayList<>();

    private double scrollAmount;
    private boolean followTail = true;
    private boolean scrollbarDragActive;
    private double scrollbarDragOffset;

    public SFMConsoleWidget(
            Font font,
            int x,
            int y,
            int width,
            int height
    ) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        clampScrollAmount();
    }

    public List<Component> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(this.lines));
    }

    public String copyableText() {
        StringBuilder text = new StringBuilder();
        for (Component line : this.lines) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(line.getString());
        }
        return text.toString();
    }

    public void replaceLines(Iterable<? extends Component> replacement) {
        this.lines.clear();
        for (Component line : replacement) {
            if (line != null) {
                this.lines.add(line);
            }
        }
        trimToLimit();
        if (this.followTail) {
            scrollToBottom();
        } else {
            clampScrollAmount();
        }
    }

    public void append(Component line) {
        if (line == null) {
            return;
        }
        this.lines.add(line);
        trimToLimit();
        if (this.followTail) {
            scrollToBottom();
        }
    }

    public void clear() {
        this.lines.clear();
        this.scrollAmount = 0.0d;
        this.followTail = true;
    }

    public void setFollowTail(boolean followTail) {
        this.followTail = followTail;
        if (followTail) {
            scrollToBottom();
        }
    }

    public boolean isFollowingTail() {
        return this.followTail;
    }

    public void scrollToBottom() {
        this.scrollAmount = getMaxScrollAmount();
    }

    public double getScrollAmount() {
        return this.scrollAmount;
    }

    public double getMaxScrollAmount() {
        int contentHeight = getContentHeight();
        int lineHeight = Math.max(1, this.font.lineHeight);
        return Math.max(0.0d, this.lines.size() * (double) lineHeight - contentHeight);
    }

    public void render(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        int contentLeft = this.x + PADDING;
        int contentTop = this.y + PADDING;
        int contentWidth = getContentWidth();
        int contentHeight = getContentHeight();

        GuiComponent.fill(
                poseStack,
                this.x,
                this.y,
                this.x + this.width,
                this.y + this.height,
                0x66000000
        );

        if (contentWidth > 0 && contentHeight > 0 && !this.lines.isEmpty()) {
            SFMScissorStack.pushGui(
                    poseStack,
                    contentLeft,
                    contentTop,
                    contentLeft + contentWidth,
                    contentTop + contentHeight
            );
            try {
                renderLines(poseStack, contentLeft, contentTop);
            } finally {
                SFMScissorStack.pop();
            }
        }

        renderScrollbar(poseStack, mouseX, mouseY);
    }

    private void renderLines(PoseStack poseStack, int contentLeft, int contentTop) {
        int lineHeight = Math.max(1, this.font.lineHeight);
        int firstLine = Mth.clamp(
                (int) Math.floor(this.scrollAmount / lineHeight),
                0,
                Math.max(0, this.lines.size() - 1)
        );
        int visibleLineCount = Math.max(1, getContentHeight() / lineHeight + 2);
        int endLine = Math.min(this.lines.size(), firstLine + visibleLineCount);
        float firstLineY = (float) (contentTop + firstLine * lineHeight - this.scrollAmount);

        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(
                Tesselator.getInstance().getBuilder()
        );
        for (int index = firstLine; index < endLine; index++) {
            SFMFontUtils.drawInBatch(
                    this.lines.get(index),
                    this.font,
                    contentLeft,
                    firstLineY + (index - firstLine) * lineHeight,
                    false,
                    false,
                    matrix,
                    buffer
            );
        }
        buffer.endBatch();
    }

    private void renderScrollbar(PoseStack poseStack, int mouseX, int mouseY) {
        if (!isScrollbarVisible()) {
            return;
        }

        int trackLeft = getScrollbarLeft();
        int trackTop = this.y + PADDING;
        int trackHeight = getContentHeight();
        int thumbHeight = getScrollbarThumbHeight();
        int thumbTop = getScrollbarThumbTop();

        GuiComponent.fill(
                poseStack,
                trackLeft,
                trackTop,
                trackLeft + SCROLLBAR_WIDTH,
                trackTop + trackHeight,
                0x55303030
        );
        boolean hovered = isInside(
                mouseX,
                mouseY,
                trackLeft,
                thumbTop,
                SCROLLBAR_WIDTH,
                thumbHeight
        );
        GuiComponent.fill(
                poseStack,
                trackLeft,
                thumbTop,
                trackLeft + SCROLLBAR_WIDTH,
                thumbTop + thumbHeight,
                hovered || this.scrollbarDragActive ? 0xFFAAAAAA : 0xFF707070
        );
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }

        if (isScrollbarVisible() && isInside(
                mouseX,
                mouseY,
                getScrollbarLeft(),
                getScrollbarThumbTop(),
                SCROLLBAR_WIDTH,
                getScrollbarThumbHeight()
        )) {
            this.scrollbarDragActive = true;
            this.scrollbarDragOffset = mouseY - getScrollbarThumbTop();
            this.followTail = false;
            return true;
        }

        if (isScrollbarVisible() && isInside(
                mouseX,
                mouseY,
                getScrollbarLeft(),
                this.y + PADDING,
                SCROLLBAR_WIDTH,
                getContentHeight()
        )) {
            double proportion = (mouseY - this.y - PADDING) / (double) getContentHeight();
            setScrollAmount(proportion * getMaxScrollAmount());
            this.followTail = isAtBottom();
            return true;
        }

        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.scrollbarDragActive) {
            this.scrollbarDragActive = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (button != 0 || !this.scrollbarDragActive || !isScrollbarVisible()) {
            return false;
        }

        int trackTop = this.y + PADDING;
        int travel = Math.max(1, getContentHeight() - getScrollbarThumbHeight());
        double thumbTop = Mth.clamp(
                mouseY - this.scrollbarDragOffset,
                trackTop,
                trackTop + travel
        );
        setScrollAmount((thumbTop - trackTop) / travel * getMaxScrollAmount());
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isMouseOver(mouseX, mouseY) || getMaxScrollAmount() <= 0.0d) {
            return false;
        }
        setScrollAmount(this.scrollAmount - delta * Math.max(1, this.font.lineHeight) * 3.0d);
        this.followTail = isAtBottom();
        return true;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, this.x, this.y, this.width, this.height);
    }

    private void setScrollAmount(double value) {
        double previous = this.scrollAmount;
        this.scrollAmount = Mth.clamp(value, 0.0d, getMaxScrollAmount());
        if (this.scrollAmount != previous && !isAtBottom()) {
            this.followTail = false;
        }
    }

    private boolean isAtBottom() {
        return this.scrollAmount >= getMaxScrollAmount() - 0.5d;
    }

    private void clampScrollAmount() {
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0.0d, getMaxScrollAmount());
    }

    private void trimToLimit() {
        int overage = this.lines.size() - MAX_LINES;
        if (overage > 0) {
            this.lines.subList(0, overage).clear();
        }
    }

    private int getContentWidth() {
        int scrollbarSpace = isScrollbarVisible() ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0;
        return Math.max(0, this.width - PADDING * 2 - scrollbarSpace);
    }

    private int getContentHeight() {
        return Math.max(0, this.height - PADDING * 2);
    }

    private boolean isScrollbarVisible() {
        return getMaxScrollAmount() > 0.0d;
    }

    private int getScrollbarLeft() {
        return this.x + this.width - PADDING - SCROLLBAR_WIDTH;
    }

    private int getScrollbarThumbHeight() {
        int trackHeight = getContentHeight();
        if (trackHeight <= 0) {
            return 0;
        }
        int contentHeight = Math.max(trackHeight, this.lines.size() * Math.max(1, this.font.lineHeight));
        return Math.min(
                trackHeight,
                Math.max(SCROLLBAR_MIN_THUMB_HEIGHT, trackHeight * trackHeight / contentHeight)
        );
    }

    private int getScrollbarThumbTop() {
        int trackTop = this.y + PADDING;
        int travel = Math.max(0, getContentHeight() - getScrollbarThumbHeight());
        double maxScroll = getMaxScrollAmount();
        if (travel == 0 || maxScroll == 0.0d) {
            return trackTop;
        }
        return trackTop + (int) Math.round(travel * this.scrollAmount / maxScroll);
    }

    private static boolean isInside(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height
    ) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

}
