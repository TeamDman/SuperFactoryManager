package ca.teamdman.sfm.client.screen.item_picker;

/** Pure responsive geometry for full-screen and multiplexed item-picker panels. */
public record SFMItemPickerLayout(
        Rect content,
        Rect header,
        Rect search,
        Rect results,
        Rect preview,
        Rect footer,
        int columns,
        int cellWidth,
        int cellHeight,
        boolean compact,
        boolean belowMinimum
) {
    public static final int MINIMUM_WIDTH = 140;
    public static final int MINIMUM_HEIGHT = 150;
    public static final int CELL_HEIGHT = 34;
    public static final int DENSE_CELL_SIZE = 22;

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    public static SFMItemPickerLayout calculate(int x, int y, int width, int height) {
        return calculate(x, y, width, height, SFMItemPickerModel.ViewMode.DETAILED);
    }

    public static SFMItemPickerLayout calculate(
            int x,
            int y,
            int width,
            int height,
            SFMItemPickerModel.ViewMode viewMode
    ) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        boolean below = safeWidth < MINIMUM_WIDTH || safeHeight < MINIMUM_HEIGHT;
        boolean compact = below || safeWidth < 500 || safeHeight < 260;
        int margin = compact ? 4 : 10;
        Rect content = new Rect(x + margin, y + margin,
                Math.max(1, safeWidth - margin * 2), Math.max(1, safeHeight - margin * 2));
        int headerHeight = Math.min(compact ? 24 : 30, content.height());
        int searchHeight = Math.min(24, Math.max(0, content.height() - headerHeight));
        int footerHeight = Math.min(compact ? 30 : 38,
                Math.max(0, content.height() - headerHeight - searchHeight));
        int bodyY = content.y() + headerHeight + searchHeight;
        int bodyHeight = Math.max(0, content.height() - headerHeight - searchHeight - footerHeight);
        int compactPreviewHeight = compact ? Math.min(24, bodyHeight) : 0;
        int previewWidth = compact ? 0 : Math.min(280, Math.max(180, content.width() / 4));
        int resultWidth = Math.max(1, content.width() - previewWidth - (previewWidth > 0 ? 8 : 0));
        boolean dense = viewMode == SFMItemPickerModel.ViewMode.DENSE_ICONS;
        int columns = dense
                ? Math.max(1, resultWidth / DENSE_CELL_SIZE)
                : Math.max(1, resultWidth / 150);
        int cellWidth = dense ? DENSE_CELL_SIZE : Math.max(1, resultWidth / columns);
        int cellHeight = dense ? DENSE_CELL_SIZE : CELL_HEIGHT;
        return new SFMItemPickerLayout(
                content,
                new Rect(content.x(), content.y(), content.width(), headerHeight),
                new Rect(content.x(), content.y() + headerHeight, content.width(), searchHeight),
                new Rect(content.x(), bodyY, resultWidth, Math.max(0, bodyHeight - compactPreviewHeight)),
                compact
                        ? new Rect(content.x(), bodyY + Math.max(0, bodyHeight - compactPreviewHeight),
                                content.width(), compactPreviewHeight)
                        : new Rect(content.x() + resultWidth + 8, bodyY, previewWidth, bodyHeight),
                new Rect(content.x(), content.y() + content.height() - footerHeight, content.width(), footerHeight),
                columns,
                cellWidth,
                cellHeight,
                compact,
                below
        );
    }
}
