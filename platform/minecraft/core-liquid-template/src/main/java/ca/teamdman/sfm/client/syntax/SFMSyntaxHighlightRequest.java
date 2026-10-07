package ca.teamdman.sfm.client.syntax;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable provider-neutral request for highlighting one exact source snapshot. */
public record SFMSyntaxHighlightRequest(
        String schema,
        long requestId,
        long requestGeneration,
        String originId,
        long originGeneration,
        String language,
        String source,
        String sourceSha256,
        long maximumSpans
) {
    public static final String SCHEMA = "sfm.syntax-highlight.request/1";
    private static final String SHA256_PREFIX = "sha256:";

    public SFMSyntaxHighlightRequest {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(originId, "originId");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceSha256, "sourceSha256");
        validateWellFormedUnicode(source, "syntax source");
        validate(SFMSyntaxHighlightLimits.defaults(), schema, requestId, requestGeneration, originId,
                originGeneration, language, source, sourceSha256, maximumSpans);
    }

    public static SFMSyntaxHighlightRequest create(
            long requestId,
            long requestGeneration,
            String originId,
            long originGeneration,
            String language,
            String source,
            long maximumSpans
    ) {
        return new SFMSyntaxHighlightRequest(
                SCHEMA,
                requestId,
                requestGeneration,
                originId,
                originGeneration,
                language,
                source,
                sha256(source),
                maximumSpans
        );
    }

    public void validate(SFMSyntaxHighlightLimits limits) {
        Objects.requireNonNull(limits, "limits");
        validate(limits, schema, requestId, requestGeneration, originId, originGeneration, language, source,
                sourceSha256, maximumSpans);
    }

    public long sourceBytes() {
        return utf8Length(source);
    }

    public static String sha256(String source) {
        Objects.requireNonNull(source, "source");
        validateWellFormedUnicode(source, "syntax source");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return SHA256_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static void validateSha256(String hash) {
        Objects.requireNonNull(hash, "hash");
        if (!hash.startsWith(SHA256_PREFIX) || hash.length() != SHA256_PREFIX.length() + 64) {
            throw new IllegalArgumentException("Syntax source hash must be canonical sha256:<64 lowercase hex>");
        }
        for (int index = SHA256_PREFIX.length(); index < hash.length(); index++) {
            char value = hash.charAt(index);
            if (!((value >= '0' && value <= '9') || (value >= 'a' && value <= 'f'))) {
                throw new IllegalArgumentException("Syntax source hash must use lowercase hexadecimal");
            }
        }
    }

    static void validateLanguage(String language) {
        Objects.requireNonNull(language, "language");
        if (language.isEmpty() || language.length() > 64) {
            throw new IllegalArgumentException(
                    "Syntax language must contain 1..=64 lower-case ASCII id characters"
            );
        }
        for (int index = 0; index < language.length(); index++) {
            char value = language.charAt(index);
            boolean accepted = value >= 'a' && value <= 'z'
                    || value >= '0' && value <= '9'
                    || value == '-'
                    || value == '+'
                    || value == '.'
                    || value == '_';
            if (!accepted) {
                throw new IllegalArgumentException(
                        "Syntax language must contain 1..=64 lower-case ASCII id characters"
                );
            }
        }
    }

    static void validateOriginId(String originId) {
        Objects.requireNonNull(originId, "originId");
        int bytes = utf8Length(originId);
        if (originId.strip().isEmpty() || bytes > 256) {
            throw new IllegalArgumentException("Syntax origin id must contain 1..=256 UTF-8 bytes");
        }
    }

    static void validateWellFormedUnicode(String value, String label) {
        Objects.requireNonNull(value, label);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(label + " contains an unpaired UTF-16 surrogate");
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(label + " contains an unpaired UTF-16 surrogate");
            }
        }
    }

    static int utf8Length(String value) {
        validateWellFormedUnicode(value, "text");
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static void validate(
            SFMSyntaxHighlightLimits limits,
            String schema,
            long requestId,
            long requestGeneration,
            String originId,
            long originGeneration,
            String language,
            String source,
            String sourceSha256,
            long maximumSpans
    ) {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported syntax request schema");
        if (requestId <= 0 || requestGeneration <= 0 || originGeneration <= 0) {
            throw new IllegalArgumentException("Syntax request ids and generations must be positive");
        }
        validateOriginId(originId);
        validateLanguage(language);
        validateWellFormedUnicode(source, "syntax source");
        int sourceBytes = utf8Length(source);
        if (sourceBytes > limits.maximumSourceBytes()) {
            throw new IllegalArgumentException("Syntax source exceeds the process byte limit");
        }
        validateSha256(sourceSha256);
        if (!sourceSha256.equals(sha256(source))) {
            throw new IllegalArgumentException("Syntax source hash does not match exact request text");
        }
        if (maximumSpans <= 0 || maximumSpans > limits.maximumSpans()) {
            throw new IllegalArgumentException("Syntax maximum span count exceeds the process limit");
        }
    }
}
