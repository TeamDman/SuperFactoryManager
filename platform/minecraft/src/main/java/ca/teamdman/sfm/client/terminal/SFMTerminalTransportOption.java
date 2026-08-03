package ca.teamdman.sfm.client.terminal;

/** One server-advertised raster transport after intersecting it with Java presenters. */
public record SFMTerminalTransportOption(
        String id,
        String rendererId,
        String damageModeId,
        int version,
        boolean supported,
        String unavailableReason) {
    public SFMTerminalTransportOption {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("transport id must not be blank");
        rendererId = rendererId == null ? "" : rendererId;
        damageModeId = damageModeId == null ? "" : damageModeId;
        unavailableReason = unavailableReason == null ? "" : unavailableReason;
    }

    public String label() {
        return id + (supported ? "" : " (unavailable)");
    }
}
