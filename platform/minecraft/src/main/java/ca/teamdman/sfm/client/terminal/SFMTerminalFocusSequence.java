package ca.teamdman.sfm.client.terminal;

/**
 * Detects the terminal escape hatches without making a single Esc or Tab
 * unusable inside the hosted program.
 */
final class SFMTerminalFocusSequence {
    static final long WINDOW_NANOS = 1_500_000_000L;

    enum Decision {
        FORWARD,
        EXIT,
        JAVA_FOCUS
    }

    private int escapes;
    private int tabs;
    private long lastEscape;
    private long lastTab;

    Decision escape(long now) {
        escapes = now - lastEscape <= WINDOW_NANOS ? escapes + 1 : 1;
        lastEscape = now;
        if (escapes >= 3) {
            escapes = 0;
            return Decision.EXIT;
        }
        return Decision.FORWARD;
    }

    Decision tab(long now) {
        tabs = now - lastTab <= WINDOW_NANOS ? tabs + 1 : 1;
        lastTab = now;
        if (tabs >= 3) {
            tabs = 0;
            return Decision.JAVA_FOCUS;
        }
        return Decision.FORWARD;
    }

    void reset() {
        escapes = 0;
        tabs = 0;
        lastEscape = 0;
        lastTab = 0;
    }
}
