package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/** Immutable authority for delayed review navigation, independent of transient palette focus. */
record SFMReleaseReviewNavigationGuard(
        SFMClientActionContinuation origin,
        SFMClientActionContinuation destination,
        Path reviewPath,
        long openEpoch,
        long generation,
        Set<SFMPath> roots
) {
    SFMReleaseReviewNavigationGuard {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        reviewPath = Objects.requireNonNull(reviewPath, "reviewPath").toAbsolutePath().normalize();
        roots = Set.copyOf(roots);
    }

    boolean isCurrent(SFMReleaseReviewRuntime.Snapshot current) {
        return origin.isCurrent() && destination.isCurrent()
                && destination.panel() instanceof SFMExplorerPanel explorer
                && matches(origin.host(), current, explorer.sessionSnapshot().roots());
    }

    /** Pure seam for close/recreate, workspace, epoch and root-replacement regressions. */
    boolean matches(Object activeHost, SFMReleaseReviewRuntime.Snapshot current, Set<SFMPath> currentRoots) {
        return origin.matches(activeHost) && destination.matches(activeHost)
                && current.openEpoch() == openEpoch && current.generation() == generation
                && current.path().map(path -> path.toAbsolutePath().normalize().equals(reviewPath)).orElse(false)
                && roots.equals(currentRoots);
    }
}
