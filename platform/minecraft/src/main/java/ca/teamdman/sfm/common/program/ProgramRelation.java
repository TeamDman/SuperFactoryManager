package ca.teamdman.sfm.common.program;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** An immutable, ordered, occurrence-preserving relation. */
public record ProgramRelation(List<ProgramRelationRow> rows) {
    public static final ProgramRelation EMPTY = new ProgramRelation(List.of());

    public ProgramRelation {
        rows = List.copyOf(Objects.requireNonNull(rows));
    }

    public static ProgramRelation singleton(Object value) {
        return new ProgramRelation(List.of(new ProgramRelationRow(ProgramOccurrenceId.create(), value)));
    }

    public ProgramRelation map(Function<ProgramRelationRow, Object> mapper) {
        Objects.requireNonNull(mapper);
        return new ProgramRelation(rows.stream().map(row -> row.mapValue(mapper.apply(row))).toList());
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
