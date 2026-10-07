package ca.teamdman.sfm.client.history.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTemporalReplayArchiveJsonCodecTests {
    private static final String FIXTURE = "temporal-replay-archive-v1.json";

    @Test
    void canonicalFixtureRoundTripsEveryRecordKindByteForByte() throws Exception {
        String encoded = fixture();
        SFMTemporalReplayArchive.Archive archive = SFMTemporalReplayArchiveJsonCodec.read(encoded);

        assertAll(
                () -> assertTrue(encoded.endsWith("\n")),
                () -> assertFalse(encoded.endsWith("\n\n")),
                () -> assertFalse(encoded.contains("\r")),
                () -> assertEquals(encoded, SFMTemporalReplayArchiveJsonCodec.write(archive)),
                () -> assertEquals(archive,
                        SFMTemporalReplayArchiveJsonCodec.read(SFMTemporalReplayArchiveJsonCodec.write(archive))),
                () -> assertEquals(1, archive.bindingSnapshots().size()),
                () -> assertEquals(4, archive.sourceEvents().size()),
                () -> assertEquals(2, archive.bindingDecisions().size()),
                () -> assertEquals(2, archive.invocations().size()),
                () -> assertEquals(1, archive.selectionExpressions().size()),
                () -> assertEquals(1, archive.selectionWitnesses().size()),
                () -> assertEquals(3, archive.frames().size()),
                () -> assertEquals(2, archive.transitions().size()),
                () -> assertEquals(1, archive.observations().size()),
                () -> assertEquals(1, archive.headMovements().size()),
                () -> assertEquals(1, archive.replayReports().size())
        );
    }

    @Test
    void fixtureRetainsDistinctRawTracesThatYieldEqualSemanticActionCounts() throws Exception {
        SFMTemporalReplayArchive.Archive archive = SFMTemporalReplayArchiveJsonCodec.read(fixture());
        SFMTemporalReplayArchive.BindingDecision traceA = archive.bindingDecisions().get(0);
        SFMTemporalReplayArchive.BindingDecision traceB = archive.bindingDecisions().get(1);
        Map<String, SFMTemporalReplayArchive.ActionInvocation> invocations = new HashMap<>();
        archive.invocations().forEach(value -> invocations.put(value.id(), value));

        assertNotEquals(traceA.sourceEventIds(), traceB.sourceEventIds());
        assertEquals(3, traceA.sourceEventIds().size());
        assertEquals(1, traceB.sourceEventIds().size());
        assertEquals(1, traceA.invocationIds().size());
        assertEquals(1, traceB.invocationIds().size());
        assertEquals(
                invocations.get(traceA.invocationIds().get(0)).actionId(),
                invocations.get(traceB.invocationIds().get(0)).actionId()
        );
        assertEquals(
                archive.requireFrame("state:exact").text(),
                archive.requireFrame("state:replayed").text()
        );
    }

    @Test
    void missingUnknownAndMalformedFieldsFailClosed() throws Exception {
        JsonObject missingRequired = fixtureObject();
        missingRequired.remove("current_state_id");

        JsonObject unknownRoot = fixtureObject();
        unknownRoot.addProperty("future_field", true);

        JsonObject unknownNested = fixtureObject();
        object(array(unknownNested, "source_events").get(0)).addProperty("future_field", true);

        JsonObject missingOptional = fixtureObject();
        object(array(missingOptional, "source_events").get(0)).remove("payload");

        JsonObject malformedOptional = fixtureObject();
        object(array(malformedOptional, "source_events").get(0)).add("payload", new JsonObject());

        JsonObject malformedBoolean = fixtureObject();
        JsonObject bindingSnapshot = object(array(malformedBoolean, "binding_snapshots").get(0));
        object(array(bindingSnapshot, "bindings").get(0)).addProperty("enabled", "true");

        assertAll(
                () -> rejects(missingRequired),
                () -> rejects(unknownRoot),
                () -> rejects(unknownNested),
                () -> rejects(missingOptional),
                () -> rejects(malformedOptional),
                () -> rejects(malformedBoolean),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMTemporalReplayArchiveJsonCodec.read("{")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMTemporalReplayArchiveJsonCodec.read("null")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMTemporalReplayArchiveJsonCodec.read(fixture() + "{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMTemporalReplayArchiveJsonCodec.read("/* comment */" + fixture())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMTemporalReplayArchiveJsonCodec.read("{unquoted: true}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMTemporalReplayArchiveJsonCodec.read("{\"schema\":1,\"schema\":2}"))
        );
    }

    @Test
    void schemasEnumsIntegersAndOptionalScalarTypesAreStrict() throws Exception {
        JsonObject unknownSchema = fixtureObject();
        unknownSchema.addProperty("schema", "sfm.temporal-replay-archive/2");

        JsonObject unknownNestedSchema = fixtureObject();
        object(array(unknownNestedSchema, "binding_snapshots").get(0))
                .addProperty("schema", "sfm.effective-binding-snapshot/2");

        JsonObject nonCanonicalEnum = fixtureObject();
        object(array(nonCanonicalEnum, "source_events").get(0)).addProperty("kind", "KEY");

        JsonObject fractionalInteger = fixtureObject();
        fractionalInteger.addProperty("generation", 1.5);

        JsonObject overflowingInteger = fixtureObject();
        overflowingInteger.add("generation", JsonParser.parseString("9223372036854775808"));

        JsonObject malformedOptionalInteger = fixtureObject();
        object(array(malformedOptionalInteger, "source_events").get(0)).addProperty("key_code", "341");

        assertAll(
                () -> rejects(unknownSchema),
                () -> rejects(unknownNestedSchema),
                () -> rejects(nonCanonicalEnum),
                () -> rejects(fractionalInteger),
                () -> rejects(overflowingInteger),
                () -> rejects(malformedOptionalInteger)
        );
    }

    @Test
    void constructorValidationRejectsHashCorruptionAndDanglingReferences() throws Exception {
        JsonObject invalidBindingDigest = fixtureObject();
        object(array(invalidBindingDigest, "binding_snapshots").get(0))
                .addProperty("digest", "sha256:not-the-content-digest");

        JsonObject invalidFrameHash = fixtureObject();
        frame(invalidFrameHash, "state:root").addProperty("text_hash", "sha256:not-the-text-hash");

        JsonObject invalidWitnessHash = fixtureObject();
        object(array(invalidWitnessHash, "selection_witnesses").get(0))
                .addProperty("source_text_hash", "sha256:not-the-parent-hash");

        JsonObject danglingCurrentState = fixtureObject();
        danglingCurrentState.addProperty("current_state_id", "state:absent");

        JsonObject danglingInvocation = fixtureObject();
        object(array(danglingInvocation, "transitions").get(0))
                .addProperty("invocation_id", "invocation:absent");

        assertAll(
                () -> rejects(invalidBindingDigest),
                () -> rejects(invalidFrameHash),
                () -> rejects(invalidWitnessHash),
                () -> rejects(danglingCurrentState),
                () -> rejects(danglingInvocation)
        );
    }

    @Test
    void boundedCollectionsAndFramePayloadsFailBeforePublication() throws Exception {
        JsonObject tooManyRecords = fixtureObject();
        JsonArray oversized = new JsonArray();
        for (int index = 0; index <= SFMTemporalReplayArchive.MAX_RECORDS_PER_KIND; index++) {
            oversized.add(JsonNull.INSTANCE);
        }
        tooManyRecords.add("observations", oversized);

        JsonObject oversizedFrame = fixtureObject();
        frame(oversizedFrame, "state:exact").addProperty(
                "text",
                "x".repeat(SFMTemporalReplayArchive.MAX_FRAME_UTF8_BYTES + 1)
        );

        String tooDeep = "[".repeat(SFMTemporalReplayArchiveJsonCodec.MAX_JSON_NESTING_DEPTH + 2)
                + "null"
                + "]".repeat(SFMTemporalReplayArchiveJsonCodec.MAX_JSON_NESTING_DEPTH + 2);

        assertAll(
                () -> rejects(tooManyRecords),
                () -> rejects(oversizedFrame),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMTemporalReplayArchiveJsonCodec.read(tooDeep))
        );
    }

    private static void rejects(JsonObject value) {
        assertThrows(IllegalArgumentException.class,
                () -> SFMTemporalReplayArchiveJsonCodec.read(value.toString()));
    }

    private static JsonObject fixtureObject() throws IOException {
        return JsonParser.parseString(fixture()).getAsJsonObject();
    }

    private static JsonObject frame(JsonObject root, String stateId) {
        for (var element : array(root, "frames")) {
            JsonObject value = object(element);
            if (stateId.equals(value.get("state_id").getAsString())) return value;
        }
        throw new AssertionError("Fixture does not contain frame " + stateId);
    }

    private static JsonArray array(JsonObject owner, String key) {
        return owner.getAsJsonArray(key);
    }

    private static JsonObject object(com.google.gson.JsonElement value) {
        return value.getAsJsonObject();
    }

    private static String fixture() throws IOException {
        try (InputStream stream = Objects.requireNonNull(
                SFMTemporalReplayArchiveJsonCodecTests.class.getResourceAsStream(FIXTURE),
                FIXTURE
        )) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
