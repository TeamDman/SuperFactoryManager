package ca.teamdman.sfm.client.terminal;

import java.util.List;

/** Attempts every independently owned resource before reporting a cleanup failure. */
final class TouchDisplayTerminalCleanup {
    private TouchDisplayTerminalCleanup() {}

    static void runAll(Runnable... operations) { runAll(List.of(operations)); }

    static void runAll(Iterable<Runnable> operations) {
        RuntimeException failure = null;
        for (Runnable operation : operations) {
            try { operation.run(); }
            catch (RuntimeException error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    static void suppressOn(RuntimeException failure, Runnable... operations) {
        try { runAll(operations); }
        catch (RuntimeException cleanup) { if (failure != cleanup) failure.addSuppressed(cleanup); }
    }
}
