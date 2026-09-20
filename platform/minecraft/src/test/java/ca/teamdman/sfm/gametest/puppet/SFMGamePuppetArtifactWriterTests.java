package ca.teamdman.sfm.gametest.puppet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMGamePuppetArtifactWriterTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesStrictUtf8AndJsonWithDeterministicNames() throws Exception {
        String text = "query-count=3 雪\n";
        var utf8 = SFMGamePuppetArtifactWriter.write(
                temporaryDirectory,
                "artifact-probe",
                "1280x720@auto",
                "resolver-timing",
                SFMGamePuppetArtifactFormat.UTF8,
                text
        );
        var json = SFMGamePuppetArtifactWriter.write(
                temporaryDirectory,
                "artifact-probe",
                "1280x720@auto",
                "machine-state",
                SFMGamePuppetArtifactFormat.JSON,
                "{\"schema\":\"sfm.test/1\",\"ok\":true}"
        );

        assertEquals("artifact-probe__resolver-timing__1280x720_auto.txt", utf8.path().getFileName().toString());
        assertEquals("artifact-probe__machine-state__1280x720_auto.json", json.path().getFileName().toString());
        assertEquals(text.getBytes(StandardCharsets.UTF_8).length, utf8.bytes());
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(utf8.path()));
        assertEquals("application/json", json.format().contentType());
    }

    @Test
    void rejectsUnsafeNamesMalformedUnicodeAndInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> SFMGamePuppetArtifactWriter.write(
                temporaryDirectory, "artifact-probe", "1280x720@auto", "../escape",
                SFMGamePuppetArtifactFormat.UTF8, "no"));
        assertThrows(IllegalArgumentException.class, () -> SFMGamePuppetArtifactWriter.write(
                temporaryDirectory, "artifact-probe", "1280x720@auto", "bad-unicode",
                SFMGamePuppetArtifactFormat.UTF8, "\uD800"));
        assertThrows(IllegalArgumentException.class, () -> SFMGamePuppetArtifactWriter.write(
                temporaryDirectory, "artifact-probe", "1280x720@auto", "bad-json",
                SFMGamePuppetArtifactFormat.JSON, "{\"missing\":}"));
    }

    @Test
    void enforcesTheUtf8ByteLimitBeforeWriting() throws Exception {
        String maximum = "x".repeat(SFMGamePuppetArtifactWriter.MAX_ARTIFACT_BYTES);
        String oversized = maximum + "x";

        var accepted = SFMGamePuppetArtifactWriter.write(
                temporaryDirectory, "artifact-probe", "1280x720@auto", "maximum",
                SFMGamePuppetArtifactFormat.UTF8, maximum);
        assertEquals(SFMGamePuppetArtifactWriter.MAX_ARTIFACT_BYTES, accepted.bytes());

        assertThrows(IllegalArgumentException.class, () -> SFMGamePuppetArtifactWriter.write(
                temporaryDirectory, "artifact-probe", "1280x720@auto", "oversized",
                SFMGamePuppetArtifactFormat.UTF8, oversized));
        assertTrue(Files.notExists(temporaryDirectory.resolve(
                "artifact-probe__oversized__1280x720_auto.txt")));
    }
}
