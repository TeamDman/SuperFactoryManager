package ca.teamdman.sfm.client.screen.workspace.timeline;

/** Pure deterministic keyframes; presentation state is reconstructed without visit history. */
public final class SFMInventoryReplayFixture {
    public static final SFMTimelineBounds BOUNDS = new SFMTimelineBounds(0, 3);
    /** Pickup is brief, transit is deliberately long, and placement is medium length. */
    public static final SFMKeyframeTimeline TIMELINE = new SFMKeyframeTimeline(6, 24, 10);

    private SFMInventoryReplayFixture() {
    }

    public static Frame frameAt(int keyframe) {
        if (keyframe < BOUNDS.first() || keyframe > BOUNDS.last()) {
            throw new IllegalArgumentException("Inventory replay keyframe is outside bounds: " + keyframe);
        }
        return sample(keyframe);
    }

    public static Frame sample(double keyframePosition) {
        double position = BOUNDS.clamp(keyframePosition);
        if (position < 1D) {
            return new Frame(true, false, false, 0D, position,
                    "Chest owns cobblestone; approaching pickup");
        }
        if (position < 2D) {
            double transit = position - 1D;
            return new Frame(false, false, true, transit, position,
                    transit == 0D ? "Pickup keyframe: cursor owns cobblestone"
                            : "Interpolating held stack toward player inventory");
        }
        if (position < 3D) {
            return new Frame(false, false, true, 1D, position,
                    position == 2D ? "Destination keyframe: ready to place"
                            : "Holding at destination before placement");
        }
        return new Frame(false, true, false, 1D, position,
                "Placement keyframe: player inventory owns cobblestone");
    }

    public record Frame(
            boolean chestOwnsCobblestone,
            boolean playerOwnsCobblestone,
            boolean cursorOwnsCobblestone,
            double cursorPathPosition,
            double keyframePosition,
            String phase
    ) {
        public Frame {
            int ownerCount = (chestOwnsCobblestone ? 1 : 0)
                    + (playerOwnsCobblestone ? 1 : 0)
                    + (cursorOwnsCobblestone ? 1 : 0);
            if (ownerCount != 1) throw new IllegalArgumentException("Exactly one fixture owner is required");
            if (cursorPathPosition < 0D || cursorPathPosition > 1D) {
                throw new IllegalArgumentException("Cursor path position must be normalized");
            }
        }
    }
}
