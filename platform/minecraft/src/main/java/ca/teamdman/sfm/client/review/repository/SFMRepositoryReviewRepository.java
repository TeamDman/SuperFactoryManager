package ca.teamdman.sfm.client.review.repository;

import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentKernelDataSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Narrow managed-inbox boundary consumed by repository review presentation. */
public interface SFMRepositoryReviewRepository {
    record BundleSummary(String id, String name, String repositoryName, String beforeLabel,
                         String afterLabel, int changedFileCount) {}
    record ChangedFile(SFMRepositoryReviewBundleV1.ChangeKind kind, @Nullable String beforePath,
                       @Nullable String afterPath, boolean binary, List<String> diagnostics) {
        public ChangedFile { diagnostics = List.copyOf(diagnostics); }
    }
    record OpenBundle(BundleSummary summary, String sessionId, SFMRepositoryReviewBundleV1 bundle,
                      List<ChangedFile> changedFiles, SFMReviewCommentKernelDataSource dataSource,
                      boolean restored, List<String> diagnostics) {
        public OpenBundle { changedFiles = List.copyOf(changedFiles); diagnostics = List.copyOf(diagnostics); }
    }

    List<BundleSummary> listBundles();
    OpenBundle open(String bundleIdOrName);
}
