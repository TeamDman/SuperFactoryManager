package ca.teamdman.sfm.client.terminal;

import java.util.Optional;

/** Immediate result of validating and queueing one panel-local tuning change. */
public record SFMTerminalTuningChangeResult(
        boolean accepted,
        String message,
        Optional<SFMTerminalTuningRejection> rejection
) {
    public SFMTerminalTuningChangeResult {
        message = message == null ? "" : message;
        rejection = rejection == null ? Optional.empty() : rejection;
    }

    public static SFMTerminalTuningChangeResult accepted(String message) {
        return new SFMTerminalTuningChangeResult(true, message, Optional.empty());
    }

    public static SFMTerminalTuningChangeResult rejected(SFMTerminalTuningRejection rejection) {
        return new SFMTerminalTuningChangeResult(
                false,
                rejection.error().code() + ": " + rejection.error().message(),
                Optional.of(rejection));
    }

    public static SFMTerminalTuningChangeResult rejected(String message) {
        return rejected(SFMTerminalTuningRejection.localInvalid(message, "local validation"));
    }
}
