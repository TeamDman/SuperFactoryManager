package ca.teamdman.sfm.client.explorer;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable provenance for an undo/redo head movement. */
public record SFMSelectionHeadEvent(
        long id,
        SFMSelectionId selectionId,
        long fromRevisionId,
        long toRevisionId,
        Kind kind,
        Optional<String> headName,
        String actor,
        String requestId,
        Instant createdAt
) {
    public enum Kind {
        UNDO,
        REDO,
        CHECKOUT,
        NAME_HEAD
    }

    public SFMSelectionHeadEvent {
        if (id <= 0 || fromRevisionId <= 0 || toRevisionId <= 0) {
            throw new IllegalArgumentException("Head event and revision ids must be positive");
        }
        Objects.requireNonNull(selectionId, "selectionId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(headName, "headName");
        headName.ifPresent(value -> requireText(value, "selection.invalid-head-name", "Head name"));
        if (kind == Kind.NAME_HEAD && headName.isEmpty()) {
            throw new IllegalArgumentException("Named-head events require a head name");
        }
        if (kind != Kind.NAME_HEAD && headName.isPresent()) {
            throw new IllegalArgumentException("Only named-head events may carry a head name");
        }
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
