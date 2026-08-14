package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable, non-stringly result of one bounded resolver text read. */
public record SFMResolverTextResult(
        Status status,
        SFMPath path,
        SFMPath authorizedRoot,
        long resolverGeneration,
        Optional<String> text,
        Optional<String> sha256,
        OptionalLong byteLength,
        Optional<Instant> lastModified,
        Optional<LineEndingKind> lineEndingKind,
        Optional<String> diagnostic
) {
    public enum Status {
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
        IO_ERROR
    }

    public enum LineEndingKind {
        NONE,
        LF,
        CRLF,
        CR,
        MIXED
    }

    public SFMResolverTextResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(authorizedRoot, "authorizedRoot");
        if (resolverGeneration < 0) {
            throw new IllegalArgumentException("Resolver generation must not be negative");
        }
        text = Objects.requireNonNull(text, "text");
        sha256 = Objects.requireNonNull(sha256, "sha256");
        sha256.ifPresent(value -> {
            if (!value.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Observed SHA-256 must be 64 lowercase hexadecimal digits");
            }
        });
        Objects.requireNonNull(byteLength, "byteLength");
        lastModified = Objects.requireNonNull(lastModified, "lastModified");
        lineEndingKind = Objects.requireNonNull(lineEndingKind, "lineEndingKind");
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
        if (byteLength.isPresent() && byteLength.getAsLong() < 0) {
            throw new IllegalArgumentException("Observed byte length must not be negative");
        }
        if (status == Status.READY) {
            if (text.isEmpty() || sha256.isEmpty() || byteLength.isEmpty() || lineEndingKind.isEmpty()) {
                throw new IllegalArgumentException("A ready text result requires text, hash, byte length, and line endings");
            }
            if (diagnostic.isPresent()) {
                throw new IllegalArgumentException("A ready text result cannot carry a failure diagnostic");
            }
        } else if (text.isPresent() || lineEndingKind.isPresent()) {
            throw new IllegalArgumentException("Only a ready result may expose decoded text or line endings");
        }
        if (status == Status.STALE_CONTENT && sha256.isEmpty()) {
            throw new IllegalArgumentException("Stale content must report the observed SHA-256");
        }
    }

    public static SFMResolverTextResult ready(
            SFMResolverTextRequest request,
            long generation,
            String text,
            String sha256,
            long byteLength,
            Optional<Instant> lastModified,
            LineEndingKind lineEndingKind
    ) {
        return new SFMResolverTextResult(
                Status.READY,
                request.path(),
                request.authorizedRoot(),
                generation,
                Optional.of(text),
                Optional.of(sha256),
                OptionalLong.of(byteLength),
                lastModified,
                Optional.of(lineEndingKind),
                Optional.empty()
        );
    }

    public static SFMResolverTextResult failure(
            SFMResolverTextRequest request,
            Status status,
            long generation,
            String diagnostic
    ) {
        return failure(request, status, generation, Optional.empty(), OptionalLong.empty(), Optional.empty(), diagnostic);
    }

    public static SFMResolverTextResult failure(
            SFMResolverTextRequest request,
            Status status,
            long generation,
            Optional<String> sha256,
            OptionalLong byteLength,
            Optional<Instant> lastModified,
            String diagnostic
    ) {
        if (status == Status.READY) throw new IllegalArgumentException("Use ready() for successful text reads");
        return new SFMResolverTextResult(
                status,
                request.path(),
                request.authorizedRoot(),
                generation,
                Optional.empty(),
                sha256,
                byteLength,
                lastModified,
                Optional.empty(),
                Optional.of(Objects.requireNonNull(diagnostic, "diagnostic"))
        );
    }
}
