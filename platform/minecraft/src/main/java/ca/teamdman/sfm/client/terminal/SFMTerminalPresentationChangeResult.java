package ca.teamdman.sfm.client.terminal;

/** Immediate result of a non-blocking, panel-local atomic presentation request. */
public record SFMTerminalPresentationChangeResult(boolean accepted, String message) {
    public SFMTerminalPresentationChangeResult {
        message = message == null ? "" : message;
    }

    public static SFMTerminalPresentationChangeResult accepted(String message) {
        return new SFMTerminalPresentationChangeResult(true, message);
    }

    public static SFMTerminalPresentationChangeResult rejected(String message) {
        return new SFMTerminalPresentationChangeResult(false, message);
    }
}
