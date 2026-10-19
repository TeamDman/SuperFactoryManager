package ca.teamdman.sfm.gametest.puppet;

import java.util.Objects;

/** Scoped ownership of the options and mouse-grab policy used by unattended captures. */
public final class SFMGamePuppetInputOwnership {
    public static final SFMGamePuppetInputOwnership INSTANCE = new SFMGamePuppetInputOwnership();

    public enum End {
        FINISHED, HANDOFF, FAILED, CANCELLED
    }

    /** No focus or mouse-grab operation is exposed: only an existing foreground grab can be released. */
    public interface Host {
        boolean pauseOnLostFocus();
        void pauseOnLostFocus(boolean value);
        boolean hideGui();
        void hideGui(boolean value);
        boolean vsync();
        void vsync(boolean value);
        int framerateLimit();
        void framerateLimit(int value);
        boolean windowActive();
        boolean mouseGrabbed();
        void releaseMouse();
    }

    public record Snapshot(boolean pauseOnLostFocus, boolean hideGui, boolean vsync, int framerateLimit) {
        private static Snapshot capture(Host host) {
            return new Snapshot(host.pauseOnLostFocus(), host.hideGui(), host.vsync(), host.framerateLimit());
        }
    }

    private volatile Lease active;

    public boolean suppressMouseGrab() {
        return active != null;
    }

    public Lease acquire(Host host) {
        Objects.requireNonNull(host, "host");
        if (active != null) throw new IllegalStateException("Capture input already has an owner");
        Lease lease = new Lease(host, Snapshot.capture(host));
        active = lease;
        try {
            // Install suppression before any runtime option callback or world-entry operation.
            lease.maintain();
            // Vanilla releaseMouse recentres the cursor. Never do that in another application's window.
            if (host.windowActive() && host.mouseGrabbed()) host.releaseMouse();
            return lease;
        } catch (RuntimeException | Error failure) {
            try {
                lease.close(End.FAILED);
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /** Null-player startup notifications and declared world transitions do not cancel a capture. */
    public static boolean shouldCancelOnLogout(boolean hadPlayer, boolean worldConfigured, boolean expectedTransition) {
        return hadPlayer && worldConfigured && !expectedTransition;
    }

    public final class Lease implements AutoCloseable {
        private final Host host;
        private final Snapshot before;
        private End ended;

        private Lease(Host host, Snapshot before) {
            this.host = host;
            this.before = before;
        }

        public Snapshot before() {
            return before;
        }

        public End ended() {
            return ended;
        }

        /** A stale capture tick cannot reacquire ownership or overwrite a user's restored settings. */
        public void maintain() {
            if (ended != null) return;
            if (host.pauseOnLostFocus()) host.pauseOnLostFocus(false);
            if (host.vsync()) host.vsync(false);
            if (host.framerateLimit() != 260) host.framerateLimit(260);
        }

        @Override
        public void close() {
            close(End.FINISHED);
        }

        public void close(End reason) {
            Objects.requireNonNull(reason, "reason");
            if (ended != null) return;
            ended = reason;
            if (active == this) active = null;
            // Release the policy first, then restore every option even if one restoration fails.
            // Regrabbing or focusing is deliberately left to an intentional vanilla user action.
            Throwable failure = null;
            for (Runnable restore : new Runnable[]{
                    () -> host.pauseOnLostFocus(before.pauseOnLostFocus()),
                    () -> host.hideGui(before.hideGui()),
                    () -> host.vsync(before.vsync()),
                    () -> host.framerateLimit(before.framerateLimit())
            }) {
                try {
                    restore.run();
                } catch (RuntimeException | Error problem) {
                    if (failure == null) failure = problem;
                    else if (problem != failure) failure.addSuppressed(problem);
                }
            }
            if (failure instanceof RuntimeException problem) throw problem;
            if (failure instanceof Error problem) throw problem;
        }
    }
}
