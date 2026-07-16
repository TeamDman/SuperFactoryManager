package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/**
 * Waits for a bounded number of puppet ticks.
 */
public final class WaitTicksPuppetAction implements SFMPuppetAction {
    private final int requiredTicks;

    private int elapsedTicks;

    public WaitTicksPuppetAction(int requiredTicks) {
        if (requiredTicks <= 0) {
            throw new IllegalArgumentException("Required ticks must be positive");
        }
        this.requiredTicks = requiredTicks;
    }

    @Override
    public String description() {
        return "wait " + requiredTicks + " ticks";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        return ++elapsedTicks >= requiredTicks;
    }
}
