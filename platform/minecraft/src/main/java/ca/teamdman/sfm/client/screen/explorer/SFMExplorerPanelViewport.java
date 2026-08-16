package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure bounded geometry and visible-row slicing for the generic explorer panel. */
public final class SFMExplorerPanelViewport {
    public static final int LIST_ROW_HEIGHT = 18;
    public static final int SMALL_ICON_CELL_HEIGHT = 38;
    public static final int SMALL_ICON_MINIMUM_WIDTH = 96;

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Rectangle dimensions must not be negative");
            }
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }

        public Rect inset(int pixels) {
            if (pixels < 0) throw new IllegalArgumentException("Inset must not be negative");
            int horizontal = Math.min(width, pixels * 2);
            int vertical = Math.min(height, pixels * 2);
            return new Rect(
                    x + Math.min(pixels, width),
                    y + Math.min(pixels, height),
                    width - horizontal,
                    height - vertical
            );
        }
    }

    public record Layout(
            Rect content,
            Rect header,
            Rect filter,
            Rect bodyFrame,
            Rect body,
            Rect status,
            Rect locationControl,
            Rect filterControl
    ) {
        public Layout {
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(filter, "filter");
            Objects.requireNonNull(bodyFrame, "bodyFrame");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(locationControl, "locationControl");
            Objects.requireNonNull(filterControl, "filterControl");
        }
    }

    public record Cell(
            int absoluteIndex,
            int viewportRow,
            int column,
            Rect bounds,
            Rect chevron,
            SFMExplorerProjection.Row row
    ) {
        public Cell {
            if (absoluteIndex < 0 || viewportRow < 0 || column < 0) {
                throw new IllegalArgumentException("Cell indices must not be negative");
            }
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(chevron, "chevron");
            Objects.requireNonNull(row, "row");
        }
    }

    public record Snapshot(
            Layout layout,
            SFMExplorerProjection.View view,
            int columns,
            int visibleGridRows,
            int scrollRow,
            int maximumScrollRow,
            int firstVisibleIndex,
            int capacity,
            int totalItems,
            List<Cell> cells
    ) {
        public Snapshot {
            Objects.requireNonNull(layout, "layout");
            Objects.requireNonNull(view, "view");
            if (columns < 1 || visibleGridRows < 0 || scrollRow < 0 || maximumScrollRow < 0
                    || firstVisibleIndex < 0 || capacity < 0 || totalItems < 0) {
                throw new IllegalArgumentException("Viewport counts must not be negative");
            }
            cells = List.copyOf(cells);
            if (cells.size() > capacity) {
                throw new IllegalArgumentException("A viewport cannot materialize more cells than its capacity");
            }
        }

        public Optional<Cell> hit(double mouseX, double mouseY) {
            return cells.stream().filter(cell -> cell.bounds().contains(mouseX, mouseY)).findFirst();
        }

        public int lastVisibleIndexExclusive() {
            return firstVisibleIndex + cells.size();
        }
    }

    private SFMExplorerPanelViewport() {
    }

    public static Snapshot calculate(
            SFMScreenPanelBounds bounds,
            SFMExplorerProjection.View view,
            List<SFMExplorerProjection.Row> rows,
            int requestedScrollRow
    ) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(view, "view");
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (requestedScrollRow < 0) throw new IllegalArgumentException("Scroll row must not be negative");

        Layout layout = layout(bounds);
        int columns = view == SFMExplorerProjection.View.LIST
                ? 1
                : Math.max(1, layout.bodyFrame().width() / SMALL_ICON_MINIMUM_WIDTH);
        int cellHeight = view == SFMExplorerProjection.View.LIST
                ? LIST_ROW_HEIGHT
                : SMALL_ICON_CELL_HEIGHT;
        int visibleGridRows = cellHeight == 0 ? 0 : layout.body().height() / cellHeight;
        int totalGridRows = divideRoundUp(rows.size(), columns);
        int maximumScrollRow = Math.max(0, totalGridRows - visibleGridRows);
        int scrollRow = Math.min(requestedScrollRow, maximumScrollRow);
        int capacity = visibleGridRows * columns;
        int firstVisibleIndex = Math.min(rows.size(), scrollRow * columns);
        int endExclusive = Math.min(rows.size(), firstVisibleIndex + capacity);
        int cellWidth = columns == 0 ? layout.body().width() : layout.body().width() / columns;
        ArrayList<Cell> cells = new ArrayList<>(Math.max(0, endExclusive - firstVisibleIndex));
        for (int index = firstVisibleIndex; index < endExclusive; index++) {
            int visibleIndex = index - firstVisibleIndex;
            int viewportRow = visibleIndex / columns;
            int column = visibleIndex % columns;
            int x = layout.body().x() + column * cellWidth;
            int width = column == columns - 1
                    ? layout.body().x() + layout.body().width() - x
                    : cellWidth;
            Rect cellBounds = new Rect(
                    x,
                    layout.body().y() + viewportRow * cellHeight,
                    Math.max(0, width),
                    cellHeight
            );
            SFMExplorerProjection.Row row = rows.get(index);
            int chevronIndent = view == SFMExplorerProjection.View.LIST
                    ? Math.min(Math.max(0, cellBounds.width() - 10), row.depth() * 12)
                    : 0;
            Rect chevron = new Rect(
                    cellBounds.x() + chevronIndent,
                    cellBounds.y(),
                    Math.min(12, cellBounds.width()),
                    cellBounds.height()
            );
            cells.add(new Cell(index, viewportRow, column, cellBounds, chevron, row));
        }
        return new Snapshot(
                layout,
                view,
                columns,
                visibleGridRows,
                scrollRow,
                maximumScrollRow,
                firstVisibleIndex,
                capacity,
                rows.size(),
                cells
        );
    }

    public static Layout layout(SFMScreenPanelBounds rawBounds) {
        int margin = rawBounds.width() < 220 || rawBounds.height() < 140 ? 3 : 6;
        SFMScreenPanelBounds inset = rawBounds.inset(margin);
        Rect content = new Rect(inset.x(), inset.y(), inset.width(), inset.height());
        int headerHeight = Math.min(24, content.height());
        int remainingAfterHeader = Math.max(0, content.height() - headerHeight);
        int filterHeight = Math.min(18, remainingAfterHeader);
        int remainingAfterFilter = Math.max(0, remainingAfterHeader - filterHeight);
        int statusHeight = Math.min(14, remainingAfterFilter);
        int bodyHeight = Math.max(0, remainingAfterFilter - statusHeight);
        Rect header = new Rect(content.x(), content.y(), content.width(), headerHeight);
        Rect filter = new Rect(content.x(), content.y() + headerHeight, content.width(), filterHeight);
        Rect bodyFrame = new Rect(content.x(), filter.y() + filter.height(), content.width(), bodyHeight);
        Rect body = bodyFrame.inset(1);
        Rect status = new Rect(content.x(), bodyFrame.y() + bodyFrame.height(), content.width(), statusHeight);
        Rect locationControl = new Rect(
                header.x(),
                header.y(),
                header.width(),
                header.height()
        );
        Rect filterControl = new Rect(filter.x(), filter.y(), filter.width(), filter.height());
        return new Layout(content, header, filter, bodyFrame, body, status, locationControl, filterControl);
    }

    private static int divideRoundUp(int value, int divisor) {
        if (value == 0) return 0;
        return (value + divisor - 1) / divisor;
    }
}
