package ca.teamdman.sfm.client.explorer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Lexical hierarchy operations shared by resolver-backed explorer intents. */
public final class SFMPathHierarchy {
    private SFMPathHierarchy() {
    }

    /**
     * Returns whether {@code candidate} is the root itself or one of its
     * descendants without performing filesystem IO or resolving symlinks.
     */
    public static boolean contains(SFMPath root, SFMPath candidate) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(candidate, "candidate");
        if (root.kind() != candidate.kind()
                || !root.scheme().equals(candidate.scheme())
                || !root.authority().equals(candidate.authority())
                || !root.revision().equals(candidate.revision())) return false;
        if (root.kind() == SFMPath.Kind.SELECTION) return root.equals(candidate);
        if (root.kind() == SFMPath.Kind.FILE) {
            Path rootPath = root.toNativePath().toAbsolutePath().normalize();
            Path candidatePath = candidate.toNativePath().toAbsolutePath().normalize();
            return candidatePath.startsWith(rootPath);
        }
        if (root.segments().size() > candidate.segments().size()) return false;
        return candidate.segments().subList(0, root.segments().size()).equals(root.segments());
    }

    /** Deepest root that can represent the concrete target. */
    public static Optional<SFMPath> deepestContainingRoot(Iterable<SFMPath> roots, SFMPath target) {
        Objects.requireNonNull(roots, "roots");
        Objects.requireNonNull(target, "target");
        SFMPath answer = null;
        for (SFMPath root : roots) {
            if (!contains(root, target)) continue;
            if (answer == null || depth(root) > depth(answer)
                    || depth(root) == depth(answer) && root.canonical().compareTo(answer.canonical()) < 0) {
                answer = root;
            }
        }
        return Optional.ofNullable(answer);
    }

    /** Root-to-target chain, preserving exact root and target identities. */
    public static List<SFMPath> chain(SFMPath root, SFMPath target) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(target, "target");
        if (!contains(root, target)) {
            throw new IllegalArgumentException(
                    "Target is outside explorer root: " + target.canonical() + " not under " + root.canonical()
            );
        }
        if (root.equals(target)) return List.of(root);

        ArrayList<SFMPath> answer = new ArrayList<>();
        answer.add(root);
        if (root.kind() == SFMPath.Kind.FILE) {
            Path rootPath = root.toNativePath().toAbsolutePath().normalize();
            Path targetPath = target.toNativePath().toAbsolutePath().normalize();
            Path relative = rootPath.relativize(targetPath);
            Path current = rootPath;
            for (int index = 0; index < relative.getNameCount(); index++) {
                current = current.resolve(relative.getName(index));
                answer.add(index == relative.getNameCount() - 1
                        ? target
                        : SFMPath.fromNative(current));
            }
            return List.copyOf(answer);
        }

        for (int size = root.segments().size() + 1; size <= target.segments().size(); size++) {
            answer.add(size == target.segments().size()
                    ? target
                    : new SFMPath(
                            target.kind(),
                            target.scheme(),
                            target.authority(),
                            target.segments().subList(0, size),
                            target.revision(),
                            false
                    ));
        }
        return List.copyOf(answer);
    }

    public static int depth(SFMPath path) {
        Objects.requireNonNull(path, "path");
        return path.segments().size();
    }
}
