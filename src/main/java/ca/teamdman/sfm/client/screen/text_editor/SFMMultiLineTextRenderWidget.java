package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScreenRenderUtils;
import ca.teamdman.sfm.common.util.Mth;
import ca.teamdman.sfm.common.util.SFMComponentUtils;
import com.bbscn.MultilineTextField;
import com.bbscn.Rect2i;
import com.bbscn.Renderable;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.text.ITextComponent;

import java.util.ArrayList;
import java.util.List;

public class SFMMultiLineTextRenderWidget implements Renderable {
    /// Fast lookup to the first character for each line in the document
    private final Int2IntArrayMap lineStartIndices = new Int2IntArrayMap();

    private FontRenderer font;

    private List<? extends ITextComponent> styledTextContentLines = new ArrayList<>();

    private int frame = 0;

    private Rect2i area;

    private int cursorIndex = 0;

    private boolean focused = false;

    private double scrollAmount = 0.0d;

    private MultilineTextField.StringView selected;

    private String textContent = "";

    public SFMMultiLineTextRenderWidget(
            FontRenderer font,
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

    public void setStyledTextContentLines(List<? extends ITextComponent> styledTextContentLines) {

        this.styledTextContentLines = styledTextContentLines;
    }

    // TODO: we want to store the last known cursor position then revamp the V1 editor to use this
    @Override
    public void render(
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {

        frame++;

        if (styledTextContentLines.isEmpty()) {
            return;
        }

        final boolean isCursorVisible = focused && frame / 20 % 2 == 0;

        final int lineHeight = Math.max(1, font.FONT_HEIGHT);

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

        // One buffer for the entire text pass
       var buffer = Tessellator.getInstance().getBuffer();
//        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());

        // Collect selection highlights rects and draw them after the text
        List<int[]> highlightRects = new ArrayList<>();

        for (int line = viewLineIndexStart; line < viewLineIndexEnd; ++line) {
            var componentColoured = styledTextContentLines.get(line);
            String plainLine = componentColoured.getUnformattedText();
            int lineLength = plainLine.length();

            boolean cursorOnThisLine =
                    isCursorVisible && cursorIndex >= charCountAccum
                    && cursorIndex <= charCountAccum + lineLength;

            if (SFMTextEditorUtils.shouldShowLineNumbers()) {
                // Draw line number
                String lineNumber = String.valueOf(line + 1);
                SFMFontUtils.drawInBatch(
                        lineNumber,
                        font,
                        lineX - 2 - font.getStringWidth(lineNumber),
                        lineY,
                        true,
                        false
                );
            }

            if (cursorOnThisLine) {
                isCursorAtEndOfLine = cursorIndex == charCountAccum + lineLength;
                cursorY = lineY;
                int relativeCursorIndex = cursorIndex - charCountAccum;
                int drawnWidthBeforeCursor = font.getStringWidth(plainLine.substring(0, relativeCursorIndex));
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

                int highlightStartX = font.getStringWidth(plainLine.substring(0, lineSelectionStart));
                int highlightEndX = font.getStringWidth(plainLine.substring(0, lineSelectionEnd));

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

        if (drewCursorGlyph) {
            if (isCursorAtEndOfLine) {
                SFMFontUtils.draw(
                        font,
                        "_",
                        cursorX,
                        cursorY,
                        -1,
                        true
                );
            } else {
                Gui.drawRect(
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
                (int) Math.floor(innerY / Math.max(1, this.font.FONT_HEIGHT)),
                0,
                Math.max(0, getLineCount() - 1)
        );
        int lineStartIndex = getLineStartIndex(lineIndex);
        if (styledTextContentLines.isEmpty()) {
            return lineStartIndex;
        }
        int clampedLine = Mth.clamp(lineIndex, 0, Math.max(0, styledTextContentLines.size() - 1));
        String plainLine = styledTextContentLines.get(clampedLine).getUnformattedText();
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
