package ca.teamdman.sfm.client.widget;

public class VirtualScrollViewport {
    private final int rowHeight;
    private double scrollOffset;

    public VirtualScrollViewport(int rowHeight) {
        this.rowHeight = Math.max(1, rowHeight);
    }

    public int rowHeight() {
        return rowHeight;
    }

    public double scrollOffset() {
        return scrollOffset;
    }

    public void reset() {
        scrollOffset = 0;
    }

    public void clamp(int rowCount, int viewportHeight) {
        int max = maxScrollOffset(rowCount, viewportHeight);
        if (scrollOffset < 0) {
            scrollOffset = 0;
        } else if (scrollOffset > max) {
            scrollOffset = max;
        }
    }

    public void scrollByWheel(double wheelDelta, int rowCount, int viewportHeight) {
        scrollOffset -= wheelDelta * rowHeight * 3.0D;
        clamp(rowCount, viewportHeight);
    }

    public Integer rowAt(int mouseY, int contentTop, int rowCount, int viewportHeight) {
        if (mouseY < contentTop || mouseY >= contentTop + viewportHeight) {
            return null;
        }
        int index = (int) ((mouseY - contentTop + scrollOffset) / rowHeight);
        if (index < 0 || index >= rowCount) {
            return null;
        }
        return index;
    }

    public int firstVisibleRow(int rowCount, int viewportHeight) {
        if (rowCount <= 0 || viewportHeight <= 0) {
            return 0;
        }
        clamp(rowCount, viewportHeight);
        return Math.max(0, (int) (scrollOffset / rowHeight));
    }

    public int lastVisibleRowExclusive(int rowCount, int viewportHeight) {
        if (rowCount <= 0 || viewportHeight <= 0) {
            return 0;
        }
        int first = firstVisibleRow(rowCount, viewportHeight);
        int visibleRows = (viewportHeight / rowHeight) + 2;
        return Math.min(rowCount, first + visibleRows);
    }

    public int rowTop(int rowIndex, int contentTop) {
        return contentTop + rowIndex * rowHeight - (int) scrollOffset;
    }

    public int maxScrollOffset(int rowCount, int viewportHeight) {
        return Math.max(0, rowCount * rowHeight - viewportHeight);
    }
}
