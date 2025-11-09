package ca.teamdman.sfm.client.text_editor;

import com.github.bsideup.jabel.Desugar;

import java.util.ArrayDeque;

@Desugar
public record MultiCursor(
        ArrayDeque<Cursor> cursors
) {
    public MultiCursor() {
        this(new ArrayDeque<>());
        cursors().add(new Cursor(0));
    }

    public void addCursor(
            int headLineIndex,
            int headGapIndex,
            int tailLineIndex,
            int tailGapIndex
    ) {
        cursors().add(new Cursor(new Caret(headLineIndex, headGapIndex), new Caret(tailLineIndex, tailGapIndex)));
    }

    @Override
    public String toString() {
        return "MultiCursor{" +
               "cursors=" + cursors +
               '}';
    }
}
