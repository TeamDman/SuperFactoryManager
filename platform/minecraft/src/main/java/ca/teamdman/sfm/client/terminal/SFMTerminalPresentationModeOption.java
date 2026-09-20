package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/** One advertised tuple after intersection with Java's bounded pixel presenters. */
public record SFMTerminalPresentationModeOption(
        SFMTerminalPresentationTuple tuple,
        SFMTerminalRasterEncoding encoding,
        SFMTerminalRasterFrameKind steadyFrameKind,
        int frameContractVersion,
        int maxPixelWidth,
        int maxPixelHeight,
        long maxFrameBytes,
        int maxRegions,
        boolean supported,
        String unavailableReason
) {
    public SFMTerminalPresentationModeOption {
        Objects.requireNonNull(tuple, "tuple");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(steadyFrameKind, "steadyFrameKind");
        unavailableReason = unavailableReason == null ? "" : unavailableReason;
        if (supported && !unavailableReason.isBlank()) {
            throw new IllegalArgumentException("supported mode cannot have an unavailable reason");
        }
        if (!supported && unavailableReason.isBlank()) {
            throw new IllegalArgumentException("unsupported mode must explain why it is unavailable");
        }
    }
}
