package ca.teamdman.sfm.client.review.release_review;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Strict Java mirror of the transport-neutral Rust release-review surface v1
 * contract. Generated text is presentation only; {@link Mapping mappings}
 * retain the immutable corpus ranges that remain authoritative for comments.
 */
public final class SFMReleaseReviewSurfaceV1 {
    public static final String FILE_PAIR_SCHEMA = "sfm.review-file-pair/1";
    public static final String REQUEST_SCHEMA = "sfm.review-surface-request/1";
    public static final String SURFACE_SCHEMA = "sfm.review-surface/1";
    public static final String CORRESPONDENCE_SCHEMA = "sfm.review-correspondence-report/1";

    public static final int DEFAULT_MAX_SOURCE_BYTES_PER_SIDE = 4 * 1024 * 1024;
    public static final int DEFAULT_MAX_OUTPUT_BYTES = 8 * 1024 * 1024;
    public static final int DEFAULT_MAX_MAPPINGS = 262_144;
    public static final int DEFAULT_MAX_REGIONS = 65_536;
    public static final int DEFAULT_MAX_DIAGNOSTICS = 256;
    public static final int DEFAULT_CONTEXT_LINES = 3;
    public static final int MAX_CONTEXT_LINES = 64;

    private SFMReleaseReviewSurfaceV1() {
    }

    public enum SurfaceKind {
        TEXT_DIFF("text-diff"),
        JAVA_STRUCTURED_DIFF("java-structured-diff");

        private final String wireName;

        SurfaceKind(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static SurfaceKind fromWireName(String value) {
            return enumFromWire(values(), value, SurfaceKind::wireName, "surface kind");
        }
    }

    public enum Outcome {
        PRODUCED("produced", true),
        UNCHANGED("unchanged", true),
        FALLBACK("fallback", true),
        UNSUPPORTED("unsupported", false),
        LIMIT_EXCEEDED("limit-exceeded", false);

        private final String wireName;
        private final boolean complete;

        Outcome(String wireName, boolean complete) {
            this.wireName = wireName;
            this.complete = complete;
        }

        public String wireName() { return wireName; }
        public boolean complete() { return complete; }

        public static Outcome fromWireName(String value) {
            return enumFromWire(values(), value, Outcome::wireName, "surface outcome");
        }
    }

    public enum Severity {
        INFO("info"), WARNING("warning"), ERROR("error");
        private final String wireName;
        Severity(String wireName) { this.wireName = wireName; }
        public String wireName() { return wireName; }
        public static Severity fromWireName(String value) {
            return enumFromWire(values(), value, Severity::wireName, "diagnostic severity");
        }
    }

    public enum MappingKind {
        CONTEXT("context"),
        ADDITION("addition"),
        DELETION("deletion"),
        STRUCTURAL_BEFORE("structural-before"),
        STRUCTURAL_AFTER("structural-after"),
        STRUCTURAL_CORRESPONDENCE("structural-correspondence");
        private final String wireName;
        MappingKind(String wireName) { this.wireName = wireName; }
        public String wireName() { return wireName; }
        public static MappingKind fromWireName(String value) {
            return enumFromWire(values(), value, MappingKind::wireName, "mapping kind");
        }
    }

    public enum RegionKind {
        TEXT_HUNK("text-hunk"),
        ADDITION("addition"),
        DELETION("deletion"),
        CONTEXT("context"),
        JAVA_DECLARATION("java-declaration"),
        JAVA_IMPORT("java-import"),
        FALLBACK("fallback");
        private final String wireName;
        RegionKind(String wireName) { this.wireName = wireName; }
        public String wireName() { return wireName; }
        public static RegionKind fromWireName(String value) {
            return enumFromWire(values(), value, RegionKind::wireName, "region kind");
        }
    }

    public enum CorrespondenceKind {
        UNCHANGED("unchanged"), EDITED("edited"), ADDED("added"), DELETED("deleted"),
        MOVED("moved"), RENAMED("renamed"), MOVED_AND_RENAMED("moved-and-renamed"),
        FORMATTING_ONLY("formatting-only"), AMBIGUOUS("ambiguous");
        private final String wireName;
        CorrespondenceKind(String wireName) { this.wireName = wireName; }
        public String wireName() { return wireName; }
        public static CorrespondenceKind fromWireName(String value) {
            return enumFromWire(values(), value, CorrespondenceKind::wireName, "correspondence kind");
        }
    }

    public enum CorrespondenceConfidence {
        EXACT("exact"), STRUCTURAL("structural"), CONSERVATIVE("conservative"), UNAVAILABLE("unavailable");
        private final String wireName;
        CorrespondenceConfidence(String wireName) { this.wireName = wireName; }
        public String wireName() { return wireName; }
        public static CorrespondenceConfidence fromWireName(String value) {
            return enumFromWire(values(), value, CorrespondenceConfidence::wireName,
                    "correspondence confidence");
        }
    }

    public record Limits(
            int maximumSourceBytesPerSide,
            int maximumOutputBytes,
            int maximumMappings,
            int maximumRegions,
            int maximumDiagnostics
    ) {
        public Limits {
            if (maximumSourceBytesPerSide <= 0 || maximumOutputBytes <= 0 || maximumMappings <= 0
                    || maximumRegions <= 0 || maximumDiagnostics <= 0) {
                throw new IllegalArgumentException("Review-surface limits must be positive");
            }
        }

        public static Limits defaults() {
            return new Limits(DEFAULT_MAX_SOURCE_BYTES_PER_SIDE, DEFAULT_MAX_OUTPUT_BYTES,
                    DEFAULT_MAX_MAPPINGS, DEFAULT_MAX_REGIONS, DEFAULT_MAX_DIAGNOSTICS);
        }
    }

    public record Utf8Range(int startByte, int endByte) {
        public Utf8Range {
            if (startByte < 0 || endByte < startByte) {
                throw new IllegalArgumentException("UTF-8 range must be forward and non-negative");
            }
        }

        public int length() { return endByte - startByte; }

        public void validateAgainst(String text, String label) {
            byte[] bytes = Objects.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8);
            if (endByte > bytes.length) throw new IllegalArgumentException(label + " exceeds UTF-8 text");
            decodeUtf8(bytes, startByte, endByte, label);
        }
    }

    public record Source(
            String documentRevisionId,
            String path,
            String language,
            String sha256,
            String text
    ) {
        public Source {
            documentRevisionId = boundedText(documentRevisionId, 512, "document revision id");
            path = boundedText(path, 4096, "source path");
            language = boundedText(language, 64, "source language");
            sha256 = requirePrefixedSha256(sha256, "source SHA-256");
            Objects.requireNonNull(text, "text");
            if (!sha256.equals(SFMReleaseReviewSurfaceV1.sha256(text))) {
                throw new IllegalArgumentException("Source SHA-256 does not match exact source text");
            }
        }

        public static Source fromCorpus(
                String documentRevisionId,
                String path,
                String language,
                String text
        ) {
            return new Source(documentRevisionId, path, language, SFMReleaseReviewSurfaceV1.sha256(text), text);
        }
    }

    public record FilePair(
            String schema,
            String id,
            String laneId,
            SFMReleaseReviewV1.ChangeOperation operation,
            List<String> reviewUnitIds,
            Optional<Source> before,
            Optional<Source> after
    ) {
        public FilePair {
            if (!FILE_PAIR_SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported file-pair schema");
            id = boundedText(id, 512, "file-pair id");
            laneId = boundedText(laneId, 512, "file-pair lane id");
            Objects.requireNonNull(operation, "operation");
            reviewUnitIds = List.copyOf(reviewUnitIds);
            before = Objects.requireNonNull(before, "before");
            after = Objects.requireNonNull(after, "after");
            if (reviewUnitIds.isEmpty() || reviewUnitIds.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("File-pair review-unit IDs must not be empty");
            }
            if (!reviewUnitIds.equals(reviewUnitIds.stream().distinct().sorted().toList())) {
                throw new IllegalArgumentException("File-pair review-unit IDs must be sorted and unique");
            }
            if (before.isEmpty() && after.isEmpty()) throw new IllegalArgumentException("File pair has no source");
            switch (operation) {
                case ADDED -> {
                    if (before.isPresent() || after.isEmpty()) throw new IllegalArgumentException("Added pair shape is invalid");
                }
                case DELETED -> {
                    if (before.isEmpty() || after.isPresent()) throw new IllegalArgumentException("Deleted pair shape is invalid");
                }
                default -> {
                    if (before.isEmpty() || after.isEmpty()) {
                        throw new IllegalArgumentException("Non-add/delete pair requires both sources");
                    }
                }
            }
        }

        public FilePair(
                String id,
                String laneId,
                SFMReleaseReviewV1.ChangeOperation operation,
                List<String> reviewUnitIds,
                Optional<Source> before,
                Optional<Source> after
        ) {
            this(FILE_PAIR_SCHEMA, id, laneId, operation, reviewUnitIds, before, after);
        }

        public Optional<Source> source(SFMReleaseReviewV1.SnapshotSide side) {
            return side == SFMReleaseReviewV1.SnapshotSide.BEFORE ? before : after;
        }
    }

    /** Immutable cache recipe; transport request IDs are deliberately excluded. */
    public record Recipe(
            FilePair filePair,
            SurfaceKind surfaceKind,
            int contextLines,
            int maximumOutputBytes,
            int maximumMappings,
            int maximumRegions,
            int maximumDiagnostics
    ) {
        public Recipe {
            Objects.requireNonNull(filePair, "filePair");
            Objects.requireNonNull(surfaceKind, "surfaceKind");
            if (contextLines < 0 || contextLines > MAX_CONTEXT_LINES) {
                throw new IllegalArgumentException("Review-surface context line count is invalid");
            }
            Limits defaults = Limits.defaults();
            positiveAtMost(maximumOutputBytes, defaults.maximumOutputBytes, "output bytes");
            positiveAtMost(maximumMappings, defaults.maximumMappings, "mappings");
            positiveAtMost(maximumRegions, defaults.maximumRegions, "regions");
            positiveAtMost(maximumDiagnostics, defaults.maximumDiagnostics, "diagnostics");
            filePair.before().ifPresent(source -> sourceBytes(source, defaults));
            filePair.after().ifPresent(source -> sourceBytes(source, defaults));
        }

        public Recipe(FilePair filePair, SurfaceKind surfaceKind) {
            this(filePair, surfaceKind, DEFAULT_CONTEXT_LINES, DEFAULT_MAX_OUTPUT_BYTES,
                    DEFAULT_MAX_MAPPINGS, DEFAULT_MAX_REGIONS, DEFAULT_MAX_DIAGNOSTICS);
        }

        public Request request(long requestId, long requestGeneration) {
            return new Request(REQUEST_SCHEMA, requestId, requestGeneration, filePair, surfaceKind,
                    contextLines, maximumOutputBytes, maximumMappings, maximumRegions, maximumDiagnostics);
        }
    }

    public record Request(
            String schema,
            long requestId,
            long requestGeneration,
            FilePair filePair,
            SurfaceKind surfaceKind,
            int contextLines,
            int maximumOutputBytes,
            int maximumMappings,
            int maximumRegions,
            int maximumDiagnostics
    ) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported request schema");
            if (requestId <= 0 || requestGeneration <= 0) {
                throw new IllegalArgumentException("Request identity and generation must be positive");
            }
            Objects.requireNonNull(filePair, "filePair");
            Objects.requireNonNull(surfaceKind, "surfaceKind");
            // Reuse the immutable recipe's complete bounds validation.
            new Recipe(filePair, surfaceKind, contextLines, maximumOutputBytes, maximumMappings,
                    maximumRegions, maximumDiagnostics);
        }
    }

    public record Diagnostic(
            String code,
            Severity severity,
            String message,
            Optional<SFMReleaseReviewV1.SnapshotSide> side,
            Optional<Utf8Range> sourceRange
    ) {
        public Diagnostic {
            code = boundedText(code, 256, "diagnostic code");
            Objects.requireNonNull(severity, "severity");
            message = boundedText(message, 8192, "diagnostic message");
            side = Objects.requireNonNull(side, "side");
            sourceRange = Objects.requireNonNull(sourceRange, "sourceRange");
            if (side.isPresent() != sourceRange.isPresent()) {
                throw new IllegalArgumentException("Diagnostic side and source range must be present together");
            }
        }

        public String displayText() {
            return code + ": " + message;
        }
    }

    public record SourceRange(
            SFMReleaseReviewV1.SnapshotSide side,
            String documentRevisionId,
            String documentSha256,
            String path,
            Utf8Range range
    ) {
        public SourceRange {
            Objects.requireNonNull(side, "side");
            documentRevisionId = boundedText(documentRevisionId, 512, "source-range revision id");
            documentSha256 = requirePrefixedSha256(documentSha256, "source-range SHA-256");
            path = boundedText(path, 4096, "source-range path");
            Objects.requireNonNull(range, "range");
        }
    }

    public record Mapping(Utf8Range surfaceRange, MappingKind kind, List<SourceRange> sourceRanges) {
        public Mapping {
            Objects.requireNonNull(surfaceRange, "surfaceRange");
            Objects.requireNonNull(kind, "kind");
            sourceRanges = List.copyOf(sourceRanges);
            if (sourceRanges.isEmpty()) throw new IllegalArgumentException("A mapping requires a source range");
        }
    }

    public record Region(
            String id,
            RegionKind kind,
            String label,
            Utf8Range surfaceRange,
            List<SourceRange> sourceRanges
    ) {
        public Region {
            id = boundedText(id, 512, "region id");
            Objects.requireNonNull(kind, "kind");
            label = boundedText(label, 4096, "region label");
            Objects.requireNonNull(surfaceRange, "surfaceRange");
            sourceRanges = List.copyOf(sourceRanges);
        }
    }

    public record Correspondence(
            String id,
            CorrespondenceKind kind,
            CorrespondenceConfidence confidence,
            Optional<String> semanticKeyBefore,
            Optional<String> semanticKeyAfter,
            List<SourceRange> beforeRanges,
            List<SourceRange> afterRanges,
            List<String> evidence
    ) {
        public Correspondence {
            id = boundedText(id, 512, "correspondence id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(confidence, "confidence");
            semanticKeyBefore = optionalBounded(semanticKeyBefore, 4096, "before semantic key");
            semanticKeyAfter = optionalBounded(semanticKeyAfter, 4096, "after semantic key");
            beforeRanges = List.copyOf(beforeRanges);
            afterRanges = List.copyOf(afterRanges);
            evidence = List.copyOf(evidence);
            evidence.forEach(value -> boundedText(value, 4096, "correspondence evidence"));
        }
    }

    public record CorrespondenceReport(
            String schema,
            String filePairId,
            boolean complete,
            List<Correspondence> correspondences,
            List<Diagnostic> diagnostics
    ) {
        public CorrespondenceReport {
            if (!CORRESPONDENCE_SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("Unsupported correspondence schema");
            }
            filePairId = boundedText(filePairId, 512, "correspondence file-pair id");
            correspondences = List.copyOf(correspondences);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public record Surface(
            String schema,
            long requestId,
            long requestGeneration,
            String filePairId,
            SurfaceKind surfaceKind,
            String algorithm,
            Outcome outcome,
            boolean complete,
            Optional<SurfaceKind> fallbackKind,
            String text,
            String textSha256,
            List<Mapping> mappings,
            List<Region> regions,
            CorrespondenceReport correspondence,
            List<Diagnostic> diagnostics
    ) {
        public Surface {
            if (!SURFACE_SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported surface schema");
            if (requestId <= 0 || requestGeneration <= 0) {
                throw new IllegalArgumentException("Surface identity and generation must be positive");
            }
            filePairId = boundedText(filePairId, 512, "surface file-pair id");
            Objects.requireNonNull(surfaceKind, "surfaceKind");
            algorithm = boundedText(algorithm, 512, "surface algorithm");
            Objects.requireNonNull(outcome, "outcome");
            fallbackKind = Objects.requireNonNull(fallbackKind, "fallbackKind");
            Objects.requireNonNull(text, "text");
            textSha256 = requirePrefixedSha256(textSha256, "surface text SHA-256");
            mappings = List.copyOf(mappings);
            regions = List.copyOf(regions);
            Objects.requireNonNull(correspondence, "correspondence");
            diagnostics = List.copyOf(diagnostics);
        }

        public void validateAgainst(Request request) {
            Objects.requireNonNull(request, "request");
            if (requestId != request.requestId() || requestGeneration != request.requestGeneration()
                    || !filePairId.equals(request.filePair().id()) || surfaceKind != request.surfaceKind()) {
                throw new IllegalArgumentException("Review-surface identity does not match its request");
            }
            if (complete != outcome.complete()) throw new IllegalArgumentException("Surface completeness disagrees");
            if ((outcome == Outcome.FALLBACK) != fallbackKind.isPresent()) {
                throw new IllegalArgumentException("Surface fallback identity disagrees with outcome");
            }
            if (!textSha256.equals(sha256(text))) throw new IllegalArgumentException("Surface text hash is stale");
            int textBytes = text.getBytes(StandardCharsets.UTF_8).length;
            if (textBytes > request.maximumOutputBytes()) throw new IllegalArgumentException("Surface output exceeds bound");
            if (mappings.size() > request.maximumMappings() || regions.size() > request.maximumRegions()
                    || diagnostics.size() > request.maximumDiagnostics()
                    || correspondence.diagnostics().size() > request.maximumDiagnostics()) {
                throw new IllegalArgumentException("Surface collection exceeds request bound");
            }
            if (!correspondence.filePairId().equals(filePairId)) {
                throw new IllegalArgumentException("Correspondence file-pair identity is stale");
            }
            for (Diagnostic diagnostic : diagnostics) validateDiagnostic(diagnostic, request.filePair());
            for (Diagnostic diagnostic : correspondence.diagnostics()) validateDiagnostic(diagnostic, request.filePair());
            for (Mapping mapping : mappings) validateMapping(mapping, text, request.filePair());
            for (Region region : regions) {
                region.surfaceRange().validateAgainst(text, "region surface range");
                for (SourceRange source : region.sourceRanges()) validateSourceRange(source, request.filePair());
            }
            for (Correspondence value : correspondence.correspondences()) {
                for (SourceRange source : value.beforeRanges()) {
                    if (source.side() != SFMReleaseReviewV1.SnapshotSide.BEFORE) {
                        throw new IllegalArgumentException("Before correspondence contains an after range");
                    }
                    validateSourceRange(source, request.filePair());
                }
                for (SourceRange source : value.afterRanges()) {
                    if (source.side() != SFMReleaseReviewV1.SnapshotSide.AFTER) {
                        throw new IllegalArgumentException("After correspondence contains a before range");
                    }
                    validateSourceRange(source, request.filePair());
                }
            }
        }

        /** Project a generated half-open byte range back to exact source ranges. */
        public List<SourceRange> sourceRangesFor(Utf8Range selected) {
            selected.validateAgainst(text, "selected surface range");
            ArrayList<SourceRange> answer = new ArrayList<>();
            for (Mapping mapping : mappings) {
                int start = Math.max(selected.startByte(), mapping.surfaceRange().startByte());
                int end = Math.min(selected.endByte(), mapping.surfaceRange().endByte());
                if (start >= end) continue;
                int offset = start - mapping.surfaceRange().startByte();
                int length = end - start;
                for (SourceRange source : mapping.sourceRanges()) {
                    answer.add(new SourceRange(source.side(), source.documentRevisionId(),
                            source.documentSha256(), source.path(),
                            new Utf8Range(source.range().startByte() + offset,
                                    source.range().startByte() + offset + length)));
                }
            }
            return answer.stream().distinct().toList();
        }
    }

    public static String sha256(String text) {
        Objects.requireNonNull(text, "text");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public static String snapshotSha256(String prefixed) {
        return requirePrefixedSha256(prefixed, "prefixed SHA-256").substring("sha256:".length());
    }

    public static String operationWireName(SFMReleaseReviewV1.ChangeOperation operation) {
        return operation.name().toLowerCase(Locale.ROOT);
    }

    public static SFMReleaseReviewV1.ChangeOperation operationFromWireName(String value) {
        try {
            return SFMReleaseReviewV1.ChangeOperation.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unknown change operation `" + value + "`", error);
        }
    }

    public static String sideWireName(SFMReleaseReviewV1.SnapshotSide side) {
        return side.name().toLowerCase(Locale.ROOT);
    }

    public static SFMReleaseReviewV1.SnapshotSide sideFromWireName(String value) {
        try {
            return SFMReleaseReviewV1.SnapshotSide.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unknown snapshot side `" + value + "`", error);
        }
    }

    private static void validateMapping(Mapping mapping, String surfaceText, FilePair pair) {
        mapping.surfaceRange().validateAgainst(surfaceText, "mapping surface range");
        byte[] display = sliceUtf8(surfaceText, mapping.surfaceRange(), "mapping surface range");
        for (SourceRange sourceRange : mapping.sourceRanges()) {
            Source source = validateSourceRange(sourceRange, pair);
            byte[] sourceBytes = sliceUtf8(source.text(), sourceRange.range(), "mapping source range");
            if (!java.util.Arrays.equals(display, sourceBytes)) {
                throw new IllegalArgumentException("Mapped display bytes disagree with immutable source bytes");
            }
        }
    }

    private static void validateDiagnostic(Diagnostic diagnostic, FilePair pair) {
        if (diagnostic.side().isPresent()) {
            Source source = pair.source(diagnostic.side().orElseThrow()).orElseThrow(() ->
                    new IllegalArgumentException("Diagnostic refers to a missing source side"));
            diagnostic.sourceRange().orElseThrow().validateAgainst(source.text(), "diagnostic source range");
        }
    }

    private static Source validateSourceRange(SourceRange range, FilePair pair) {
        Source source = pair.source(range.side()).orElseThrow(() ->
                new IllegalArgumentException("Source mapping refers to a missing source side"));
        if (!source.documentRevisionId().equals(range.documentRevisionId())
                || !source.sha256().equals(range.documentSha256())
                || !source.path().equals(range.path())) {
            throw new IllegalArgumentException("Source mapping identity is stale");
        }
        range.range().validateAgainst(source.text(), "source mapping range");
        return source;
    }

    private static byte[] sliceUtf8(String text, Utf8Range range, String label) {
        range.validateAgainst(text, label);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return java.util.Arrays.copyOfRange(bytes, range.startByte(), range.endByte());
    }

    private static void decodeUtf8(byte[] bytes, int start, int end, String label) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, start, end - start));
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException(label + " does not align to UTF-8 code-point boundaries", error);
        }
    }

    private static void sourceBytes(Source source, Limits limits) {
        if (source.text().getBytes(StandardCharsets.UTF_8).length > limits.maximumSourceBytesPerSide()) {
            throw new IllegalArgumentException("Review source exceeds process byte limit");
        }
    }

    private static void positiveAtMost(int value, int maximum, String label) {
        if (value <= 0 || value > maximum) {
            throw new IllegalArgumentException("Review-surface " + label + " limit is invalid");
        }
    }

    private static String requirePrefixedSha256(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("sha256:[0-9a-f]{64}")) throw new IllegalArgumentException(label + " is invalid");
        return value;
    }

    private static String boundedText(String value, int maximumBytes, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        if (value.getBytes(StandardCharsets.UTF_8).length > maximumBytes) {
            throw new IllegalArgumentException(label + " exceeds byte limit");
        }
        return value;
    }

    private static Optional<String> optionalBounded(Optional<String> value, int maximumBytes, String label) {
        Objects.requireNonNull(value, label);
        return value.map(candidate -> boundedText(candidate, maximumBytes, label));
    }

    private static <T> T enumFromWire(T[] values, String wire, java.util.function.Function<T, String> name,
                                      String label) {
        Objects.requireNonNull(wire, label);
        for (T value : values) if (name.apply(value).equals(wire)) return value;
        throw new IllegalArgumentException("Unknown " + label + " `" + wire + "`");
    }
}
