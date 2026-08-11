package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.common.program.IProgramHooks;

import java.time.Duration;
import java.util.List;

public final class SequentialAssertionHooks implements IProgramHooks {

    private final List<Runnable> assertions;

    private final SFMGameTestHelper helper;

    private int index = 0;

    public SequentialAssertionHooks(
            SFMGameTestHelper helper,
            List<Runnable> assertions
    ) {

        this.helper = helper;
        this.assertions = assertions;
    }

    @Override
    public void onProgramDidSomething(Duration elapsed) {
        if (index < assertions.size()) {
            // enqueue to run inside the game test harness
            helper.runAfterDelay(0, assertions.get(index));
            index++;
        }
    }


    @Override
    public String toString() {

        return "SequentialAssertionHooks[" +
               "assertionCount=" + assertions.size()
               + ", index=" + index
               + ']';
    }

}
