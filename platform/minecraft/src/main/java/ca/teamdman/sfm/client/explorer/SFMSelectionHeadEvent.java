package ca.teamdman.sfm.client.explorer;

import java.time.Instant;
import java.util.Objects;

/** Immutable provenance for an undo/redo head movement. */
public record SFMSelectionHeadEvent(
        long id,
        SFMSelectionId selectionId,
        long fromRevisionId,
        long toRevisionId,
        Kind kind,
        String actor,
        String requestId,
        Instant createdAt
) {
    public enum Kind {
        UNDO,
        REDO
    }

    public SFMSelectionHeadEvent {
        if (id <= 0 || fromRevisionId <= 0 || toRevisionId <= 0) {
            throw new IllegalArgumentException("Head event and revision ids must be positive");
        }
        Objects.requireNonNull(selectionId, "selectionId");
        Objects.requireNonNull(kind, "kind");
        actor = requireText(actor, "selection.invalid-actor", "Actor");
        requestId = requireText(requestId, "selection.invalid-request-id", "Request id");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String requireText(String value, String code, String label) {
        Objects.requireNonNull(value, label);
        SFMCanonicalText.requireValidUnicode(value, code);
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return value;
    }
}
