package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Keeps client ticks flowing while a terminal-owned command produces a witness line. */
public final class WaitForTerminalLinePuppetAction implements SFMPuppetAction {
    private static final int TIMEOUT_TICKS = 20 * 30;

    private final String line;
    private int ticks;

    public WaitForTerminalLinePuppetAction(String line) {
        if (line.isBlank()) {
            throw new IllegalArgumentException("Terminal witness line must not be blank");
        }
        this.line = line;
    }

    @Override
    public String description() {
        return "wait for terminal line " + line;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (runtime.terminalContentHasExactLine(line)) {
            return true;
        }
        if (++ticks > TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for terminal line " + line);
        }
        return false;
    }
}
