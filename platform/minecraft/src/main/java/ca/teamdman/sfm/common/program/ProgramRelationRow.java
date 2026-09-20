package ca.teamdman.sfm.common.program;

import java.util.Objects;

/** One occurrence-preserving relation row. */
public record ProgramRelationRow(
        ProgramOccurrenceId occurrenceId,
        Object value
) {
    public ProgramRelationRow {
        Objects.requireNonNull(occurrenceId);
        Objects.requireNonNull(value);
    }

    public ProgramRelationRow mapValue(Object replacement) {
        return new ProgramRelationRow(occurrenceId, replacement);
    }
}
