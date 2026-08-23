package ca.teamdman.sfm.client.action;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMReleaseReviewCreateRuntimeTests {
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
                    "review", "session", "create",
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
