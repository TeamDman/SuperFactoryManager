package ca.teamdman.sfm.client.history.replay;

import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.Archive;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.Frame;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTemporalReplayRuntimeTests {
    private static final String FIXTURE = "temporal-replay-archive-v1.json";

    @Test
    void restoresEveryRecordAndRandomSeeksFramesAndCausalRecords() throws Exception {
        Archive source = SFMTemporalReplayArchiveJsonCodec.read(fixture());
        SFMTemporalReplayRuntime runtime = SFMTemporalReplayRuntime.restore(source);

        assertNotSame(source, runtime.archive(), "restoration must construct a fresh archive boundary");
        assertEquals(source, runtime.archive());
        assertEquals(totalTopLevelRecords(source), runtime.addressedRecords().size());
        assertEveryRecordAddressed(source, runtime);

        List<String> nonLinearSeekOrder = List.of("state:replayed", "state:root", "state:exact");
        for (String stateId : nonLinearSeekOrder) {
            Frame before = source.requireFrame(stateId);
            Frame restored = runtime.requireFrame(stateId);
            assertArrayEquals(before.text().getBytes(StandardCharsets.UTF_8),
                    restored.text().getBytes(StandardCharsets.UTF_8));
            assertEquals(before.textHash(), restored.textHash());
            assertEquals(before.stateHash(), restored.stateHash());
            assertEquals(before.parentStateId(), restored.parentStateId());
            assertEquals(before.selectionWitnessId(), restored.selectionWitnessId());
        }

        List<Long> expectedSequences = new ArrayList<>();
        source.sourceEvents().forEach(value -> expectedSequences.add(value.sequence()));
        source.bindingDecisions().forEach(value -> expectedSequences.add(value.sequence()));
        source.invocations().forEach(value -> expectedSequences.add(value.sequence()));
        source.transitions().forEach(value -> expectedSequences.add(value.sequence()));
        source.observations().forEach(value -> expectedSequences.add(value.sequence()));
        source.headMovements().forEach(value -> expectedSequences.add(value.sequence()));
        source.replayReports().forEach(value -> expectedSequences.add(value.sequence()));
        expectedSequences.sort(Long::compareTo);
        assertEquals(expectedSequences,
                runtime.causalTimeline().stream()
                        .map(record -> record.logicalSequence().orElseThrow())
                        .toList());
        for (long sequence : List.of(13L, 1L, 8L, 5L, 10L, 4L)) {
            assertEquals(sequence, runtime.requireCausalRecord(sequence).logicalSequence().orElseThrow());
        }
        assertTrue(runtime.causalRecord(14).isEmpty());
    }

    @Test
    void exposesImmutableParentChildForestAndHead() throws Exception {
        SFMTemporalReplayRuntime runtime = SFMTemporalReplayRuntime.restoreCanonical(fixture());
        SFMTemporalReplayRuntime.FrameGraph graph = runtime.frameGraph();

        assertEquals("state:replayed", graph.headStateId());
        assertEquals(runtime.requireFrame("state:replayed"), graph.head());
        assertEquals(List.of("state:root"), graph.rootStateIds());
        assertEquals(Optional.empty(), graph.parent("state:root"));
        assertEquals(Optional.of(runtime.requireFrame("state:root")), graph.parent("state:exact"));
        assertEquals(
                List.of("state:exact", "state:replayed"),
                graph.children("state:root").stream().map(Frame::stateId).toList()
        );
        assertTrue(graph.children("state:exact").isEmpty());

        assertThrows(UnsupportedOperationException.class,
                () -> runtime.frames().put("state:other", runtime.head()));
        assertThrows(UnsupportedOperationException.class, runtime.addressedRecords()::clear);
        assertThrows(UnsupportedOperationException.class, runtime.causalTimeline()::clear);
        assertThrows(UnsupportedOperationException.class,
                () -> graph.parentStateIds().put("state:other", Optional.empty()));
        assertThrows(UnsupportedOperationException.class,
                () -> graph.childStateIds().get("state:root").add("state:other"));
        assertThrows(UnsupportedOperationException.class,
                () -> graph.rootStateIds().add("state:other"));

        byte[] exported = runtime.encodeCanonicalUtf8();
        exported[0] = 'x';
        assertEquals('{', runtime.encodeCanonicalUtf8()[0]);
    }

    @Test
    void canonicalDecodeRestoreEncodeIsByteIdentical() throws Exception {
        Archive original = SFMTemporalReplayArchiveJsonCodec.read(fixture());
        String canonical = SFMTemporalReplayArchiveJsonCodec.write(original);
        Archive decoded = SFMTemporalReplayArchiveJsonCodec.read(canonical);
        SFMTemporalReplayRuntime runtime = SFMTemporalReplayRuntime.restore(decoded);

        assertEquals(fixture(), canonical);
        assertEquals(canonical, runtime.encodeCanonical());
        assertArrayEquals(canonical.getBytes(StandardCharsets.UTF_8), runtime.encodeCanonicalUtf8());
        assertEquals(canonical,
                SFMTemporalReplayRuntime.restoreCanonical(runtime.encodeCanonical()).encodeCanonical());
        assertEquals(canonical, SFMTemporalReplayArchiveJsonCodec.write(runtime.archive()));
    }

    @Test
    void malformedDanglingHashInvalidAndCyclicArchivesFailClosed() throws Exception {
        JsonObject dangling = fixtureObject();
        dangling.addProperty("current_state_id", "state:missing");

        JsonObject invalidHash = fixtureObject();
        frame(invalidHash, "state:root").addProperty("text_hash", "sha256:wrong");

        JsonObject malformed = fixtureObject();
        malformed.addProperty("generation", -1);

        JsonObject cyclic = fixtureObject();
        frame(cyclic, "state:root").addProperty("parent_state_id", "state:exact");
        Archive constructorValidCycle = SFMTemporalReplayArchiveJsonCodec.read(cyclic.toString());

        assertThrows(IllegalArgumentException.class,
                () -> SFMTemporalReplayRuntime.restoreCanonical(dangling.toString()));
        assertThrows(IllegalArgumentException.class,
                () -> SFMTemporalReplayRuntime.restoreCanonical(invalidHash.toString()));
        assertThrows(IllegalArgumentException.class,
                () -> SFMTemporalReplayRuntime.restoreCanonical(malformed.toString()));
        IllegalArgumentException cycleFailure = assertThrows(
                IllegalArgumentException.class,
                () -> SFMTemporalReplayRuntime.restore(constructorValidCycle)
        );
        assertTrue(cycleFailure.getMessage().contains("cycle"));
    }

    private static void assertEveryRecordAddressed(
            Archive archive,
            SFMTemporalReplayRuntime runtime
    ) {
        archive.bindingSnapshots().forEach(value -> assertEquals(value, runtime.requireRecord(
                SFMTemporalReplayRuntime.RecordKind.BINDING_SNAPSHOT, value.id()).value()));
        archive.sourceEvents().forEach(value -> assertEquals(value, runtime.requireRecord(
                SFMTemporalReplayRuntime.RecordKind.SOURCE_EVENT, value.id()).value()));
        archive.bindingDecisions().forEach(value -> assertEquals(value, runtime.requireRecord(
                SFMTemporalReplayRuntime.RecordKind.BINDING_DECISION, value.id()).value()));
        archive.invocations().forEach(value -> assertEquals(value, runtime.requireRecord(
                SFMTemporalReplayRuntime.RecordKind.ACTION_INVOCATION, value.id()).value()));
        archive.selectionExpressions().forEach(value -> assertEquals(value, runtime.requireRecord(
                SFMTemporalReplayRuntime.RecordKind.SELECTION_EXPRESSION, value.id()).value()));
        archive.selectionWitnesses().forEach(value -> assertEquals(value, runtime.requireRecord(
                SFMTemporalReplayRuntime.RecordKind.SELECTION_WITNESS, value.id()).value()));
        archive.frames().forEach(value -> assertEquals(value, runtime.requireRecord(
                SFMTemporalReplayRuntime.RecordKind.FRAME, value.stateId()).value()));
        archive.transitions().forEach(value -> assertEquals(value, runtime.requireRecord(
                SFMTemporalReplayRuntime.RecordKind.SEMANTIC_TRANSITION, value.id()).value()));
        archive.observations().forEach(value -> assertEquals(value, runtime.requireRecord(
                SFMTemporalReplayRuntime.RecordKind.OBSERVATION, value.id()).value()));
        archive.headMovements().forEach(value -> assertEquals(value, runtime.requireRecord(
                SFMTemporalReplayRuntime.RecordKind.HEAD_MOVEMENT, value.id()).value()));
        archive.replayReports().forEach(value -> assertEquals(value, runtime.requireRecord(
                SFMTemporalReplayRuntime.RecordKind.REPLAY_REPORT, value.id()).value()));
    }

    private static int totalTopLevelRecords(Archive archive) {
        return archive.bindingSnapshots().size()
                + archive.sourceEvents().size()
                + archive.bindingDecisions().size()
                + archive.invocations().size()
                + archive.selectionExpressions().size()
                + archive.selectionWitnesses().size()
                + archive.frames().size()
                + archive.transitions().size()
                + archive.observations().size()
                + archive.headMovements().size()
                + archive.replayReports().size();
    }

    private static JsonObject fixtureObject() throws IOException {
        return JsonParser.parseString(fixture()).getAsJsonObject();
    }

    private static JsonObject frame(JsonObject root, String stateId) {
        for (var element : root.getAsJsonArray("frames")) {
            JsonObject value = element.getAsJsonObject();
            if (stateId.equals(value.get("state_id").getAsString())) return value;
        }
        throw new AssertionError("Fixture does not contain frame " + stateId);
    }

    private static String fixture() throws IOException {
        try (InputStream stream = Objects.requireNonNull(
                SFMTemporalReplayRuntimeTests.class.getResourceAsStream(FIXTURE),
                FIXTURE
        )) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
