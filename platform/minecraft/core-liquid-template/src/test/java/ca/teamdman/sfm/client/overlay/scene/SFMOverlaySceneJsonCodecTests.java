package ca.teamdman.sfm.client.overlay.scene;

import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.ContentRecipe;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.InputMode;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.OverlayInstanceId;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.OverlayState;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.SceneState;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMOverlaySceneJsonCodecTests {
    @Test
    public void canonicalJsonRoundTripAndDirectRestoreAreByteIdentical() {
        OverlayInstanceId focusedId = new OverlayInstanceId("test:b");
        SceneState source = new SceneState(
                SFMOverlaySceneContract.SCHEMA,
                41,
                List.of(
                        new OverlayState(
                                focusedId,
                                new ContentRecipe("test:content/b", "focused"),
                                true,
                                SFMOverlaySceneContract.Placement.topRight(333, 222),
                                InputMode.INTERACTIVE,
                                17,
                                Map.of("zeta", "last", "alpha", "first")
                        ),
                        new OverlayState(
                                new OverlayInstanceId("test:a"),
                                new ContentRecipe("test:content/a", ""),
                                false,
                                SFMOverlaySceneContract.Placement.topRight(200, 100),
                                InputMode.PASSIVE,
                                -3,
                                Map.of()
                        )
                ),
                Optional.of(focusedId)
        );

        String canonical = SFMOverlaySceneJsonCodec.write(source);
        SceneState decoded = SFMOverlaySceneJsonCodec.read(canonical);
        String reencoded = SFMOverlaySceneJsonCodec.write(decoded);

        assertEquals(source, decoded);
        assertArrayEquals(
                canonical.getBytes(StandardCharsets.UTF_8),
                reencoded.getBytes(StandardCharsets.UTF_8)
        );
        assertTrue(canonical.indexOf("\"id\": \"test:a\"") < canonical.indexOf("\"id\": \"test:b\""));
        assertTrue(canonical.indexOf("\"alpha\": \"first\"") < canonical.indexOf("\"zeta\": \"last\""));

        SFMOverlaySceneController restored = new SFMOverlaySceneController();
        restored.restoreCanonical(canonical);

        assertEquals(source, restored.snapshot());
        assertEquals(41, restored.snapshot().revision(), "direct restore must not replay mutations");
        assertArrayEquals(
                canonical.getBytes(StandardCharsets.UTF_8),
                restored.encodeCanonical().getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    public void malformedDuplicateAndUnknownJsonFailClosed() {
        IllegalArgumentException malformed = assertThrows(
                IllegalArgumentException.class,
                () -> SFMOverlaySceneJsonCodec.read("{")
        );
        assertTrue(malformed.getMessage().contains("Malformed client scene JSON"));

        IllegalArgumentException duplicateField = assertThrows(
                IllegalArgumentException.class,
                () -> SFMOverlaySceneJsonCodec.read("""
                        {
                          "schema":"sfm.client-scene/1",
                          "schema":"sfm.client-scene/1",
                          "revision":0,
                          "focused_overlay":null,
                          "overlays":[]
                        }
                        """)
        );
        assertTrue(duplicateField.getMessage().contains("Duplicate JSON field: schema"));

        IllegalArgumentException unknownField = assertThrows(
                IllegalArgumentException.class,
                () -> SFMOverlaySceneJsonCodec.read("""
                        {
                          "schema":"sfm.client-scene/1",
                          "revision":0,
                          "focused_overlay":null,
                          "overlays":[],
                          "surprise":true
                        }
                        """)
        );
        assertTrue(unknownField.getMessage().contains("Unknown client scene field: surprise"));

        IllegalArgumentException unknownSchema = assertThrows(
                IllegalArgumentException.class,
                () -> SFMOverlaySceneJsonCodec.read(emptySceneJson("sfm.client-scene/999"))
        );
        assertTrue(unknownSchema.getMessage().contains("Unsupported client-scene schema"));
    }

    @Test
    public void duplicateOverlayIdentityFailsEvenWhenJsonFieldsAreUnique() {
        String duplicate = overlayJson("test:duplicate", "focused", false, "passive", 0);
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> SFMOverlaySceneJsonCodec.read(sceneJson(
                        SFMOverlaySceneContract.SCHEMA,
                        "null",
                        duplicate + "," + duplicate
                ))
        );

        assertTrue(failure.getMessage().contains("Duplicate overlay instance id test:duplicate"));
    }

    @Test
    public void boundedJsonOverlayCountAndTextLimitsFailClosed() {
        String oversizedJson = "{\"padding\":\""
                + "x".repeat(SFMOverlaySceneContract.MAX_SCENE_JSON_BYTES)
                + "\"}";
        IllegalArgumentException jsonFailure = assertThrows(
                IllegalArgumentException.class,
                () -> SFMOverlaySceneJsonCodec.read(oversizedJson)
        );
        assertTrue(jsonFailure.getMessage().contains("exceeds the bounded UTF-8 size"));

        String tooManyOverlays = String.join(
                ",",
                Collections.nCopies(
                        SFMOverlaySceneContract.MAX_OVERLAYS + 1,
                        overlayJson("test:many", "", false, "passive", 0)
                )
        );
        IllegalArgumentException countFailure = assertThrows(
                IllegalArgumentException.class,
                () -> SFMOverlaySceneJsonCodec.read(sceneJson(
                        SFMOverlaySceneContract.SCHEMA,
                        "null",
                        tooManyOverlays
                ))
        );
        assertTrue(countFailure.getMessage().contains("bounded element count"));

        String oversizedArgument = "x".repeat(SFMOverlaySceneContract.MAX_TEXT_BYTES + 1);
        IllegalArgumentException textFailure = assertThrows(
                IllegalArgumentException.class,
                () -> SFMOverlaySceneJsonCodec.read(sceneJson(
                        SFMOverlaySceneContract.SCHEMA,
                        "null",
                        overlayJson("test:text", oversizedArgument, false, "passive", 0)
                ))
        );
        assertTrue(textFailure.getMessage().contains("exceeds the bounded UTF-8 length"));
    }

    private static String emptySceneJson(String schema) {
        return sceneJson(schema, "null", "");
    }

    private static String sceneJson(String schema, String focusedOverlay, String overlays) {
        return """
                {
                  "schema":"%s",
                  "revision":0,
                  "focused_overlay":%s,
                  "overlays":[%s]
                }
                """.formatted(schema, focusedOverlay, overlays);
    }

    private static String overlayJson(
            String id,
            String argument,
            boolean visible,
            String inputMode,
            int zOrder
    ) {
        return """
                {
                  "id":"%s",
                  "recipe":{"content_id":"test:content","argument":"%s"},
                  "visible":%s,
                  "placement":{
                    "reference_frame":"gui-safe",
                    "reference_anchor":[0,0],
                    "content_anchor":[0,0],
                    "logical_offset":[0,0],
                    "size_constraints":null,
                    "clip_policy":"clip"
                  },
                  "input_mode":"%s",
                  "z_order":%d,
                  "persisted_state":{}
                }
                """.formatted(id, argument, visible, inputMode, zOrder);
    }
}
