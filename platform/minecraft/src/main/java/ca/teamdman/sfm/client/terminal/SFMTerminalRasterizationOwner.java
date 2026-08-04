package ca.teamdman.sfm.client.terminal;

import java.util.Arrays;

/** Process boundary that owns the final terminal-cell rasterization step. */
public enum SFMTerminalRasterizationOwner {
    SERVER("server"),
    CLIENT("client");

    private final String wireId;

    SFMTerminalRasterizationOwner(String wireId) {
        this.wireId = wireId;
    }

    public String wireId() {
        return wireId;
    }

    public static SFMTerminalRasterizationOwner fromWireId(String wireId) {
        return Arrays.stream(values())
                .filter(value -> value.wireId.equals(wireId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown terminal rasterization owner: " + wireId));
    }
}
