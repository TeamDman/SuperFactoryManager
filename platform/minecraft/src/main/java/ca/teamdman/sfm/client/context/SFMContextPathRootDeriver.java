package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure contextual-root derivation that never invents filesystem authority.
 *
 * <p>A directory seeds itself and a file seeds its parent, but only when the
 * contributor supplied an authorized root containing both the projected path
 * and the derived candidate. UNKNOWN shape, selections, and unbounded paths
 * deliberately produce no root.</p>
 */
public final class SFMContextPathRootDeriver {
    private SFMContextPathRootDeriver() {
    }

    public static Optional<SFMPath> derive(SFMContextPathProjection projection) {
        Objects.requireNonNull(projection, "projection");
        return derive(projection.path(), projection.authorizedRoot(), projection.nodeKind());
    }

    /** Text documents are leaf addresses, so addressed documents seed their parent. */
    public static Optional<SFMPath> derive(SFMContextDocumentProjection document) {
        Objects.requireNonNull(document, "document");
        return document.baseline().path().flatMap(path -> derive(
                path,
                document.baseline().authorizedRoot(),
                SFMContextPathProjection.NodeKind.FILE
        ));
    }

    public static Optional<SFMPath> derive(
            SFMPath path,
            Optional<SFMPath> authorizedRoot,
            SFMContextPathProjection.NodeKind nodeKind
    ) {
        Objects.requireNonNull(path, "path");
        authorizedRoot = Objects.requireNonNull(authorizedRoot, "authorizedRoot");
        Objects.requireNonNull(nodeKind, "nodeKind");
        if (path.kind() == SFMPath.Kind.SELECTION || authorizedRoot.isEmpty()) return Optional.empty();

        SFMPath root = authorizedRoot.orElseThrow();
        if (!contains(root, path)) return Optional.empty();
        Optional<SFMPath> candidate = switch (nodeKind) {
            case DIRECTORY -> Optional.of(path);
            case FILE -> parent(path);
            case UNKNOWN -> Optional.empty();
        };
        return candidate.filter(value -> contains(root, value));
    }

    static Optional<SFMPath> parent(SFMPath path) {
        Objects.requireNonNull(path, "path");
        if (path.kind() == SFMPath.Kind.SELECTION || path.segments().isEmpty()) return Optional.empty();
        if (isResolverRoot(path)) return Optional.empty();
        List<String> parentSegments = path.segments().subList(0, path.segments().size() - 1);
        return Optional.of(new SFMPath(
                path.kind(),
                path.scheme(),
                path.authority(),
                parentSegments,
                Optional.empty(),
                true
        ));
    }

    static boolean contains(SFMPath root, SFMPath candidate) {
        if (root.kind() != candidate.kind()
                || !root.scheme().equals(candidate.scheme())
                || !root.authority().equals(candidate.authority())
                || root.revision().isPresent()
                || candidate.revision().isPresent()
                || root.segments().size() > candidate.segments().size()) {
            return false;
        }
        for (int index = 0; index < root.segments().size(); index++) {
            if (!root.segments().get(index).equals(candidate.segments().get(index))) return false;
        }
        return true;
    }

    private static boolean isResolverRoot(SFMPath path) {
        if (path.kind() == SFMPath.Kind.FILE) {
            if (path.segments().isEmpty()) return true;
            if (!path.authority().isEmpty()) return path.segments().size() == 1;
            return path.segments().size() == 1 && path.segments().get(0).endsWith(":");
        }
        return path.kind() == SFMPath.Kind.REGISTRY && path.segments().size() == 1;
    }
}
