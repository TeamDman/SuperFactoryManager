package ca.teamdman.sfm.client.screen.file_explorer;

import java.util.Objects;

public record SFMFileReadResult(State state, String text, String message) {
    public enum State { READY, UNSUPPORTED, ERROR }

    public SFMFileReadResult {
        Objects.requireNonNull(state);
        Objects.requireNonNull(text);
        Objects.requireNonNull(message);
    }

    public static SFMFileReadResult ready(String text) {
        return new SFMFileReadResult(State.READY, text, "");
    }

    public static SFMFileReadResult unsupported(String message) {
        return new SFMFileReadResult(State.UNSUPPORTED, "", message);
    }

    public static SFMFileReadResult error(String message) {
        return new SFMFileReadResult(State.ERROR, "", message);
    }
}
