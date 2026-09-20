package ca.teamdman.sfm.client.explorer.action;

import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMSelectorResolution;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Typed outcome and revision evidence for an explorer action transaction. */
public record SFMExplorerActionResult(
        Status status,
        String operation,
        SFMSelectorResolution<SFMExplorerId> selectorResolution,
        long capturedRegistryGeneration,
        long resultingRegistryGeneration,
        List<TargetResult> targets,
        List<String> diagnostics
) {
    public enum Status {
        SUCCEEDED,
        NO_TARGETS,
        REJECTED,
        STALE
    }

    public enum TargetOutcome {
        DESCRIBED,
        UNCHANGED,
        APPLIED,
        REFRESH_REQUESTED,
        CREATED,
        REJECTED,
        STALE
    }

    public record RevisionEvidence(
            long sessionRevision,
            long selectionRepositoryGeneration,
            Optional<Long> locationSelectionRevision,
            long relationRevision,
            long relationStatusGeneration
    ) {
        public RevisionEvidence {
            if (sessionRevision < 0 || selectionRepositoryGeneration < 0 || relationRevision < 0
                    || relationStatusGeneration < 0) {
                throw new IllegalArgumentException("Revision evidence must not be negative");
            }
            Objects.requireNonNull(locationSelectionRevision, "locationSelectionRevision");
        }
    }

    public record TargetResult(
            SFMExplorerId explorerId,
            TargetOutcome outcome,
            Optional<RevisionEvidence> before,
            RevisionEvidence after,
            SFMExplorerSession.Snapshot snapshot,
            Optional<SFMLazyExplorerLoader.RequestEvidence> loadRequest,
            List<String> diagnostics
    ) {
        public TargetResult {
            Objects.requireNonNull(explorerId, "explorerId");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(loadRequest, "loadRequest");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public SFMExplorerActionResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(selectorResolution, "selectorResolution");
        if (capturedRegistryGeneration < 0 || resultingRegistryGeneration < 0) {
            throw new IllegalArgumentException("Registry generations must not be negative");
        }
        targets = List.copyOf(targets);
        diagnostics = List.copyOf(diagnostics);
    }
}
