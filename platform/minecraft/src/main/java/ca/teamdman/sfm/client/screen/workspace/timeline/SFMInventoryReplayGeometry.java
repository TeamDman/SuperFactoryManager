package ca.teamdman.sfm.client.screen.workspace.timeline;

/**
 * Responsive projection of the 1.19.2 three-row generic-container menu geometry.
 * Vanilla {@code ChestMenu} slot origins are chest (8,18), player main (8,85), and hotbar (8,143)
 * in the three-row 176x168 screen: 18px slot pitch, 13px between section origins after three
 * chest pitches, then a 4px extra hotbar gap after three player-main pitches.
 */
public record SFMInventoryReplayGeometry(
        int left,
        int chestTop,
        int playerMainTop,
        int hotbarTop,
        int slotPitch
) {
    public static final int COLUMNS = 9;
    public static final int CHEST_ROWS = 3;
    public static final int PLAYER_MAIN_ROWS = 3;

    public static SFMInventoryReplayGeometry fit(int x, int y, int width, int height) {
        int pitch = Math.max(14, Math.min(22, Math.min((width - 32) / COLUMNS, (height - 88) / 7)));
        int gridWidth = pitch * COLUMNS;
        int left = x + Math.max(8, (width - gridWidth) / 2);
        int sectionGap = scaled(13, pitch);
        int hotbarGap = scaled(4, pitch);
        int contentHeight = CHEST_ROWS * pitch + sectionGap + PLAYER_MAIN_ROWS * pitch + hotbarGap + pitch;
        int chestTop = y + 32 + Math.max(0, (height - 50 - contentHeight) / 2);
        int playerMainTop = chestTop + CHEST_ROWS * pitch + sectionGap;
        int hotbarTop = playerMainTop + PLAYER_MAIN_ROWS * pitch + hotbarGap;
        return new SFMInventoryReplayGeometry(left, chestTop, playerMainTop, hotbarTop, pitch);
    }

    public int gridWidth() { return slotPitch * COLUMNS; }
    /** Extra origin distance beyond three normal pitches: 4px at vanilla pitch. */
    public int hotbarGap() { return hotbarTop - (playerMainTop + PLAYER_MAIN_ROWS * slotPitch); }
    /** Blank raster rows produced by our [top, top+pitch-1) slot fill before the next section. */
    public int chestMainVisibleBlankRows() {
        return playerMainTop - (chestTop + CHEST_ROWS * slotPitch - 1);
    }

    public Point chestSlotCenter(int index) {
        return slotCenter(left, chestTop, index);
    }

    public Point playerSlotCenter(int index) {
        if (index < 0 || index >= 36) throw new IllegalArgumentException("Player slot index outside 0..35");
        if (index < 27) return slotCenter(left, playerMainTop, index);
        return slotCenter(left, hotbarTop, index - 27);
    }

    private Point slotCenter(int originX, int originY, int index) {
        return new Point(
                originX + index % COLUMNS * slotPitch + slotPitch / 2,
                originY + index / COLUMNS * slotPitch + slotPitch / 2
        );
    }

    private static int scaled(int vanillaPixels, int pitch) {
        return Math.max(1, (int) Math.round(vanillaPixels * pitch / 18D));
    }

    public record Point(int x, int y) {
    }
}
