package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerMountProvider;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Mounts explicit review projections beneath an ordinary review-file row. */
public final class SFMReleaseReviewFileMountProvider implements SFMExplorerMountProvider {
    public static final String ID = "sfm:release-review";

    private final SFMReleaseReviewRuntime reviewRuntime;
    private final SFMReleaseReviewExplorerRuntime reviewExplorer;
    private Path openingPath;
    private CompletableFuture<SFMReleaseReviewRuntime.Snapshot> opening;

    public SFMReleaseReviewFileMountProvider(
            SFMReleaseReviewRuntime reviewRuntime,
            SFMReleaseReviewExplorerRuntime reviewExplorer
    ) {
        this.reviewRuntime = Objects.requireNonNull(reviewRuntime, "reviewRuntime");
        this.reviewExplorer = Objects.requireNonNull(reviewExplorer, "reviewExplorer");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(SFMPath path) {
        if (path.kind() != SFMPath.Kind.FILE || path.segments().isEmpty()) return false;
        return path.segments().get(path.segments().size() - 1)
                .toLowerCase(Locale.ROOT)
                .endsWith(".sfm-review.json");
    }

    @Override
    public CompletableFuture<Page> resolveChildren(
            SFMExplorerResolver.ChildRequest request,
            SFMExplorerEntry backingEntry
    ) {
        if (!supports(request.parent())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Path is not a release-review mount: " + request.parent().canonical()));
        }
        Path reviewPath = request.parent().toNativePath().toAbsolutePath().normalize();
        return ensureOpen(reviewPath).thenApply(review -> {
            request.cancellation().throwIfCancelled();
            var entries = reviewExplorer.mountedProjectionEntries(
                    reviewPath,
                    SFMReviewExplorerModel.PathLayout.HIERARCHY
            );
            ArrayList<String> diagnostics = new ArrayList<>();
            diagnostics.add("Mounted release review " + (review.writable() ? "writable" : "read-only")
                    + "; projections are explicit children and JSON remains independently openable as text");
            return new Page(entries, Optional.empty(), diagnostics, entries.size());
        });
    }

    private synchronized CompletableFuture<SFMReleaseReviewRuntime.Snapshot> ensureOpen(Path path) {
        SFMReleaseReviewRuntime.Snapshot current = reviewRuntime.snapshot();
        if (current.document().isPresent() && current.path().filter(path::equals).isPresent()) {
            return CompletableFuture.completedFuture(current);
        }
        if (opening != null && path.equals(openingPath)) return opening;
        CompletableFuture<SFMReleaseReviewRuntime.Snapshot> started = reviewRuntime.openAsync(path, false)
                .thenApply(result -> {
                    if (result.document().isEmpty()) {
                        throw new IllegalArgumentException("Release-review file did not contain a usable document: "
                                + String.join("; ", result.diagnostics()));
                    }
                    SFMReleaseReviewRuntime.Snapshot published = reviewRuntime.snapshot();
                    if (published.document().isEmpty() || published.path().filter(path::equals).isEmpty()) {
                        throw new IllegalStateException("A different release review replaced this mount while it loaded");
                    }
                    return published;
                });
        openingPath = path;
        opening = started;
        started.whenComplete((ignored, failure) -> clearOpening(path, started));
        return started;
    }

    private synchronized void clearOpening(
            Path path,
            CompletableFuture<SFMReleaseReviewRuntime.Snapshot> completed
    ) {
        if (opening == completed && Objects.equals(openingPath, path)) {
            opening = null;
            openingPath = null;
        }
    }
}
