package ca.teamdman.sfm.client.terminal;

/** Negotiated hard bounds applied before allocating or composing raster data. */
public record SFMTerminalRasterLimits(
        int maxWidth,
        int maxHeight,
        long maxPayloadBytes,
        int maxRegions) {
    public static final int RGBA8_V1_MAX_WIDTH = 4096;
    public static final int RGBA8_V1_MAX_HEIGHT = 4096;
    public static final long RGBA8_V1_MAX_PAYLOAD_BYTES = 64L * 1024L * 1024L;
    public static final int RGBA8_V1_MAX_REGIONS = 64;

    public static final SFMTerminalRasterLimits RGBA8_V1_DEFAULTS =
            new SFMTerminalRasterLimits(
                    RGBA8_V1_MAX_WIDTH,
                    RGBA8_V1_MAX_HEIGHT,
                    RGBA8_V1_MAX_PAYLOAD_BYTES,
                    RGBA8_V1_MAX_REGIONS);

    public SFMTerminalRasterLimits {
        if (maxWidth <= 0) throw new IllegalArgumentException("maxWidth must be positive");
        if (maxHeight <= 0) throw new IllegalArgumentException("maxHeight must be positive");
        if (maxPayloadBytes <= 0) throw new IllegalArgumentException("maxPayloadBytes must be positive");
        if (maxRegions <= 0) throw new IllegalArgumentException("maxRegions must be positive");
        if (maxWidth > RGBA8_V1_MAX_WIDTH) {
            throw new IllegalArgumentException("maxWidth exceeds the RGBA8 v1 hard bound");
        }
        if (maxHeight > RGBA8_V1_MAX_HEIGHT) {
            throw new IllegalArgumentException("maxHeight exceeds the RGBA8 v1 hard bound");
        }
        if (maxPayloadBytes > RGBA8_V1_MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("maxPayloadBytes exceeds the RGBA8 v1 hard bound");
        }
        if (maxRegions > RGBA8_V1_MAX_REGIONS) {
            throw new IllegalArgumentException("maxRegions exceeds the RGBA8 v1 hard bound");
        }
    }
}
