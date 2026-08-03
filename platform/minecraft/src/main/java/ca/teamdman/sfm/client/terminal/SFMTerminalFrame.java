package ca.teamdman.sfm.client.terminal;

import java.util.Arrays;

/** Immutable transport-neutral snapshot presented by a remote terminal backend. */
public record SFMTerminalFrame(
        long sequence,
        boolean full,
        boolean png,
        byte[] payload,
        SFMTerminalFrameMetadata metadata,
        String streamIdentity) {
    public SFMTerminalFrame {
        payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
        metadata = metadata == null
                ? new SFMTerminalFrameMetadata(0, 0, 0, 0, 0, 0, 0, 0,
                        "", "", 0, 0, 0, 0, 0, 0, 0, 0, "")
                : metadata;
        streamIdentity = normalizeStreamIdentity(streamIdentity, metadata);
    }

    /**
     * Source-compatible constructor for transport adapters which expose their
     * request-scoped stream token through the frame correlation metadata.
     */
    public SFMTerminalFrame(
            long sequence,
            boolean full,
            boolean png,
            byte[] payload,
            SFMTerminalFrameMetadata metadata) {
        this(sequence, full, png, payload, metadata, null);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    private static String normalizeStreamIdentity(
            String streamIdentity,
            SFMTerminalFrameMetadata metadata) {
        if (streamIdentity != null && !streamIdentity.isBlank()) return streamIdentity;
        if (!metadata.correlationId().isBlank()) return metadata.correlationId();
        return "legacy";
    }
}
