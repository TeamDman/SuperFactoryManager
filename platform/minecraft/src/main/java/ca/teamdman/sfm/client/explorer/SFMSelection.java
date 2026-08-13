package ca.teamdman.sfm.client.explorer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable current head and navigation history for one session selection. */
public record SFMSelection(
        SFMSelectionId id,
        Optional<String> name,
        Lifetime lifetime,
        long headRevisionId,
        List<Long> undoRevisionIds,
        List<Long> redoRevisionIds
) {
    public enum Lifetime {
        SESSION
    }

    public SFMSelection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lifetime, "lifetime");
        if (headRevisionId <= 0) throw new IllegalArgumentException("Head revision id must be positive");
        undoRevisionIds = immutableRevisionIds(undoRevisionIds, "undoRevisionIds");
        redoRevisionIds = immutableRevisionIds(redoRevisionIds, "redoRevisionIds");
        name.ifPresent(value -> {
            SFMCanonicalText.requireValidUnicode(value, "selection.invalid-name");
            if (value.isEmpty()) throw new IllegalArgumentException("Selection name must not be empty");
        });
    }

    private static List<Long> immutableRevisionIds(List<Long> values, String label) {
        Objects.requireNonNull(values, label);
        values = List.copyOf(values);
        if (values.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException(label + " must contain only positive revision ids");
        }
        return values;
    }
}
