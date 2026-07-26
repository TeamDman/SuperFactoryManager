package ca.teamdman.sfm.client.terminal;

import java.util.List;

/** Typed result returned by both the Java-local service and future Vox backends. */
public record SFMTerminalResponse(boolean success, List<String> lines, String workingDirectory) {
    public SFMTerminalResponse {
        lines = List.copyOf(lines);
    }

    public static SFMTerminalResponse ok(List<String> lines, String workingDirectory) {
        return new SFMTerminalResponse(true, lines, workingDirectory);
    }

    public static SFMTerminalResponse error(String message, String workingDirectory) {
        return new SFMTerminalResponse(false, List.of(message), workingDirectory);
    }
}
