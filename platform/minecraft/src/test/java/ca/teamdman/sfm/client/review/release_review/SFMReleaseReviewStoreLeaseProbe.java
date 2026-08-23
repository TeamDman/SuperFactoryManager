package ca.teamdman.sfm.client.review.release_review;

import java.nio.file.Path;

/** Child-process probe used to prove that writable review leases cross JVM boundaries. */
public final class SFMReleaseReviewStoreLeaseProbe {
    private SFMReleaseReviewStoreLeaseProbe() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected <review-path>");
        }
        try (SFMReleaseReviewStore ignored = SFMReleaseReviewStore.open(
                Path.of(arguments[0]), SFMReleaseReviewStore.Access.WRITABLE)) {
            System.out.println("LEASE_READY");
            System.out.flush();
            System.in.read();
        }
    }
}
