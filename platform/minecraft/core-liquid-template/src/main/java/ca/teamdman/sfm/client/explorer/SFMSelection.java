package ca.teamdman.sfm.client.explorer;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable movable heads for one session selection's revision tree. */
public record SFMSelection(
        SFMSelectionId id,
        Optional<String> name,
        Lifetime lifetime,
        long headRevisionId,
        Map<String, Long> namedHeadRevisionIds,
        Map<Long, Long> preferredChildRevisionIds
) {
    public enum Lifetime {
        SESSION
    }

    public SFMSelection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lifetime, "lifetime");
        if (headRevisionId <= 0) throw new IllegalArgumentException("Head revision id must be positive");
        namedHeadRevisionIds = immutableNamedHeads(namedHeadRevisionIds);
        preferredChildRevisionIds = immutableRevisionMap(
                preferredChildRevisionIds,
                "preferredChildRevisionIds"
        );
        name.ifPresent(value -> {
            SFMCanonicalText.requireValidUnicode(value, "selection.invalid-name");
            if (value.isEmpty()) throw new IllegalArgumentException("Selection name must not be empty");
        });
    }

    private static Map<String, Long> immutableNamedHeads(Map<String, Long> values) {
        Objects.requireNonNull(values, "namedHeadRevisionIds");
        TreeMap<String, Long> answer = new TreeMap<>();
        values.forEach((name, revisionId) -> {
            Objects.requireNonNull(name, "named head");
            SFMCanonicalText.requireValidUnicode(name, "selection.invalid-head-name");
            if (name.isEmpty()) throw new IllegalArgumentException("Named head must not be empty");
            if (revisionId == null || revisionId <= 0) {
                throw new IllegalArgumentException("Named head revision ids must be positive");
            }
            answer.put(name, revisionId);
        });
        return Collections.unmodifiableMap(answer);
    }

    private static Map<Long, Long> immutableRevisionMap(Map<Long, Long> values, String label) {
        Objects.requireNonNull(values, label);
        TreeMap<Long, Long> answer = new TreeMap<>();
        values.forEach((from, to) -> {
            if (from == null || from <= 0 || to == null || to <= 0) {
                throw new IllegalArgumentException(label + " must contain only positive revision ids");
            }
            answer.put(from, to);
        });
        return Collections.unmodifiableMap(answer);
    }

    public Optional<Long> namedHead(String headName) {
        Objects.requireNonNull(headName, "headName");
        return Optional.ofNullable(namedHeadRevisionIds.get(headName));
    }

    public Optional<Long> preferredChild(long parentRevisionId) {
        if (parentRevisionId <= 0) {
            throw new IllegalArgumentException("Parent revision id must be positive");
        }
        return Optional.ofNullable(preferredChildRevisionIds.get(parentRevisionId));
    }
}
