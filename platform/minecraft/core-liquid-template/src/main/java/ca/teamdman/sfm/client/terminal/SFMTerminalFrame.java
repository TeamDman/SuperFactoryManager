package ca.teamdman.sfm.client.terminal;

import java.util.Arrays;

/** Immutable transport-neutral snapshot presented by a remote terminal backend. */
{% if features.terminal_frame_metadata %}
public record SFMTerminalFrame(
        long sequence,
        boolean full,
        boolean png,
        byte[] payload,
        SFMTerminalFrameMetadata metadata,
        String streamIdentity) {
{% else %}
public record SFMTerminalFrame(
        long sequence,
        boolean full,
        boolean png,
        byte[] payload) {
{% endif %}
    public SFMTerminalFrame {
        payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
{% if features.terminal_frame_metadata %}
        metadata = metadata == null
                ? new SFMTerminalFrameMetadata(0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        "", "", 0, 0, 0, 0, 0, 0, 0, 0, "")
                : metadata;
        streamIdentity = normalizeStreamIdentity(streamIdentity, metadata);
{% endif %}
    }

{% if features.terminal_frame_metadata %}
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

{% endif %}
    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
{% if features.terminal_frame_metadata %}

    private static String normalizeStreamIdentity(
            String streamIdentity,
            SFMTerminalFrameMetadata metadata) {
        if (streamIdentity != null && !streamIdentity.isBlank()) return streamIdentity;
        if (!metadata.correlationId().isBlank()) return metadata.correlationId();
        return "legacy";
    }
{% endif %}
}
