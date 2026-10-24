package ca.teamdman.sfm.client.terminal;

import java.util.Objects;
import java.util.Optional;

/** Typed result of a Rust-owned guarded or explicitly approved paste request. */
public record SFMTerminalPasteResult(
        Disposition disposition, String preview, String contentId, Optional<SFMTerminalError> error) {
    public SFMTerminalPasteResult(Disposition disposition, String preview, String contentId) {
        this(disposition, preview, contentId, Optional.empty());
    }

    public static SFMTerminalPasteResult failed(SFMTerminalError error) {
        return new SFMTerminalPasteResult(Disposition.FAILED, "", "", Optional.of(error));
    }

    public SFMTerminalPasteResult {
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(error, "error");
        if ((disposition == Disposition.FAILED) != error.isPresent()) {
            throw new IllegalArgumentException("Only failed paste results carry a structured error");
        }
        preview = preview == null ? "" : preview;
        contentId = contentId == null ? "" : contentId;
        if (disposition == Disposition.CONFIRMATION_REQUIRED && contentId.isEmpty()) {
            throw new IllegalArgumentException("Paste confirmation requires an opaque content identity");
        }
        if (disposition != Disposition.CONFIRMATION_REQUIRED && (!preview.isEmpty() || !contentId.isEmpty())) {
            throw new IllegalArgumentException("Paste success or failure cannot retain confirmation data");
        }
    }

    public enum Disposition {
        PASTED,
        CONFIRMATION_REQUIRED,
        FAILED
    }
}
