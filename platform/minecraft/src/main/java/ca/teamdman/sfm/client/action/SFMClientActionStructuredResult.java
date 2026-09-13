package ca.teamdman.sfm.client.action;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/** Optional bounded machine result emitted beside, never inside, action feedback. */
public record SFMClientActionStructuredResult(
        String schemaId,
        String json
) {
    public static final int MAX_SCHEMA_ID_BYTES = 128;
    public static final int MAX_JSON_BYTES = 512 * 1024;
    private static final Pattern SCHEMA_ID = Pattern.compile("[a-z0-9][a-z0-9._-]*/[1-9][0-9]*");

    public SFMClientActionStructuredResult {
        Objects.requireNonNull(schemaId, "schemaId");
        Objects.requireNonNull(json, "json");
        if (!SCHEMA_ID.matcher(schemaId).matches()
                || utf8Length(schemaId, "schema id") > MAX_SCHEMA_ID_BYTES) {
            throw new IllegalArgumentException("Invalid client action structured-result schema id");
        }
        if (utf8Length(json, "JSON") > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Client action structured-result JSON is too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Client action structured-result JSON must be an object");
            }
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Malformed client action structured-result JSON", invalid);
        }
    }

    public static SFMClientActionStructuredResult of(String schemaId, JsonElement json) {
        return new SFMClientActionStructuredResult(schemaId, Objects.requireNonNull(json, "json").toString());
    }

    private static int utf8Length(String value, String label) {
        try {
            return StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value))
                    .remaining();
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("Client action structured-result " + label + " has malformed Unicode",
                    invalid);
        }
    }
}
