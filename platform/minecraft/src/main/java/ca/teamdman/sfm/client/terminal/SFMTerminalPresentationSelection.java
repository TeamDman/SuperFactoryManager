package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/** The two independently selected axes that are applied as one atomic request. */
public record SFMTerminalPresentationSelection(
        SFMTerminalRendererId rendererId,
        SFMTerminalTransportId transportId
) {
    public static final SFMTerminalPresentationSelection DEFAULT =
            new SFMTerminalPresentationSelection(
                    SFMTerminalRendererId.RUST_CPU_FONTDUE,
                    SFMTerminalTransportId.FULL_PNG);

    public SFMTerminalPresentationSelection {
        Objects.requireNonNull(rendererId, "rendererId");
        Objects.requireNonNull(transportId, "transportId");
    }

    public SFMTerminalPresentationSelection withRenderer(SFMTerminalRendererId renderer) {
        return new SFMTerminalPresentationSelection(renderer, transportId);
    }

    public SFMTerminalPresentationSelection withTransport(SFMTerminalTransportId transport) {
        return new SFMTerminalPresentationSelection(rendererId, transport);
    }

    public String label() {
        return rendererId.wireId() + " / " + transportId.wireId();
    }
}
