package ca.teamdman.sfm.client.review.release_review;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class SFMWorkingTreeReviewV2Tests {
    private static String golden() throws Exception {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            var path = root.resolve("docs/architecture/fixtures/release-review-working-tree-v2.json");
            if (Files.isRegularFile(path)) return Files.readString(path).replace("\r\n", "\n");
        }
        throw new IllegalStateException("Working-tree review fixture not found");
    }

    @Test
    void portableMixedSourceEnvelopeKeepsExactCommentsAndCanonicalBytes() throws Exception {
        var json = golden();
        var review = SFMReleaseReviewV1Codec.parse(json);
        SFMReleaseReviewKernel.validate(review);
        assertEquals(json, SFMReleaseReviewV1Codec.write(review));
        assertEquals("c3f1c531c093d2b37246723d38772a5841f7f95ecc77880351a55dee106333bd",
                SFMReleaseReviewKernel.semanticStateHash(review));
        var capture = review.repositoryBindings().get(0).workingTreeCapture().orElseThrow();
        assertEquals(2, capture.entries().stream().filter(entry -> !entry.tracked()).count());
        assertNull(review.repositoryBindings().get(0).candidateCommit());
        assertTrue(review.repositoryBindings().get(1).workingTreeCapture().isEmpty());
        assertEquals(SFMReleaseReviewKernel.query(review, "effective(#approved) intersect 1.19.2 HEAD").reviewUnitIds(),
                SFMReleaseReviewKernel.query(review, "effective(#approved) intersect 1.19.2 " + capture.id()).reviewUnitIds());
        assertEquals(SFMReleaseReviewKernel.query(review, "HEAD").reviewUnitIds(),
                SFMReleaseReviewKernel.query(review, "candidate").reviewUnitIds());
    }

    private static void rejects(Consumer<JsonObject> mutation) throws Exception {
        var json = JsonParser.parseString(golden()).getAsJsonObject();
        mutation.accept(json);
        assertThrows(RuntimeException.class, () -> SFMReleaseReviewKernel.validate(SFMReleaseReviewV1Codec.parse(json.toString())));
    }

    private static JsonObject binding(JsonObject json) { return json.getAsJsonArray("repository_bindings").get(0).getAsJsonObject(); }
    private static JsonObject after(JsonObject json) { return json.getAsJsonObject("review_session")
            .getAsJsonArray("revision_lanes").get(0).getAsJsonObject().getAsJsonObject("after"); }

    @Test
    void sourceKindsIdentityAndPreviewBytesFailClosed() throws Exception {
        rejects(json -> json.addProperty("schema", "sfm.release-review/1"));
        rejects(json -> binding(json).addProperty("candidate_commit", "a".repeat(40)));
        rejects(json -> binding(json).remove("working_tree_capture"));
        rejects(json -> after(json).addProperty("id", "different-capture"));
        rejects(json -> after(json).getAsJsonArray("documents").remove(0));
        rejects(json -> after(json).getAsJsonArray("documents").get(0).getAsJsonObject().addProperty("text", "tampered"));
        rejects(json -> binding(json).getAsJsonObject("working_tree_capture").addProperty("include_untracked", "true"));
        rejects(json -> {
            var query = new JsonObject(); query.addProperty("id", "candidate"); query.addProperty("expression", "HEAD");
            json.getAsJsonArray("named_queries").add(query);
        });
    }

    @Test
    void capturedCommentBeyondEofIsRetainedButEvaluatesInvalidRatherThanClamped() throws Exception {
        var json = JsonParser.parseString(golden()).getAsJsonObject();
        json.getAsJsonObject("review_session").getAsJsonArray("comments")
                .get(0).getAsJsonObject().getAsJsonObject("target").getAsJsonObject("selection_rule")
                .addProperty("end_byte", 1_000_000);
        var review = SFMReleaseReviewV1Codec.parse(json.toString());
        SFMReleaseReviewKernel.validate(review);
        var roundTrip = SFMReleaseReviewV1Codec.parse(SFMReleaseReviewV1Codec.write(review));
        var target = (ca.teamdman.sfm.client.review.session.SFMReviewSessionV2.CommittedReviewTarget)
                roundTrip.reviewSession().comments().get(0).target();
        var index = SFMReleaseReviewEvaluator.Index.build(roundTrip,
                SFMReleaseReviewEvaluator.PreparedEvidence.empty(), SFMReleaseReviewEvaluator.Limits.defaults());
        var result = index.evaluate("invalid-captured-target", target.selectionRule(),
                SFMReleaseReviewEvaluator.Scope.all()).result();
        assertEquals(SFMReleaseReviewV1.EvaluationStatus.INVALID, result.status());
        assertTrue(result.ranges().isEmpty(), "invalid targets must never acquire clamped approval coverage");
        assertFalse(result.diagnostics().isEmpty(), "preserve an inspectable explanation");
    }
}
