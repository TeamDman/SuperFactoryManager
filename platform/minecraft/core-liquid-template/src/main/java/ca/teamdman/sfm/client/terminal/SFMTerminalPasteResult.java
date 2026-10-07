package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/** Typed result of a Rust-owned guarded or explicitly approved paste request. */
public record SFMTerminalPasteResult(Disposition disposition, String preview, String contentId) {
    public SFMTerminalPasteResult {
        Objects.requireNonNull(disposition, "disposition");
        preview = preview == null ? "" : preview;
        contentId = contentId == null ? "" : contentId;
        if (disposition == Disposition.CONFIRMATION_REQUIRED && contentId.isEmpty()) {
            throw new IllegalArgumentException("Paste confirmation requires an opaque content identity");
        }
        if (disposition == Disposition.PASTED && (!preview.isEmpty() || !contentId.isEmpty())) {
            throw new IllegalArgumentException("Completed paste cannot retain confirmation data");
        }
    }

    public enum Disposition {
        PASTED,
        CONFIRMATION_REQUIRED
    }
}
