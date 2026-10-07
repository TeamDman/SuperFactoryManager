package ca.teamdman.sfm.client.explorer;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * One immutable repository view captured before evaluating a selector.
 *
 * <p>The generation belongs to the repository, not the selector. Callers can
 * revalidate it before publishing a later mutation.</p>
 */
public record SFMSelectorRepositorySnapshot<I>(
        long generation,
        List<SFMSelectorRepositoryEntry<I>> entries
) {
    public SFMSelectorRepositorySnapshot {
        if (generation < 0) {
            throw new IllegalArgumentException("Repository generation must not be negative");
        }
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
        HashSet<I> identities = new HashSet<>();
        for (SFMSelectorRepositoryEntry<I> entry : entries) {
            Objects.requireNonNull(entry, "entry");
            if (!identities.add(entry.id())) {
                throw new IllegalArgumentException("Selector snapshot contains a duplicate typed identity");
            }
        }
    }
}
