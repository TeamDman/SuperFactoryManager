package ca.teamdman.sfm.client.review.release_review;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SFMReleaseReviewLedgerV3Tests {
    @Test void nestedWritersKeepCanonicalBytesAndReturnIndependentTrees() {
        var state = ledger().state();
        var json = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        var tree = SFMReleaseReviewV1Codec.writeTree(state);
        var canonical = SFMReleaseReviewV1Codec.write(state);
        // Reconstruct the old string/parse nesting boundary independently.
        tree.add("review_session", JsonParser.parseString(
                ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Codec.write(state.reviewSession())));
        assertEquals(canonical, json.toJson(tree) + "\n");
        var outer = JsonParser.parseString(SFMReleaseReviewLedgerV3Codec.write(ledger())).getAsJsonObject();
        outer.add("state", JsonParser.parseString(canonical));
        assertEquals(SFMReleaseReviewLedgerV3Codec.write(ledger()), json.toJson(outer) + "\n");
        tree.getAsJsonObject("review_session").addProperty("title", "mutated caller tree");
        assertEquals(canonical, SFMReleaseReviewV1Codec.write(state));
        assertEquals(state, SFMReleaseReviewV1Codec.parse(canonical));
    }

    @Test void emptyLiveReviewIsSmallAndRoundTripsWithoutSourceResolution() {
        var ledger = ledger();
        String json = SFMReleaseReviewLedgerV3Codec.write(ledger);
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length < 2500, json);
        assertEquals(ledger, SFMReleaseReviewLedgerV3Codec.parse(json));
        assertEquals(json, SFMReleaseReviewLedgerV3Codec.write(SFMReleaseReviewLedgerV3Codec.parse(json)));
        assertTrue(ledger.evidence().contents().isEmpty());
        assertTrue(ledger.state().reviewSession().comments().isEmpty());
        assertTrue(ledger.targets().get(0).live());
    }

    @Test void exactEmbeddedContentSurvivesSingleFileRoundTrip() {
        var ledger = ledger();
        String source = "line 😀\r\n";
        String hash = SFMReleaseReviewKernel.sha256(source.getBytes(StandardCharsets.UTF_8));
        var evidence = new SFMReviewEvidenceTable(
                List.of(new SFMReviewEvidenceTable.Content(hash, source)),
                List.of(new SFMReviewEvidenceTable.Document("revision", "src/A.java", hash, Optional.empty())));
        var captured = new SFMReleaseReviewLedgerV3(ledger.targets(), ledger.state(), evidence);
        assertEquals(captured, SFMReleaseReviewLedgerV3Codec.parse(SFMReleaseReviewLedgerV3Codec.write(captured)));
    }

    @Test void targetKindUnknownFieldsAndMutableGitAliasesAreRejected() {
        var json = JsonParser.parseString(SFMReleaseReviewLedgerV3Codec.write(ledger())).getAsJsonObject();
        json.addProperty("ignored_data", true);
        assertThrows(IllegalArgumentException.class, () -> SFMReleaseReviewLedgerV3Codec.parse(json.toString()));
        json.remove("ignored_data");
        var target = json.getAsJsonArray("targets").get(0).getAsJsonObject();
        target.addProperty("after_kind", "git");
        assertThrows(IllegalArgumentException.class, () -> SFMReleaseReviewLedgerV3Codec.parse(json.toString()));
        target.addProperty("after_commit", "b".repeat(40));
        assertFalse(SFMReleaseReviewLedgerV3Codec.parse(json.toString()).targets().get(0).live());
        target.addProperty("after_commit", "HEAD");
        assertThrows(IllegalArgumentException.class, () -> SFMReleaseReviewLedgerV3Codec.parse(json.toString()));
    }

    @Test void repositoryRelativeScopeCannotEscapeItsRoot() {
        for (String path : List.of("../outside", "/absolute", "C:/absolute", "src/../other", "src\\file", "src//file"))
            assertThrows(IllegalArgumentException.class, () -> new SFMReleaseReviewLedgerV3.TargetLane(
                    "lane", "sfm", ".", "a".repeat(40), Optional.empty(), List.of(path), List.of(), true));
    }

    @Test void transientObservationIsNotADurableStateEnvelope() {
        var json = JsonParser.parseString(SFMReleaseReviewLedgerV3Codec.write(ledger())).getAsJsonObject();
        for (String schema : List.of(SFMReleaseReviewV1.OBSERVATION_SCHEMA, SFMReleaseReviewV1.WORKING_TREE_SCHEMA)) {
            json.getAsJsonObject("state").addProperty("schema", schema);
            assertThrows(IllegalArgumentException.class, () -> SFMReleaseReviewLedgerV3Codec.parse(json.toString()));
        }
    }

    private static SFMReleaseReviewLedgerV3 ledger() {
        return SFMReleaseReviewLedgerV3.create("review", "Small live review", List.of(
                new SFMReleaseReviewLedgerV3.TargetLane("1.19.2", "sfm", ".", "a".repeat(40),
                        Optional.empty(), List.of("."), List.of("review.sfm-review.json"), true)));
    }
}
