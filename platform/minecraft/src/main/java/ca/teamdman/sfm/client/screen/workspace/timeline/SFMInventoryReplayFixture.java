package ca.teamdman.sfm.client.screen.workspace.timeline;

/** Pure deterministic description; the client panel materializes copied ItemStacks from each frame. */
public final class SFMInventoryReplayFixture {
    public static final SFMTimelineBounds BOUNDS = new SFMTimelineBounds(0, 5);

    private SFMInventoryReplayFixture() {
    }

    public static Frame frameAt(int timestep) {
        if (timestep < BOUNDS.first() || timestep > BOUNDS.last()) {
            throw new IllegalArgumentException("Inventory replay timestep is outside bounds: " + timestep);
        }
        return switch (timestep) {
            case 0 -> new Frame(true, false, false, 0D, "Cobblestone begins in the chest");
            case 1 -> new Frame(false, false, true, 0D, "Picked up by the virtual cursor");
            case 2 -> new Frame(false, false, true, 0.33D, "Transit: leaving the chest");
            case 3 -> new Frame(false, false, true, 0.67D, "Transit: crossing into player inventory");
            case 4 -> new Frame(false, false, true, 1D, "Ready to place over the destination slot");
            case 5 -> new Frame(false, true, false, 1D, "Cobblestone placed in player inventory");
            default -> throw new IllegalStateException("Unreachable timestep: " + timestep);
        };
    }

    public record Frame(
            boolean chestOwnsCobblestone,
            boolean playerOwnsCobblestone,
            boolean cursorOwnsCobblestone,
            double cursorPathPosition,
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
