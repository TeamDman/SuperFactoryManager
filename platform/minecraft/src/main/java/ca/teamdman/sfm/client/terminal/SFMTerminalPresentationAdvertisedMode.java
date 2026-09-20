package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/**
 * Protocol-independent capability input. Vox bindings and tests both adapt to
 * this shape, so ordinary Java model tests do not load generated classes.
 */
public record SFMTerminalPresentationAdvertisedMode(
        String rendererId,
        String rasterizationOwner,
        String damageModeId,
        String transportId,
        int transportVersion,
        SFMTerminalRasterEncoding encoding,
        SFMTerminalRasterFrameKind steadyFrameKind,
        int frameContractVersion,
        SFMTerminalRasterOrigin origin,
        SFMTerminalRasterAlphaMode alphaMode,
        SFMTerminalRasterColorSpace colorSpace,
        int maxPixelWidth,
        int maxPixelHeight,
        long maxFrameBytes,
        int maxRegions
) {
    public SFMTerminalPresentationAdvertisedMode {
        rendererId = Objects.requireNonNull(rendererId, "rendererId");
        rasterizationOwner = Objects.requireNonNull(rasterizationOwner, "rasterizationOwner");
        damageModeId = Objects.requireNonNull(damageModeId, "damageModeId");
        transportId = Objects.requireNonNull(transportId, "transportId");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(steadyFrameKind, "steadyFrameKind");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(alphaMode, "alphaMode");
        Objects.requireNonNull(colorSpace, "colorSpace");
    }
}
