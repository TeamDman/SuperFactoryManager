package ca.teamdman.sfm.client.explorer;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** One immutable selection-membership revision and its operation provenance. */
public record SFMSelectionRevision(
        long id,
        SFMSelectionId selectionId,
        List<Long> parentRevisionIds,
        Set<SFMPath> members,
        Operation operation,
        String actor,
        String requestId,
        Instant createdAt
) {
    public enum OperationKind {
        CREATE,
        ADD,
        REMOVE,
        UNION,
        INTERSECTION,
        DIFFERENCE
    }

    public record Operation(
            OperationKind kind,
            List<SFMSelectionId> sourceSelections,
            Set<SFMPath> operandPaths
    ) {
        public Operation {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(sourceSelections, "sourceSelections");
            Objects.requireNonNull(operandPaths, "operandPaths");
            sourceSelections = List.copyOf(sourceSelections);
            sourceSelections.forEach(source -> Objects.requireNonNull(source, "sourceSelection"));
            operandPaths = Collections.unmodifiableSet(new TreeSet<>(operandPaths));
        }
    }

    public SFMSelectionRevision {
        if (id <= 0) throw new IllegalArgumentException("Revision id must be positive");
        Objects.requireNonNull(selectionId, "selectionId");
        Objects.requireNonNull(parentRevisionIds, "parentRevisionIds");
        parentRevisionIds = List.copyOf(parentRevisionIds);
        if (parentRevisionIds.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException("Parent revision ids must be positive");
        }
        Objects.requireNonNull(members, "members");
        members = Collections.unmodifiableSet(new TreeSet<>(members));
        Objects.requireNonNull(operation, "operation");
        actor = requireText(actor, "selection.invalid-actor", "Actor");
        requestId = requireText(requestId, "selection.invalid-request-id", "Request id");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public String revisionToken() {
        return "revision-" + id;
    }

    private static String requireText(String value, String code, String label) {
        Objects.requireNonNull(value, label);
        SFMCanonicalText.requireValidUnicode(value, code);
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return value;
    }
}
