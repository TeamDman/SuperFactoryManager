package ca.teamdman.sfm.client.text_editor;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;

public record Caret(int lineIndex, int gapIndex) {
    /**
     * Compares two {@code caret} values numerically.
     * @return the value {@code 0} if {@code this == other};
     *         a value less than {@code 0} if {@code other comes after this}; and
     *         a value greater than {@code 0} if {@code this comes after other}
     */
    public int compareTo(Caret other) {
        if (lineIndex != other.lineIndex) {
            return Integer.compare(lineIndex, other.lineIndex);
        }
        return Integer.compare(gapIndex, other.gapIndex);
    }

    public Caret moveLeftOneCharacter(Int2IntFunction lineLengths) {
        if (this.lineIndex() == 0 && this.gapIndex() == 0) {
            // Already at the start of the document, unable to grow left
            return this;
        } else if (this.gapIndex() == 0) {
            // Move to the end of the previous line
            int newLineIndex = this.lineIndex() - 1;
            int newGapIndex = lineLengths.get(newLineIndex);
            return new Caret(newLineIndex, newGapIndex);
        } else {
            // Move one character left
            int newGapIndex = this.gapIndex() - 1;
            return new Caret(this.lineIndex(), newGapIndex);
        }
    }

    public Caret moveRightOneCharacter(Int2IntFunction lineLengths, int numLines) {
        if (this.lineIndex() >= numLines - 1 && this.gapIndex() >= lineLengths.get(this.lineIndex())) {
            // Already at the end of the document, unable to grow right
            return this;
        } else if (this.gapIndex() >= lineLengths.get(this.lineIndex())) {
            // Move to the start of the next line
            int newLineIndex = this.lineIndex() + 1;
            int newGapIndex = 0;
            return new Caret(newLineIndex, newGapIndex);
        } else {
            // Move one character right
            int newGapIndex = this.gapIndex() + 1;
            return new Caret(this.lineIndex(), newGapIndex);
        }
    }

    public Caret moveDownOneLine(Int2IntFunction lineLengths, int numLines) {
        if (this.lineIndex() >= numLines - 1) {
            // Already at the last line, unable to move down
            return this;
        } else {
            // Move down one line, keeping the same gap index
            int newLineIndex = this.lineIndex() + 1;
            int newGapIndex = Math.min(this.gapIndex(), lineLengths.get(newLineIndex));
            return new Caret(newLineIndex, newGapIndex);
        }
    }

    public Caret moveUpOneLine(Int2IntFunction lineLengths) {
        if (this.lineIndex() == 0) {
            // Already at the first line, unable to move up
            return this;
        } else {
            // Move up one line, keeping the same gap index
            int newLineIndex = this.lineIndex() - 1;
            int newGapIndex = Math.min(this.gapIndex(), lineLengths.get(newLineIndex));
            return new Caret(newLineIndex, newGapIndex);
        }
    }
}
