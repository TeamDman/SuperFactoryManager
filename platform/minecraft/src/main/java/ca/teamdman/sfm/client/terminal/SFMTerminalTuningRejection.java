package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/** Structured rejection retained independently from terminal connection health. */
public record SFMTerminalTuningRejection(
        SFMTerminalError error,
        String request
) {
    public SFMTerminalTuningRejection {
        error = Objects.requireNonNull(error, "error");
        request = request == null ? "" : request;
    }

    public static SFMTerminalTuningRejection localInvalid(String message, String request) {
        return new SFMTerminalTuningRejection(
                new SFMTerminalError(
                        SFMTerminalErrorCode.INVALID_REQUEST,
                        message == null || message.isBlank()
                                ? "Terminal tuning request is invalid"
                                : message,
                        false,
                        0),
                request);
    }
}
