package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure label projection independent from list-versus-icon geometry. */
public final class SFMExplorerPathLabeler {
    private SFMExplorerPathLabeler() {
    }

    public static String label(SFMExplorerProjection.Row row, SFMExplorerSession.Snapshot session) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(session, "session");
        return switch (session.settings().pathDisplay()) {
            case NAME -> row.entry().label();
            case ABSOLUTE_PATH -> row.path().canonical();
            case RELATIVE_PATH -> relative(row, session);
        };
    }

    private static String relative(SFMExplorerProjection.Row row, SFMExplorerSession.Snapshot session) {
        SFMPath path = row.path();
        Optional<SFMPath> root = session.roots().stream()
                .filter(candidate -> contains(candidate, path))
                .max(Comparator.comparingInt(candidate -> candidate.segments().size()));
        if (root.isEmpty()) return path.canonical();
        SFMPath matchedRoot = root.orElseThrow();
        if (matchedRoot.equals(path)) return ".";
        List<String> relative = path.segments().subList(matchedRoot.segments().size(), path.segments().size());
        return String.join("/", relative);
    }

    private static boolean contains(SFMPath root, SFMPath candidate) {
        return root.kind() == candidate.kind()
                && root.scheme().equals(candidate.scheme())
                && root.authority().equals(candidate.authority())
                && root.segments().size() <= candidate.segments().size()
                && candidate.segments().subList(0, root.segments().size()).equals(root.segments());
    }
}
