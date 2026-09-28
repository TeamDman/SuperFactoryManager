package ca.teamdman.sfm.client.text_editor;

import it.unimi.dsi.fastutil.ints.*;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.IntervalSet;

import java.util.*;
import java.util.stream.Collectors;


public record TextEditContext(
        MultiCursor multiCursor,
        LinkedList<StringBuilder> lines
) {
    public TextEditContext() {
        this(new MultiCursor(), new LinkedList<>());
        lines.add(new StringBuilder());
    }

    public TextEditContext(String initialContent) {
        this(new MultiCursor(), new LinkedList<>());
        if (initialContent.isEmpty()) {
            lines.add(new StringBuilder());
        } else {
            String[] splitLines = initialContent.split("\n");
            for (String line : splitLines) {
                lines.add(new StringBuilder(line));
            }
        }
    }

    public Int2IntFunction lineLengths() {
        return Int2IntFunctions.primitive(x -> {
            if (x < 0 || x >= lines.size()) {
                return -1;
            }
            return lines.get(x).length();
        });
    }

    public TextEditContext copy() {
        return new TextEditContext(
                new MultiCursor(new ArrayDeque<>(multiCursor().cursors())),
                lines.stream()
                        .map(StringBuilder::new)
                        .collect(Collectors.toCollection(LinkedList::new))
        );
    }

    @Override
    public String toString() {
        return "TextEditContext{" +
               "multiCursor=" + multiCursor +
               ", lines=" + lines.size() +
               ", length=" + getCharacterCount() +
               '}';
    }

    public int getCharacterCount() {
        return lines.stream().mapToInt(StringBuilder::length).sum() + lines().size() - 1;
    }

    public void assertInvariants() {
        if (multiCursor().cursors().isEmpty()) {
            throw new IllegalStateException("Must have at least one cursor.");
        }
        for (Cursor cursor : multiCursor().cursors()) {
            if (cursor == null) {
                throw new IllegalStateException("Cursor cannot be null.");
            }
            if (cursor.head().lineIndex() < 0) {
                throw new IllegalStateException("Cursor head line cannot be negative.");
            }
            if (cursor.tail().lineIndex() < 0) {
                throw new IllegalStateException("Cursor head line cannot be negative.");
            }
            if (lines.get(cursor.head().lineIndex()).length() < cursor.head().gapIndex()) {
                throw new IllegalStateException("Cursor head gap index cannot be greater than line length.");
            }
            if (lines.get(cursor.tail().lineIndex()).length() < cursor.tail().gapIndex()) {
                throw new IllegalStateException("Cursor tail gap index cannot be greater than line length.");
            }
        }
    }

    public Int2ObjectOpenHashMap<IntervalSet> selectedCharactersByLine() {
        Int2ObjectOpenHashMap<IntervalSet> rtn = new Int2ObjectOpenHashMap<>();
        int numLines = lines().size();
        for (int lineIndex = 0; lineIndex < numLines; lineIndex++) {
            IntervalSet selectedCharacterIndicesInLine = new IntervalSet();
            int lineLength = lines().get(lineIndex).length();
            for (Cursor cursor : multiCursor().cursors()) {
                if (!cursor.hasSelection()) continue;
                Caret beginning = cursor.getBeginning();
                Caret end = cursor.getEnd();
                if (beginning.lineIndex() == lineIndex && end.lineIndex() == lineIndex) {
                    // the selection is entirely on this line
                    selectedCharacterIndicesInLine.add(beginning.gapIndex(), end.gapIndex() - 1);
                } else if (beginning.lineIndex() < lineIndex && end.lineIndex() > lineIndex) {
                    // the selection begins on a previous line and ends on a later line
                    selectedCharacterIndicesInLine.add(0, lineLength - 1);
                } else if (beginning.lineIndex() < lineIndex && end.lineIndex() == lineIndex) {
                    // the selection begins on a previous line and ends on this line
                    selectedCharacterIndicesInLine.add(0, end.gapIndex() - 1);
                } else if (beginning.lineIndex() == lineIndex && end.lineIndex() > lineIndex) {
                    // the selection begins on this line and ends on a later line
                    selectedCharacterIndicesInLine.add(beginning.gapIndex(), lineLength - 1);
                }
            }
            if (!selectedCharacterIndicesInLine.isNil()) {
                rtn.put(lineIndex, selectedCharacterIndicesInLine);
            }
        }
        return rtn;
    }

    /**
     * @return The indices of the cursors that were modified by this operation.
     */
    @SuppressWarnings("ExtractMethodRecommender")
    public void deleteSelectedText() {
        // Remove all selected text from the lines.
        Int2ObjectOpenHashMap<IntervalSet> selectedCharactersByLine = selectedCharactersByLine();
        ListIterator<StringBuilder> lineIterator = lines().listIterator(lines().size());
        while (lineIterator.hasPrevious()) {
            int lineIndex = lineIterator.previousIndex();
            StringBuilder line = lineIterator.previous();
            IntervalSet selectedCharacterIndicesInLine = selectedCharactersByLine.get(lineIndex);
            if (selectedCharacterIndicesInLine != null) {
                // Remove from right to left to avoid index shifting issues.
                List<Interval> intervals = selectedCharacterIndicesInLine.getIntervals();
                ListIterator<Interval> intervalIterator = intervals.listIterator(intervals.size());
                while (intervalIterator.hasPrevious()) {
                    Interval interval = intervalIterator.previous();
                    line.delete(interval.a, interval.b);
                }
            }
        }

        // Join lines where the newline character was removed.
        IntervalSet linesToJoinIntoPrevious = new IntervalSet();
        for (Cursor cursor : multiCursor().cursors()) {
            if (!cursor.hasSelection()) continue;
            Caret beginning = cursor.getBeginning();
            Caret end = cursor.getEnd();
            if (beginning.lineIndex() < end.lineIndex()) {
                // The selection spans multiple lines, so we need to join them.
                linesToJoinIntoPrevious.add(beginning.lineIndex() + 1, end.lineIndex());
            }
        }
        if (linesToJoinIntoPrevious.contains(0)) {
            throw new IllegalStateException("Cannot join the first line into a previous line.");
        }

        lineIterator = lines().listIterator();
        lineIterator.next(); // skip the first line, as it cannot be joined into a previous line
        int lineIndex = 0;
        while (lineIterator.hasNext()) {
            StringBuilder line = lineIterator.next();
            if (linesToJoinIntoPrevious.contains(lineIndex)) {
                // Join this line with the previous line.
                lineIterator.remove();
                StringBuilder previousLine = lineIterator.previous();
                previousLine.append(line);
            }
            lineIndex += 1;
        }

        // Update cursors with selection to go to the beginning of selection
        IntervalSet modifiedCursorIndices = new IntervalSet();
        Iterator<Cursor> iterator = multiCursor().cursors().iterator();
        int cursorIndex = 0;
        while (iterator.hasNext()) {
            Cursor cursor = iterator.next();
            if (cursor.hasSelection()) {
                // Move the cursor to the beginning of the selection
                Caret newHead = cursor.getBeginning();
                Caret newTail = cursor.getBeginning(); // Tail is moved to head as selection is removed
                iterator.remove();
                multiCursor().cursors().add(new Cursor(newHead, newTail));
                modifiedCursorIndices.add(cursorIndex, cursorIndex);
            }
            cursorIndex += 1;
        }

        // assert postconditions
        for (Cursor cursor : multiCursor().cursors()) {
            if (cursor.hasSelection()) {
                throw new IllegalStateException("There should be no selections after removing selections.");
            }
        }
        assertInvariants();
//        return modifiedCursorIndices;

        // TODO: this method probably doesn't correctly reposition all cursors. Add test for this.
    }


    public void insertTextAtCursors(String text) {
        // TODO: add support for when text contains a newline character.
        deleteSelectedText();
        var caretsByLine = new Int2ObjectOpenHashMap<IntList>();
        for (Cursor cursor : multiCursor().cursors()) {
            if (cursor.hasSelection()) {
                throw new IllegalStateException("Cannot insert text at cursors with selections.");
            }
            caretsByLine
                    .computeIfAbsent(cursor.head().lineIndex(), k -> new IntArrayList())
                    .add(cursor.head().gapIndex());
        }
        caretsByLine.values().forEach(caretPositions -> caretPositions.sort(IntComparators.NATURAL_COMPARATOR));
        new StringBuilder().trimToSize();
        caretsByLine.forEach((lineIndex, carets) -> {
            StringBuilder line = lines().get(lineIndex);
            // reserve capacity for the new text to avoid multiple reallocations
            int oldLength = line.length();
            line.setLength(line.length() + carets.size() * text.length());
            line.setLength(oldLength);

            // Insert the text at each caret position in reverse order to avoid index shifting issues.
            IntListIterator gapIterator = carets.listIterator(carets.size());
            while (gapIterator.hasPrevious()) {
                int gapIndex = gapIterator.previousInt();
                // Insert the text at the current caret position.
                line.insert(gapIndex, text);
            }
        });
        assertInvariants();
    }

    public String getContent() {
        StringBuilder rtn = new StringBuilder();
        for (StringBuilder line : lines()) {
            rtn.append(line);
            if (line != lines().getLast()) {
                rtn.append('\n'); // add newline except for the last line
            }
        }
        return rtn.toString();
    }
}
