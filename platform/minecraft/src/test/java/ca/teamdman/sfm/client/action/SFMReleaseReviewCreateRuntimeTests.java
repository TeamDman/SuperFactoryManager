package ca.teamdman.sfm.client.action;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMReleaseReviewCreateRuntimeTests {
    @Test
    void recapturePreservesAllScopesExclusionsAndUntrackedPolicy(@TempDir Path repository) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !java.nio.file.Files.isRegularFile(root.resolve("docs/architecture/fixtures/release-review-working-tree-v2.json")))
            root = root.getParent();
        var review = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1Codec.parse(
                java.nio.file.Files.readString(root.resolve("docs/architecture/fixtures/release-review-working-tree-v2.json")));
        var binding = review.repositoryBindings().get(0);
        var policy = binding.workingTreeCapture().orElseThrow();
        var request = new SFMReleaseReviewCreateRuntime.Request(Path.of("new.sfm-review.json"), "1.19.2",
                binding.beforeCommit(), null, Optional.of(repository), Optional.of("."),
                Optional.of(policy), List.of("old.sfm-review.json"));
        var command = SFMReleaseReviewCreateRuntime.command(request, repository);
        var scopes = new java.util.ArrayList<String>();
        var exclusions = new java.util.ArrayList<String>();
        for (int i = 0; i + 1 < command.size(); i++) {
            if (command.get(i).equals("--scope")) scopes.add(command.get(i + 1));
            if (command.get(i).equals("--exclude")) exclusions.add(command.get(i + 1));
        }
        assertEquals(policy.scopePaths(), scopes);
        org.junit.jupiter.api.Assertions.assertTrue(exclusions.containsAll(policy.excludedPaths()));
        org.junit.jupiter.api.Assertions.assertTrue(exclusions.contains("old.sfm-review.json"));
        assertEquals(!policy.includeUntracked(), command.contains("--tracked-only"));
    }

    @Test
    void capturedSourceIsExplicitAndNeverInventsAGitCandidate(@TempDir Path repository) {
        var request = new SFMReleaseReviewCreateRuntime.Request(Path.of("new.sfm-review.json"), "1.19.2",
                "HEAD", null, Optional.of(repository), Optional.of("platform/minecraft/src"));
        var command = SFMReleaseReviewCreateRuntime.command(request, repository);
        org.junit.jupiter.api.Assertions.assertTrue(command.contains("--working-tree"));
        org.junit.jupiter.api.Assertions.assertFalse(command.contains("--candidate"));
        assertEquals("platform/minecraft/src", command.get(command.indexOf("--scope") + 1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                new SFMReleaseReviewCreateRuntime.Request(Path.of("new.sfm-review.json"), "1.19.2", "HEAD", "HEAD",
                        Optional.of(repository), Optional.of("src")));
    }
    @Test
    void completeGitProducerCommandUsesExplicitPortableInputs(@TempDir Path repository) {
        String previous = System.getProperty(SFMReleaseReviewCreateRuntime.EXECUTABLE_PROPERTY);
        System.setProperty(SFMReleaseReviewCreateRuntime.EXECUTABLE_PROPERTY, "toolchain-test.exe");
        try {
            var request = new SFMReleaseReviewCreateRuntime.Request(
                    Path.of("docs/reviews/4.34.0-to-candidate.sfm-review.json"),
                    "1.19.2",
                    "4.34.0-1.19.2",
                    "HEAD",
                    Optional.empty()
            );
            assertEquals(List.of(
                    "toolchain-test.exe",
                    "--output-format", "json",
                    "review", "session", "create-ledger",
                    "--file", "docs\\reviews\\4.34.0-to-candidate.sfm-review.json",
                    "--branch", "1.19.2",
                    "--before", "4.34.0-1.19.2",
                    "--candidate", "HEAD",
                    "--repository-root", repository.toAbsolutePath().normalize().toString()
            ), SFMReleaseReviewCreateRuntime.command(request, repository));
        } finally {
            if (previous == null) System.clearProperty(SFMReleaseReviewCreateRuntime.EXECUTABLE_PROPERTY);
            else System.setProperty(SFMReleaseReviewCreateRuntime.EXECUTABLE_PROPERTY, previous);
        }
    }
}
