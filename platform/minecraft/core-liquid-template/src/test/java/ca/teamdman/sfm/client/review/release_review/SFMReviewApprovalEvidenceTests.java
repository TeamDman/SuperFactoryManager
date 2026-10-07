package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewApprovalEvidenceTests {
    @Test void attributionMatchesCanonicalCoverageWithoutChangingTheReview() throws Exception {
        String original = fixture();
        var review = SFMReleaseReviewV1Codec.parse(original);
        String before = SFMReleaseReviewV1Codec.write(review);
        var explanation = SFMReleaseReviewKernel.approvalEvidence(review);
        assertFalse(explanation.isEmpty());
        var explained = SFMReviewSessionV1Kernel.normalize(explanation.stream()
                .flatMap(value -> value.effectiveCurrentRanges().stream()).toList());
        var canonical = SFMReviewSessionV1Kernel.normalize(SFMReleaseReviewKernel.completion(review).surfaceCoverage().stream()
                .flatMap(value -> value.approved().stream()).toList());
        assertEquals(canonical, explained);
        assertEquals(before, SFMReleaseReviewV1Codec.write(review));
    }

    @Test void archivedApprovalIsVisibleButContributesNoCoverage() throws Exception {
        var json = JsonParser.parseString(fixture()).getAsJsonObject();
        for (var value : json.getAsJsonObject("review_session").getAsJsonArray("comments")) {
            var comment = value.getAsJsonObject();
            if (comment.get("id").getAsString().equals("human:approved-value"))
                comment.addProperty("text", "#approved #archived Keep the original evidence");
        }
        var review = SFMReleaseReviewV1Codec.parse(json.toString());
        var explanation = SFMReleaseReviewKernel.approvalEvidence(review).stream()
                .filter(value -> value.commentId().equals("human:approved-value")).findFirst().orElseThrow();
        assertTrue(explanation.explanation().startsWith("Archived"));
        assertTrue(explanation.effectiveCurrentRanges().isEmpty());
        assertFalse(explanation.targetRanges().isEmpty());
    }

    @Test void targetLabelIsReadableWhileExactSourceIdentityIsPreserved() throws Exception {
        var review = SFMReleaseReviewV1Codec.parse(fixture());
        var model = ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel.releaseStatus(review);
        var category = model.root().children().stream().filter(node -> node.id().equals("release/approval-evidence"))
                .findFirst().orElseThrow();
        var target = category.children().stream().flatMap(node -> node.children().stream())
                .filter(node -> node.label().startsWith("Target source · ")).findFirst().orElseThrow();
        assertEquals("Target source · Cafe.java [35..60)", target.label());
        assertEquals("1.19.2:after:src/Cafe.java", target.leaf().documentRevisionId().orElseThrow());
        assertEquals("src/Cafe.java", target.leaf().path());
        assertEquals(35, target.leaf().targetRange().orElseThrow().startByte());
        assertTrue(target.leaf().sha256().isPresent());
    }

    @Test void invalidOriginalWitnessIsExplainedWithoutClampingOrCrashingStatus() throws Exception {
        var json = JsonParser.parseString(fixture()).getAsJsonObject();
        for (var value : json.getAsJsonObject("review_session").getAsJsonArray("comments")) {
            var comment = value.getAsJsonObject();
            if (comment.get("id").getAsString().equals("human:approved-value"))
                comment.getAsJsonObject("target").getAsJsonObject("selection_rule").addProperty("end_byte", 99999);
        }
        var review = SFMReleaseReviewV1Codec.parse(json.toString());
        String original = SFMReleaseReviewV1Codec.write(review);
        var model = ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel.releaseStatus(review);
        var category = model.root().children().stream().filter(node -> node.id().equals("release/approval-evidence"))
                .findFirst().orElseThrow();
        assertTrue(category.children().stream().flatMap(node -> node.children().stream())
                .anyMatch(node -> node.leaf() != null && node.leaf().path().equals("approval-target-diagnostic.txt")
                        && node.leaf().text().contains("99999") && node.leaf().targetRange().isEmpty()));
        assertEquals(original, SFMReleaseReviewV1Codec.write(review));
    }

    private static String fixture() throws Exception {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path file = root.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(file)) return Files.readString(file);
        }
        throw new IllegalStateException("Review fixture not found");
    }
}
