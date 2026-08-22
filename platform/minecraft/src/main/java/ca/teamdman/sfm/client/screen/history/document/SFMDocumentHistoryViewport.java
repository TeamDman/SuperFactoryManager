package ca.teamdman.sfm.client.screen.history.document;

import ca.teamdman.sfm.client.history.canvas.SFMHistoryCanvasLayout;

/** Immutable pan/zoom transform between panel-local pixels and canvas coordinates. */
public record SFMDocumentHistoryViewport(double panX, double panY, double zoom) {
    public static final double MIN_ZOOM = 0.25D;
    public static final double MAX_ZOOM = 4.0D;

    public SFMDocumentHistoryViewport {
        requireFinite(panX, "panX");
        requireFinite(panY, "panY");
        requireFinite(zoom, "zoom");
        if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) {
            throw new IllegalArgumentException("zoom must be between " + MIN_ZOOM + " and " + MAX_ZOOM);
        }
    }

    public static SFMDocumentHistoryViewport identity() {
        return new SFMDocumentHistoryViewport(0.0D, 0.0D, 1.0D);
    }

    public static SFMDocumentHistoryViewport fit(
            SFMHistoryCanvasLayout.Rect content,
            int width,
            int height,
            int padding
    ) {
        if (width <= 0 || height <= 0 || content.isEmpty()) return identity();
        int safePadding = Math.max(0, padding);
        double availableWidth = Math.max(1.0D, width - safePadding * 2.0D);
        double availableHeight = Math.max(1.0D, height - safePadding * 2.0D);
        double zoom = clamp(Math.min(
                availableWidth / Math.max(1.0D, content.width()),
                availableHeight / Math.max(1.0D, content.height())
        ));
        double renderedWidth = content.width() * zoom;
        double renderedHeight = content.height() * zoom;
        double panX = (width - renderedWidth) / 2.0D - content.x() * zoom;
        double panY = (height - renderedHeight) / 2.0D - content.y() * zoom;
        return new SFMDocumentHistoryViewport(panX, panY, zoom);
    }

    public SFMDocumentHistoryViewport panBy(double deltaX, double deltaY) {
        return new SFMDocumentHistoryViewport(panX + deltaX, panY + deltaY, zoom);
    }

    /** Zooms around one panel-local point so the canvas point beneath it remains stationary. */
    public SFMDocumentHistoryViewport zoomAt(double localX, double localY, double factor) {
        requireFinite(localX, "localX");
        requireFinite(localY, "localY");
        requireFinite(factor, "factor");
        if (factor <= 0.0D) throw new IllegalArgumentException("factor must be positive");
        double nextZoom = clamp(zoom * factor);
        double canvasX = (localX - panX) / zoom;
        double canvasY = (localY - panY) / zoom;
        return new SFMDocumentHistoryViewport(
                localX - canvasX * nextZoom,
                localY - canvasY * nextZoom,
                nextZoom
        );
    }

    public SFMHistoryCanvasLayout.Point screenToCanvas(double localX, double localY) {
        return new SFMHistoryCanvasLayout.Point(
                safeFloor((localX - panX) / zoom),
                safeFloor((localY - panY) / zoom)
        );
    }

    public double canvasToScreenX(double canvasX) {
        return panX + canvasX * zoom;
    }

    public double canvasToScreenY(double canvasY) {
        return panY + canvasY * zoom;
    }

    public SFMHistoryCanvasLayout.Rect visibleCanvasBounds(int width, int height) {
        if (width <= 0 || height <= 0) return new SFMHistoryCanvasLayout.Rect(0, 0, 0, 0);
        int left = safeFloor(-panX / zoom);
        int top = safeFloor(-panY / zoom);
        int right = safeCeil((width - panX) / zoom);
        int bottom = safeCeil((height - panY) / zoom);
        return new SFMHistoryCanvasLayout.Rect(
                left,
                top,
                Math.max(1, safeDifference(right, left)),
                Math.max(1, safeDifference(bottom, top))
        );
    }

    private static double clamp(double value) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }

    private static int safeFloor(double value) {
        if (value <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.floor(value);
    }

    private static int safeCeil(double value) {
        if (value <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.ceil(value);
    }

    private static int safeDifference(int right, int left) {
        long difference = (long) right - left;
        return difference >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, difference);
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(label + " must be finite");
    }
}
