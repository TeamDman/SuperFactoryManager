package ca.teamdman.sfm.client.terminal;

import java.util.List;
import java.util.Objects;

/** Typed result returned by both the Java-local service and future Vox backends. */
public final class SFMTerminalResponse {
    private final boolean success;
    private final List<String> lines;
    private final String workingDirectory;
    private final List<SFMTerminalLine> styledLines;

    public SFMTerminalResponse(boolean success, List<String> lines, String workingDirectory) {
        this(success, lines, workingDirectory, lines.stream().map(SFMTerminalLine::plain).toList());
    }

    private SFMTerminalResponse(
            boolean success,
            List<String> lines,
            String workingDirectory,
            List<SFMTerminalLine> styledLines
    ) {
        this.success = success;
        this.lines = List.copyOf(lines);
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
        this.styledLines = List.copyOf(styledLines);
        if (this.lines.size() != this.styledLines.size()) {
            throw new IllegalArgumentException("Terminal text and styled-line counts must match");
        }
    }

    public boolean success() { return success; }

    public List<String> lines() { return lines; }

    public String workingDirectory() { return workingDirectory; }

    public List<SFMTerminalLine> styledLines() { return styledLines; }

    public static SFMTerminalResponse ok(List<String> lines, String workingDirectory) {
        return new SFMTerminalResponse(true, lines, workingDirectory);
    }

    public static SFMTerminalResponse styledOk(List<SFMTerminalLine> lines, String workingDirectory) {
        return new SFMTerminalResponse(true, lines.stream().map(SFMTerminalLine::text).toList(), workingDirectory, lines);
    }

    public static SFMTerminalResponse error(String message, String workingDirectory) {
        return new SFMTerminalResponse(false, List.of(message), workingDirectory);
    }
}
