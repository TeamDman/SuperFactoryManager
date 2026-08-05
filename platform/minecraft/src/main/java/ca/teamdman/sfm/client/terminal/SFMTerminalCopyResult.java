package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/** Typed result of atomically copying and clearing the Rust-owned selection. */
public record SFMTerminalCopyResult(Disposition disposition, String text) {
    public SFMTerminalCopyResult {
        Objects.requireNonNull(disposition, "disposition");
        text = text == null ? "" : text;
        if (disposition == Disposition.NO_SELECTION && !text.isEmpty()) {
            throw new IllegalArgumentException("No-selection copy result cannot contain text");
        }
    }

    public enum Disposition {
        COPIED,
        NO_SELECTION
    }
}
