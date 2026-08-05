package ca.teamdman.sfm.client.terminal;

/** Latest typed Rust-raster stream identity and bounded delivery counters. */
public record SFMTerminalPresentationDiagnostics(
        String presentationGeneration,
        long terminalSequence,
        long frameSequence,
        long baseFrameSequence,
        boolean fullResync,
        String frameKind,
        int payloadBytes,
        int maximumPayloadBytes,
        long framesReceived,
        long framesAccepted,
        long framesRejected,
        long staleFrames,
        long receiverFailures,
        long fullResyncFrames,
        int maximumFrameBytes
) {
    public SFMTerminalPresentationDiagnostics {
        presentationGeneration = normalize(presentationGeneration);
        frameKind = normalize(frameKind);
        payloadBytes = Math.max(0, payloadBytes);
        maximumPayloadBytes = Math.max(payloadBytes, maximumPayloadBytes);
        maximumFrameBytes = Math.max(0, maximumFrameBytes);
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
