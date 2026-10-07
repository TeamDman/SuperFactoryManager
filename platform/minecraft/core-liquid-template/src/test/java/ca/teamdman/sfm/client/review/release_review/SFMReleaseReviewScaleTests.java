package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.lazy.*;
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
import ca.teamdman.sfm.client.search.SFMTextMatchOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Optional read-only acceptance against a CLI-generated disposable release-scale review. */
class SFMReleaseReviewScaleTests {
    @Test void completeSearchOnCapturedReleaseReview() throws Exception {
        String configured = System.getenv("SFM_TEST_REVIEW_SCALE_FIXTURE");
        assumeTrue(configured != null && !configured.isBlank(), "Set SFM_TEST_REVIEW_SCALE_FIXTURE to a disposable captured review");
        Path file = Path.of(configured);
        byte[] before = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        try (var runtime = new SFMReleaseReviewRuntime()) {
            runtime.open(file, false);
            var resolver = new SFMReleaseReviewExplorerRuntime(runtime);
            var root = resolver.prepareLens(file, SFMReleaseReviewExplorerScreenType.Projection.CHANGES, Optional.empty());
            var page = resolver.resolveChildren(new SFMExplorerResolver.ChildRequest(root, Optional.empty(), 128,
                    resolver.generation(), new SFMExplorerCancellationToken())).get(60, TimeUnit.SECONDS);
            assertFalse(page.entries().isEmpty());
            var domain = resolver.resolveFilterDomain(new SFMExplorerFilterDomainResolver.FilterDomainRequest(
                    root, "SFMExplorerPanel.java", 4096, resolver.generation(),
                    new SFMExplorerCancellationToken(), SFMTextMatchOptions.defaults())).get(60, TimeUnit.SECONDS);
            assertTrue(domain.complete(), () -> domain.diagnostics().toString());
            assertEquals(1, domain.totalMatchCount(), "The pinned release capture includes this committed Java file");
            var untracked = resolver.resolveFilterDomain(new SFMExplorerFilterDomainResolver.FilterDomainRequest(
                    root, "ExploreReviewInteractivelyPuppetAction.java", 4096, resolver.generation(),
                    new SFMExplorerCancellationToken(), SFMTextMatchOptions.defaults())).get(60, TimeUnit.SECONDS);
            assertTrue(untracked.complete());
            assertEquals(0, untracked.totalMatchCount(), "This fixed commit pair excludes the subsequently untracked puppet");
            assertFalse(runtime.snapshot().dirty());
        }
        assertArrayEquals(before, java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
