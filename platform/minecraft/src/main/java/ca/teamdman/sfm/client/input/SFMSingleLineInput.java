package ca.teamdman.sfm.client.input;

import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.*;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Shared editing state for a single-line field; independent of Minecraft and font layout. */
public final class SFMSingleLineInput {
    private final int maximumLength;
    private final SFMDocumentHistorySession history;
    private final Predicate<String> validator;
    private String text;
    private int cursor;
    private int anchor;
    private char pendingHighSurrogate;
    private long sequence;

    public SFMSingleLineInput(String initialText) {
        this(initialText, 32768, true);
    }

    /** Set recordHistory=false when a surrounding document host already owns history. */
    public SFMSingleLineInput(String initialText, int maximumLength, boolean recordHistory) {
        this(initialText, maximumLength, recordHistory, value -> true);
    }

    public SFMSingleLineInput(String initialText, int maximumLength, boolean recordHistory,
                              Predicate<String> validator) {
        if (maximumLength < 0) throw new IllegalArgumentException("Negative field length");
        this.maximumLength = maximumLength;
        this.validator = Objects.requireNonNull(validator);
        text = truncate(singleLine(initialText), maximumLength);
        cursor = anchor = text.length();
        history = recordHistory ? SFMDocumentHistorySession.create(
                new SessionIdentity("field-" + UUID.randomUUID(), "single-line", Optional.empty()), snapshot()) : null;
    }

    public String text() { return text; }
    public int cursor() { return cursor; }
    public int anchor() { return anchor; }
    public int selectionStart() { return Math.min(cursor, anchor); }
    public int selectionEnd() { return Math.max(cursor, anchor); }
    public String selectedText() { return text.substring(selectionStart(), selectionEnd()); }
    public Optional<SFMDocumentHistorySession> history() { return Optional.ofNullable(history); }

    /** Synchronize an external edit/caret, preserving state when the value has not changed. */
    public void synchronize(String value, int active, int fixed) {
        String clean = truncate(singleLine(value), maximumLength);
        int nextCursor = boundary(clean, active);
        int nextAnchor = boundary(clean, fixed);
        if (text.equals(clean) && cursor == nextCursor && anchor == nextAnchor) return;
        boolean contentChanged = !text.equals(clean);
        text = clean;
        cursor = nextCursor;
        anchor = nextAnchor;
        pendingHighSurrogate = 0;
        record(contentChanged ? MutationKind.ACTION : MutationKind.SELECTION_CHANGE, EditDirection.NONE, Optional.empty());
    }

    public void setText(String value) {
        String clean = truncate(singleLine(value), maximumLength);
        if (!text.equals(clean)) synchronize(clean, clean.length(), clean.length());
    }

    public void select(int fixed, int active) {
        synchronize(text, active, fixed);
    }

    public void selectAll() { select(0, text.length()); }
    public void clear() { selectAll(); replace("", MutationKind.ACTION, EditDirection.NONE); }

    public void insert(String value) { replace(singleLine(value), MutationKind.PASTE, EditDirection.FORWARD); }

    public boolean charTyped(char character, int modifiers) {
        if ((modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0
                || Character.isISOControl(character)) {
            pendingHighSurrogate = 0;
            return false;
        }
        if (Character.isHighSurrogate(character)) {
            pendingHighSurrogate = character;
            return true;
        }
        String value;
        if (Character.isLowSurrogate(character)) {
            if (pendingHighSurrogate == 0) return false;
            value = new String(new char[]{pendingHighSurrogate, character});
        } else value = Character.toString(character);
        pendingHighSurrogate = 0;
        replace(value, MutationKind.TYPE, EditDirection.FORWARD);
        return true;
    }

    /** Host routes focus/completion/submit first; edit operations consume only their own keys. */
    public boolean keyPressed(int key, int modifiers, Supplier<String> clipboardRead, Consumer<String> clipboardWrite) {
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if ((modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) return false;
        pendingHighSurrogate = 0;
        if (control) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> { selectAll(); return true; }
                case GLFW.GLFW_KEY_C -> { clipboardWrite.accept(selectedText()); return true; }
                case GLFW.GLFW_KEY_X -> {
                    clipboardWrite.accept(selectedText());
                    if (cursor != anchor) replace("", MutationKind.DELETE_BACKWARD, EditDirection.BACKWARD);
                    return true;
                }
                case GLFW.GLFW_KEY_V -> { insert(clipboardRead.get()); return true; }
                case GLFW.GLFW_KEY_Z -> { return moveHistory(shift); }
                case GLFW.GLFW_KEY_Y -> { return moveHistory(true); }
                default -> { }
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                boolean forward = key == GLFW.GLFW_KEY_RIGHT;
                int target;
                if (!shift && !control && cursor != anchor) target = forward ? selectionEnd() : selectionStart();
                else target = control ? wordDestination(text, cursor, forward)
                        : step(text, cursor, forward);
                select(shift ? anchor : target, target);
            }
            case GLFW.GLFW_KEY_HOME -> select(shift ? anchor : 0, 0);
            case GLFW.GLFW_KEY_END -> select(shift ? anchor : text.length(), text.length());
            case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> {
                boolean forward = key == GLFW.GLFW_KEY_DELETE;
                int originalAnchor = anchor;
                if (cursor == anchor) {
                    int destination = control ? wordDestination(text, cursor, forward) : step(text, cursor, forward);
                    // This transient deletion range is part of one edit, not another undo operation.
                    anchor = destination;
                }
                if (!replace("", forward ? MutationKind.DELETE_FORWARD : MutationKind.DELETE_BACKWARD,
                        forward ? EditDirection.FORWARD : EditDirection.BACKWARD)) anchor = originalAnchor;
            }
            default -> { return false; }
        }
        return true;
    }

    private boolean replace(String inserted, MutationKind kind, EditDirection direction) {
        int start = selectionStart();
        int end = selectionEnd();
        String accepted = truncate(inserted, maximumLength - text.length() + end - start);
        String next = text.substring(0, start) + accepted + text.substring(end);
        if (!validator.test(next)) return false;
        String removed = text.substring(start, end);
        if (next.equals(text) && cursor == start + accepted.length() && anchor == cursor) return true;
        text = next;
        cursor = anchor = start + accepted.length();
        record(kind, direction, Optional.of(accepted.isEmpty() ? removed : accepted));
        return true;
    }

    private void record(MutationKind kind, EditDirection direction, Optional<String> changedText) {
        if (history == null) return;
        String id = "field-edit-" + ++sequence;
        history.append(new MutationRequest(id, kind, direction, snapshot(), Optional.empty(), Optional.empty(),
                changedText, new MutationProvenance("field", id, Math.max(0, System.nanoTime() / 50_000_000L),
                "single-line", List.of()), Optional.empty()));
    }

    private DocumentState snapshot() {
        return new DocumentState(text, List.of(new LogicalSelection("primary",
                LogicalPoint.at(text, text.codePointCount(0, anchor)),
                LogicalPoint.at(text, text.codePointCount(0, cursor)))), Optional.of("primary"));
    }

    private boolean moveHistory(boolean redo) {
        if (history == null) return false;
        String before = text;
        while (true) {
            String request = "field-history-" + ++sequence;
            var moved = redo ? history.redo(Optional.empty(), "field", request, List.of())
                    : history.undo("field", request, List.of());
            if (moved.status() != SFMDocumentHistorySession.HeadMoveStatus.APPLIED) break;
            DocumentState state = history.currentState();
            text = state.text();
            LogicalSelection selection = state.selections().get(0);
            cursor = text.offsetByCodePoints(0, selection.active().codePointOffset());
            anchor = text.offsetByCodePoints(0, selection.anchor().codePointOffset());
            if (!text.equals(before)) break;
        }
        return true;
    }

    /** Word characters include underscores and combining marks; punctuation is an explicit stop. */
    public static int wordDestination(String value, int position, boolean forward) {
        int at = boundary(value, position);
        if (forward) {
            if (at == value.length()) return at;
            int kind = category(value.codePointAt(at));
            do { at = step(value, at, true); }
            while (kind != 2 && at < value.length() && category(value.codePointAt(at)) == kind);
        } else {
            while (at > 0 && Character.isWhitespace(value.codePointBefore(at))) at = step(value, at, false);
            if (at == 0) return 0;
            int kind = category(value.codePointBefore(at));
            do { at = step(value, at, false); }
            while (kind != 2 && at > 0 && category(value.codePointBefore(at)) == kind);
        }
        return at;
    }

    private static int category(int codePoint) {
        if (Character.isWhitespace(codePoint)) return 0;
        int kind = Character.getType(codePoint);
        return Character.isLetterOrDigit(codePoint) || codePoint == '_'
                || kind == Character.NON_SPACING_MARK || kind == Character.COMBINING_SPACING_MARK
                || kind == Character.ENCLOSING_MARK ? 1 : 2;
    }

    private static int step(String value, int at, boolean forward) {
        return forward ? at == value.length() ? at : value.offsetByCodePoints(at, 1)
                : at == 0 ? at : value.offsetByCodePoints(at, -1);
    }

    private static int boundary(String value, int position) {
        int at = Math.max(0, Math.min(value.length(), position));
        return at > 0 && at < value.length() && Character.isLowSurrogate(value.charAt(at))
                && Character.isHighSurrogate(value.charAt(at - 1)) ? at - 1 : at;
    }

    private static String truncate(String value, int maximumLength) {
        return value.substring(0, boundary(value, Math.min(value.length(), maximumLength)));
    }

    private static String singleLine(String value) {
        Objects.requireNonNull(value, "field value");
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) continue;
            if (!Character.isISOControl(codePoint) && codePoint != 0x2028 && codePoint != 0x2029)
                result.appendCodePoint(codePoint);
        }
        return result.toString();
    }
}
