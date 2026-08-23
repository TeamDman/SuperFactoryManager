package ca.teamdman.sfm.client.review.release_review;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewSurfaceJsonCodecTests {
    @Test
    void strictRequestAndSurfaceRoundTripUnicodeAndCrLfByteRanges() {
        SFMReleaseReviewSurfaceV1.Request request = request(41, 9);
        SFMReleaseReviewSurfaceV1.Surface surface = afterSurface(request);

        String encodedRequest = SFMReleaseReviewSurfaceJsonCodec.encodeRequest(request);
        String encodedSurface = SFMReleaseReviewSurfaceJsonCodec.encodeSurface(surface);

        assertEquals(request, SFMReleaseReviewSurfaceJsonCodec.decodeRequest(encodedRequest));
        assertEquals(surface, SFMReleaseReviewSurfaceJsonCodec.decodeSurface(encodedSurface));
        surface.validateAgainst(request);
        assertTrue(encodedRequest.contains("Café"));
        assertTrue(encodedRequest.contains("\\r\\n"));
    }

    @Test
    void unknownMissingAndNonIntegralFieldsFailClosed() {
        SFMReleaseReviewSurfaceV1.Request request = request(4, 2);
        var unknown = JsonParser.parseString(SFMReleaseReviewSurfaceJsonCodec.encodeRequest(request)).getAsJsonObject();
        unknown.addProperty("future_field", true);
        assertThrows(IllegalArgumentException.class,
                () -> SFMReleaseReviewSurfaceJsonCodec.decodeRequest(unknown.toString()));

        var missing = JsonParser.parseString(SFMReleaseReviewSurfaceJsonCodec.encodeSurface(afterSurface(request)))
                .getAsJsonObject();
        missing.remove("text_sha256");
        assertThrows(IllegalArgumentException.class,
                () -> SFMReleaseReviewSurfaceJsonCodec.decodeSurface(missing.toString()));

        var fractional = JsonParser.parseString(SFMReleaseReviewSurfaceJsonCodec.encodeRequest(request))
                .getAsJsonObject();
        fractional.addProperty("request_id", 1.5);
        assertThrows(IllegalArgumentException.class,
                () -> SFMReleaseReviewSurfaceJsonCodec.decodeRequest(fractional.toString()));
    }

    @Test
    void staleIdentityHashAndMidCodePointMappingsAreRejected() {
        SFMReleaseReviewSurfaceV1.Request request = request(17, 3);
        SFMReleaseReviewSurfaceV1.Surface valid = afterSurface(request);
        SFMReleaseReviewSurfaceV1.Surface stale = copy(valid, valid.requestId() + 1, valid.textSha256(),
                valid.mappings());
        assertThrows(IllegalArgumentException.class, () -> stale.validateAgainst(request));

        SFMReleaseReviewSurfaceV1.Surface staleHash = copy(valid, valid.requestId(),
                SFMReleaseReviewSurfaceV1.sha256("different"), valid.mappings());
        assertThrows(IllegalArgumentException.class, () -> staleHash.validateAgainst(request));

        SFMReleaseReviewSurfaceV1.Source after = request.filePair().after().orElseThrow();
        int accent = after.text().indexOf('é');
        int accentByte = after.text().substring(0, accent).getBytes(StandardCharsets.UTF_8).length;
        var invalidMapping = new SFMReleaseReviewSurfaceV1.Mapping(
                new SFMReleaseReviewSurfaceV1.Utf8Range(accentByte + 1, accentByte + 2),
                SFMReleaseReviewSurfaceV1.MappingKind.ADDITION,
                List.of(new SFMReleaseReviewSurfaceV1.SourceRange(
                        SFMReleaseReviewV1.SnapshotSide.AFTER,
                        after.documentRevisionId(), after.sha256(), after.path(),
                        new SFMReleaseReviewSurfaceV1.Utf8Range(accentByte + 1, accentByte + 2))));
        SFMReleaseReviewSurfaceV1.Surface malformed = copy(
                valid, valid.requestId(), valid.textSha256(), List.of(invalidMapping));
        assertThrows(IllegalArgumentException.class, () -> malformed.validateAgainst(request));
    }

    static SFMReleaseReviewSurfaceV1.Request request(long requestId, long generation) {
        String beforeText = "class Café {\r\n    int value = 1;\r\n}\r\n";
        String afterText = "class Café {\r\n    int value = 2;\r\n}\r\n";
        SFMReleaseReviewSurfaceV1.FilePair pair = new SFMReleaseReviewSurfaceV1.FilePair(
                "pair-cafe",
                "1.19.2",
                SFMReleaseReviewV1.ChangeOperation.MODIFIED,
                List.of("unit-cafe"),
                Optional.of(SFMReleaseReviewSurfaceV1.Source.fromCorpus(
                        "before-cafe", "src/Cafe.java", "java", beforeText)),
                Optional.of(SFMReleaseReviewSurfaceV1.Source.fromCorpus(
                        "after-cafe", "src/Cafe.java", "java", afterText))
        );
        return new SFMReleaseReviewSurfaceV1.Recipe(
                pair, SFMReleaseReviewSurfaceV1.SurfaceKind.TEXT_DIFF).request(requestId, generation);
    }

    static SFMReleaseReviewSurfaceV1.Surface afterSurface(SFMReleaseReviewSurfaceV1.Request request) {
        SFMReleaseReviewSurfaceV1.Source after = request.filePair().after().orElseGet(() ->
                request.filePair().before().orElseThrow());
        int bytes = after.text().getBytes(StandardCharsets.UTF_8).length;
        SFMReleaseReviewSurfaceV1.Utf8Range range = new SFMReleaseReviewSurfaceV1.Utf8Range(0, bytes);
        return new SFMReleaseReviewSurfaceV1.Surface(
                SFMReleaseReviewSurfaceV1.SURFACE_SCHEMA,
                request.requestId(),
                request.requestGeneration(),
                request.filePair().id(),
                request.surfaceKind(),
                "test-source-map/1",
                SFMReleaseReviewSurfaceV1.Outcome.PRODUCED,
                true,
                Optional.empty(),
                after.text(),
                SFMReleaseReviewSurfaceV1.sha256(after.text()),
                List.of(new SFMReleaseReviewSurfaceV1.Mapping(
                        range,
                        after == request.filePair().after().orElse(null)
                                ? SFMReleaseReviewSurfaceV1.MappingKind.ADDITION
                                : SFMReleaseReviewSurfaceV1.MappingKind.DELETION,
                        List.of(new SFMReleaseReviewSurfaceV1.SourceRange(
                                after == request.filePair().after().orElse(null)
                                        ? SFMReleaseReviewV1.SnapshotSide.AFTER
                                        : SFMReleaseReviewV1.SnapshotSide.BEFORE,
                                after.documentRevisionId(), after.sha256(), after.path(), range)))),
                List.of(new SFMReleaseReviewSurfaceV1.Region(
                        "region-all", SFMReleaseReviewSurfaceV1.RegionKind.TEXT_HUNK,
                        "complete fixture", range, List.of())),
                new SFMReleaseReviewSurfaceV1.CorrespondenceReport(
                        SFMReleaseReviewSurfaceV1.CORRESPONDENCE_SCHEMA,
                        request.filePair().id(), true, List.of(), List.of()),
                List.of()
        );
    }

    private static SFMReleaseReviewSurfaceV1.Surface copy(
            SFMReleaseReviewSurfaceV1.Surface value,
            long requestId,
            String hash,
            List<SFMReleaseReviewSurfaceV1.Mapping> mappings
    ) {
        return new SFMReleaseReviewSurfaceV1.Surface(
                value.schema(), requestId, value.requestGeneration(), value.filePairId(), value.surfaceKind(),
                value.algorithm(), value.outcome(), value.complete(), value.fallbackKind(), value.text(), hash,
                mappings, value.regions(), value.correspondence(), value.diagnostics());
    }
}
