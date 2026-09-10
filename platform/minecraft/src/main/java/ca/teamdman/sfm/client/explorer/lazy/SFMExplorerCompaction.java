package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.*;
import java.util.*;

/** A compact row is a view over exact entries, never a newly invented resource. */
public final class SFMExplorerCompaction {
    public static final int MAX_SEGMENTS = 128;
    public static final int MAX_OVERRIDES = 4096;

    public record Options(boolean enabled, Map<SFMPath, Boolean> overrides) {
        public Options {
            overrides = Map.copyOf(overrides);
            if (overrides.size() > MAX_OVERRIDES) throw new IllegalArgumentException("Too many compact-path overrides");
        }
        public static Options defaults() { return new Options(true, Map.of()); }
        public boolean allows(SFMPath parent) { return overrides.getOrDefault(parent, enabled); }
        public Options enabled(boolean value) { return new Options(value, overrides); }
        public Options override(Collection<SFMPath> parents, Optional<Boolean> value) {
            var next = new TreeMap<>(overrides);
            for (var parent : parents) {
                if (value.isPresent()) next.put(parent, value.orElseThrow()); else next.remove(parent);
            }
            return new Options(enabled, next);
        }
    }

    private SFMExplorerCompaction() { }

    /** Uses actual immediate-child evidence, never filtered row counts or an icon guess. */
    public static boolean canJoin(SFMPath parent, SFMPath child, Options options, Set<SFMPath> roots,
                                  SFMChildRelationRepository.Snapshot relation,
                                  Map<SFMPath, List<SFMPath>> children,
                                  Map<SFMPath, SFMExplorerEntry> entries) {
        if (!options.allows(parent) || roots.contains(parent) || roots.contains(child) || parent.equals(child)) return false;
        if (parent.kind() != child.kind() || !parent.scheme().equals(child.scheme())
                || !parent.authority().equals(child.authority()) || !parent.revision().equals(child.revision())) return false;
        var parentRoot = SFMPathHierarchy.deepestContainingRoot(roots, parent);
        if (parentRoot.isEmpty() || !parentRoot.equals(SFMPathHierarchy.deepestContainingRoot(roots, child))) return false;
        var page = relation.pageStates().get(parent);
        if (page == null || page.materialization() != SFMChildRelationRepository.PageState.Materialization.MATERIALIZED
                || page.completeness() != SFMChildPage.Completeness.COMPLETE || page.continuation().isPresent()) return false;
        var actual = children.getOrDefault(parent, List.of());
        // Query projections use request 0 and can publish COMPLETE *matching* pages.
        // They require an explicit full-topology attestation from their resolver.
        if (page.lastRequestId() == 0 && (entries.get(parent) == null
                || !entries.get(parent).sortKey(SFMExplorerEntry.SUBJECT_COMPLETE_CHILD_COUNT).value().filter("1"::equals).isPresent())) return false;
        return actual.size() == 1 && actual.get(0).equals(child) && container(entries.get(parent)) && container(entries.get(child));
    }

    private static boolean container(SFMExplorerEntry entry) {
        return entry != null && entry.expandable()
                && entry.sortKey(SFMExplorerEntry.SUBJECT_KIND).value().filter("container"::equals).isPresent();
    }

    /** Input remains a depth-first logical projection; aliases retain its order and identities. */
    public static List<SFMExplorerProjection.Row> compact(List<SFMExplorerProjection.Row> source, Options options,
            Set<SFMPath> roots, SFMChildRelationRepository.Snapshot relation,
            Map<SFMPath, List<SFMPath>> children, Map<SFMPath, SFMExplorerEntry> entries) {
        var result = new ArrayList<SFMExplorerProjection.Row>();
        var foldedDepths = new ArrayList<Integer>();
        for (var row : source) {
            while (!foldedDepths.isEmpty() && foldedDepths.get(foldedDepths.size() - 1) >= row.depth())
                foldedDepths.remove(foldedDepths.size() - 1);
            int depth = row.depth() - foldedDepths.size();
            var previous = result.isEmpty() ? null : result.get(result.size() - 1);
            if (previous != null && !row.loading() && !previous.loading() && !row.root() && !previous.root()
                    && previous.depth() + 1 == depth && previous.segments().size() < MAX_SEGMENTS
                    && canJoin(previous.path(), row.path(), options, roots, relation, children, entries)
                    && !previous.contains(row.path())) {
                var segments = new ArrayList<>(previous.segments());
                segments.addAll(row.segments());
                var role = previous.filterMatch() || row.filterMatch() ? SFMExplorerProjection.FilterRole.MATCH
                        : previous.filterRole();
                result.set(result.size() - 1, new SFMExplorerProjection.Row(row.path(), row.entry(), previous.depth(),
                        false, row.expanded(), previous.activeSortKey(), role, row.rowKind(), segments));
                foldedDepths.add(row.depth());
            } else result.add(new SFMExplorerProjection.Row(row.path(), row.entry(), depth, row.root(), row.expanded(),
                    row.activeSortKey(), row.filterRole(), row.rowKind(), row.segments()));
        }
        return List.copyOf(result);
    }
}
