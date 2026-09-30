package ca.teamdman.sfm.client.text_editor;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;

/**
 * Represents a cursor in a text editor, with optional selection.
 */
public record Cursor(
        Caret tail,
        Caret head
) {

    @Override
    public String toString() {
        return "Cursor{" +
               "tail=" + tail +
               ", head=" + head +
               '}';
    }

    public Cursor(int position) {
        this(
                new Caret(position, 0),
                new Caret(position, 0)
        );
    }

    public Cursor growSelectionLeft(Int2IntFunction lineLengths) {
        if (isHeadOnLeft()) {
            return new Cursor(tail(), head().moveLeftOneCharacter(lineLengths));
        } else {
            return new Cursor(tail().moveLeftOneCharacter(lineLengths), head());
        }
    }

    public Cursor growSelectionRight(Int2IntFunction lineLengths, int numLines) {
        if (isHeadOnLeft()) {
            return new Cursor(tail(), head().moveRightOneCharacter(lineLengths, numLines));
        } else {
            return new Cursor(tail().moveRightOneCharacter(lineLengths, numLines), head());
        }
    }

    public boolean isHeadOnLeft() {
        return head().compareTo(tail()) > 0;
    }

    public boolean hasSelection() {
        return !head().equals(tail());
    }

    public Caret getBeginning() {
        if (head().compareTo(tail()) <= 0) {
            // tail comes after, so head is the beginning
            return head();
        } else {
            return tail();
        }
    }

    public Caret getEnd() {
        if (head().compareTo(tail()) <= 0) {
            // tail comes after, so tail is the end
            return tail();
        } else {
            return head();
        }
    }
}
