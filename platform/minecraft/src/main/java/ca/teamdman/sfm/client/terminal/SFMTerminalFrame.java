package ca.teamdman.sfm.client.terminal;

import java.util.Arrays;

/** Immutable transport-neutral snapshot presented by a remote terminal backend. */
public record SFMTerminalFrame(
        long sequence,
        boolean full,
        boolean png,
        byte[] payload,
        SFMTerminalFrameMetadata metadata) {
    public SFMTerminalFrame {
        payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
        metadata = metadata == null
                ? new SFMTerminalFrameMetadata(0, 0, 0, 0, 0, 0, 0, 0,
                        "", "", 0, 0, 0, 0, 0, 0, 0, 0, "")
                : metadata;
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
