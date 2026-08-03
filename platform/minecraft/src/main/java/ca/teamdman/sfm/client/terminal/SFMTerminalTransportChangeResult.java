package ca.teamdman.sfm.client.terminal;

/** Immediate result of a non-blocking, panel-local transport request. */
public record SFMTerminalTransportChangeResult(boolean accepted, String message) {
    public SFMTerminalTransportChangeResult {
        message = message == null ? "" : message;
    }

    public static SFMTerminalTransportChangeResult accepted(String message) {
        return new SFMTerminalTransportChangeResult(true, message);
    }

    public static SFMTerminalTransportChangeResult rejected(String message) {
        return new SFMTerminalTransportChangeResult(false, message);
    }
}
