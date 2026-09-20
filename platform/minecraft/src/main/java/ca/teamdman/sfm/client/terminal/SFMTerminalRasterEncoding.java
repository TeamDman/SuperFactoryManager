package ca.teamdman.sfm.client.terminal;

/** Payload encodings used by the bounded terminal raster model. */
public enum SFMTerminalRasterEncoding {
    PNG,
    /**
     * Four bytes per pixel in red, green, blue, alpha order. Contract v1
     * further requires top-left origin, top-to-bottom rows, straight alpha,
     * and sRGB color values.
     */
    RGBA8
}
