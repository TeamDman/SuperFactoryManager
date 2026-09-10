package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Resolver-contributed presentation and sort metadata for one logical path. */
public record SFMExplorerEntry(
        SFMPath path,
        String label,
        boolean expandable,
        Map<String, SortKey> sortKeys,
        List<String> searchTerms,
        List<String> diagnostics
) {
    public static final String SORT_NAME = "name";
    public static final String SORT_EXTENSION = "extension";
    public static final String SORT_ICON = "icon";
    /** Presentation metadata; unlike {@link #SORT_ICON}, this is not a selectable sort axis. */
    public static final String PRESENTATION_ICON_FALLBACK = "sfm:presentation/icon-fallback";
    public static final String PRESENTATION_ICON_LABEL = "sfm:presentation/icon-label";
    /** Semantic subject facts are distinct from decorated labels and ordering keys. */
    public static final String SUBJECT_NAME = "sfm:subject/name";
    public static final String SUBJECT_KIND = "sfm:subject/kind";
    /** An entry may open a document while also exposing children through its chevron. */
    public static final String PRIMARY_ACTION_OPEN = "sfm:interaction/primary-open";

    public boolean opensOnActivate() {
        return !expandable || sortKey(PRIMARY_ACTION_OPEN).value().filter("true"::equals).isPresent();
    }
    /** Resolver attestation of the full immediate-child count, not a filtered display count. */
    public static final String SUBJECT_COMPLETE_CHILD_COUNT = "sfm:subject/complete-child-count";

    /** A sort value or a stable explanation of why that value is unavailable. */
    public record SortKey(Optional<String> value, Optional<String> unavailableReason) {
        public SortKey {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(unavailableReason, "unavailableReason");
            if (value.isPresent() == unavailableReason.isPresent()) {
                throw new IllegalArgumentException(
                        "A sort key must contain either a value or an unavailable reason"
                );
            }
            value.ifPresent(candidate -> {
                if (candidate.isEmpty()) {
                    throw new IllegalArgumentException("Available sort values must not be empty");
                }
            });
            unavailableReason.ifPresent(reason -> {
                if (reason.isEmpty()) {
                    throw new IllegalArgumentException("Unavailable sort reasons must not be empty");
                }
            });
        }

        public static SortKey available(String value) {
            return new SortKey(Optional.of(value), Optional.empty());
        }

        public static SortKey unavailable(String reason) {
            return new SortKey(Optional.empty(), Optional.of(reason));
        }

        public boolean available() {
            return value.isPresent();
        }
    }

    public SFMExplorerEntry {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(label, "label");
        if (label.isEmpty()) throw new IllegalArgumentException("Explorer labels must not be empty");
        Objects.requireNonNull(sortKeys, "sortKeys");
        TreeMap<String, SortKey> immutableKeys = new TreeMap<>();
        sortKeys.forEach((id, key) -> {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("Sort key ids must not be empty");
            }
            immutableKeys.put(id, Objects.requireNonNull(key, "sortKey"));
        });
        SortKey name = immutableKeys.get(SORT_NAME);
        if (name == null || !name.available()) {
            throw new IllegalArgumentException("Every explorer entry must contribute an available name key");
        }
        sortKeys = Collections.unmodifiableMap(immutableKeys);
        searchTerms = List.copyOf(searchTerms);
        if (searchTerms.isEmpty() || searchTerms.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("Explorer search terms must contain non-empty values");
        }
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * Default search metadata keeps the historical label and canonical-path
     * behaviour. Resolvers whose visible labels repeat inherited context can
     * use the canonical constructor to contribute a narrower semantic surface.
     */
    public SFMExplorerEntry(
            SFMPath path,
            String label,
            boolean expandable,
            Map<String, SortKey> sortKeys,
            List<String> diagnostics
    ) {
        this(path, label, expandable, sortKeys, List.of(label, path.canonical()), diagnostics);
    }

    public SortKey sortKey(String id) {
        Objects.requireNonNull(id, "id");
        return sortKeys.getOrDefault(
                id,
                SortKey.unavailable("resolver did not contribute sort key `" + id + "`")
        );
    }

    public static SFMExplorerEntry simple(
            SFMPath path,
            String label,
            boolean expandable,
            Optional<String> iconKey
    ) {
        TreeMap<String, SortKey> keys = new TreeMap<>();
        keys.put(SORT_NAME, SortKey.available(label));
        String extension = path.extension();
        keys.put(
                SORT_EXTENSION,
                extension.isEmpty()
                        ? SortKey.unavailable("path has no extension")
                        : SortKey.available(extension)
        );
        keys.put(
                SORT_ICON,
                iconKey.<SortKey>map(SortKey::available)
                        .orElseGet(() -> SortKey.unavailable("resolver did not contribute an icon key"))
        );
        return new SFMExplorerEntry(path, label, expandable, keys, List.of(label, path.canonical()), List.of());
    }
}
