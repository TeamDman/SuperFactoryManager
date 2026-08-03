package ca.teamdman.sfm.client.terminal;

import java.util.Arrays;

/** Stable wire identifiers for Rust-authoritative terminal raster transports. */
public enum SFMTerminalTransportId {
    FULL_PNG("full-png"),
    FULL_RAW_RGBA("full-raw-rgba"),
    DIRTY_RAW_RGBA("dirty-raw-rgba");

    private final String wireId;

    SFMTerminalTransportId(String wireId) {
        this.wireId = wireId;
    }

    public String wireId() {
        return wireId;
    }

    public static SFMTerminalTransportId fromWireId(String wireId) {
        return Arrays.stream(values())
                .filter(value -> value.wireId.equals(wireId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown terminal transport id: " + wireId));
    }
}
