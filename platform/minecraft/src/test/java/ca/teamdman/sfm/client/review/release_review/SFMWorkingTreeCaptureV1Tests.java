package ca.teamdman.sfm.client.review.release_review;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class SFMWorkingTreeCaptureV1Tests {
    private static String golden() throws Exception {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            var path = root.resolve("docs/architecture/fixtures/working-tree-capture-v1/capture.json");
            if (Files.isRegularFile(path)) return Files.readString(path).replace("\r\n", "\n");
        }
        throw new IllegalStateException("Working-tree capture fixture not found");
    }

    @Test
    void sharedGoldenRoundtripUsesUtf8OrderingAndByteLengths() throws Exception {
        var original = golden();
        var capture = SFMWorkingTreeCaptureV1Codec.parse(original);
        assertEquals("working-tree:sha256:b4744d1e1c7c092e7a40ce9b30b3909c75094649c9ecb854dba98d2dfc6ba6fc", capture.id());
        assertEquals(original, SFMWorkingTreeCaptureV1Codec.write(capture));
        assertEquals("src/\uE000.txt", capture.entries().get(4).path());
        assertEquals("src/\uD800\uDC00.txt", capture.entries().get(5).path());
    }

    @Test
    void clockAndDerivedLabelsDoNotChangeIdentityButSourceBytesDo() throws Exception {
        var json = JsonParser.parseString(golden()).getAsJsonObject();
        json.addProperty("captured_at_unix_ms", 1788726000001L);
        json.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("document_revision_id", "another-derived-id");
        json.getAsJsonArray("entries").get(1).getAsJsonObject().addProperty("diagnostic", "Localized explanation");
        assertDoesNotThrow(() -> SFMWorkingTreeCaptureV1Codec.parse(json.toString()));
        json.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("sha256", "d".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> SFMWorkingTreeCaptureV1Codec.parse(json.toString()));
    }

    private static void rejects(Consumer<JsonObject> mutation) throws Exception {
        var json = JsonParser.parseString(golden()).getAsJsonObject();
        mutation.accept(json);
        assertThrows(RuntimeException.class, () -> SFMWorkingTreeCaptureV1Codec.parse(json.toString()));
    }

    private static JsonObject entry(JsonObject json, int index) {
        return json.getAsJsonArray("entries").get(index).getAsJsonObject();
    }

    @Test
    void malformedSourceAndTypedFieldsFailClosed() throws Exception {
        for (String path : new String[]{"src/../Escape.java", "src/.GIT/config", "src/private/secret.java",
                "src-other/Escape.java", "a".repeat(4097), "src/\uD800.java"})
            rejects(json -> entry(json, 0).addProperty("path", path));
        rejects(json -> json.addProperty("surprise", true));
        rejects(json -> entry(json, 0).addProperty("surprise", true));
        rejects(json -> json.addProperty("include_untracked", "true"));
        rejects(json -> json.addProperty("include_untracked", false));
        rejects(json -> json.addProperty("captured_at_unix_ms", -1));
        rejects(json -> entry(json, 0).addProperty("byte_length", 1.5));
        rejects(json -> entry(json, 0).addProperty("byte_length", -1));
        rejects(json -> entry(json, 0).addProperty("byte_length", 4 * 1024 * 1024 + 1));
        rejects(json -> entry(json, 0).remove("document_revision_id"));
        rejects(json -> entry(json, 1).addProperty("sha256", "a".repeat(64)));
        rejects(json -> entry(json, 1).addProperty("tracked", false));
        rejects(json -> entry(json, 2).remove("diagnostic"));
        rejects(json -> json.getAsJsonArray("scope_paths").add("src/private"));
        rejects(json -> json.addProperty("consistency", "best_effort"));
    }

    @Test
    void scopeContainmentIsSegmentAware() {
        assertTrue(SFMWorkingTreeCaptureV1.contains("src", "src/Example.java"));
        assertFalse(SFMWorkingTreeCaptureV1.contains("src", "src-other/Example.java"));
        assertTrue(SFMWorkingTreeCaptureV1.contains(".", "src/Example.java"));
    }
}
