package ca.teamdman.sfm.client.screen.file_explorer;

/** Pure responsive geometry shared by the full-screen host and future panels. */
public record SFMFileExplorerLayout(
        Rect content,
        Rect header,
        Rect list,
        Rect status,
        boolean compact,
        boolean belowMinimum
) {
    public static final int MINIMUM_WIDTH = 180;
    public static final int MINIMUM_HEIGHT = 120;
    private static final int OUTER_MARGIN = 8;
    private static final int COMPACT_OUTER_MARGIN = 3;

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(
                double pointX,
                double pointY
        ) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }
    }

    public static SFMFileExplorerLayout calculate(
            int x,
            int y,
            int width,
            int height
    ) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        boolean belowMinimum = safeWidth < MINIMUM_WIDTH || safeHeight < MINIMUM_HEIGHT;
        boolean compact = belowMinimum || safeWidth < 320 || safeHeight < 180;
        int margin = compact ? COMPACT_OUTER_MARGIN : OUTER_MARGIN;
        int contentWidth = Math.max(1, safeWidth - margin * 2);
        int contentHeight = Math.max(1, safeHeight - margin * 2);
        Rect content = new Rect(x + margin, y + margin, contentWidth, contentHeight);
        int headerHeight = Math.min(compact ? 22 : 32, contentHeight);
        int statusHeight = Math.min(compact ? 14 : 20, Math.max(0, contentHeight - headerHeight));
        int listHeight = Math.max(0, contentHeight - headerHeight - statusHeight);
        return new SFMFileExplorerLayout(
                content,
                new Rect(content.x(), content.y(), content.width(), headerHeight),
                new Rect(content.x(), content.y() + headerHeight, content.width(), listHeight),
                new Rect(content.x(), content.y() + headerHeight + listHeight, content.width(), statusHeight),
                compact,
                belowMinimum
        );
    }
}
