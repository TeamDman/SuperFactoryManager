package ca.teamdman.sfm.client.explorer;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Immutable result of resolving a live or revision-pinned selection path. */
public record SFMSelectionPathResolution(
        SFMPath requestedPath,
        long repositoryGeneration,
        Completeness completeness,
        Optional<SFMSelectionId> selectionId,
        Optional<Long> revisionId,
        boolean pinned,
        Set<SFMPath> members,
        List<SFMSelectorResolution.Diagnostic> diagnostics
) {
    public enum Completeness {
        COMPLETE,
        MISSING,
        INVALID
    }

    public SFMSelectionPathResolution {
        Objects.requireNonNull(requestedPath, "requestedPath");
        if (repositoryGeneration < 0) {
            throw new IllegalArgumentException("Repository generation must not be negative");
        }
        Objects.requireNonNull(completeness, "completeness");
        Objects.requireNonNull(selectionId, "selectionId");
        Objects.requireNonNull(revisionId, "revisionId");
        Objects.requireNonNull(members, "members");
        Objects.requireNonNull(diagnostics, "diagnostics");
        members = Collections.unmodifiableSet(new TreeSet<>(members));
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean complete() {
        return completeness == Completeness.COMPLETE;
    }
}
