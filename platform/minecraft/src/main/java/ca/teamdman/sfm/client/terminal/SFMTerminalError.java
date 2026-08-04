package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/** Structured terminal failure retained independently from generated Vox bindings. */
public record SFMTerminalError(
        SFMTerminalErrorCode code,
        String message,
        boolean retryable,
        long serverSequence
) {
    public SFMTerminalError {
        Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
    }
}
