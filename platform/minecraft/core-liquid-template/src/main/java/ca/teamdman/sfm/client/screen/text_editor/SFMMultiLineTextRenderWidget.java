package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScreenRenderUtils;
import ca.teamdman.sfm.common.util.SFMComponentUtils;
{% case minecraft_version %}
{% when '1.19.2' %}
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Matrix4f;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
{% when '1.21', '1.21.1' %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when '26.1.2' %}
{% endcase %}
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.client.gui.Font;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
import net.minecraft.client.gui.GuiComponent;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.gui.GuiGraphics;
{% when '26.1.2' %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
import net.minecraft.client.gui.components.MultilineTextField;
{% case minecraft_version %}
{% when '1.19.2' %}
import net.minecraft.client.gui.components.Widget;
import net.minecraft.client.renderer.MultiBufferSource;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.renderer.MultiBufferSource;
{% when '26.1.2' %}
import net.minecraft.client.gui.components.Renderable;
{% endcase %}
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
{% case minecraft_version %}
{% when '1.19.2', '26.1.2' %}
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import org.joml.Matrix4f;
{% endcase %}

import java.util.ArrayList;
import java.util.List;

{% case minecraft_version %}
{% when '1.19.2' %}
public class SFMMultiLineTextRenderWidget implements Widget {
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
public class SFMMultiLineTextRenderWidget implements Renderable {
{% endcase %}
    /// Fast lookup to the first character for each line in the document
    private final Int2IntArrayMap lineStartIndices = new Int2IntArrayMap();

    private Font font;

    private List<? extends Component> styledTextContentLines = new ArrayList<>();

    private int frame = 0;

    private Rect2i area;

    private int cursorIndex = 0;

    private boolean focused = false;

    private double scrollAmount = 0.0d;

    private MultilineTextField.StringView selected;

    private String textContent = "";

    public SFMMultiLineTextRenderWidget(
            Font font,
            Rect2i area
    ) {

        this.font = font;
        this.area = area;
    }

    public void setTextContent(String textContent) {

        this.textContent = textContent;
        rebuildLineLookup();
    }

    public void setSelected(MultilineTextField.StringView selected) {

        this.selected = selected;
    }

    public void setScrollAmount(double scrollAmount) {

        this.scrollAmount = scrollAmount;
    }

    public void setCursorIndex(int cursorIndex) {

        this.cursorIndex = cursorIndex;
    }

    public void setFocused(boolean focused) {

        this.focused = focused;
    }

    public void setStyledTextContentLines(List<? extends Component> styledTextContentLines) {

        this.styledTextContentLines = styledTextContentLines;
    }

    // TODO: we want to store the last known cursor position then revamp the V1 editor to use this
    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
    public void render(
            PoseStack pPoseStack,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public void render(
            GuiGraphics pGuiGraphics,
{% when '26.1.2' %}
    public void extractRenderState(
            GuiGraphicsExtractor pGuiGraphics,
{% endcase %}
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}

{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}

        PoseStack pPoseStack = pGuiGraphics.pose();

{% when '26.1.2' %}
{% endcase %}
        frame++;

        if (styledTextContentLines.isEmpty()) {
            return;
        }

        final boolean isCursorVisible = focused && frame / 20 % 2 == 0;

        final int lineHeight = Math.max(1, font.lineHeight);

        // Determine which logical line is at the top
        final int viewLineIndexStart = Mth.clamp(
                (int) Math.floor(scrollAmount / lineHeight),
                0,
                Math.max(0, styledTextContentLines.size() - 1)
        );
        // Render a small overscan
        final int numVisibleLines = Math.max(1, area.getHeight() / lineHeight + 2);
        final int viewLineIndexEnd = Math.min(styledTextContentLines.size(), viewLineIndexStart + numVisibleLines);

        final int lineX = area.getX() + SFMTextEditorUtils.getLineNumberWidth(font, styledTextContentLines.size());

        boolean isCursorAtEndOfLine = false;
        boolean drewCursorGlyph = false;

        // IMPORTANT: do not subtract (scroll % lineHeight) here.
        // The parent has already translated by -scrollAmount.
        // Draw at content-space Y positions as if there was no scrolling:
        int lineY = area.getY() + viewLineIndexStart * lineHeight;
        int charCountAccum = getLineStartIndex(viewLineIndexStart);

        int cursorX = 0;
        int cursorY = 0;

        final int selectionStart = selected.beginIndex();
        final int selectionEnd = selected.endIndex();

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        // One buffer for the entire text pass
        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
{% when '1.21', '1.21.1' %}
        // One buffer for the entire text pass
        MultiBufferSource.BufferSource buffer = pGuiGraphics.bufferSource();
{% when '26.1.2' %}
{% endcase %}

        // Collect selection highlights rects and draw them after the text
        List<int[]> highlightRects = new ArrayList<>();

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        Matrix4f matrix4f = pPoseStack.last().pose();

{% when '26.1.2' %}
{% endcase %}
        for (int line = viewLineIndexStart; line < viewLineIndexEnd; ++line) {
            var componentColoured = styledTextContentLines.get(line);
            String plainLine = componentColoured.getString();
            int lineLength = plainLine.length();

            boolean cursorOnThisLine =
                    isCursorVisible && cursorIndex >= charCountAccum
                    && cursorIndex <= charCountAccum + lineLength;

            if (SFMTextEditorUtils.shouldShowLineNumbers()) {
                // Draw line number
                String lineNumber = String.valueOf(line + 1);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                SFMFontUtils.drawInBatch(
                        lineNumber,
{% when '26.1.2' %}
                SFMFontUtils.draw(
                        pGuiGraphics,
{% endcase %}
                        font,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
                        lineNumber,
{% endcase %}
                        lineX - 2 - font.width(lineNumber),
                        lineY,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                        true,
                        false,
                        matrix4f,
                        buffer
{% when '26.1.2' %}
                        -1,
                        true
{% endcase %}
                );
            }

            if (cursorOnThisLine) {
                isCursorAtEndOfLine = cursorIndex == charCountAccum + lineLength;
                cursorY = lineY;
                int relativeCursorIndex = cursorIndex - charCountAccum;
                int drawnWidthBeforeCursor = font.width(plainLine.substring(0, relativeCursorIndex));
                cursorX = lineX + drawnWidthBeforeCursor;
                // draw text before cursor
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                SFMFontUtils.drawInBatch(
                        SFMComponentUtils.substring(componentColoured, 0, relativeCursorIndex),
{% when '26.1.2' %}
                SFMFontUtils.draw(
                        pGuiGraphics,
{% endcase %}
                        font,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
                        SFMComponentUtils.substring(componentColoured, 0, relativeCursorIndex),
{% endcase %}
                        lineX,
                        lineY,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                        true,
                        false,
                        matrix4f,
                        buffer
{% when '26.1.2' %}
                        -1,
                        true
{% endcase %}
                );

                // draw text after cursor
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                SFMFontUtils.drawInBatch(
                        SFMComponentUtils.substring(componentColoured, relativeCursorIndex, lineLength),
{% when '26.1.2' %}
                SFMFontUtils.draw(
                        pGuiGraphics,
{% endcase %}
                        font,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
                        SFMComponentUtils.substring(componentColoured, relativeCursorIndex, lineLength),
{% endcase %}
                        cursorX,
                        lineY,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                        true,
                        false,
                        matrix4f,
                        buffer
{% when '26.1.2' %}
                        -1,
                        true
{% endcase %}
                );
                drewCursorGlyph = true;
            } else {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                SFMFontUtils.drawInBatch(
                        componentColoured,
{% when '26.1.2' %}
                SFMFontUtils.draw(
                        pGuiGraphics,
{% endcase %}
                        font,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
                        componentColoured,
{% endcase %}
                        lineX,
                        lineY,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                        true,
                        false,
                        matrix4f,
                        buffer
{% when '26.1.2' %}
                        -1,
                        true
{% endcase %}
                );
            }

            // Check if the selection is within the current line
            if (selectionStart <= charCountAccum + lineLength && selectionEnd > charCountAccum) {
                int lineSelectionStart = Math.max(selectionStart - charCountAccum, 0);
                int lineSelectionEnd = Math.min(selectionEnd - charCountAccum, lineLength);

                int highlightStartX = font.width(plainLine.substring(0, lineSelectionStart));
                int highlightEndX = font.width(plainLine.substring(0, lineSelectionEnd));

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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        // Flush the text batch once
        buffer.endBatch();

{% when '26.1.2' %}
{% endcase %}
        // Draw selection highlights after text
        for (int[] r : highlightRects) {
            SFMScreenRenderUtils.renderHighlight(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
                    pPoseStack,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                    pGuiGraphics,
{% endcase %}
                    r[0],
                    r[1],
                    r[2],
                    r[3]
            );
        }

        if (drewCursorGlyph) {
            if (isCursorAtEndOfLine) {
                SFMFontUtils.draw(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
                        pPoseStack,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                        pGuiGraphics,
{% endcase %}
                        font,
                        "_",
                        cursorX,
                        cursorY,
                        -1,
                        true
                );
            } else {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
                GuiComponent.fill(
                        pPoseStack,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                pGuiGraphics.fill(
{% endcase %}
                        cursorX,
                        cursorY - 1,
                        cursorX + 1,
                        cursorY + 1 + 9,
                        -1
                );
            }
        }
    }

    public int pointToCharacterIndex(
            double innerX,
            double innerY
    ) {

        int lineIndex = Mth.clamp(
                (int) Math.floor(innerY / Math.max(1, this.font.lineHeight)),
                0,
                Math.max(0, getLineCount() - 1)
        );
        int lineStartIndex = getLineStartIndex(lineIndex);
        if (styledTextContentLines.isEmpty()) {
            return lineStartIndex;
        }
        int clampedLine = Mth.clamp(lineIndex, 0, Math.max(0, styledTextContentLines.size() - 1));
        String plainLine = styledTextContentLines.get(clampedLine).getString();
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
                getCharacterCount()
        );
    }

    private int getLineCount() {

        return styledTextContentLines.size();
    }

    private int getCharacterCount() {
        return textContent.length();
    }

    private void rebuildLineLookup() {

        lineStartIndices.clear();
        lineStartIndices.put(0, 0);
        int line = 0;
        for (int i = 0; i < textContent.length(); i++) {
            if (textContent.charAt(i) == '\n') {
                line++;
                lineStartIndices.put(line, i + 1);
            }
        }
    }

    private int getLineStartIndex(int lineIndex) {

        if (lineStartIndices.isEmpty()) return 0;
        int clamped = Mth.clamp(
                lineIndex,
                0,
                Math.max(0, lineStartIndices.size() - 1)
        );
        return lineStartIndices.get(clamped);
    }

}
