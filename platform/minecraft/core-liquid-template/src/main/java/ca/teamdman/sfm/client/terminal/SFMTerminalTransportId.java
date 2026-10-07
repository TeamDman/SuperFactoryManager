package ca.teamdman.sfm.client.terminal;

import java.util.Arrays;

/** Stable wire identifiers for Rust-authoritative terminal raster transports. */
public enum SFMTerminalTransportId {
    FULL_PNG("full-png", "full"),
    FULL_RAW_RGBA("full-raw-rgba", "full"),
    DIRTY_RAW_RGBA("dirty-raw-rgba", "dirty");

    public static final int SUPPORTED_VERSION = 1;

    private final String wireId;
    private final String damageModeId;

    SFMTerminalTransportId(String wireId, String damageModeId) {
        this.wireId = wireId;
        this.damageModeId = damageModeId;
    }

    public String wireId() {
        return wireId;
    }

    public String damageModeId() {
        return damageModeId;
    }

    public static SFMTerminalTransportId fromWireId(String wireId) {
        return Arrays.stream(values())
                .filter(value -> value.wireId.equals(wireId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown terminal transport id: " + wireId));
    }
}
