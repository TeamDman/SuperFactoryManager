package ca.teamdman.sfm.client.text_editor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Small Vim-style command subset; this is not a Neovim engine or compatibility layer. */
public final class SFMVimModalState {
    public enum Mode { NORMAL, INSERT }
    public record Snapshot(String text, int cursor) {
        public Snapshot {
            Objects.requireNonNull(text);
            cursor = Math.max(0, Math.min(text.length(), cursor));
            if (cursor > 0 && cursor < text.length()
                    && Character.isLowSurrogate(text.charAt(cursor))
                    && Character.isHighSurrogate(text.charAt(cursor - 1))) cursor--;
        }
    }

    private static final int HISTORY_LIMIT = 100;
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    private Snapshot current;
    private Snapshot insertStart;
    private Mode mode = Mode.NORMAL;
    private boolean pendingDelete;
    private int desiredColumn = -1;

    public SFMVimModalState(String text) {
        current = normal(new Snapshot(text, 0));
    }

    public Snapshot snapshot() { return current; }
    public Mode mode() { return mode; }
    public boolean pendingDelete() { return pendingDelete; }

    /** Accept native widget edits verbatim. One Insert session is one undo step. */
    public void acceptWidgetValue(String text, int cursor) {
        Snapshot next = new Snapshot(text, cursor);
        if (mode == Mode.NORMAL) {
            if (!current.text().equals(text)) recordChange(current);
            next = normal(next);
        }
        if (current.cursor() != next.cursor()) desiredColumn = -1;
        current = next;
    }

    /** Escape always leaves Insert or cancels a pending command; it never closes a document. */
    public void escape() {
        if (mode == Mode.INSERT) {
            if (!insertStart.text().equals(current.text())) recordChange(insertStart);
            int cursor = current.cursor();
            if (cursor > lineStart(current.text(), cursor)) cursor = previous(current.text(), cursor);
            current = normal(new Snapshot(current.text(), cursor));
            insertStart = null;
        }
        mode = Mode.NORMAL;
        pendingDelete = false;
        desiredColumn = -1;
    }

    /** Returns true for a recognized command, including a blocked read-only edit. */
    public boolean command(char command, boolean readOnly) {
        if (mode != Mode.NORMAL) return false;
        boolean wasPending = pendingDelete;
        boolean deleteLine = wasPending && command == 'd';
        pendingDelete = false;
        if (wasPending && !deleteLine) return true;
        String text = current.text();
        int cursor = current.cursor();
        int start = lineStart(text, cursor);
        int end = lineEnd(text, cursor);
        if (command != 'j' && command != 'k') desiredColumn = -1;
        switch (command) {
            case 'h' -> move(cursor > start ? previous(text, cursor) : cursor);
            case 'l' -> move(cursor < end ? next(text, cursor) : cursor);
            case 'j' -> vertical(1);
            case 'k' -> vertical(-1);
            case '0' -> move(start);
            case '$' -> move(end);
            case 'w' -> move(wordForward(text, cursor));
            case 'b' -> move(wordBack(text, cursor));
            case 'i', 'a', 'I', 'A' -> {
                if (readOnly) return true;
                insertStart = current;
                int insertion = switch (command) {
                    case 'a' -> cursor < end ? next(text, cursor) : cursor;
                    case 'A' -> end;
                    case 'I' -> firstNonBlank(text, start, end);
                    default -> cursor;
                };
                current = new Snapshot(text, insertion);
                mode = Mode.INSERT;
            }
            case 'x' -> {
                if (!readOnly && cursor < end) replace(cursor, next(text, cursor), "");
            }
            case 'd' -> {
                if (readOnly) return true;
                if (!deleteLine) pendingDelete = true;
                else if (end < text.length()) replace(start, end + 1, "");
                else if (start > 0) replace(start - 1, end, "");
                else replace(start, end, "");
            }
            case 'u' -> { if (!readOnly) undo(); }
            default -> { return false; }
        }
        return true;
    }

    public void undo() {
        escape();
        if (undo.isEmpty()) return;
        redo.addLast(current);
        current = normal(undo.removeLast());
    }

    public void redo() {
        escape();
        if (redo.isEmpty()) return;
        addUndo(current);
        current = normal(redo.removeLast());
    }

    private void replace(int start, int end, String replacement) {
        String text = current.text();
        if (start == end && replacement.isEmpty()) return;
        recordChange(current);
        current = normal(new Snapshot(text.substring(0, start) + replacement + text.substring(end), start));
    }

    private void recordChange(Snapshot before) {
        addUndo(before);
        redo.clear();
    }

    private void addUndo(Snapshot before) {
        undo.addLast(before);
        if (undo.size() > HISTORY_LIMIT) undo.removeFirst();
    }

    private void move(int cursor) {
        current = normal(new Snapshot(current.text(), cursor));
    }

    private void vertical(int direction) {
        String text = current.text();
        int start = lineStart(text, current.cursor());
        if (desiredColumn < 0) desiredColumn = text.codePointCount(start, current.cursor());
        int targetStart;
        if (direction < 0) {
            if (start == 0) return;
            targetStart = lineStart(text, start - 1);
        } else {
            int end = lineEnd(text, current.cursor());
            if (end == text.length()) return;
            targetStart = end + 1;
        }
        int end = lineEnd(text, targetStart);
        int column = Math.min(desiredColumn, text.codePointCount(targetStart, end));
        move(text.offsetByCodePoints(targetStart, column));
    }

    private static Snapshot normal(Snapshot value) {
        int start = lineStart(value.text(), value.cursor());
        int end = lineEnd(value.text(), value.cursor());
        return value.cursor() == end && end > start
                ? new Snapshot(value.text(), previous(value.text(), end)) : value;
    }

    private static int lineStart(String text, int cursor) { return text.lastIndexOf('\n', cursor - 1) + 1; }
    private static int lineEnd(String text, int cursor) {
        int newline = text.indexOf('\n', cursor);
        return newline < 0 ? text.length() : newline;
    }
    private static int next(String text, int cursor) { return text.offsetByCodePoints(cursor, 1); }
    private static int previous(String text, int cursor) { return text.offsetByCodePoints(cursor, -1); }
    private static int firstNonBlank(String text, int start, int end) {
        while (start < end && Character.isWhitespace(text.codePointAt(start))) start = next(text, start);
        return start;
    }
    private static int wordClass(int codePoint) {
        if (Character.isWhitespace(codePoint)) return 0;
        return Character.isLetterOrDigit(codePoint) || codePoint == '_' ? 1 : 2;
    }
    private static int wordForward(String text, int cursor) {
        if (cursor == text.length()) return cursor;
        int kind = wordClass(text.codePointAt(cursor));
        while (cursor < text.length() && wordClass(text.codePointAt(cursor)) == kind) cursor = next(text, cursor);
        while (cursor < text.length() && wordClass(text.codePointAt(cursor)) == 0) cursor = next(text, cursor);
        return cursor;
    }
    private static int wordBack(String text, int cursor) {
        if (cursor == 0) return 0;
        cursor = previous(text, cursor);
        while (cursor > 0 && wordClass(text.codePointAt(cursor)) == 0) cursor = previous(text, cursor);
        int kind = wordClass(text.codePointAt(cursor));
        while (cursor > 0 && wordClass(text.codePointAt(previous(text, cursor))) == kind) cursor = previous(text, cursor);
        return cursor;
    }
}
