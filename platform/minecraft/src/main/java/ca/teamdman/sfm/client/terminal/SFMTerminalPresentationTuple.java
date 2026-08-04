package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/** Complete negotiated identity guarded by one presentation generation. */
public record SFMTerminalPresentationTuple(
        SFMTerminalRendererId rendererId,
        SFMTerminalRasterizationOwner rasterizationOwner,
        SFMTerminalTransportId transportId,
        String damageModeId,
        int transportVersion
) {
    public SFMTerminalPresentationTuple {
        Objects.requireNonNull(rendererId, "rendererId");
        Objects.requireNonNull(rasterizationOwner, "rasterizationOwner");
        Objects.requireNonNull(transportId, "transportId");
        if (damageModeId == null || damageModeId.isBlank()) {
            throw new IllegalArgumentException("damage mode id must not be blank");
        }
        if (transportVersion <= 0) {
            throw new IllegalArgumentException("transport version must be positive");
        }
    }

    public SFMTerminalPresentationSelection selection() {
        return new SFMTerminalPresentationSelection(rendererId, transportId);
    }
}
