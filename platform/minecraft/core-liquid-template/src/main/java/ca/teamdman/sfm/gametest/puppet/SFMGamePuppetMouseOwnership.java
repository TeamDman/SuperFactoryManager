package ca.teamdman.sfm.gametest.puppet;

/** Per-client mouse policy for declarative game puppets. Each client has its own JVM. */
public final class SFMGamePuppetMouseOwnership {
    private static volatile boolean owned = true;

    private SFMGamePuppetMouseOwnership() {}

    public static boolean suppressMouseGrab() { return owned; }

    public static void begin() { owned = true; }

    public static void end() { owned = false; }
}
