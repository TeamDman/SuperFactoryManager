package ca.teamdman.sfm.client.terminal;

/**
 * One immutable descriptor into an enclosing frame's packed dirty-region payload.
 * Regions are ordered by {@code (y, x)}, may not geometrically overlap, and
 * consume one contiguous segment of the enclosing payload. Each region uses
 * tightly packed RGBA8 rows, so {@code stride == width * 4}.
 *
 * Long byte fields permit validation to reject values before narrowing to JVM array indices.
 */
public record SFMTerminalRasterRegion(
        int x,
        int y,
        int width,
        int height,
        long stride,
        long payloadOffset,
        long payloadLength) {
}
