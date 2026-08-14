package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.Objects;
import java.util.Optional;

/** Selected or otherwise relevant path projected by an explorer-like origin. */
public record SFMContextPathProjection(
        SFMPath path,
        Optional<SFMPath> authorizedRoot,
        NodeKind nodeKind,
        String role
) implements SFMContextProjection {
    /**
     * Resolver-supplied shape used for safe contextual-root derivation.
     * UNKNOWN deliberately produces no default root instead of guessing from
     * a path suffix or display label.
     */
    public enum NodeKind {
        FILE,
        DIRECTORY,
        UNKNOWN
    }

    public SFMContextPathProjection {
        Objects.requireNonNull(path, "path");
        authorizedRoot = Objects.requireNonNull(authorizedRoot, "authorizedRoot");
        Objects.requireNonNull(nodeKind, "nodeKind");
        Objects.requireNonNull(role, "role");
        if (role.isBlank()) throw new IllegalArgumentException("Path projection role must not be blank");
    }

    /** Compatibility constructor for contributors that cannot yet prove node shape. */
    public SFMContextPathProjection(
            SFMPath path,
            Optional<SFMPath> authorizedRoot,
            String role
    ) {
        this(path, authorizedRoot, NodeKind.UNKNOWN, role);
    }

    public Optional<SFMPath> defaultSearchRoot() {
        return SFMContextPathRootDeriver.derive(this);
    }
}
