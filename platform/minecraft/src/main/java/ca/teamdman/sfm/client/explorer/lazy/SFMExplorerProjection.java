package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.search.SFMFuzzyScorer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Pure relation-backed projection of one explorer-session snapshot. */
public final class SFMExplorerProjection {
    public enum View {
        LIST,
        SMALL_ICONS
    }

    public enum Sort {
        NAME(SFMExplorerEntry.SORT_NAME),
        EXTENSION(SFMExplorerEntry.SORT_EXTENSION),
        ICON(SFMExplorerEntry.SORT_ICON);

        private final String contributionId;

        Sort(String contributionId) {
            this.contributionId = contributionId;
        }

        public String contributionId() {
            return contributionId;
        }
    }

    public enum Group {
        HIERARCHY,
        NONE
    }

    public enum Hoist {
        AUTO,
        SHOW_ROOTS
    }

    public enum PathDisplay {
        NAME,
        RELATIVE_PATH,
        ABSOLUTE_PATH
    }

    public record Settings(
            View view,
            Sort sort,
            Group group,
            Hoist hoist,
            PathDisplay pathDisplay,
            String filterQuery
    ) {
        public Settings {
            Objects.requireNonNull(view, "view");
            Objects.requireNonNull(sort, "sort");
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(hoist, "hoist");
            Objects.requireNonNull(pathDisplay, "pathDisplay");
            filterQuery = Objects.requireNonNull(filterQuery, "filterQuery").strip();
        }

        /** Source-compatible constructor for the projection axes that predate X-8b. */
        public Settings(View view, Sort sort, Group group, Hoist hoist) {
            this(view, sort, group, hoist, PathDisplay.NAME, "");
        }

        public static Settings defaults() {
            return new Settings(View.LIST, Sort.NAME, Group.HIERARCHY, Hoist.AUTO, PathDisplay.NAME, "");
        }

        public boolean filterActive() {
            return !filterQuery.isEmpty();
        }
    }

    public record Row(
            SFMPath path,
            SFMExplorerEntry entry,
            int depth,
            boolean root,
            boolean expanded,
            SFMExplorerEntry.SortKey activeSortKey
    ) {
        public Row {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(entry, "entry");
            if (!path.equals(entry.path())) {
                throw new IllegalArgumentException("Projection row path and entry must agree");
            }
            if (depth < 0) throw new IllegalArgumentException("Projection depth must not be negative");
            Objects.requireNonNull(activeSortKey, "activeSortKey");
        }
    }

    public record Result(
            Settings settings,
            long relationRevision,
            List<Row> rows,
            List<String> diagnostics,
            FilterEvidence filter
    ) {
        public Result {
            Objects.requireNonNull(settings, "settings");
            if (relationRevision < 0) {
                throw new IllegalArgumentException("Relation revision must not be negative");
            }
            rows = List.copyOf(rows);
            diagnostics = List.copyOf(diagnostics);
            Objects.requireNonNull(filter, "filter");
        }
    }

    /** Evidence that filtering remained local to the currently published lazy relation. */
    public record FilterEvidence(
            String query,
            int candidateCount,
            int matchCount,
            boolean incompleteMaterialization
    ) {
        public FilterEvidence {
            query = Objects.requireNonNull(query, "query");
            if (candidateCount < 0 || matchCount < 0 || matchCount > candidateCount) {
                throw new IllegalArgumentException("Invalid explorer filter counts");
            }
        }

        public boolean active() {
            return !query.isEmpty();
        }
    }

    private SFMExplorerProjection() {
    }

    public static Result project(
            SFMExplorerSession.Snapshot session,
            SFMChildRelationRepository.Snapshot relations,
            Map<SFMPath, SFMExplorerEntry> entries
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(relations, "relations");
        Objects.requireNonNull(entries, "entries");
        Projection projection = new Projection(session, relations, entries);
        return projection.project();
    }

    private static final class Projection {
        private final SFMExplorerSession.Snapshot session;
        private final SFMChildRelationRepository.Snapshot relations;
        private final Map<SFMPath, SFMExplorerEntry> entries;
        private final ArrayList<Row> rows = new ArrayList<>();
        private final ArrayList<String> diagnostics = new ArrayList<>();

        private Projection(
                SFMExplorerSession.Snapshot session,
                SFMChildRelationRepository.Snapshot relations,
                Map<SFMPath, SFMExplorerEntry> entries
        ) {
            this.session = session;
            this.relations = relations;
            this.entries = entries;
        }

        private Result project() {
            List<SFMPath> roots = orderedRoots();
            boolean showRoots = session.settings().hoist() == Hoist.SHOW_ROOTS
                    || roots.size() != 1
                    || !rootHasPublishedPage(roots.get(0));
            FilterEvidence filter;
            if (session.settings().filterActive()) {
                filter = projectFilteredMaterialization(roots, showRoots);
            } else if (session.settings().group() == Group.NONE) {
                projectFlat(roots, showRoots);
                filter = new FilterEvidence("", rows.size(), rows.size(), false);
            } else {
                projectHierarchy(roots, showRoots);
                filter = new FilterEvidence("", rows.size(), rows.size(), false);
            }
            return new Result(
                    session.settings(),
                    relations.relation().id(),
                    rows,
                    diagnostics,
                    filter
            );
        }

        /**
         * Filtering is a flat ranking over already-published relation rows.
         * It deliberately ignores expansion for candidate discovery and never
         * asks a resolver for more children.
         */
        private FilterEvidence projectFilteredMaterialization(List<SFMPath> roots, boolean showRoots) {
            HashSet<SFMPath> visited = new HashSet<>();
            HashSet<SFMPath> ancestry = new HashSet<>();
            for (SFMPath root : roots) {
                boolean newRoot = visited.add(root);
                if (showRoots && newRoot) addRow(root, 0, true);
                addMaterializedChildren(root, showRoots ? 1 : 0, visited, ancestry);
            }
            ArrayList<RowMatch> matches = new ArrayList<>();
            String query = session.settings().filterQuery();
            for (Row row : rows) {
                float score = Math.min(
                        SFMFuzzyScorer.score(query, row.entry().label()),
                        SFMFuzzyScorer.score(query, row.path().canonical())
                );
                if (score <= SFMFuzzyScorer.DEFAULT_THRESHOLD) matches.add(new RowMatch(row, score));
            }
            int candidates = rows.size();
            matches.sort(Comparator
                    .comparingDouble(RowMatch::score)
                    .thenComparing(match -> match.row().path().canonical()));
            rows.clear();
            matches.stream().map(RowMatch::row).forEach(rows::add);
            return new FilterEvidence(query, candidates, rows.size(), materializationIsIncomplete(visited));
        }

        private void addMaterializedChildren(
                SFMPath parent,
                int depth,
                Set<SFMPath> visited,
                Set<SFMPath> ancestry
        ) {
            if (!ancestry.add(parent)) {
                diagnostics.add("cycle suppressed below " + parent.canonical());
                return;
            }
            for (SFMPath child : sorted(relations.relation().childrenOf(parent))) {
                if (visited.add(child)) {
                    addRow(child, depth, false);
                    addMaterializedChildren(child, depth + 1, visited, ancestry);
                }
            }
            ancestry.remove(parent);
        }

        private boolean materializationIsIncomplete(Set<SFMPath> candidates) {
            for (SFMPath path : candidates) {
                SFMExplorerEntry entry = entry(path);
                if (!entry.expandable()) continue;
                SFMChildRelationRepository.PageState state = relations.pageStates().get(path);
                if (state == null
                        || state.materialization()
                        != SFMChildRelationRepository.PageState.Materialization.MATERIALIZED
                        || state.continuation().isPresent()) return true;
            }
            return false;
        }

        /** Keep an unmaterialized single root visible so it still has an expandable control. */
        private boolean rootHasPublishedPage(SFMPath root) {
            SFMChildRelationRepository.PageState state = relations.pageStates().get(root);
            return state != null
                    && (state.materialization()
                    == SFMChildRelationRepository.PageState.Materialization.MATERIALIZED
                    || !relations.relation().childrenOf(root).isEmpty());
        }

        private void projectHierarchy(List<SFMPath> roots, boolean showRoots) {
            HashSet<SFMPath> ancestry = new HashSet<>();
            for (SFMPath root : roots) {
                if (showRoots) {
                    addRow(root, 0, true);
                    if (session.expanded().contains(root)) {
                        addExpandedChildren(root, 1, ancestry);
                    }
                } else {
                    for (SFMPath child : sorted(relations.relation().childrenOf(root))) {
                        addRow(child, 0, false);
                        if (session.expanded().contains(child)) {
                            addExpandedChildren(child, 1, ancestry);
                        }
                    }
                }
            }
        }

        private void addExpandedChildren(SFMPath parent, int depth, Set<SFMPath> ancestry) {
            if (!ancestry.add(parent)) {
                diagnostics.add("cycle suppressed below " + parent.canonical());
                return;
            }
            for (SFMPath child : sorted(relations.relation().childrenOf(parent))) {
                addRow(child, depth, false);
                if (session.expanded().contains(child)) {
                    addExpandedChildren(child, depth + 1, ancestry);
                }
            }
            ancestry.remove(parent);
        }

        private void projectFlat(List<SFMPath> roots, boolean showRoots) {
            LinkedHashSet<SFMPath> rootSet = new LinkedHashSet<>(roots);
            LinkedHashSet<SFMPath> descendants = new LinkedHashSet<>();
            for (SFMPath root : roots) collectDescendants(root, descendants, new HashSet<>());
            if (showRoots) roots.forEach(root -> addRow(root, 0, true));
            descendants.removeAll(rootSet);
            sorted(descendants).forEach(path -> addRow(path, 0, false));
        }

        private void collectDescendants(
                SFMPath parent,
                Set<SFMPath> answer,
                Set<SFMPath> ancestry
        ) {
            if (!ancestry.add(parent)) return;
            for (SFMPath child : relations.relation().childrenOf(parent)) {
                if (answer.add(child)) collectDescendants(child, answer, ancestry);
            }
            ancestry.remove(parent);
        }

        private void addRow(SFMPath path, int depth, boolean root) {
            SFMExplorerEntry entry = entries.getOrDefault(path, fallback(path));
            SFMExplorerEntry.SortKey active = entry.sortKey(session.settings().sort().contributionId());
            active.unavailableReason().ifPresent(reason -> diagnostics.add(
                    path.canonical() + ": " + reason
            ));
            diagnostics.addAll(entry.diagnostics());
            rows.add(new Row(
                    path,
                    entry,
                    depth,
                    root,
                    session.expanded().contains(path),
                    active
            ));
        }

        private List<SFMPath> orderedRoots() {
            ArrayList<SFMPath> answer = new ArrayList<>();
            for (SFMPath root : session.manualRootOrder()) {
                if (session.roots().contains(root) && !answer.contains(root)) answer.add(root);
            }
            TreeSet<SFMPath> remainder = new TreeSet<>(session.roots());
            remainder.removeAll(answer);
            answer.addAll(remainder);
            return List.copyOf(answer);
        }

        private List<SFMPath> sorted(Collection<SFMPath> paths) {
            return paths.stream().sorted(comparator()).toList();
        }

        private Comparator<SFMPath> comparator() {
            String sortId = session.settings().sort().contributionId();
            return Comparator
                    .comparing((SFMPath path) -> !entry(path).sortKey(sortId).available())
                    .thenComparing(path -> entry(path).sortKey(sortId).value()
                            .orElse("").toLowerCase(Locale.ROOT))
                    .thenComparing(path -> entry(path).sortKey(sortId).value().orElse(""))
                    .thenComparing(SFMPath::canonical);
        }

        private SFMExplorerEntry entry(SFMPath path) {
            return entries.getOrDefault(path, fallback(path));
        }

        private static SFMExplorerEntry fallback(SFMPath path) {
            String label = path.segments().isEmpty()
                    ? path.authority()
                    : path.segments().get(path.segments().size() - 1);
            if (label.isEmpty()) label = path.canonical();
            return SFMExplorerEntry.simple(path, label, true, Optional.empty());
        }

        private record RowMatch(Row row, float score) {
        }
    }
}
