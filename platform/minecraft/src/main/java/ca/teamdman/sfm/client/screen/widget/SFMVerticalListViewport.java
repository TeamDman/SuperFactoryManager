package ca.teamdman.sfm.client.screen.widget;

import java.util.OptionalInt;

/**
 * Pure state and geometry for a vertically scrolling list of fixed-height rows.
 *
 * <p>The model deliberately has no Minecraft dependencies so screens can share
 * one set of selection, scrolling, hit-testing, and scrollbar laws.</p>
 */
public final class SFMVerticalListViewport {
    public static final int NO_SELECTION = -1;

    private int itemCount;
    private int visibleRowCount;
    private int firstVisibleRow;
    private int selectedRow = NO_SELECTION;
    private boolean scrollbarDragActive;
    private double scrollbarDragOffset;

    public void configure(int itemCount, int visibleRowCount) {
        if (itemCount < 0) throw new IllegalArgumentException("Item count must be non-negative");
        if (visibleRowCount < 0) throw new IllegalArgumentException("Visible row count must be non-negative");
        this.itemCount = itemCount;
        this.visibleRowCount = visibleRowCount;
        if (itemCount == 0) {
            this.firstVisibleRow = 0;
            this.selectedRow = NO_SELECTION;
        } else {
            this.selectedRow = Math.min(this.selectedRow, itemCount - 1);
            this.firstVisibleRow = clamp(this.firstVisibleRow, 0, maxFirstVisibleRow());
            ensureSelectedVisible();
        }
        if (!canScroll()) this.scrollbarDragActive = false;
    }

    public int itemCount() {
        return itemCount;
    }

    public int visibleRowCount() {
        return visibleRowCount;
    }

    public int firstVisibleRow() {
        return firstVisibleRow;
    }

    public int lastVisibleRowExclusive() {
        return Math.min(itemCount, firstVisibleRow + visibleRowCount);
    }

    public int selectedRow() {
        return selectedRow;
    }

    public int maxFirstVisibleRow() {
        return Math.max(0, itemCount - visibleRowCount);
    }

    public boolean canScroll() {
        return visibleRowCount > 0 && itemCount > visibleRowCount;
    }

    public boolean select(int row) {
        if (row == NO_SELECTION || itemCount == 0) {
            boolean changed = selectedRow != NO_SELECTION;
            selectedRow = NO_SELECTION;
            return changed;
        }
        if (row < 0 || row >= itemCount) throw new IllegalArgumentException("Selected row is outside the list");
        boolean changed = selectedRow != row;
        selectedRow = row;
        ensureSelectedVisible();
        return changed;
    }

    public boolean moveSelection(int rows) {
        if (itemCount == 0 || rows == 0) return false;
        if (selectedRow == NO_SELECTION) return select(rows < 0 ? itemCount - 1 : 0);
        return select(clamp(selectedRow + rows, 0, itemCount - 1));
    }

    public boolean pageSelection(int pages) {
        if (pages == 0) return false;
        return moveSelection(pages * Math.max(1, visibleRowCount));
    }

    public boolean selectFirst() {
        return itemCount > 0 && select(0);
    }

    public boolean selectLast() {
        return itemCount > 0 && select(itemCount - 1);
    }

    public boolean scrollRows(int rows) {
        if (rows == 0) return false;
        return setFirstVisibleRow(firstVisibleRow + rows);
    }

    public boolean scrollWheel(double delta) {
        if (!canScroll() || delta == 0.0d) return false;
        int rows = Math.max(1, (int) Math.ceil(Math.abs(delta) * 3.0d));
        scrollRows(delta > 0.0d ? -rows : rows);
        return true;
    }

    public OptionalInt rowAt(
            double mouseX,
            double mouseY,
            Bounds rows,
            int rowStride,
            int rowHeight
    ) {
        if (rowStride <= 0) throw new IllegalArgumentException("Row stride must be positive");
        if (rowHeight <= 0 || rowHeight > rowStride) {
            throw new IllegalArgumentException("Row height must be between one and the row stride");
        }
        if (!rows.contains(mouseX, mouseY)) return OptionalInt.empty();
        int offset = (int) Math.floor(mouseY - rows.y());
        int viewportRow = offset / rowStride;
        if (viewportRow >= visibleRowCount || offset % rowStride >= rowHeight) return OptionalInt.empty();
        int item = firstVisibleRow + viewportRow;
        return item < itemCount ? OptionalInt.of(item) : OptionalInt.empty();
    }

    public ScrollbarGeometry scrollbarGeometry(Bounds track, int minimumThumbHeight) {
        if (minimumThumbHeight < 0) throw new IllegalArgumentException("Minimum thumb height must be non-negative");
        if (!canScroll() || track.width() == 0 || track.height() == 0) {
            return new ScrollbarGeometry(track, new Bounds(track.x(), track.y(), track.width(), 0), false);
        }
        int proportionalHeight = track.height() * visibleRowCount / itemCount;
        int thumbHeight = Math.min(track.height(), Math.max(minimumThumbHeight, proportionalHeight));
        int travel = track.height() - thumbHeight;
        int thumbTop = track.y() + (int) Math.round(
                travel * firstVisibleRow / (double) Math.max(1, maxFirstVisibleRow())
        );
        return new ScrollbarGeometry(
                track,
                new Bounds(track.x(), thumbTop, track.width(), thumbHeight),
                true
        );
    }

    public boolean mouseClickedScrollbar(
            double mouseX,
            double mouseY,
            int button,
            ScrollbarGeometry geometry
    ) {
        if (button != 0 || !geometry.visible() || !geometry.track().contains(mouseX, mouseY)) return false;
        if (geometry.thumb().contains(mouseX, mouseY)) {
            scrollbarDragActive = true;
            scrollbarDragOffset = mouseY - geometry.thumb().y();
            return true;
        }
        double proportion = (mouseY - geometry.track().y()) / geometry.track().height();
        setFirstVisibleRow((int) Math.round(proportion * maxFirstVisibleRow()));
        return true;
    }

    public boolean mouseDraggedScrollbar(double mouseY, int button, ScrollbarGeometry geometry) {
        if (button != 0 || !scrollbarDragActive || !geometry.visible()) return false;
        int travel = geometry.track().height() - geometry.thumb().height();
        if (travel <= 0) return true;
        double thumbTop = clamp(
                mouseY - scrollbarDragOffset,
                geometry.track().y(),
                geometry.track().y() + travel
        );
        double proportion = (thumbTop - geometry.track().y()) / travel;
        setFirstVisibleRow((int) Math.round(proportion * maxFirstVisibleRow()));
        return true;
    }

    public boolean mouseReleasedScrollbar(int button) {
        if (button != 0 || !scrollbarDragActive) return false;
        scrollbarDragActive = false;
        return true;
    }

    public boolean isScrollbarDragActive() {
        return scrollbarDragActive;
    }

    private boolean setFirstVisibleRow(int row) {
        int clamped = clamp(row, 0, maxFirstVisibleRow());
        boolean changed = firstVisibleRow != clamped;
        firstVisibleRow = clamped;
        clampSelectionToViewport();
        return changed;
    }

    private void ensureSelectedVisible() {
        if (selectedRow == NO_SELECTION || visibleRowCount == 0) return;
        if (selectedRow < firstVisibleRow) {
            firstVisibleRow = selectedRow;
        } else if (selectedRow >= firstVisibleRow + visibleRowCount) {
            firstVisibleRow = selectedRow - visibleRowCount + 1;
        }
        firstVisibleRow = clamp(firstVisibleRow, 0, maxFirstVisibleRow());
    }

    private void clampSelectionToViewport() {
        if (selectedRow == NO_SELECTION || visibleRowCount == 0 || itemCount == 0) return;
        if (selectedRow < firstVisibleRow) {
            selectedRow = firstVisibleRow;
        } else if (selectedRow >= lastVisibleRowExclusive()) {
            selectedRow = lastVisibleRowExclusive() - 1;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Bounds(int x, int y, int width, int height) {
        public Bounds {
            if (width < 0 || height < 0) throw new IllegalArgumentException("Bounds dimensions must be non-negative");
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public record ScrollbarGeometry(Bounds track, Bounds thumb, boolean visible) {
    }
}
