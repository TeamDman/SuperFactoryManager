package com.bbscn;


import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@SideOnly(Side.CLIENT)
public class MultilineTextField {
    public static final int NO_CHARACTER_LIMIT = Integer.MAX_VALUE;
    private static final int LINE_SEEK_PIXEL_BIAS = 2;
    private final FontRenderer font;
    private final List<StringView> displayLines = Lists.newArrayList();
    private String value;
    public int cursor;
    public int selectCursor;
    private boolean selecting;
    private int characterLimit = Integer.MAX_VALUE;
    private final int width;
    private Consumer<String> valueListener = p_239235_ -> {
    };
    private Runnable cursorListener = () -> {
    };

    public MultilineTextField(FontRenderer pFont, int pWidth) {
        this.font = pFont;
        this.width = pWidth;
        this.setValue("");
    }

    public int characterLimit() {
        return this.characterLimit;
    }

    public void setCharacterLimit(int pCharacterLimit) {
        if (pCharacterLimit < 0) {
            throw new IllegalArgumentException("Character limit cannot be negative");
        } else {
            this.characterLimit = pCharacterLimit;
        }
    }

    public boolean hasCharacterLimit() {
        return this.characterLimit != Integer.MAX_VALUE;
    }

    public void setValueListener(Consumer<String> pValueListener) {
        this.valueListener = pValueListener;
    }

    public void setCursorListener(Runnable pCursorListener) {
        this.cursorListener = pCursorListener;
    }

    public void setValue(String pFullText) {
        this.value = this.truncateFullText(pFullText);
        this.cursor = this.value.length();
        this.selectCursor = this.cursor;
        this.onValueChange();
    }

    public String value() {
        return this.value;
    }

    public void insertText(String pText) {
        if (!pText.isEmpty() || this.hasSelection()) {
            String s = this.truncateInsertionText(Tools.filterText(pText, true));
            StringView multilinetextfield$stringview = this.getSelected();
            this.value = new StringBuilder(this.value).replace(multilinetextfield$stringview.beginIndex, multilinetextfield$stringview.endIndex, s).toString();
            this.cursor = multilinetextfield$stringview.beginIndex + s.length();
            this.selectCursor = this.cursor;
            this.onValueChange();
        }
    }

    public void deleteText(int pLength) {
        if (!this.hasSelection()) {
            this.selectCursor = Tools.clamp(this.cursor + pLength, 0, this.value.length());
        }

        this.insertText("");
    }

    public int cursor() {
        return this.cursor;
    }

    public void setSelecting(boolean pSelecting) {
        this.selecting = pSelecting;
    }

    public StringView getSelected() {
        return new StringView(Math.min(this.selectCursor, this.cursor), Math.max(this.selectCursor, this.cursor));
    }

    public int getLineCount() {
        return this.displayLines.size();
    }

    public int getLineAtCursor() {
        for (int i = 0; i < this.displayLines.size(); i++) {
            StringView multilinetextfield$stringview = this.displayLines.get(i);
            if (this.cursor >= multilinetextfield$stringview.beginIndex && this.cursor <= multilinetextfield$stringview.endIndex) {
                return i;
            }
        }

        return -1;
    }

    public StringView getLineView(int pLineNumber) {
        return this.displayLines.get(Tools.clamp(pLineNumber, 0, this.displayLines.size() - 1));
    }

    public void seekCursor(Whence pWhence, int pPosition) {
        switch (pWhence) {
            case ABSOLUTE:
                this.cursor = pPosition;
                break;
            case RELATIVE:
                this.cursor += pPosition;
                break;
            case END:
                this.cursor = this.value.length() + pPosition;
        }

        this.cursor = Tools.clamp(this.cursor, 0, this.value.length());
        this.cursorListener.run();
        if (!this.selecting) {
            this.selectCursor = this.cursor;
        }
    }

    public void seekCursorLine(int pOffset) {
        if (pOffset != 0) {
            int i = this.font.getStringWidth(this.value.substring(Math.min(this.getCursorLineView().beginIndex, this.value.length() - 1), Math.min(this.cursor, this.value.length() - 1))) + 2;
            StringView multilinetextfield$stringview = this.getCursorLineView(pOffset);
            int j = this.font
                    .trimStringToWidth(this.value.substring(multilinetextfield$stringview.beginIndex, multilinetextfield$stringview.endIndex), i)
                    .length();
            this.seekCursor(Whence.ABSOLUTE, multilinetextfield$stringview.beginIndex + j);
        }
    }

    public void seekCursorToPoint(double pX, double pY) {
        int i = (int) Math.floor(pX);
        int j = (int) Math.floor(pY / 9.0);
        StringView multilinetextfield$stringview = this.displayLines.get(Tools.clamp(j, 0, this.displayLines.size() - 1));
        if (multilinetextfield$stringview.endIndex > this.value.length()) {
            return;
        }
        int k = this.font
                .trimStringToWidth(this.value.substring(multilinetextfield$stringview.beginIndex, multilinetextfield$stringview.endIndex), i)
                .length();
        int hoverCharHalfWidth = 0;

        if (multilinetextfield$stringview.beginIndex + k > this.value.length()) {
            return;
        }

        if (k != 0) {
            hoverCharHalfWidth = this.font.getStringWidth(
                    this.font.trimStringToWidth(this.value.substring(multilinetextfield$stringview.beginIndex + k - 1, multilinetextfield$stringview.beginIndex + k), 100)) / 2;
        }
        k = this.font
                .trimStringToWidth(this.value.substring(multilinetextfield$stringview.beginIndex, multilinetextfield$stringview.endIndex), i + hoverCharHalfWidth)
                .length();
        this.seekCursor(Whence.ABSOLUTE, multilinetextfield$stringview.beginIndex + k);
    }

    public boolean keyPressed(int pKeyCode) {
        this.selecting = GuiScreen.isShiftKeyDown();
        if (GuiScreen.isKeyComboCtrlA(pKeyCode)) {
            this.cursor = this.value.length();
            this.selectCursor = 0;
            return true;
        } else if (GuiScreen.isKeyComboCtrlC(pKeyCode)) {
            GuiScreen.setClipboardString(this.getSelectedText());
            return true;
        } else if (GuiScreen.isKeyComboCtrlV(pKeyCode)) {
            this.insertText(GuiScreen.getClipboardString());
            return true;
        } else if (GuiScreen.isKeyComboCtrlX(pKeyCode)) {
            GuiScreen.setClipboardString(this.getSelectedText());
            this.insertText("");
            return true;
        } else {
            switch (pKeyCode) {
                case Keyboard.KEY_RETURN:
                case Keyboard.KEY_NUMPADENTER:
                    this.insertText("\n");
                    return true;
                case Keyboard.KEY_BACK:
                    if (GuiScreen.isCtrlKeyDown()) {
                        StringView multilinetextfield$stringview3 = this.getPreviousWord();
                        this.deleteText(multilinetextfield$stringview3.beginIndex - this.cursor);
                    } else {
                        this.deleteText(-1);
                    }
                    return true;
                case Keyboard.KEY_DELETE:
                    if (GuiScreen.isCtrlKeyDown()) {
                        StringView multilinetextfield$stringview2 = this.getNextWord();
                        this.deleteText(multilinetextfield$stringview2.beginIndex - this.cursor);
                    } else {
                        this.deleteText(1);
                    }
                    return true;
                case Keyboard.KEY_RIGHT:
                    if (GuiScreen.isCtrlKeyDown()) {
                        StringView multilinetextfield$stringview1 = this.getNextWord();
                        this.seekCursor(Whence.ABSOLUTE, multilinetextfield$stringview1.beginIndex);
                    } else {
                        this.seekCursor(Whence.RELATIVE, 1);
                    }
                    return true;
                case Keyboard.KEY_LEFT:
                    if (GuiScreen.isCtrlKeyDown()) {
                        StringView multilinetextfield$stringview = this.getPreviousWord();
                        this.seekCursor(Whence.ABSOLUTE, multilinetextfield$stringview.beginIndex);
                    } else {
                        this.seekCursor(Whence.RELATIVE, -1);
                    }
                    return true;
                case Keyboard.KEY_DOWN:
                    if (!GuiScreen.isCtrlKeyDown()) {
                        this.seekCursorLine(1);
                    }
                    return true;
                case Keyboard.KEY_UP:
                    if (!GuiScreen.isCtrlKeyDown()) {
                        this.seekCursorLine(-1);
                    }
                    return true;
                case Keyboard.KEY_PRIOR:
                    this.seekCursor(Whence.ABSOLUTE, 0);
                    return true;
                case Keyboard.KEY_NEXT:
                    this.seekCursor(Whence.END, 0);
                    return true;
                case Keyboard.KEY_HOME:
                    if (GuiScreen.isCtrlKeyDown()) {
                        this.seekCursor(Whence.ABSOLUTE, 0);
                    } else {
                        this.seekCursor(Whence.ABSOLUTE, this.getCursorLineView().beginIndex);
                    }
                    return true;
                case Keyboard.KEY_END:
                    if (GuiScreen.isCtrlKeyDown()) {
                        this.seekCursor(Whence.END, 0);
                    } else {
                        this.seekCursor(Whence.ABSOLUTE, this.getCursorLineView().endIndex);
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    public Iterable<StringView> iterateLines() {
        return this.displayLines;
    }

    public boolean hasSelection() {
        return this.selectCursor != this.cursor;
    }

    @VisibleForTesting
    public String getSelectedText() {
        StringView multilinetextfield$stringview = this.getSelected();
        return this.value.substring(multilinetextfield$stringview.beginIndex, multilinetextfield$stringview.endIndex);
    }

    private StringView getCursorLineView() {
        return this.getCursorLineView(0);
    }

    private StringView getCursorLineView(int pOffset) {
        int i = this.getLineAtCursor();
        if (i < 0) {
            throw new IllegalStateException("Cursor is not within text (cursor = " + this.cursor + ", length = " + this.value.length() + ")");
        } else {
            return this.displayLines.get(Tools.clamp(i + pOffset, 0, this.displayLines.size() - 1));
        }
    }

    @VisibleForTesting
    public StringView getPreviousWord() {
        if (this.value.isEmpty()) {
            return StringView.EMPTY;
        } else {
            int i = Tools.clamp(this.cursor, 0, this.value.length() - 1);

            while (i > 0 && Character.isWhitespace(this.value.charAt(i - 1))) {
                i--;
            }

            while (i > 0 && !Character.isWhitespace(this.value.charAt(i - 1))) {
                i--;
            }

            return new StringView(i, this.getWordEndPosition(i));
        }
    }

    @VisibleForTesting
    public StringView getNextWord() {
        if (this.value.isEmpty()) {
            return StringView.EMPTY;
        } else {
            int i = Tools.clamp(this.cursor, 0, this.value.length() - 1);

            while (i < this.value.length() && !Character.isWhitespace(this.value.charAt(i))) {
                i++;
            }

            while (i < this.value.length() && Character.isWhitespace(this.value.charAt(i))) {
                i++;
            }

            return new StringView(i, this.getWordEndPosition(i));
        }
    }

    private int getWordEndPosition(int pCursor) {
        int i = pCursor;

        while (i < this.value.length() && !Character.isWhitespace(this.value.charAt(i))) {
            i++;
        }

        return i;
    }

    private void onValueChange() {
        this.reflowDisplayLines();
        this.valueListener.accept(this.value);
        this.cursorListener.run();
    }

    protected void reflowDisplayLines() {
        this.displayLines.clear();
        if (this.value.isEmpty()) {
            this.displayLines.add(StringView.EMPTY);
        } else {
            List<String> lines = new ArrayList<>();
            for (String paragraph : this.value.split("\n", -1)) { // 按 \n 分割段落
                lines.addAll(this.font.listFormattedStringToWidth(paragraph, this.width));
            }
            int startIndex = 0;
            for (String line : lines) {
                int endIndex = startIndex + line.length();
                this.displayLines.add(new StringView(startIndex, endIndex));
                startIndex = endIndex + 1;
            }
//            this.font.
//                    .getSplitter()
//                    .splitLines(
//                            this.value,
//                            this.width,
//                            Style.EMPTY,
//                            false,
//                            (p_239846_, p_239847_, p_239848_) -> this.displayLines.add(new MultilineTextField.StringView(p_239847_, p_239848_))
//                    );
            if (this.value.charAt(this.value.length() - 1) == '\n') {
                this.displayLines.add(new StringView(this.value.length(), this.value.length()));
            }
        }
    }

    private String truncateFullText(String pFullText) {
        return this.hasCharacterLimit() ? Tools.truncateStringIfNecessary(pFullText, this.characterLimit, false) : pFullText;
    }

    private String truncateInsertionText(String pText) {
        if (this.hasCharacterLimit()) {
            int i = this.characterLimit - this.value.length();
            return Tools.truncateStringIfNecessary(pText, i, false);
        } else {
            return pText;
        }
    }

    @SideOnly(Side.CLIENT)
    public static class StringView {
        int beginIndex;
        int endIndex;
        public static StringView EMPTY = new StringView(0, 0);

        public StringView(int beginIndex, int endIndex) {
            this.beginIndex = beginIndex;
            this.endIndex = endIndex;
        }

        public int beginIndex() {
            return beginIndex;
        }

        public int endIndex() {
            return endIndex;
        }
    }
}
