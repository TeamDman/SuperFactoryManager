package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure mapping from a Rust-authoritative terminal cell selection to physical
 * framebuffer rectangles. The returned endpoints use the same
 * start-inclusive/end-exclusive convention as
 * {@code SFMScreenRenderUtils.renderHighlight}.
 */
public final class SFMTerminalSelectionLayout {
    private SFMTerminalSelectionLayout() {
    }

    /** A zero-based cell in the accepted logical terminal grid. */
    public record Cell(int column, int row) {
    }

    /**
     * Accepted native raster geometry. The native image may contain padding
     * after the cell grid, but it must contain every complete cell.
     */
    public record NativeGrid(
            int columns,
            int rows,
            int cellWidth,
            int cellHeight,
            int imageWidth,
            int imageHeight
    ) {
        public NativeGrid {
            if (columns < 1 || rows < 1) {
                throw new IllegalArgumentException("Terminal grid dimensions must be positive");
            }
            if (cellWidth < 1 || cellHeight < 1) {
                throw new IllegalArgumentException("Terminal cell dimensions must be positive");
            }
            if (imageWidth < 1 || imageHeight < 1) {
                throw new IllegalArgumentException("Terminal image dimensions must be positive");
            }
            long gridWidth = (long) columns * cellWidth;
            long gridHeight = (long) rows * cellHeight;
            if (gridWidth > imageWidth || gridHeight > imageHeight) {
                throw new IllegalArgumentException("Terminal image does not contain the accepted cell grid");
            }
        }
    }

    /** A clipped, non-empty physical framebuffer rectangle. */
    public record PhysicalHighlight(int startX, int startY, int endX, int endY) {
        public PhysicalHighlight {
            if (endX <= startX || endY <= startY) {
                throw new IllegalArgumentException("Terminal highlight must be non-empty");
            }
        }

        public int width() {
            return endX - startX;
        }

        public int height() {
            return endY - startY;
        }
    }

    /** A clipped panel-local rectangle suitable for {@code renderHighlight}. */
    public record LogicalHighlight(double startX, double startY, double endX, double endY) {
        public LogicalHighlight {
            if (!Double.isFinite(startX) || !Double.isFinite(startY)
                    || !Double.isFinite(endX) || !Double.isFinite(endY)
                    || endX <= startX || endY <= startY) {
                throw new IllegalArgumentException("Terminal logical highlight must be finite and non-empty");
            }
        }
    }

    /**
     * Maps the same inclusive selection to panel-local rectangles. Fractional
     * cell boundaries are retained so the active panel/gui transform performs
     * the final framebuffer projection exactly once.
     */
    public static List<LogicalHighlight> logicalHighlights(
            Cell anchor,
            Cell active,
            NativeGrid grid,
            SFMTerminalImageLayout imageLayout,
            SFMScreenPanelBounds logicalClip
    ) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(imageLayout, "imageLayout");
        Objects.requireNonNull(logicalClip, "logicalClip");
        Cell start = clamp(anchor, grid);
        Cell end = clamp(active, grid);
        if (start.equals(end)) return List.of();
        if (compare(start, end) > 0) {
            Cell swap = start;
            start = end;
            end = swap;
        }

        double clipRight = (double) logicalClip.x() + logicalClip.width();
        double clipBottom = (double) logicalClip.y() + logicalClip.height();
        List<LogicalHighlight> highlights = new ArrayList<>(end.row() - start.row() + 1);
        for (int row = start.row(); row <= end.row(); row++) {
            int startColumn = row == start.row() ? start.column() : 0;
            int endColumn = row == end.row() ? end.column() : grid.columns() - 1;
            double startX = imageLayout.x()
                    + startColumn * (double) grid.cellWidth() * imageLayout.width() / grid.imageWidth();
            double endX = imageLayout.x()
                    + (endColumn + 1) * (double) grid.cellWidth() * imageLayout.width() / grid.imageWidth();
            double startY = imageLayout.y()
                    + row * (double) grid.cellHeight() * imageLayout.height() / grid.imageHeight();
            double endY = imageLayout.y()
                    + (row + 1) * (double) grid.cellHeight() * imageLayout.height() / grid.imageHeight();
            startX = Math.max(startX, logicalClip.x());
            startY = Math.max(startY, logicalClip.y());
            endX = Math.min(endX, clipRight);
            endY = Math.min(endY, clipBottom);
            if (endX > startX && endY > startY) {
                highlights.add(new LogicalHighlight(startX, startY, endX, endY));
            }
        }
        return List.copyOf(highlights);
    }

    /**
     * Maps an inclusive anchor/active cell pair to one physical rectangle per
     * participating row. Endpoints are clamped before ordering, so forward and
     * reverse drags produce identical geometry. A collapsed pair denotes no
     * selection.
     *
     * @param imageLayout fitted panel-local image layout used by the presenter
     * @param imageMetrics measurement of exactly {@code imageLayout.bounds()}
     * @param physicalClip physical framebuffer bounds allowed to be highlighted
     */
    public static List<PhysicalHighlight> physicalHighlights(
            Cell anchor,
            Cell active,
            NativeGrid grid,
            SFMTerminalImageLayout imageLayout,
            SFMWorkspacePanelMetrics imageMetrics,
            SFMScreenPanelBounds physicalClip
    ) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(imageLayout, "imageLayout");
        Objects.requireNonNull(imageMetrics, "imageMetrics");
        Objects.requireNonNull(physicalClip, "physicalClip");
        if (!imageLayout.bounds().equals(imageMetrics.logicalBounds())) {
            throw new IllegalArgumentException("Image metrics must measure the fitted terminal image layout");
        }
        if (physicalClip.width() < 0 || physicalClip.height() < 0) {
            throw new IllegalArgumentException("Physical clip dimensions must be non-negative");
        }
        requirePositiveFinite(imageMetrics.panelRenderScale(), "panel render scale");
        requirePositiveFinite(imageMetrics.guiToPhysicalScaleX(), "horizontal GUI scale");
        requirePositiveFinite(imageMetrics.guiToPhysicalScaleY(), "vertical GUI scale");

        Cell start = clamp(anchor, grid);
        Cell end = clamp(active, grid);
        if (start.equals(end)) return List.of();
        if (compare(start, end) > 0) {
            Cell swap = start;
            start = end;
            end = swap;
        }

        long clipRight = (long) physicalClip.x() + physicalClip.width();
        long clipBottom = (long) physicalClip.y() + physicalClip.height();
        List<PhysicalHighlight> highlights = new ArrayList<>(end.row() - start.row() + 1);
        for (int row = start.row(); row <= end.row(); row++) {
            int startColumn = row == start.row() ? start.column() : 0;
            int endColumn = row == end.row() ? end.column() : grid.columns() - 1;
            int nativeStartX = Math.multiplyExact(startColumn, grid.cellWidth());
            int nativeEndX = Math.multiplyExact(endColumn + 1, grid.cellWidth());
            int nativeStartY = Math.multiplyExact(row, grid.cellHeight());
            int nativeEndY = Math.multiplyExact(row + 1, grid.cellHeight());

            int startX = physicalBoundaryX(
                    imageLayout, imageMetrics, nativeStartX, grid.imageWidth(), false);
            int endX = physicalBoundaryX(
                    imageLayout, imageMetrics, nativeEndX, grid.imageWidth(), true);
            int startY = physicalBoundaryY(
                    imageLayout, imageMetrics, nativeStartY, grid.imageHeight(), false);
            int endY = physicalBoundaryY(
                    imageLayout, imageMetrics, nativeEndY, grid.imageHeight(), true);

            startX = (int) Math.max(startX, physicalClip.x());
            startY = (int) Math.max(startY, physicalClip.y());
            endX = (int) Math.min(endX, clipRight);
            endY = (int) Math.min(endY, clipBottom);
            if (endX > startX && endY > startY) {
                highlights.add(new PhysicalHighlight(startX, startY, endX, endY));
            }
        }
        return List.copyOf(highlights);
    }

    private static Cell clamp(Cell cell, NativeGrid grid) {
        return new Cell(
                Math.max(0, Math.min(grid.columns() - 1, cell.column())),
                Math.max(0, Math.min(grid.rows() - 1, cell.row()))
        );
    }

    private static int compare(Cell left, Cell right) {
        int row = Integer.compare(left.row(), right.row());
        return row == 0 ? Integer.compare(left.column(), right.column()) : row;
    }

    private static int physicalBoundaryX(
            SFMTerminalImageLayout layout,
            SFMWorkspacePanelMetrics metrics,
            int nativeOffset,
            int nativeExtent,
            boolean roundUp
    ) {
        double guiContentX = metrics.globalGuiLogicalBounds().x()
                - Math.floor(layout.x() * metrics.panelRenderScale());
        double localX = layout.x() + nativeOffset * (double) layout.width() / nativeExtent;
        double physicalX = (guiContentX + localX * metrics.panelRenderScale())
                * metrics.guiToPhysicalScaleX();
        return roundedBoundary(physicalX, roundUp);
    }

    private static int physicalBoundaryY(
            SFMTerminalImageLayout layout,
            SFMWorkspacePanelMetrics metrics,
            int nativeOffset,
            int nativeExtent,
            boolean roundUp
    ) {
        double guiContentY = metrics.globalGuiLogicalBounds().y()
                - Math.floor(layout.y() * metrics.panelRenderScale());
        double localY = layout.y() + nativeOffset * (double) layout.height() / nativeExtent;
        double physicalY = (guiContentY + localY * metrics.panelRenderScale())
                * metrics.guiToPhysicalScaleY();
        return roundedBoundary(physicalY, roundUp);
    }

    private static int roundedBoundary(double value, boolean roundUp) {
        double rounded = roundUp ? Math.ceil(value) : Math.floor(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Terminal highlight boundary is outside the physical coordinate range");
        }
        return (int) rounded;
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException("Terminal " + label + " must be positive and finite");
        }
    }
}
