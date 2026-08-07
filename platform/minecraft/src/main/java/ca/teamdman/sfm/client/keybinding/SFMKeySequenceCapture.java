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
    }

    public void capture(int keyCode, Set<SFMKeyModifier> modifiers, long nowMillis) {
        flushExpired(nowMillis);
        commitPendingEscapes();
        strokes.add(new SFMKeyStroke(keyCode, modifiers));
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
