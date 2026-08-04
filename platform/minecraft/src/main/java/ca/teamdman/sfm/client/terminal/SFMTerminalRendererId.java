package ca.teamdman.sfm.client.terminal;

import java.util.Arrays;

/** Stable renderer identities understood by the current Rust-raster SFM panel. */
public enum SFMTerminalRendererId {
    RUST_CPU_FONTDUE("rust-cpu-fontdue", SFMTerminalRasterizationOwner.SERVER),
    RUST_GPU_SLUG("rust-gpu-slug", SFMTerminalRasterizationOwner.SERVER);

    private final String wireId;
    private final SFMTerminalRasterizationOwner rasterizationOwner;

    SFMTerminalRendererId(
            String wireId,
            SFMTerminalRasterizationOwner rasterizationOwner
    ) {
        this.wireId = wireId;
        this.rasterizationOwner = rasterizationOwner;
    }

    public String wireId() {
        return wireId;
    }

    /** Declared capability metadata; callers must still validate the advertised owner. */
    public SFMTerminalRasterizationOwner rasterizationOwner() {
        return rasterizationOwner;
    }

    public static SFMTerminalRendererId fromWireId(String wireId) {
        return Arrays.stream(values())
                .filter(value -> value.wireId.equals(wireId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown terminal renderer id: " + wireId));
    }
}
