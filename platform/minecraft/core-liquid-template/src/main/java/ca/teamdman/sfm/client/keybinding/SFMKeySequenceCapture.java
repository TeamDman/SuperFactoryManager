package ca.teamdman.sfm.client.keybinding;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Stateful, testable recorder for physical key sequences. */
public final class SFMKeySequenceCapture {
    public static final long ESCAPE_WINDOW_MILLIS = 3000L;

    public enum EscapeResult { PENDING, CAPTURED, CANCELLED }

    private final List<SFMKeyStroke> strokes = new ArrayList<>();
    private int pendingEscapes;
    private long firstPendingEscape = Long.MIN_VALUE;
    private int focusedToken = -1;

    public List<SFMKeyStroke> strokes() {
        return List.copyOf(strokes);
    }

    public boolean isEmpty() {
        return strokes.isEmpty() && pendingEscapes == 0;
    }

    public void clear() {
        strokes.clear();
        pendingEscapes = 0;
        firstPendingEscape = Long.MIN_VALUE;
        focusedToken = -1;
    }

    public int tokenCount() {
        int count = pendingEscapes;
        for (SFMKeyStroke stroke : strokes) count += stroke.modifiers().size() + 1;
        return count;
    }

    public int focusedToken() {
        return focusedToken;
    }

    public boolean hasFocusedToken() {
        return focusedToken >= 0 && focusedToken < tokenCount();
    }

    public boolean focusToken(int tokenIndex) {
        if (tokenCount() == 0) {
            focusedToken = -1;
            return false;
        }
        int next = Math.max(0, Math.min(tokenIndex, tokenCount() - 1));
        boolean changed = focusedToken != next;
        focusedToken = next;
        return changed;
    }

    public boolean moveFocusedToken(int delta) {
        if (tokenCount() == 0) return false;
        return focusToken(focusedToken < 0 ? (delta < 0 ? tokenCount() - 1 : 0) : focusedToken + delta);
    }

    public boolean removeFocusedToken() {
        if (!hasFocusedToken()) return false;
        Token token = tokenAt(focusedToken);
        boolean removed = removeToken(token.strokeIndex(), token.tokenIndex());
        if (removed) focusToken(Math.min(focusedToken, tokenCount() - 1));
        return removed;
    }

    public void capture(int keyCode, Set<SFMKeyModifier> modifiers, long nowMillis) {
        flushExpired(nowMillis);
        commitPendingEscapes();
        strokes.add(new SFMKeyStroke(keyCode, modifiers));
        focusedToken = -1;
    }

    public EscapeResult escape(long nowMillis) {
        flushExpired(nowMillis);
        if (pendingEscapes == 0) firstPendingEscape = nowMillis;
        pendingEscapes++;
        if (pendingEscapes >= 3) {
            clear();
            return EscapeResult.CANCELLED;
        }
        return EscapeResult.PENDING;
    }

    public boolean flushExpired(long nowMillis) {
        if (pendingEscapes == 0 || nowMillis - firstPendingEscape <= ESCAPE_WINDOW_MILLIS) return false;
        commitPendingEscapes();
        return true;
    }

    /** Commits one or two Escape strokes when the cancellation window is no longer being used. */
    public boolean commitPendingEscapes() {
        if (pendingEscapes == 0) return false;
        for (int i = 0; i < pendingEscapes; i++) {
            strokes.add(SFMKeyStroke.of(GLFW.GLFW_KEY_ESCAPE));
        }
        pendingEscapes = 0;
        firstPendingEscape = Long.MIN_VALUE;
        return true;
    }

    public boolean removeToken(int strokeIndex, int tokenIndex) {
        if (strokeIndex < 0 || strokeIndex >= strokes.size() || tokenIndex < 0) return false;
        SFMKeyStroke stroke = strokes.get(strokeIndex);
        int modifierCount = orderedModifierCount(stroke);
        if (tokenIndex < modifierCount) {
            EnumSet<SFMKeyModifier> modifiers = EnumSet.copyOf(stroke.modifiers());
            modifiers.remove(orderedModifier(stroke, tokenIndex));
            strokes.set(strokeIndex, new SFMKeyStroke(stroke.keyCode(), modifiers));
        } else if (tokenIndex == modifierCount) {
            strokes.remove(strokeIndex);
        } else {
            return false;
        }
        return true;
    }

    private Token tokenAt(int flatIndex) {
        int cursor = 0;
        for (int strokeIndex = 0; strokeIndex < strokes.size(); strokeIndex++) {
            SFMKeyStroke stroke = strokes.get(strokeIndex);
            int count = stroke.modifiers().size() + 1;
            if (flatIndex < cursor + count) {
                return new Token(strokeIndex, flatIndex - cursor);
            }
            cursor += count;
        }
        throw new IllegalArgumentException("Token index is outside the capture");
    }

    private record Token(int strokeIndex, int tokenIndex) {
    }

    private static int orderedModifierCount(SFMKeyStroke stroke) {
        return stroke.modifiers().size();
    }

    private static SFMKeyModifier orderedModifier(SFMKeyStroke stroke, int index) {
        List<SFMKeyModifier> ordered = List.of(
                SFMKeyModifier.CONTROL,
                SFMKeyModifier.ALT,
                SFMKeyModifier.SHIFT,
                SFMKeyModifier.SUPER);
        return ordered.stream().filter(stroke.modifiers()::contains).toList().get(index);
    }
}
