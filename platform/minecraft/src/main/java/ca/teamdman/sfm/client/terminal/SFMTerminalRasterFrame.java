package ca.teamdman.sfm.client.terminal;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, transport-neutral terminal raster publication.
 *
 * <p>The constructor establishes defensive ownership only. Use
 * {@link SFMTerminalRasterFrameValidator} for negotiated contract validation
 * before allocating presentation resources or composing pixels.</p>
 */
public final class SFMTerminalRasterFrame {
    private final SFMTerminalTransportId transportId;
    private final int transportVersion;
    private final int frameContractVersion;
    private final String generation;
    private final long frameSequence;
    private final long baseFrameSequence;
    private final boolean fullResync;
    private final SFMTerminalRasterEncoding encoding;
    private final SFMTerminalRasterFrameKind kind;
    private final SFMTerminalRasterOrigin origin;
    private final SFMTerminalRasterAlphaMode alphaMode;
    private final SFMTerminalRasterColorSpace colorSpace;
    private final int width;
    private final int height;
    private final long stride;
    private final byte[] payload;
    private final List<SFMTerminalRasterRegion> regions;

    public SFMTerminalRasterFrame(
            SFMTerminalTransportId transportId,
            int transportVersion,
            int frameContractVersion,
            String generation,
            long frameSequence,
            long baseFrameSequence,
            boolean fullResync,
            SFMTerminalRasterEncoding encoding,
            SFMTerminalRasterFrameKind kind,
            SFMTerminalRasterOrigin origin,
            SFMTerminalRasterAlphaMode alphaMode,
            SFMTerminalRasterColorSpace colorSpace,
            int width,
            int height,
            long stride,
            byte[] payload,
            List<SFMTerminalRasterRegion> regions) {
        this.transportId = Objects.requireNonNull(transportId, "transportId");
        this.transportVersion = transportVersion;
        this.frameContractVersion = frameContractVersion;
        this.generation = Objects.requireNonNull(generation, "generation");
        this.frameSequence = frameSequence;
        this.baseFrameSequence = baseFrameSequence;
        this.fullResync = fullResync;
        this.encoding = Objects.requireNonNull(encoding, "encoding");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.alphaMode = Objects.requireNonNull(alphaMode, "alphaMode");
        this.colorSpace = Objects.requireNonNull(colorSpace, "colorSpace");
        this.width = width;
        this.height = height;
        this.stride = stride;
        this.payload = Arrays.copyOf(Objects.requireNonNull(payload, "payload"), payload.length);
        this.regions = List.copyOf(Objects.requireNonNull(regions, "regions"));
    }

    public SFMTerminalTransportId transportId() {
        return transportId;
    }

    public int transportVersion() {
        return transportVersion;
    }

    public int frameContractVersion() {
        return frameContractVersion;
    }

    public String generation() {
        return generation;
    }

    public long frameSequence() {
        return frameSequence;
    }

    public long baseFrameSequence() {
        return baseFrameSequence;
    }

    public boolean fullResync() {
        return fullResync;
    }

    public SFMTerminalRasterEncoding encoding() {
        return encoding;
    }

    public SFMTerminalRasterFrameKind kind() {
        return kind;
    }

    public SFMTerminalRasterOrigin origin() {
        return origin;
    }

    public SFMTerminalRasterAlphaMode alphaMode() {
        return alphaMode;
    }

    public SFMTerminalRasterColorSpace colorSpace() {
        return colorSpace;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public long stride() {
        return stride;
    }

    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public int payloadLength() {
        return payload.length;
    }

    public List<SFMTerminalRasterRegion> regions() {
        return regions;
    }
}
