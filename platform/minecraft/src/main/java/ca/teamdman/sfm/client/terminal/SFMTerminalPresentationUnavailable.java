package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/** A known non-selectable presentation tuple and its server-advertised error. */
public record SFMTerminalPresentationUnavailable(
        String rendererId,
        SFMTerminalRasterizationOwner rasterizationOwner,
        String damageModeId,
        String transportId,
        int transportVersion,
        SFMTerminalError error
) {
    public SFMTerminalPresentationUnavailable {
        rendererId = Objects.requireNonNull(rendererId, "rendererId");
        Objects.requireNonNull(rasterizationOwner, "rasterizationOwner");
        damageModeId = Objects.requireNonNull(damageModeId, "damageModeId");
        transportId = Objects.requireNonNull(transportId, "transportId");
        Objects.requireNonNull(error, "error");
    }
}
