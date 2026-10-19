package ca.teamdman.sfm.gametest.puppet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SFMGamePuppetInputOwnershipTests {
    @Test
    void captureOverridesAndEveryTerminationRestoreBothPriorBooleanValues() {
        for (boolean prior : new boolean[]{false, true}) {
            for (SFMGamePuppetInputOwnership.End end : SFMGamePuppetInputOwnership.End.values()) {
                var ownership = new SFMGamePuppetInputOwnership();
                var host = new FakeHost(prior, prior, prior, 73);
                var lease = ownership.acquire(host);
                assertTrue(ownership.suppressMouseGrab());
                assertFalse(host.pause);
                assertFalse(host.vsync);
                assertEquals(260, host.fps);
                host.hud = !prior; // Both clean-HUD and overlay-preserving captures change this option.
                lease.close(end);
                assertFalse(ownership.suppressMouseGrab());
                assertEquals(prior, host.pause, end.toString());
                assertEquals(prior, host.hud, end.toString());
                assertEquals(prior, host.vsync, end.toString());
                assertEquals(73, host.fps);
                assertEquals(end, lease.ended());
                assertEquals(0, host.mouseReleases, "A handoff must not touch an ungrabbed OS cursor");
            }
        }
    }

    @Test
    void repeatedTicksDoNotReplaceTheSnapshotAndClosedTicksCannotReapplyOverrides() {
        var ownership = new SFMGamePuppetInputOwnership();
        var host = new FakeHost(true, false, true, 60);
        var lease = ownership.acquire(host);
        host.pause = true;
        host.vsync = true;
        host.fps = 30;
        lease.maintain();
        assertFalse(host.pause);
        assertEquals(260, host.fps);
        assertThrows(IllegalStateException.class, () -> ownership.acquire(host));
        lease.close(SFMGamePuppetInputOwnership.End.HANDOFF);
        assertTrue(host.pause);
        assertEquals(60, host.fps);
        host.pause = false;
        host.hud = true;
        host.fps = 144;
        lease.maintain();
        lease.close(SFMGamePuppetInputOwnership.End.CANCELLED);
        assertFalse(host.pause, "Finite and indefinite holds must leave later user changes alone");
        assertTrue(host.hud);
        assertEquals(144, host.fps);
        assertFalse(ownership.suppressMouseGrab());
        assertEquals(SFMGamePuppetInputOwnership.End.HANDOFF, lease.ended());
    }

    @Test
    void backgroundCaptureAndHandoffNeverTouchTheOtherApplicationsCursor() {
        for (boolean initiallyGrabbed : new boolean[]{false, true}) {
            var ownership = new SFMGamePuppetInputOwnership();
            var host = new FakeHost(true, false, true, 90);
            host.active = false;
            host.grabbed = initiallyGrabbed;
            var lease = ownership.acquire(host);
            assertTrue(ownership.suppressMouseGrab());
            assertEquals(0, host.mouseReleases);
            lease.close(SFMGamePuppetInputOwnership.End.HANDOFF);
            assertEquals(0, host.mouseReleases);
            assertFalse(ownership.suppressMouseGrab());
            assertEquals(initiallyGrabbed, host.grabbed);
            // Returning focus alone does not capture. An intentional vanilla click is allowed again.
            host.active = true;
            assertEquals(initiallyGrabbed, host.grabbed);
            host.nativeUserClick(ownership);
            assertTrue(host.grabbed);
        }
    }

    @Test
    void foregroundExistingGrabIsReleasedOnceAndIsNeverForciblyRestored() {
        var ownership = new SFMGamePuppetInputOwnership();
        var host = new FakeHost(false, true, false, 120);
        host.grabbed = true;
        var lease = ownership.acquire(host);
        assertEquals(1, host.mouseReleases);
        assertFalse(host.grabbed);
        lease.maintain();
        host.nativeUserClick(ownership);
        assertFalse(host.grabbed, "Capture owns the grab policy while running");
        lease.close(SFMGamePuppetInputOwnership.End.FINISHED);
        assertEquals(1, host.mouseReleases);
        assertFalse(host.grabbed);
        host.nativeUserClick(ownership);
        assertTrue(host.grabbed, "Only the user's normal click regains mouse capture");
    }

    @Test
    void restorationFailureStillRestoresOtherOptionsAndReleasesOwnership() {
        var ownership = new SFMGamePuppetInputOwnership();
        var host = new FakeHost(true, false, true, 75);
        var lease = ownership.acquire(host);
        host.hud = true;
        host.rejectPauseRestore = true;
        assertThrows(IllegalStateException.class, () -> lease.close(SFMGamePuppetInputOwnership.End.FAILED));
        assertFalse(ownership.suppressMouseGrab());
        assertFalse(host.hud);
        assertTrue(host.vsync);
        assertEquals(75, host.fps);
        assertEquals(SFMGamePuppetInputOwnership.End.FAILED, lease.ended());
        assertDoesNotThrow(() -> { lease.close(); }, "Failure cleanup remains idempotent");
    }

    @Test
    void failedAcquisitionReleasesPolicyAndRestoresSettingsBeforeAnotherCapture() {
        var ownership = new SFMGamePuppetInputOwnership();
        var host = new FakeHost(true, true, true, 165);
        host.rejectCaptureFps = true;
        assertThrows(IllegalStateException.class, () -> ownership.acquire(host));
        assertFalse(ownership.suppressMouseGrab());
        assertTrue(host.pause);
        assertTrue(host.hud);
        assertTrue(host.vsync);
        assertEquals(165, host.fps);
        host.rejectCaptureFps = false;
        var next = ownership.acquire(host);
        assertTrue(ownership.suppressMouseGrab());
        next.close(SFMGamePuppetInputOwnership.End.CANCELLED);
        assertEquals(165, host.fps);
        assertTrue(host.pause);
    }

    @Test
    void logoutDistinguishesInitialWorldCreationAndExpectedRelogFromUserCancellation() {
        assertFalse(SFMGamePuppetInputOwnership.shouldCancelOnLogout(false, false, false));
        assertFalse(SFMGamePuppetInputOwnership.shouldCancelOnLogout(false, true, false));
        assertFalse(SFMGamePuppetInputOwnership.shouldCancelOnLogout(true, false, false));
        assertFalse(SFMGamePuppetInputOwnership.shouldCancelOnLogout(true, true, true));
        assertTrue(SFMGamePuppetInputOwnership.shouldCancelOnLogout(true, true, false));
        var ownership = new SFMGamePuppetInputOwnership();
        var host = new FakeHost(true, true, true, 80);
        var lease = ownership.acquire(host);
        if (SFMGamePuppetInputOwnership.shouldCancelOnLogout(true, true, true)) lease.close();
        assertTrue(ownership.suppressMouseGrab(), "An owned relog keeps its capture lease");
        if (SFMGamePuppetInputOwnership.shouldCancelOnLogout(true, true, false)) {
            lease.close(SFMGamePuppetInputOwnership.End.CANCELLED);
        }
        assertFalse(ownership.suppressMouseGrab());
        assertTrue(host.pause);
    }

    private static final class FakeHost implements SFMGamePuppetInputOwnership.Host {
        boolean pause;
        boolean hud;
        boolean vsync;
        int fps;
        boolean active = true;
        boolean grabbed;
        boolean rejectPauseRestore;
        boolean rejectCaptureFps;
        int mouseReleases;

        FakeHost(boolean pause, boolean hud, boolean vsync, int fps) {
            this.pause = pause;
            this.hud = hud;
            this.vsync = vsync;
            this.fps = fps;
        }

        public boolean pauseOnLostFocus() { return pause; }
        public void pauseOnLostFocus(boolean value) {
            if (value && rejectPauseRestore) throw new IllegalStateException("Pause restoration failed");
            pause = value;
        }
        public boolean hideGui() { return hud; }
        public void hideGui(boolean value) { hud = value; }
        public boolean vsync() { return vsync; }
        public void vsync(boolean value) { vsync = value; }
        public int framerateLimit() { return fps; }
        public void framerateLimit(int value) {
            if (value == 260 && rejectCaptureFps) throw new IllegalStateException("Capture FPS override failed");
            fps = value;
        }
        public boolean windowActive() { return active; }
        public boolean mouseGrabbed() { return grabbed; }
        public void releaseMouse() { mouseReleases++; grabbed = false; }
        void nativeUserClick(SFMGamePuppetInputOwnership ownership) {
            if (active && !ownership.suppressMouseGrab()) grabbed = true;
        }
    }
}
