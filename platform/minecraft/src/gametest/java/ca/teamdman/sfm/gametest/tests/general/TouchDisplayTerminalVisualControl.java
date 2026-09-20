package ca.teamdman.sfm.gametest.tests.general;

/** Thread-safe, test-only gates. The ambient constructor never creates or observes this control. */
public final class TouchDisplayTerminalVisualControl {
    public static final double U = 0.25D;
    public static final double V = 0.75D;
    public enum Stage { STARTING, READY, ACKNOWLEDGED, CLEANED, FAILED }
    public record Snapshot(Stage stage, String textureSha256, int width, int height,
                           long attempted, long acknowledged, long rejected, String failure) {}

    private volatile Snapshot snapshot = new Snapshot(Stage.STARTING, "", 0, 0, 0, 0, 0, "");
    private volatile boolean pressRequested;
    private volatile boolean finishRequested;

    public Snapshot snapshot() { return snapshot; }
    public boolean pressRequested() { return pressRequested; }
    public boolean finishRequested() { return finishRequested; }

    /** ACK text and raster publication are independent; an unchanged READY image means keep waiting. */
    public boolean hasChangedReadyRaster(String digest) {
        Snapshot ready = snapshot;
        return ready.stage() == Stage.READY && digest != null && !digest.isBlank()
                && !digest.equals(ready.textureSha256());
    }

    public synchronized void ready(String digest, int width, int height) {
        if (snapshot.stage() == Stage.FAILED || snapshot.stage() == Stage.READY) return;
        require(snapshot.stage() == Stage.STARTING, "Terminal ready arrived out of order");
        require(digest != null && !digest.isBlank() && width > 0 && width <= 512 && height > 0 && height <= 512,
                "Terminal ready requires a bounded uploaded raster");
        snapshot = new Snapshot(Stage.READY, digest, width, height, 0, 0, 0, "");
    }

    public synchronized void requestPress() {
        require(snapshot.stage() == Stage.READY && !pressRequested, "Capture a ready terminal before its one press");
        pressRequested = true;
    }

    public synchronized void acknowledged(String digest, int width, int height, long attempted, long acknowledged, long rejected) {
        require(snapshot.stage() == Stage.READY && pressRequested, "Terminal ACK arrived without the authorized file press");
        require(attempted == 1 && acknowledged == 1 && rejected == 0, "Terminal input was retried, rejected, or duplicated");
        require(hasChangedReadyRaster(digest)
                && width > 0 && width <= 512 && height > 0 && height <= 512, "Terminal ACK needs a changed bounded uploaded raster");
        snapshot = new Snapshot(Stage.ACKNOWLEDGED, digest, width, height, attempted, acknowledged, rejected, "");
    }

    public synchronized void requestFinish() {
        require(snapshot.stage() == Stage.ACKNOWLEDGED && !finishRequested, "Capture the acknowledged terminal before finishing");
        finishRequested = true;
    }

    public synchronized void cleaned() {
        require(snapshot.stage() == Stage.ACKNOWLEDGED && finishRequested, "Terminal cleanup arrived before visual proof completion");
        snapshot = new Snapshot(Stage.CLEANED, snapshot.textureSha256(), snapshot.width(), snapshot.height(),
                snapshot.attempted(), snapshot.acknowledged(), snapshot.rejected(), "");
    }

    public synchronized void fail(String message) {
        if (snapshot.stage() == Stage.FAILED) return;
        String bounded = message == null ? "Terminal visual fixture failed" : message.substring(0, Math.min(message.length(), 512));
        snapshot = new Snapshot(Stage.FAILED, snapshot.textureSha256(), snapshot.width(), snapshot.height(),
                snapshot.attempted(), snapshot.acknowledged(), snapshot.rejected(), bounded);
    }

    private static void require(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }
}
