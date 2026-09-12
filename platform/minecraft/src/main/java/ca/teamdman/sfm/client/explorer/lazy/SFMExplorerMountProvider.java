package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Adds a lazily resolved child hierarchy to an otherwise ordinary resolver entry.
 *
 * <p>The backing entry keeps its own path, text-read behavior, and subject kind.
 * A mount contributes only the capability to expand that same entry into children,
 * which may belong to another registered resolver scheme.</p>
 */
public interface SFMExplorerMountProvider {
    record Page(
            List<SFMExplorerEntry> entries,
            Optional<String> continuation,
            List<String> diagnostics,
            int observedEntries
    ) {
        public Page {
            entries = List.copyOf(entries);
            Objects.requireNonNull(continuation, "continuation");
            diagnostics = List.copyOf(diagnostics);
            if (observedEntries < entries.size()) {
                throw new IllegalArgumentException("Observed count cannot be smaller than mounted entries");
            }
        }
    }

    /** Stable diagnostic identity for the mounted capability. */
    String id();

    /** Cheap path-only test; the decorator still validates the backing entry first. */
    boolean supports(SFMPath path);

    CompletableFuture<Page> resolveChildren(
            SFMExplorerResolver.ChildRequest request,
            SFMExplorerEntry backingEntry
    );
}
