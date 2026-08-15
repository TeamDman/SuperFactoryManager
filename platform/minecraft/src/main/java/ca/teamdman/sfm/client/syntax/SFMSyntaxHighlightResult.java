package ca.teamdman.sfm.client.syntax;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable provider-neutral syntax spans for one exact request snapshot. */
public record SFMSyntaxHighlightResult(
        String schema,
        long requestId,
        long requestGeneration,
        String originId,
        long originGeneration,
        String language,
        String sourceSha256,
        long sourceBytes,
        Outcome outcome,
        boolean complete,
        String parserFingerprint,
        String formattingSchema,
        long elapsedMicros,
        CacheEvidence cache,
        List<Diagnostic> diagnostics,
        List<Span> spans
) {
    public static final String SCHEMA = "sfm.syntax-highlight.result/1";
    public static final String FORMATTING_SCHEMA = "minecraft.chat-formatting/1";
    public static final String PARSER_FINGERPRINT =
            "arborium-java/2.18.1+arborium-highlight/2.18.1+sfm-chat-formatting/1";
    public static final Set<String> CHAT_FORMATTING_NAMES = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold",
            "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white",
            "obfuscated", "bold", "strikethrough", "underline", "italic", "reset"
    );

    public enum Outcome {
        HIGHLIGHTED("highlighted", true, 0),
        UNSUPPORTED_LANGUAGE("unsupported-language", true, 4),
        INVALID_REQUEST("invalid-request", true, 2),
        CANCELLED("cancelled", false, 130),
        FAILED("failed", false, 1);

        private final String wireName;
        private final boolean complete;
        private final int exitCode;

        Outcome(String wireName, boolean complete, int exitCode) {
            this.wireName = wireName;
            this.complete = complete;
            this.exitCode = exitCode;
        }

        public String wireName() {
            return wireName;
        }

        public boolean isComplete() {
            return complete;
        }

        public int exitCode() {
            return exitCode;
        }

        public static Outcome fromWireName(String value) {
            for (Outcome outcome : values()) if (outcome.wireName.equals(value)) return outcome;
            throw new IllegalArgumentException("Unknown syntax-highlight outcome: " + value);
        }
    }

    public enum CacheStatus {
        HIT("hit"), MISS("miss"), BYPASSED("bypassed");

        private final String wireName;

        CacheStatus(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static CacheStatus fromWireName(String value) {
            for (CacheStatus status : values()) if (status.wireName.equals(value)) return status;
            throw new IllegalArgumentException("Unknown syntax-highlight cache status: " + value);
        }
    }

    public enum DiagnosticSeverity {
        INFO("info"), WARNING("warning"), ERROR("error");

        private final String wireName;

        DiagnosticSeverity(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static DiagnosticSeverity fromWireName(String value) {
            for (DiagnosticSeverity severity : values()) if (severity.wireName.equals(value)) return severity;
            throw new IllegalArgumentException("Unknown syntax-highlight diagnostic severity: " + value);
        }
    }

    public record Diagnostic(
            String code,
            DiagnosticSeverity severity,
            String message,
            Optional<Long> startByte,
            Optional<Long> endByte
    ) {
        public Diagnostic {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(message, "message");
            startByte = Objects.requireNonNull(startByte, "startByte");
            endByte = Objects.requireNonNull(endByte, "endByte");
            if (code.strip().isEmpty() || utf8Length(code) > 128) {
                throw new IllegalArgumentException("Syntax diagnostic code must contain 1..=128 UTF-8 bytes");
            }
            if (utf8Length(message) > 4096) {
                throw new IllegalArgumentException("Syntax diagnostic message exceeds 4096 UTF-8 bytes");
            }
            if (startByte.isPresent() != endByte.isPresent()) {
                throw new IllegalArgumentException("Syntax diagnostic range must provide both endpoints");
            }
            if (startByte.isPresent() && (startByte.orElseThrow() < 0 || endByte.orElseThrow() < 0)) {
                throw new IllegalArgumentException("Syntax diagnostic byte offsets must not be negative");
            }
        }

        public static Diagnostic withoutRange(String code, DiagnosticSeverity severity, String message) {
            return new Diagnostic(code, severity, message, Optional.empty(), Optional.empty());
        }
    }

    public record Span(
            long startByte,
            long endByte,
            String arboriumTag,
            List<String> chatFormatting
    ) {
        public Span {
            Objects.requireNonNull(arboriumTag, "arboriumTag");
            chatFormatting = List.copyOf(chatFormatting);
            if (startByte < 0 || endByte <= startByte) {
                throw new IllegalArgumentException("Syntax span must be a non-empty non-negative range");
            }
            if (arboriumTag.strip().isEmpty() || utf8Length(arboriumTag) > 64) {
                throw new IllegalArgumentException("Syntax Arborium tag must contain 1..=64 UTF-8 bytes");
            }
            HashSet<String> seen = new HashSet<>();
            for (String formatting : chatFormatting) {
                Objects.requireNonNull(formatting, "chatFormatting[]");
                if (!CHAT_FORMATTING_NAMES.contains(formatting)) {
                    throw new IllegalArgumentException("Unknown ChatFormatting name: " + formatting);
                }
                if (!seen.add(formatting)) {
                    throw new IllegalArgumentException("Duplicate ChatFormatting name: " + formatting);
                }
            }
        }
    }

    public record CacheEvidence(
            CacheStatus status,
            long entries,
            long retainedBytes,
            long hits,
            long misses,
            long evictions
    ) {
        public CacheEvidence {
            Objects.requireNonNull(status, "status");
            if (entries < 0 || retainedBytes < 0 || hits < 0 || misses < 0 || evictions < 0) {
                throw new IllegalArgumentException("Syntax cache evidence counters must not be negative");
            }
        }

        public static CacheEvidence bypassed() {
            return new CacheEvidence(CacheStatus.BYPASSED, 0, 0, 0, 0, 0);
        }
    }

    public SFMSyntaxHighlightResult {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(originId, "originId");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(sourceSha256, "sourceSha256");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(parserFingerprint, "parserFingerprint");
        Objects.requireNonNull(formattingSchema, "formattingSchema");
        Objects.requireNonNull(cache, "cache");
        diagnostics = List.copyOf(diagnostics);
        spans = List.copyOf(spans);
        validateStructure(schema, requestId, requestGeneration, originId, originGeneration, language, sourceSha256,
                sourceBytes, outcome, complete, parserFingerprint, formattingSchema, elapsedMicros, diagnostics, spans);
    }

    public boolean matchesIdentity(SFMSyntaxHighlightRequest request) {
        Objects.requireNonNull(request, "request");
        return requestId == request.requestId()
                && requestGeneration == request.requestGeneration()
                && originId.equals(request.originId())
                && originGeneration == request.originGeneration()
                && language.equals(request.language())
                && sourceSha256.equals(request.sourceSha256())
                && sourceBytes == request.sourceBytes();
    }

    public void validateAgainst(SFMSyntaxHighlightRequest request, SFMSyntaxHighlightLimits limits) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(limits, "limits");
        request.validate(limits);
        if (!matchesIdentity(request)) {
            throw new IllegalArgumentException("Syntax result identity does not match its request");
        }
        if (diagnostics.size() > limits.maximumDiagnostics()) {
            throw new IllegalArgumentException("Syntax result diagnostic count exceeds the process limit");
        }
        if (spans.size() > limits.maximumSpans() || spans.size() > request.maximumSpans()) {
            throw new IllegalArgumentException("Syntax result span count exceeds the request or process limit");
        }

        String source = request.source();
        long sourceBytes = utf8Length(source);
        ArrayList<Long> utf8Boundaries = new ArrayList<>(diagnostics.size() * 2 + spans.size() * 2);
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.startByte().isPresent()) {
                long start = diagnostic.startByte().orElseThrow();
                long end = diagnostic.endByte().orElseThrow();
                validateSourceRangeBounds(sourceBytes, start, end);
                utf8Boundaries.add(start);
                utf8Boundaries.add(end);
            }
        }
        long previousEnd = 0;
        for (int index = 0; index < spans.size(); index++) {
            Span span = spans.get(index);
            validateSourceRangeBounds(sourceBytes, span.startByte(), span.endByte());
            if (index != 0 && span.startByte() < previousEnd) {
                throw new IllegalArgumentException("Syntax spans overlap or are out of order at index " + index);
            }
            utf8Boundaries.add(span.startByte());
            utf8Boundaries.add(span.endByte());
            previousEnd = span.endByte();
        }
        validateUtf8Boundaries(source, utf8Boundaries);
    }

    private static void validateStructure(
            String schema,
            long requestId,
            long requestGeneration,
            String originId,
            long originGeneration,
            String language,
            String sourceSha256,
            long sourceBytes,
            Outcome outcome,
            boolean complete,
            String parserFingerprint,
            String formattingSchema,
            long elapsedMicros,
            List<Diagnostic> diagnostics,
            List<Span> spans
    ) {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported syntax result schema");
        if (requestId <= 0 || requestGeneration <= 0 || originGeneration <= 0) {
            throw new IllegalArgumentException("Syntax result ids and generations must be positive");
        }
        SFMSyntaxHighlightRequest.validateOriginId(originId);
        SFMSyntaxHighlightRequest.validateLanguage(language);
        SFMSyntaxHighlightRequest.validateSha256(sourceSha256);
        if (sourceBytes < 0 || sourceBytes > SFMSyntaxHighlightLimits.DEFAULT_MAXIMUM_SOURCE_BYTES
                || elapsedMicros < 0) {
            throw new IllegalArgumentException("Syntax result source bytes or elapsed counter is outside bounds");
        }
        if (complete != outcome.isComplete()) {
            throw new IllegalArgumentException("Syntax result completeness disagrees with its outcome");
        }
        if (!PARSER_FINGERPRINT.equals(parserFingerprint)) {
            throw new IllegalArgumentException("Unsupported syntax parser fingerprint");
        }
        if (!FORMATTING_SCHEMA.equals(formattingSchema)) {
            throw new IllegalArgumentException("Unsupported syntax formatting schema");
        }
        if (diagnostics.size() > SFMSyntaxHighlightLimits.DEFAULT_MAXIMUM_DIAGNOSTICS) {
            throw new IllegalArgumentException("Syntax result diagnostic count exceeds the process limit");
        }
        if (spans.size() > SFMSyntaxHighlightLimits.DEFAULT_MAXIMUM_SPANS) {
            throw new IllegalArgumentException("Syntax result span count exceeds the process limit");
        }
        if (outcome != Outcome.HIGHLIGHTED && !spans.isEmpty()) {
            throw new IllegalArgumentException("Non-highlighted syntax outcome must not contain spans");
        }
    }

    private static void validateSourceRangeBounds(long sourceBytes, long startByte, long endByte) {
        if (startByte < 0 || startByte >= endByte || endByte > sourceBytes) {
            throw new IllegalArgumentException(
                    "Syntax byte range " + startByte + ".." + endByte + " is outside exact source text"
            );
        }
    }

    private static void validateUtf8Boundaries(String source, List<Long> boundaries) {
        if (boundaries.isEmpty()) return;
        Collections.sort(boundaries);
        int boundaryIndex = 0;
        long byteOffset = 0;
        for (int utf16Offset = 0; boundaryIndex < boundaries.size();) {
            long requested = boundaries.get(boundaryIndex);
            if (requested == byteOffset) {
                boundaryIndex++;
                continue;
            }
            if (utf16Offset == source.length()) {
                throw new IllegalArgumentException("Syntax byte range exceeds exact source text");
            }
            int codePoint = source.codePointAt(utf16Offset);
            long next = byteOffset + utf8Bytes(codePoint);
            if (requested < next) {
                throw new IllegalArgumentException("Syntax byte range splits a UTF-8 scalar");
            }
            byteOffset = next;
            utf16Offset += Character.charCount(codePoint);
        }
    }

    private static int utf8Bytes(int codePoint) {
        if (codePoint <= 0x7f) return 1;
        if (codePoint <= 0x7ff) return 2;
        if (codePoint <= 0xffff) return 3;
        return 4;
    }

    private static int utf8Length(String value) {
        SFMSyntaxHighlightRequest.validateWellFormedUnicode(value, "syntax text");
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
