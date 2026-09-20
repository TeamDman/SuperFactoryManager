package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/** Cached transport selector row for the currently requested renderer. */
public record SFMTerminalTransportOption(
        SFMTerminalTransportId id,
        boolean supported,
        String unavailableReason) {
    public SFMTerminalTransportOption {
        Objects.requireNonNull(id, "id");
        unavailableReason = unavailableReason == null ? "" : unavailableReason;
    }

    public String label() {
        return id.wireId()
                + (supported ? "" : " (unavailable: " + unavailableReason + ")");
    }
}
