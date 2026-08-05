package ca.teamdman.sfm.client.terminal;

/**
 * Detects the terminal escape hatches without making a single Esc or Tab
 * unusable inside the hosted program.
 */
final class SFMTerminalFocusSequence {
    static final long WINDOW_NANOS = 1_500_000_000L;
    static final int REQUIRED_PRESSES = 3;

    enum Decision {
        FORWARD,
        HOST_ESCAPE,
        JAVA_FOCUS
    }

    enum HintKind {
        ESCAPE,
        TAB
    }

    record Hint(HintKind kind, int remaining) {
    }

    private int escapes;
    private int tabs;
    private long lastEscape;
    private long lastTab;

    Decision escape(long now) {
        tabs = 0;
        lastTab = 0;
        escapes = now - lastEscape <= WINDOW_NANOS ? escapes + 1 : 1;
        lastEscape = now;
        if (escapes >= REQUIRED_PRESSES) {
            escapes = 0;
            return Decision.HOST_ESCAPE;
        }
        return Decision.FORWARD;
    }

    Decision tab(long now) {
        escapes = 0;
        lastEscape = 0;
        tabs = now - lastTab <= WINDOW_NANOS ? tabs + 1 : 1;
        lastTab = now;
        if (tabs >= REQUIRED_PRESSES) {
            tabs = 0;
            return Decision.JAVA_FOCUS;
        }
        return Decision.FORWARD;
    }

    Hint hint(long now) {
        if (isActive(escapes, lastEscape, now)) {
            return new Hint(HintKind.ESCAPE, REQUIRED_PRESSES - escapes);
        }
        if (isActive(tabs, lastTab, now)) {
            return new Hint(HintKind.TAB, REQUIRED_PRESSES - tabs);
        }
        return null;
    }

    private static boolean isActive(int presses, long lastPress, long now) {
        return presses > 0 && now - lastPress <= WINDOW_NANOS;
    }

    void reset() {
        escapes = 0;
        tabs = 0;
        lastEscape = 0;
        lastTab = 0;
    }
}
