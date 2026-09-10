package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.*;

/** Orders stable match identities by the Explorer's hierarchy/sort, never fuzzy relevance. */
public final class SFMExplorerMatchTraversal {
    private SFMExplorerMatchTraversal() { }

    public static List<SFMPath> order(Set<SFMPath> matches, SFMExplorerSession.Snapshot session,
                                     SFMChildRelationRepository.Snapshot relations,
                                     Map<SFMPath, SFMExplorerEntry> entries) {
        String sortId = session.settings().sort().contributionId();
        Comparator<SFMPath> comparator = Comparator
                .comparing((SFMPath path) -> !key(path, entries, sortId).available())
                .thenComparing(path -> key(path, entries, sortId).value().orElse("").toLowerCase(Locale.ROOT))
                .thenComparing(path -> key(path, entries, sortId).value().orElse(""))
                .thenComparing(SFMPath::canonical);
        if (session.settings().group() == SFMExplorerProjection.Group.NONE)
            return matches.stream().sorted(comparator).toList();
        var roots = new LinkedHashSet<SFMPath>();
        session.manualRootOrder().stream().filter(session.roots()::contains).forEach(roots::add);
        session.roots().stream().sorted().forEach(roots::add);
        var children = SFMExplorerProjection.indexChildren(relations.relation().edges());
        var pending = new ArrayDeque<SFMPath>();
        var rootList = new ArrayList<>(roots);
        for (int i = rootList.size() - 1; i >= 0; i--) pending.push(rootList.get(i));
        var visited = new HashSet<SFMPath>();
        var answer = new LinkedHashSet<SFMPath>();
        while (!pending.isEmpty()) {
            var path = pending.pop();
            if (!visited.add(path)) continue;
            if (matches.contains(path)) answer.add(path);
            var sorted = children.getOrDefault(path, List.of()).stream().sorted(comparator).toList();
            for (int i = sorted.size() - 1; i >= 0; i--) pending.push(sorted.get(i));
        }
        // A malformed/partial relation cannot silently discard a known match.
        matches.stream().filter(path -> !answer.contains(path)).sorted(comparator).forEach(answer::add);
        return List.copyOf(answer);
    }

    private static SFMExplorerEntry.SortKey key(SFMPath path, Map<SFMPath, SFMExplorerEntry> entries, String sortId) {
        var entry = entries.get(path);
        return (entry == null ? SFMExplorerEntry.simple(path, path.canonical(), false, Optional.empty()) : entry)
                .sortKey(sortId);
    }
}
