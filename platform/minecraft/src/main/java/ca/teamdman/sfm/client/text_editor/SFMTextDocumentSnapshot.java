package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable text and provenance supplied to one independently stateful editor. */
public record SFMTextDocumentSnapshot(
        State state,
        String text,
        MutationCapability mutationCapability,
        Optional<SFMPath> path,
        Optional<SFMPath> authorizedRoot,
        Optional<String> sha256,
        OptionalLong byteLength,
        Optional<Instant> lastModified,
        Optional<SFMResolverTextResult.LineEndingKind> lineEndingKind,
        Optional<SFMTextDocumentRange> targetRange,
        List<String> diagnostics,
        Optional<SFMTextDocumentSourceRootIdentity> sourceRootIdentity
) {
    public enum State {
        READY,
        UNSUPPORTED_RESOLVER,
        UNAVAILABLE,
        REMOVED_ROOT,
        STALE_CONTENT,
        STALE_GENERATION,
        CANCELLED,
        DIRECTORY,
        BINARY,
        OVERSIZED,
        UNSUPPORTED_ENCODING,
        IO_ERROR,
        INVALID_RANGE
    }

    public enum MutationCapability {
        EDITABLE_IN_MEMORY,
        READ_ONLY
    }

    public SFMTextDocumentSnapshot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(mutationCapability, "mutationCapability");
        path = Objects.requireNonNull(path, "path");
        authorizedRoot = Objects.requireNonNull(authorizedRoot, "authorizedRoot");
        sha256 = Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(byteLength, "byteLength");
        lastModified = Objects.requireNonNull(lastModified, "lastModified");
        lineEndingKind = Objects.requireNonNull(lineEndingKind, "lineEndingKind");
        targetRange = Objects.requireNonNull(targetRange, "targetRange");
        diagnostics = List.copyOf(diagnostics);
        sourceRootIdentity = Objects.requireNonNull(sourceRootIdentity, "sourceRootIdentity");
        if (state == State.READY) {
            if (sha256.isEmpty() || byteLength.isEmpty() || lineEndingKind.isEmpty()) {
                throw new IllegalArgumentException("A ready document requires hash, byte length, and line endings");
            }
            targetRange.ifPresent(range -> range.validateAgainst(text));
        } else {
            // A failed load displays generated diagnostic text rather than the
            // addressed source. Source coordinates are meaningless in that
            // document and must never escape into the editor presentation.
            targetRange = Optional.empty();
        }
    }

    /** Backwards-compatible construction for documents without worker provenance. */
    public SFMTextDocumentSnapshot(
            State state,
            String text,
            MutationCapability mutationCapability,
            Optional<SFMPath> path,
            Optional<SFMPath> authorizedRoot,
            Optional<String> sha256,
            OptionalLong byteLength,
            Optional<Instant> lastModified,
            Optional<SFMResolverTextResult.LineEndingKind> lineEndingKind,
            Optional<SFMTextDocumentRange> targetRange,
            List<String> diagnostics
    ) {
        this(
                state,
                text,
                mutationCapability,
                path,
                authorizedRoot,
                sha256,
                byteLength,
                lastModified,
                lineEndingKind,
                targetRange,
                diagnostics,
                Optional.empty()
        );
    }

    public boolean ready() {
        return state == State.READY;
    }

    public boolean readOnly() {
        return mutationCapability == MutationCapability.READ_ONLY;
    }

    /** Actual text for ready documents; a stable visible diagnostic document otherwise. */
    public String displayText() {
        if (ready()) return text;
        StringBuilder result = new StringBuilder("Document unavailable\n")
                .append("state: ").append(state.name().toLowerCase(java.util.Locale.ROOT)).append('\n');
        path.ifPresent(value -> result.append("path: ").append(value.canonical()).append('\n'));
        authorizedRoot.ifPresent(value -> result.append("authorized root: ")
                .append(value.canonical()).append('\n'));
        for (String diagnostic : diagnostics) result.append("diagnostic: ").append(diagnostic).append('\n');
        return result.toString();
    }

    public static SFMTextDocumentSnapshot literal(String text) {
        Objects.requireNonNull(text, "text");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new SFMTextDocumentSnapshot(
                State.READY,
                text,
                MutationCapability.EDITABLE_IN_MEMORY,
                Optional.empty(),
                Optional.empty(),
                Optional.of(sha256(bytes)),
                OptionalLong.of(bytes.length),
                Optional.empty(),
                Optional.of(detectLineEndings(text)),
                Optional.empty(),
                List.of(),
                Optional.empty()
        );
    }

    /** Preserves document identity while advancing the saved-content baseline. */
    public SFMTextDocumentSnapshot withSavedText(String savedText) {
        Objects.requireNonNull(savedText, "savedText");
        byte[] bytes = savedText.getBytes(StandardCharsets.UTF_8);
        return new SFMTextDocumentSnapshot(
                State.READY,
                savedText,
                mutationCapability,
                path,
                authorizedRoot,
                Optional.of(sha256(bytes)),
                OptionalLong.of(bytes.length),
                lastModified,
                Optional.of(detectLineEndings(savedText)),
                Optional.empty(),
                diagnostics,
                sourceRootIdentity
        );
    }

    public static SFMTextDocumentSnapshot fromResolver(
            SFMResolverTextResult result,
            Optional<SFMTextDocumentRange> targetRange
    ) {
        return fromResolver(result, targetRange, Optional.empty());
    }

    public static SFMTextDocumentSnapshot fromResolver(
            SFMResolverTextResult result,
            Optional<SFMTextDocumentRange> targetRange,
            Optional<SFMTextDocumentSourceRootIdentity> sourceRootIdentity
    ) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(targetRange, "targetRange");
        Objects.requireNonNull(sourceRootIdentity, "sourceRootIdentity");
        State mapped = State.valueOf(result.status().name());
        if (result.status() == SFMResolverTextResult.Status.READY) {
            String text = result.text().orElseThrow();
            try {
                targetRange.ifPresent(range -> range.validateAgainst(text));
            } catch (IllegalArgumentException invalidRange) {
                return failure(
                        State.INVALID_RANGE,
                        result.path(),
                        result.authorizedRoot(),
                        List.of(invalidRange.getMessage()),
                        sourceRootIdentity
                );
            }
            return new SFMTextDocumentSnapshot(
                    State.READY,
                    text,
                    MutationCapability.READ_ONLY,
                    Optional.of(result.path()),
                    Optional.of(result.authorizedRoot()),
                    result.sha256(),
                    result.byteLength(),
                    result.lastModified(),
                    result.lineEndingKind(),
                    targetRange,
                    List.of(),
                    sourceRootIdentity
            );
        }
        return new SFMTextDocumentSnapshot(
                mapped,
                "",
                MutationCapability.READ_ONLY,
                Optional.of(result.path()),
                Optional.of(result.authorizedRoot()),
                result.sha256(),
                result.byteLength(),
                result.lastModified(),
                Optional.empty(),
                Optional.empty(),
                result.diagnostic().stream().toList(),
                sourceRootIdentity
        );
    }

    public static SFMTextDocumentSnapshot failure(
            State state,
            SFMPath path,
            SFMPath root,
            List<String> diagnostics
    ) {
        return failure(state, path, root, diagnostics, Optional.empty());
    }

    public static SFMTextDocumentSnapshot failure(
            State state,
            SFMPath path,
            SFMPath root,
            List<String> diagnostics,
            Optional<SFMTextDocumentSourceRootIdentity> sourceRootIdentity
    ) {
        if (state == State.READY) throw new IllegalArgumentException("Use a ready factory for ready documents");
        return new SFMTextDocumentSnapshot(
                state,
                "",
                MutationCapability.READ_ONLY,
                Optional.of(path),
                Optional.of(root),
                Optional.empty(),
                OptionalLong.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                diagnostics,
                sourceRootIdentity
        );
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static SFMResolverTextResult.LineEndingKind detectLineEndings(String text) {
        boolean lf = false;
        boolean crlf = false;
        boolean cr = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '\r') {
                if (index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    crlf = true;
                    index++;
                } else {
                    cr = true;
                }
            } else if (value == '\n') {
                lf = true;
            }
        }
        int kinds = (lf ? 1 : 0) + (crlf ? 1 : 0) + (cr ? 1 : 0);
        if (kinds == 0) return SFMResolverTextResult.LineEndingKind.NONE;
        if (kinds > 1) return SFMResolverTextResult.LineEndingKind.MIXED;
        if (crlf) return SFMResolverTextResult.LineEndingKind.CRLF;
        if (cr) return SFMResolverTextResult.LineEndingKind.CR;
        return SFMResolverTextResult.LineEndingKind.LF;
    }
}
