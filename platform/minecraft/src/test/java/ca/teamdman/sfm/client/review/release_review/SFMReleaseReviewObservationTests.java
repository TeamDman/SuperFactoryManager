package ca.teamdman.sfm.client.review.release_review;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SFMReleaseReviewObservationTests {
    @Test void historicalEvidenceIsReadableButCannotBecomeCurrentCompletionSurface() throws Exception {
        String source = "old evidence\r\n";
        String hash = SFMReleaseReviewKernel.sha256(source.getBytes(StandardCharsets.UTF_8));
        String laneId = "review-evidence:" + SFMReleaseReviewKernel.sha256("old-doc".getBytes(StandardCharsets.UTF_8));
        var json = JsonParser.parseString(fixture()).getAsJsonObject();
        json.addProperty("schema", SFMReleaseReviewV1.OBSERVATION_SCHEMA);
        String lane = """
                {"id":"%s","repository":{"id":"sfm:review-evidence","root_hint":"."},
                 "version_label":"Historical evidence",
                 "before":{"id":"evidence:empty","documents":[]},
                 "after":{"id":"evidence:sha256:%s","documents":[
                 {"id":"old-doc","path":"old.txt","encoding":"utf-8","sha256":"%s","text":"old evidence\\r\\n"}]}}
                """.formatted(laneId, hash, hash);
        json.getAsJsonObject("review_session").getAsJsonArray("revision_lanes").add(JsonParser.parseString(lane));
        String corpus = """
                {"id":"old-corpus","lane_id":"%s","snapshot_side":"after","path":"old.txt",
                "document_revision_id":"old-doc","sha256":"%s","source_owner":"sfm:review-evidence",
                "source_locator":"review-evidence://sha256/%s","materialization":"complete"}
                """.formatted(laneId, hash, hash);
        json.getAsJsonArray("corpus_documents").add(JsonParser.parseString(corpus));
        var observation = SFMReleaseReviewV1Codec.parse(json.toString());
        SFMReleaseReviewKernel.validate(observation);
        var previousCoverage = SFMReleaseReviewKernel.completion(observation).surfaceCoverage();
        var oldApproval = json.getAsJsonObject("review_session").getAsJsonArray("comments").get(1).deepCopy().getAsJsonObject();
        oldApproval.addProperty("id", "human:historical-approval");
        oldApproval.addProperty("text", "#approved This old source was reviewed");
        var range = oldApproval.getAsJsonObject("target").getAsJsonObject("selection_rule");
        range.addProperty("document_revision_id", "old-doc");
        range.addProperty("start_byte", 0);
        range.addProperty("end_byte", source.getBytes(StandardCharsets.UTF_8).length);
        range.addProperty("document_sha256", hash);
        range.addProperty("selected_text_sha256", hash);
        json.getAsJsonObject("review_session").getAsJsonArray("comments").add(oldApproval);
        observation = SFMReleaseReviewV1Codec.parse(json.toString());
        var attribution = SFMReleaseReviewKernel.approvalEvidence(observation).stream()
                .filter(value -> value.commentId().equals("human:historical-approval")).findFirst().orElseThrow();
        assertTrue(attribution.explanation().startsWith("Retained historical approval"));
        assertEquals(1, attribution.historicalRanges().size());
        assertTrue(attribution.effectiveCurrentRanges().isEmpty());
        assertEquals(previousCoverage, SFMReleaseReviewKernel.completion(observation).surfaceCoverage());
        assertEquals(observation, SFMReleaseReviewV1Codec.parse(SFMReleaseReviewV1Codec.write(observation)));
        json.addProperty("schema", SFMReleaseReviewV1.SCHEMA);
        assertThrows(IllegalArgumentException.class,
                () -> SFMReleaseReviewKernel.validate(SFMReleaseReviewV1Codec.parse(json.toString())));
        json.addProperty("schema", SFMReleaseReviewV1.OBSERVATION_SCHEMA);
        json.getAsJsonArray("review_units").get(0).getAsJsonObject().addProperty("after_document_revision_id", "old-doc");
        assertThrows(IllegalArgumentException.class,
                () -> SFMReleaseReviewKernel.validate(SFMReleaseReviewV1Codec.parse(json.toString())));
    }

    private static String fixture() throws Exception {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path file = root.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(file)) return Files.readString(file);
        }
        throw new IllegalStateException("Review fixture not found");
    }
}
