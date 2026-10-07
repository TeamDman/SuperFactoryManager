package ca.teamdman.sfm.client.text_editor;

/** Half-open vertical coverage: adjacent translucent rows share one rounded boundary. */
public final class SFMTextHighlightRaster {
    private SFMTextHighlightRaster() { }

    public static int boundary(double coordinate) {
        if (!Double.isFinite(coordinate) || coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Highlight boundary is outside the raster domain");
        }
        return (int) Math.floor(coordinate);
    }
}
