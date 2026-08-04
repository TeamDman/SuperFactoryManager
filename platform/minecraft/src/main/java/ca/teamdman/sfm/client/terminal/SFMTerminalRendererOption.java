package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/** Cached renderer selector row for the currently requested transport. */
public record SFMTerminalRendererOption(
        SFMTerminalRendererId id,
        SFMTerminalRasterizationOwner rasterizationOwner,
        boolean supported,
        String unavailableReason
) {
    public SFMTerminalRendererOption {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rasterizationOwner, "rasterizationOwner");
        unavailableReason = unavailableReason == null ? "" : unavailableReason;
    }

    public String label() {
        return id.wireId() + " [" + rasterizationOwner.wireId() + "]"
                + (supported ? "" : " (unavailable: " + unavailableReason + ")");
    }
}
