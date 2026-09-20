package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable request to read one resolver-owned path as bounded UTF-8 text.
 *
 * <p>The authorized root is part of the request identity. A resolver must not
 * satisfy the request by finding some other process-wide grant that happens to
 * contain the path.</p>
 */
public record SFMResolverTextRequest(
        SFMPath path,
        SFMPath authorizedRoot,
        Optional<String> expectedSha256,
        int maximumBytes,
        long expectedResolverGeneration,
        SFMExplorerCancellationToken cancellation
) {
    public SFMResolverTextRequest {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(authorizedRoot, "authorizedRoot");
        if (path.kind() == SFMPath.Kind.SELECTION) {
            throw new IllegalArgumentException("Text reads require one concrete path, not a selection");
        }
        if (!path.scheme().equals(authorizedRoot.scheme())) {
            throw new IllegalArgumentException("Text path and authorized root must use the same resolver");
        }
        expectedSha256 = Objects.requireNonNull(expectedSha256, "expectedSha256").map(value -> {
            if (!value.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("Expected SHA-256 must contain exactly 64 hexadecimal digits");
            }
            return value.toLowerCase(Locale.ROOT);
        });
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("Maximum text byte count must not be negative");
        }
        if (maximumBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Maximum text byte count must leave room for one overflow byte");
        }
        if (expectedResolverGeneration < 0) {
            throw new IllegalArgumentException("Resolver generation must not be negative");
        }
        Objects.requireNonNull(cancellation, "cancellation");
    }
}
