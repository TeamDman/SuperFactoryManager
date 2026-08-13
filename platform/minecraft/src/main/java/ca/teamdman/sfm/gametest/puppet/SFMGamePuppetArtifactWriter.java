package ca.teamdman.sfm.gametest.puppet;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Dependency-light staging writer for portable game-puppet evidence.
 *
 * <p>The contract lives in the main source set so the normal JUnit harness can
 * prove the validation boundary without launching Minecraft. Publication into
 * the durable preview directory remains owned by {@code sfm-propagate-changes}.</p>
 */
final class SFMGamePuppetArtifactWriter {
    static final int MAX_ARTIFACT_BYTES = 1_048_576;
    static final int MAX_ARTIFACT_NAME_LENGTH = 64;

    private SFMGamePuppetArtifactWriter() {
    }

    static WrittenArtifact write(
            Path stagingDirectory,
            String puppetName,
            String viewportVariant,
            String artifactName,
            SFMGamePuppetArtifactFormat format,
            String contents
    ) throws IOException {
        Objects.requireNonNull(stagingDirectory, "stagingDirectory");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(contents, "contents");
        requireSafeName("puppet", puppetName, Integer.MAX_VALUE);
        requireSafeName("artifact", artifactName, MAX_ARTIFACT_NAME_LENGTH);
        requireSafeVariant(viewportVariant);

        byte[] bytes = strictUtf8(contents);
        if (bytes.length > MAX_ARTIFACT_BYTES) {
            throw new IllegalArgumentException(
                    "Game puppet artifact exceeds the " + MAX_ARTIFACT_BYTES + " byte limit: " + bytes.length);
        }
        if (format == SFMGamePuppetArtifactFormat.JSON) {
            try {
                JsonParser.parseString(contents);
            } catch (JsonParseException error) {
                throw new IllegalArgumentException("Game puppet JSON artifact is not valid JSON", error);
            }
        }

        String fileName = puppetName
                + "__" + artifactName
                + "__" + viewportVariant.replace('@', '_')
                + "." + format.extension();
        Files.createDirectories(stagingDirectory);
        Path destination = stagingDirectory.resolve(fileName);
        Files.write(destination, bytes);
        return new WrittenArtifact(destination, artifactName, format, bytes.length);
    }

    private static byte[] strictUtf8(String contents) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(contents));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("Game puppet artifact contains malformed UTF-16", error);
        }
    }

    private static void requireSafeName(String kind, String value, int maxLength) {
        if (value == null
                || value.isEmpty()
                || value.length() > maxLength
                || !value.matches("[a-z0-9][a-z0-9_-]*")) {
            throw new IllegalArgumentException("Invalid game puppet " + kind + " name: " + value);
        }
    }

    private static void requireSafeVariant(String value) {
        if (value == null || !value.matches("[1-9][0-9]*x[1-9][0-9]*@(auto|[1-9][0-9]*)")) {
            throw new IllegalArgumentException("Invalid game puppet viewport variant: " + value);
        }
    }

    record WrittenArtifact(
            Path path,
            String artifactName,
            SFMGamePuppetArtifactFormat format,
            int bytes
    ) {
    }
}
