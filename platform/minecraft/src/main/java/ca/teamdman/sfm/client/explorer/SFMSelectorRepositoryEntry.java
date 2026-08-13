package ca.teamdman.sfm.client.explorer;

import java.util.Objects;
import java.util.Optional;

/** Immutable selector-visible metadata for one entity. */
public record SFMSelectorRepositoryEntry<I>(
        I id,
        Optional<String> name,
        boolean focused
) {
    public SFMSelectorRepositoryEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        name.ifPresent(value -> {
            SFMCanonicalText.requireValidUnicode(value, "selector.invalid-repository-name");
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Selector-visible names must not be empty");
            }
        });
    }

    public static <I> SFMSelectorRepositoryEntry<I> unnamed(I id, boolean focused) {
        return new SFMSelectorRepositoryEntry<>(id, Optional.empty(), focused);
    }

    public static <I> SFMSelectorRepositoryEntry<I> named(I id, String name) {
        return new SFMSelectorRepositoryEntry<>(id, Optional.of(name), false);
    }
}
