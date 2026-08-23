package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewAnalysisIdentityResolverTests {
    @Test
    void exactCandidateBytesBorrowAFileIdentityWithoutReplacingReviewIdentity(@TempDir Path root)
            throws Exception {
        SFMReleaseReviewV1 review = fixture();
        Files.createDirectories(root.resolve(".git"));
        Path reviewPath = root.resolve("docs/reviews/release.sfm-review.json");
        Files.createDirectories(reviewPath.getParent());
        SFMReleaseReviewV1.CorpusDocument after = review.corpusDocuments().stream()
                .filter(value -> value.snapshotSide() == SFMReleaseReviewV1.SnapshotSide.AFTER)
                .filter(value -> value.path().equals("src/Cafe.java"))
                .findFirst().orElseThrow();
        String text = review.reviewSession().revisionLanes().stream()
                .flatMap(lane -> lane.after().documents().stream())
                .filter(value -> value.id().equals(after.documentRevisionId()))
                .findFirst().orElseThrow().text();
        Path candidate = root.resolve(after.path());
        Files.createDirectories(candidate.getParent());
        Files.writeString(candidate, text, StandardCharsets.UTF_8);

        var identity = SFMReleaseReviewAnalysisIdentityResolver.resolve(
                review, reviewPath, after.documentRevisionId()).orElseThrow();
        assertEquals(candidate.toAbsolutePath().normalize(), identity.path().toNativePath());
        assertEquals(root.toAbsolutePath().normalize(), identity.authorizedRoot().toNativePath());

        SFMPath durableRoot = new SFMPath(
                SFMPath.Kind.CONTRIBUTED, "review", "document",
                List.of("release", after.documentRevisionId()), Optional.empty(), true);
        SFMPath durablePath = new SFMPath(
                SFMPath.Kind.CONTRIBUTED, "review", "document",
                List.of("release", after.documentRevisionId(), "src", "Cafe.java"), Optional.empty(), false);
        SFMTextDocumentSnapshot durable = new SFMTextDocumentSource.PinnedSnapshot(
                durablePath,
                durableRoot,
                text,
                after.sha256(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(identity)
        ).load(new SFMExplorerCancellationToken()).join();
        assertEquals(durablePath, durable.path().orElseThrow(),
                "the editor and durable comment target must retain the review:// identity");
        SFMTextDocumentSnapshot semantic = durable.semanticAnalysisSnapshot().orElseThrow();
        assertEquals(identity.path(), semantic.path().orElseThrow(),
                "only the language-worker projection may borrow the candidate file identity");
        assertEquals(identity.authorizedRoot(), semantic.authorizedRoot().orElseThrow());
        assertEquals(durable.text(), semantic.text());
        assertEquals(durable.sha256(), semantic.sha256());
        assertTrue(semantic.analysisIdentity().isEmpty(),
                "the worker projection must not recursively retain an alternate identity");

        SFMReleaseReviewV1.CorpusDocument before = review.corpusDocuments().stream()
                .filter(value -> value.snapshotSide() == SFMReleaseReviewV1.SnapshotSide.BEFORE)
                .findFirst().orElseThrow();
        assertTrue(SFMReleaseReviewAnalysisIdentityResolver.resolve(
                review, reviewPath, before.documentRevisionId()).isEmpty(),
                "historical bytes must not borrow the candidate checkout identity");

        Files.writeString(candidate, text + "// stale\n", StandardCharsets.UTF_8);
        assertTrue(SFMReleaseReviewAnalysisIdentityResolver.resolve(
                review, reviewPath, after.documentRevisionId()).isEmpty(),
                "a checkout whose bytes differ from the pinned corpus must fail closed");
    }

    private static SFMReleaseReviewV1 fixture() throws Exception {
        return SFMReleaseReviewV1Codec.parse(Files.readString(
                fixturePath(), StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    private static Path fixturePath() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate canonical release-review fixture");
    }
}
