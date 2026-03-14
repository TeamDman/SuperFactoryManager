package ca.teamdman.sfm.common.program;

import java.time.Duration;

public interface IProgramHooks {
    default void onProgramDidSomething(Duration elapsed) {}
}
