package ca.teamdman.sfm.client.history.workspace;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.Artifact;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.Barrier;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.HeadMovement;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.NarrativeKind;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.Operation;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.WorkspaceDocument;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.WorkspaceFrame;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMWorkspaceCounterfactualJsonCodecTests {
    @Test
    void freshParseAndWriteIsByteIdenticalAndRetainsFullUnicodeText() {
        Artifact source = fixture();
        String encoded = SFMWorkspaceCounterfactualJsonCodec.write(source);
        Artifact restored = SFMWorkspaceCounterfactualJsonCodec.read(encoded);

        assertAll(
                () -> assertEquals(source, restored),
                () -> assertEquals(encoded, SFMWorkspaceCounterfactualJsonCodec.write(restored)),
                () -> assertTrue(encoded.endsWith("\n")),
                () -> assertFalse(encoded.endsWith("\n\n")),
                () -> assertFalse(encoded.contains("\r")),
                () -> assertEquals("class A { String value = \"café 東京 😀\"; }\n",
                        restored.requireFrame("state:a-open").documents().get(0).text()),
                () -> assertTrue(SFMWorkspaceCounterfactualJsonCodec.transcript(restored)
                        .contains("full-text=\"class A { String value = \\\"café 東京 😀\\\"; }\\n\"")),
                () -> assertTrue(SFMWorkspaceCounterfactualJsonCodec.transcript(restored)
                        .contains("recorded-checkout")),
                () -> assertTrue(SFMWorkspaceCounterfactualJsonCodec.transcript(restored)
                        .contains("external-barrier"))
        );
    }

    @Test
    void missingUnknownMalformedDuplicateAndTrailingContentFailClosed() {
        JsonObject missing = object();
        missing.remove("current_state_id");

        JsonObject unknownRoot = object();
        unknownRoot.addProperty("future_field", true);

        JsonObject unknownNested = object();
        frame(unknownNested, "state:root").addProperty("future_field", true);

        JsonObject malformedOptional = object();
        frame(malformedOptional, "state:root").add("parent_id", new JsonObject());

        JsonObject wrongCollectionType = object();
        wrongCollectionType.addProperty("operations", "not-an-array");

        String encoded = encoded();
        String duplicateKey = encoded.replaceFirst(
                "\\{",
                "{\\\"schema\\\":\\\"" + SFMWorkspaceCounterfactualContract.SCHEMA + "\\\","
        );

        assertAll(
                () -> rejects(missing),
                () -> rejects(unknownRoot),
                () -> rejects(unknownNested),
                () -> rejects(malformedOptional),
                () -> rejects(wrongCollectionType),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMWorkspaceCounterfactualJsonCodec.read("{")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMWorkspaceCounterfactualJsonCodec.read("null")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMWorkspaceCounterfactualJsonCodec.read(encoded + "{}")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMWorkspaceCounterfactualJsonCodec.read(duplicateKey)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMWorkspaceCounterfactualJsonCodec.read("/* comment */" + encoded)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMWorkspaceCounterfactualJsonCodec.read("{unquoted:true}"))
        );
    }

    @Test
    void schemasEnumsNumbersAndOptionalScalarTypesAreStrict() {
        JsonObject schema = object();
        schema.addProperty("schema", "sfm.workspace-counterfactual/2");

        JsonObject fractional = object();
        fractional.addProperty("generation", 1.5);

        JsonObject overflowing = object();
        overflowing.add("generation", JsonParser.parseString("9223372036854775808"));

        JsonObject stringNumber = object();
        stringNumber.addProperty("generation", "7");

        JsonObject badPolicy = object();
        operation(badPolicy, "operation:open-a").addProperty("evaluation_policy", "FROZEN_WITNESS_REEXECUTION");

        JsonObject badNarrative = object();
        operation(badNarrative, "operation:open-a").addProperty("narrative_kind", "future-narrative");

        JsonObject wrongOptional = object();
        operation(wrongOptional, "operation:open-a").addProperty("result_state_id", 17);

        assertAll(
                () -> rejects(schema),
                () -> rejects(fractional),
                () -> rejects(overflowing),
                () -> rejects(stringNumber),
                () -> rejects(badPolicy),
                () -> rejects(badNarrative),
                () -> rejects(wrongOptional)
        );
    }

    @Test
    void duplicateIdsDanglingReferencesAndHashCorruptionFailClosed() {
        JsonObject duplicateFrame = object();
        JsonArray frames = duplicateFrame.getAsJsonArray("workspace_frames");
        frames.add(frames.get(0).deepCopy());

        JsonObject danglingCurrent = object();
        danglingCurrent.addProperty("current_state_id", "state:absent");

        JsonObject danglingParent = object();
        operation(danglingParent, "operation:open-a").addProperty("parent_state_id", "state:absent");

        JsonObject badDocumentHash = object();
        document(frame(badDocumentHash, "state:root"), "A.java").addProperty("text_hash", hash("wrong"));

        JsonObject badStateHash = object();
        frame(badStateHash, "state:a-open").addProperty("state_hash", hash("wrong-state"));

        JsonObject changedAmbient = object();
        changedAmbient.addProperty("ambient_after_hash", hash("mutated"));

        assertAll(
                () -> rejects(duplicateFrame),
                () -> rejects(danglingCurrent),
                () -> rejects(danglingParent),
                () -> rejects(badDocumentHash),
                () -> rejects(badStateHash),
                () -> rejects(changedAmbient)
        );
    }

    @Test
    void boundedDepthCollectionAndDocumentPayloadsFailBeforePublication() {
        String tooDeep = "[".repeat(SFMWorkspaceCounterfactualJsonCodec.MAX_JSON_NESTING_DEPTH + 2)
                + "null"
                + "]".repeat(SFMWorkspaceCounterfactualJsonCodec.MAX_JSON_NESTING_DEPTH + 2);

        JsonObject oversizedDocument = object();
        document(frame(oversizedDocument, "state:root"), "A.java").addProperty(
                "text",
                "x".repeat(SFMWorkspaceCounterfactualContract.MAX_DOCUMENT_UTF8_BYTES + 1)
        );

        JsonObject oversizedArray = object();
        JsonArray barriers = new JsonArray();
        for (int index = 0; index <= SFMWorkspaceCounterfactualContract.MAX_RECORDS_PER_KIND; index++) {
            barriers.add(JsonNull.INSTANCE);
        }
        oversizedArray.add("barriers", barriers);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMWorkspaceCounterfactualJsonCodec.read(tooDeep)),
                () -> rejects(oversizedDocument),
                () -> rejects(oversizedArray)
        );
    }

    private static Artifact fixture() {
        WorkspaceDocument a = WorkspaceDocument.create(
                "A.java",
                "class A { String value = \"café 東京 😀\"; }\n"
        );
        WorkspaceDocument b = WorkspaceDocument.create("B.java", "class B { int value = 2; }\n");
        WorkspaceFrame root = WorkspaceFrame.create(
                "state:root",
                Optional.empty(),
                "layout:explorer",
                "panel:explorer",
                "selection://fixture",
                Optional.empty(),
                Optional.empty(),
                List.of(b, a)
        );
        WorkspaceFrame openA = WorkspaceFrame.create(
                "state:a-open",
                Optional.of(root.id()),
                "layout:explorer-editor",
                "panel:editor",
                "selection://fixture",
                Optional.of("A.java"),
                Optional.of("A.java"),
                List.of(a, b)
        );
        Operation open = new Operation(
                "operation:open-a",
                "sfm:document/open-selected",
                root.id(),
                Optional.of(openA.id()),
                SFMHistoryGraphContract.EvaluationPolicy.FROZEN_WITNESS_REEXECUTION,
                SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                SFMHistoryGraphContract.OutcomeStatus.SUCCEEDED,
                "sfm action invoke sfm:document/open-selected",
                List.of("A.java"),
                List.of(new SFMHistoryGraphContract.DependencyWitness(
                        "fixture-selection",
                        "selection://fixture/A.java",
                        root.stateHash()
                )),
                List.of("sfm:document/edit"),
                List.of("sfm:process/launch"),
                NarrativeKind.FROZEN_WITNESS,
                List.of("Frozen witness remains A.java")
        );
        Operation external = new Operation(
                "operation:external",
                "sfm:process/launch",
                openA.id(),
                Optional.empty(),
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE,
                SFMHistoryGraphContract.OutcomeStatus.EXTERNAL_BARRIER,
                "sfm action invoke sfm:process/launch",
                List.of("fixture-helper"),
                List.of(),
                List.of(),
                List.of("sfm:process/launch"),
                NarrativeKind.REEVALUATED_INTENT,
                List.of("External effects are not projected")
        );
        HeadMovement movement = new HeadMovement(
                "movement:checkout-a",
                SFMHistoryGraphContract.HeadMovementKind.CHECKOUT,
                "Recorded A checkout",
                root.id(),
                openA.id(),
                NarrativeKind.RECORDED_CHECKOUT
        );
        Barrier barrier = new Barrier(
                "barrier:process",
                openA.id(),
                external.actionId(),
                SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE,
                SFMHistoryGraphContract.OutcomeStatus.EXTERNAL_BARRIER,
                "Launching a process is outside the in-memory fixture"
        );
        return Artifact.create(
                "episode:x5-codec",
                7,
                openA.id(),
                hash("ambient-checkout"),
                hash("ambient-checkout"),
                List.of(openA, root),
                List.of(external, open),
                List.of(movement),
                List.of(barrier)
        );
    }

    private static String encoded() {
        return SFMWorkspaceCounterfactualJsonCodec.write(fixture());
    }

    private static JsonObject object() {
        return JsonParser.parseString(encoded()).getAsJsonObject();
    }

    private static JsonObject frame(JsonObject root, String id) {
        for (var element : root.getAsJsonArray("workspace_frames")) {
            JsonObject value = element.getAsJsonObject();
            if (id.equals(value.get("id").getAsString())) return value;
        }
        throw new AssertionError("Missing frame " + id);
    }

    private static JsonObject document(JsonObject frame, String path) {
        for (var element : frame.getAsJsonArray("documents")) {
            JsonObject value = element.getAsJsonObject();
            if (path.equals(value.get("logical_path").getAsString())) return value;
        }
        throw new AssertionError("Missing document " + path);
    }

    private static JsonObject operation(JsonObject root, String id) {
        for (var element : root.getAsJsonArray("operations")) {
            JsonObject value = element.getAsJsonObject();
            if (id.equals(value.get("id").getAsString())) return value;
        }
        throw new AssertionError("Missing operation " + id);
    }

    private static void rejects(JsonObject value) {
        assertThrows(IllegalArgumentException.class,
                () -> SFMWorkspaceCounterfactualJsonCodec.read(value.toString()));
    }

    private static String hash(String value) {
        return SFMWorkspaceCounterfactualContract.sha256(value);
    }
}
