package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildEdge;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.search.SFMTextMatchOptions;
import ca.teamdman.sfm.client.search.SFMTextMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
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

    /** Explains why a row is present while a filter is active. */
    public enum FilterRole {
        NONE,
        MATCH,
        CONTEXT_ANCESTOR,
        CONTEXT_DESCENDANT
    }

    /** Distinguishes semantic resolver entries from transient projection feedback. */
    public enum RowKind {
        ENTRY,
        LOADING
    }

    public record Settings(
            View view,
            Sort sort,
            Group group,
            Hoist hoist,
            PathDisplay pathDisplay,
            String filterQuery,
            SFMTextMatchOptions filterOptions,
            SFMExplorerCompaction.Options compaction
    ) {
        public Settings {
            Objects.requireNonNull(view, "view");
            Objects.requireNonNull(sort, "sort");
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(hoist, "hoist");
            Objects.requireNonNull(pathDisplay, "pathDisplay");
            filterQuery = Objects.requireNonNull(filterQuery, "filterQuery");
            Objects.requireNonNull(filterOptions, "filterOptions");
            Objects.requireNonNull(compaction, "compaction");
        }

        public Settings(View view, Sort sort, Group group, Hoist hoist, PathDisplay pathDisplay,
                        String filterQuery, SFMTextMatchOptions filterOptions) {
            this(view, sort, group, hoist, pathDisplay, filterQuery, filterOptions, SFMExplorerCompaction.Options.defaults());
        }

        public Settings withCompaction(SFMExplorerCompaction.Options value) {
            return new Settings(view, sort, group, hoist, pathDisplay, filterQuery, filterOptions, value);
        }

        /** Old projections explicitly retain their fuzzy predicate. New UI uses defaults(). */
        public Settings(View view, Sort sort, Group group, Hoist hoist, PathDisplay pathDisplay, String filterQuery) {
            this(view, sort, group, hoist, pathDisplay, filterQuery.strip(), SFMTextMatchOptions.legacyFuzzy());
        }

        /** Source-compatible constructor for the projection axes that predate X-8b. */
        public Settings(View view, Sort sort, Group group, Hoist hoist) {
            this(view, sort, group, hoist, PathDisplay.NAME, "", SFMTextMatchOptions.defaults());
        }

        public static Settings defaults() {
            return new Settings(View.LIST, Sort.NAME, Group.HIERARCHY, Hoist.AUTO, PathDisplay.NAME, "",
                    SFMTextMatchOptions.defaults());
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
            SFMExplorerEntry.SortKey activeSortKey,
            FilterRole filterRole,
            RowKind rowKind,
            List<SFMExplorerEntry> segments
    ) {
        public Row {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(entry, "entry");
            if (!path.equals(entry.path())) {
                throw new IllegalArgumentException("Projection row path and entry must agree");
            }
            if (depth < 0) throw new IllegalArgumentException("Projection depth must not be negative");
            Objects.requireNonNull(activeSortKey, "activeSortKey");
            Objects.requireNonNull(filterRole, "filterRole");
            Objects.requireNonNull(rowKind, "rowKind");
            segments = List.copyOf(segments);
            if (segments.isEmpty() || !segments.get(segments.size() - 1).equals(entry)
                    || segments.stream().map(SFMExplorerEntry::path).distinct().count() != segments.size())
                throw new IllegalArgumentException("Compact segments must be unique and end at the row entry");
        }

        public Row(SFMPath path, SFMExplorerEntry entry, int depth, boolean root, boolean expanded,
                   SFMExplorerEntry.SortKey activeSortKey, FilterRole filterRole, RowKind rowKind) {
            this(path, entry, depth, root, expanded, activeSortKey, filterRole, rowKind, List.of(entry));
        }

        public boolean contains(SFMPath target) { return segments.stream().anyMatch(segment -> segment.path().equals(target)); }
        public List<SFMPath> paths() { return segments.stream().map(SFMExplorerEntry::path).toList(); }
        public String compactLabel() { return String.join("/", segments.stream().map(SFMExplorerEntry::label).toList()); }

        /** Source-compatible constructor for semantic rows. */
        public Row(
                SFMPath path,
                SFMExplorerEntry entry,
                int depth,
                boolean root,
                boolean expanded,
                SFMExplorerEntry.SortKey activeSortKey,
                FilterRole filterRole
        ) {
            this(path, entry, depth, root, expanded, activeSortKey, filterRole, RowKind.ENTRY);
        }

        /** Source-compatible constructor for callers that create unfiltered rows. */
        public Row(
                SFMPath path,
                SFMExplorerEntry entry,
                int depth,
                boolean root,
                boolean expanded,
                SFMExplorerEntry.SortKey activeSortKey
        ) {
            this(path, entry, depth, root, expanded, activeSortKey, FilterRole.NONE, RowKind.ENTRY);
        }

        public boolean filterMatch() {
            return filterRole == FilterRole.MATCH;
        }

        public boolean filterContextAncestor() {
            return filterRole == FilterRole.CONTEXT_ANCESTOR;
        }

        public boolean filterContextDescendant() {
            return filterRole == FilterRole.CONTEXT_DESCENDANT;
        }

        public boolean loading() {
            return rowKind == RowKind.LOADING;
        }
    }

    public record Result(
            Settings settings,
            long relationRevision,
            List<Row> rows,
            List<String> diagnostics,
            FilterEvidence filter,
            Map<SFMPath, MatchEvidence> matchEvidence
    ) {
        public Result {
            Objects.requireNonNull(settings, "settings");
            if (relationRevision < 0) {
                throw new IllegalArgumentException("Relation revision must not be negative");
            }
            rows = List.copyOf(rows);
            diagnostics = List.copyOf(diagnostics);
            Objects.requireNonNull(filter, "filter");
            matchEvidence = Map.copyOf(matchEvidence);
        }

        public Result(Settings settings, long relationRevision, List<Row> rows,
                      List<String> diagnostics, FilterEvidence filter) {
            this(settings, relationRevision, rows, diagnostics, filter, Map.of());
        }

        /** Domain bounds survive local projection; a truncated index is never a complete empty result. */
        public Result withDomainEvidence(boolean complete, List<String> domainDiagnostics) {
            var messages = java.util.stream.Stream.concat(diagnostics.stream(), domainDiagnostics.stream())
                    .distinct().limit(128).toList();
            var evidence = new FilterEvidence(filter.query(), filter.candidateCount(), filter.matchCount(),
                    filter.visibleRowCount(), filter.contextAncestorCount(), filter.contextDescendantCount(),
                    filter.incompleteMaterialization() || !complete);
            return new Result(settings, relationRevision, rows, messages, evidence, matchEvidence);
        }
    }

    /** Self membership and descendant knowledge are independent, including incomplete lazy subtrees. */
    public record MatchEvidence(SFMExplorerEntryMatch self, boolean descendantMatch, boolean descendantsComplete) {
        public MatchEvidence { Objects.requireNonNull(self, "self"); }
    }

    /** Evidence that filtering remained local to the currently published lazy relation. */
    public record FilterEvidence(
            String query,
            int candidateCount,
            int matchCount,
            int visibleRowCount,
            int contextAncestorCount,
            int contextDescendantCount,
            boolean incompleteMaterialization
    ) {
        public FilterEvidence {
            query = Objects.requireNonNull(query, "query");
            if (candidateCount < 0 || matchCount < 0 || matchCount > candidateCount) {
                throw new IllegalArgumentException("Invalid explorer filter counts");
            }
            if (visibleRowCount < 0 || contextAncestorCount < 0 || contextDescendantCount < 0
                    || visibleRowCount != matchCount + contextAncestorCount + contextDescendantCount) {
                throw new IllegalArgumentException("Invalid explorer filter projection counts");
            }
        }

        /** Source-compatible constructor for projections without descendant context. */
        public FilterEvidence(
                String query,
                int candidateCount,
                int matchCount,
                int visibleRowCount,
                int contextAncestorCount,
                boolean incompleteMaterialization
        ) {
            this(
                    query,
                    candidateCount,
                    matchCount,
                    visibleRowCount,
                    contextAncestorCount,
                    0,
                    incompleteMaterialization
            );
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
        return project(session, relations, entries, Map.of());
    }

    public static Result project(SFMExplorerSession.Snapshot session,
                                 SFMChildRelationRepository.Snapshot relations,
                                 Map<SFMPath, SFMExplorerEntry> entries,
                                 Map<SFMPath, SFMExplorerEntryMatch> preparedMatches) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(relations, "relations");
        Objects.requireNonNull(entries, "entries");
        Projection projection = new Projection(session, relations, entries, preparedMatches);
        return projection.project();
    }

    /** Build once per immutable relation snapshot, not once per visited node. */
    static Map<SFMPath, List<SFMPath>> indexChildren(Iterable<SFMChildEdge> edges) {
        HashMap<SFMPath, List<SFMPath>> children = new HashMap<>();
        for (SFMChildEdge edge : edges) {
            children.computeIfAbsent(edge.parent(), ignored -> new ArrayList<>()).add(edge.child());
        }
        children.replaceAll((parent, paths) -> List.copyOf(paths));
        return Map.copyOf(children);
    }

    private static final class Projection {
        private final SFMExplorerSession.Snapshot session;
        private final SFMChildRelationRepository.Snapshot relations;
        private final Map<SFMPath, SFMExplorerEntry> entries;
        private final Map<SFMPath, List<SFMPath>> childrenByParent;
        private final ArrayList<Row> rows = new ArrayList<>();
        private final ArrayList<String> diagnostics = new ArrayList<>();
        private boolean matchingIncomplete;
        private final Map<SFMPath, MatchEvidence> matchEvidence = new HashMap<>();
        private final Map<SFMPath, SFMExplorerEntryMatch> preparedMatches;
        private final ca.teamdman.sfm.client.search.SFMMatchBudget matchBudget = new ca.teamdman.sfm.client.search.SFMMatchBudget(1_000_000);

        private Projection(
                SFMExplorerSession.Snapshot session,
                SFMChildRelationRepository.Snapshot relations,
                Map<SFMPath, SFMExplorerEntry> entries,
                Map<SFMPath, SFMExplorerEntryMatch> preparedMatches
        ) {
            this.session = session;
            this.relations = relations;
            this.entries = entries;
            this.preparedMatches = Objects.requireNonNull(preparedMatches);
            this.childrenByParent = indexChildren(relations.relation().edges());
        }

        private List<SFMPath> childrenOf(SFMPath parent) {
            return childrenByParent.getOrDefault(parent, List.of());
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
                filter = inactiveFilterEvidence();
            } else {
                projectHierarchy(roots, showRoots);
                filter = inactiveFilterEvidence();
            }
            return new Result(
                    session.settings(),
                    relations.relation().id(),
                    session.settings().group() == Group.HIERARCHY
                            ? SFMExplorerCompaction.compact(rows, session.settings().compaction(), session.roots(), relations, childrenByParent, entries)
                            : rows,
                    diagnostics,
                    filter,
                    matchEvidence
            );
        }

        private FilterEvidence inactiveFilterEvidence() {
            return new FilterEvidence("", rows.size(), rows.size(), rows.size(), 0, false);
        }

        /**
         * Filtering ranks only the already-published relation. Hierarchy mode
         * emits each match with the materialized ancestor chain that locates
         * it; flat mode emits only matches. Neither path asks a resolver for
         * children or mutates persisted expansion state.
         */
        private FilterEvidence projectFilteredMaterialization(List<SFMPath> roots, boolean showRoots) {
            HashSet<SFMPath> visited = new HashSet<>();
            HashSet<SFMPath> ancestry = new HashSet<>();
            ArrayList<MaterializedNode> topLevel = new ArrayList<>();
            ArrayList<MaterializedNode> candidates = new ArrayList<>();
            for (SFMPath root : roots) {
                if (!visited.add(root)) continue;
                if (showRoots) {
                    MaterializedNode rootNode = new MaterializedNode(row(root, 0, true));
                    topLevel.add(rootNode);
                    candidates.add(rootNode);
                    addMaterializedChildren(root, rootNode, 1, visited, ancestry, topLevel, candidates);
                } else {
                    addMaterializedChildren(root, null, 0, visited, ancestry, topLevel, candidates);
                }
            }
            String query = session.settings().filterQuery();
            try {
                SFMTextMatcher matcher = SFMTextMatcher.compile(query, session.settings().filterOptions());
                topLevel.forEach(node -> score(node, matcher));
            } catch (SFMTextMatcher.LimitExceeded failure) {
                matchingIncomplete = true;
                diagnostics.add(failure.getMessage());
            }
            int matchCount = (int) candidates.stream().filter(MaterializedNode::matches).count();

            if (session.settings().group() == Group.NONE) {
                candidates.stream()
                        .filter(MaterializedNode::matches)
                        .sorted(Comparator
                                .comparingDouble((MaterializedNode node) -> node.directScore())
                                .thenComparing(node -> node.row().path().canonical()))
                        .map(this::flatFilteredRow)
                        .forEach(rows::add);
            } else {
                topLevel.stream()
                        .filter(MaterializedNode::included)
                        .sorted(filteredSubtreeComparator())
                        .forEach(this::emitFilteredHierarchy);
            }
            int contextAncestorCount = (int) rows.stream()
                    .filter(Row::filterContextAncestor)
                    .count();
            int contextDescendantCount = (int) rows.stream()
                    .filter(Row::filterContextDescendant)
                    .count();
            return new FilterEvidence(
                    query,
                    candidates.size(),
                    matchCount,
                    rows.size(),
                    contextAncestorCount,
                    contextDescendantCount,
                    matchingIncomplete || materializationIsIncomplete(visited)
            );
        }

        private void addMaterializedChildren(
                SFMPath parent,
                MaterializedNode visibleParent,
                int depth,
                Set<SFMPath> visited,
                Set<SFMPath> ancestry,
                List<MaterializedNode> topLevel,
                List<MaterializedNode> candidates
        ) {
            if (!ancestry.add(parent)) {
                diagnostics.add("cycle suppressed below " + parent.canonical());
                return;
            }
            for (SFMPath child : sorted(childrenOf(parent))) {
                if (ancestry.contains(child)) {
                    diagnostics.add("cycle suppressed below " + parent.canonical()
                            + " through " + child.canonical());
                    continue;
                }
                if (!visited.add(child)) continue;
                MaterializedNode childNode = new MaterializedNode(row(child, depth, false));
                candidates.add(childNode);
                if (visibleParent == null) topLevel.add(childNode);
                else visibleParent.children().add(childNode);
                addMaterializedChildren(
                        child,
                        childNode,
                        depth + 1,
                        visited,
                        ancestry,
                        topLevel,
                        candidates
                );
            }
            ancestry.remove(parent);
        }

        private float score(MaterializedNode node, SFMTextMatcher matcher) {
            SFMExplorerEntryMatch match = preparedMatches.get(node.row().path());
            if (match == null) match = SFMExplorerEntryMatch.evaluate(node.row().entry(), matcher, matchBudget);
            if (!match.complete()) {
                matchingIncomplete = true;
                if (diagnostics.size() < 64) diagnostics.addAll(match.diagnostics());
            }
            float directScore = match.score();
            node.directScore(directScore);
            node.matches(match.matches());
            float best = node.matches() ? directScore : Float.POSITIVE_INFINITY;
            for (MaterializedNode child : node.children()) best = Math.min(best, score(child, matcher));
            boolean descendantMatch = node.children().stream().anyMatch(MaterializedNode::included);
            boolean descendantsComplete = !materializationIsIncomplete(Set.of(node.row().path()))
                    && node.children().stream().allMatch(child -> {
                        var evidence = matchEvidence.get(child.row().path());
                        return evidence != null && evidence.self().complete() && evidence.descendantsComplete();
                    });
            matchEvidence.put(node.row().path(), new MatchEvidence(match, descendantMatch, descendantsComplete));
            node.bestDescendantScore(best);
            return best;
        }

        private Comparator<MaterializedNode> filteredSubtreeComparator() {
            return Comparator
                    .comparingDouble((MaterializedNode node) -> node.bestDescendantScore())
                    .thenComparing(node -> node.row().path().canonical());
        }

        private void emitFilteredHierarchy(MaterializedNode node) {
            List<MaterializedNode> includedChildren = node.children().stream()
                    .filter(MaterializedNode::included)
                    .sorted(filteredSubtreeComparator())
                    .toList();
            boolean explicitMatchExpansion = node.matches()
                    && session.expanded().contains(node.row().path());
            Row source = node.row();
            rows.add(new Row(
                    source.path(),
                    source.entry(),
                    source.depth(),
                    source.root(),
                    explicitMatchExpansion || !includedChildren.isEmpty(),
                    source.activeSortKey(),
                    node.matches() ? FilterRole.MATCH : FilterRole.CONTEXT_ANCESTOR
            ));
            if (!explicitMatchExpansion) {
                includedChildren.forEach(this::emitFilteredHierarchy);
                return;
            }

            // Filtering locates compact matching rows by default. Once the user
            // explicitly expands one direct match, its immediate children are
            // valuable context even when their own labels do not match. Do not
            // recursively force-open those contextual descendants.
            node.children().stream()
                    .sorted(filteredSubtreeComparator())
                    .forEach(child -> {
                        if (child.included()) emitFilteredHierarchy(child);
                        else emitContextDescendant(child);
                    });
        }

        private void emitContextDescendant(MaterializedNode node) {
            Row source = node.row();
            boolean expanded = session.expanded().contains(source.path());
            rows.add(new Row(
                    source.path(),
                    source.entry(),
                    source.depth(),
                    source.root(),
                    expanded,
                    source.activeSortKey(),
                    FilterRole.CONTEXT_DESCENDANT
            ));
            if (expanded) {
                node.children().stream().sorted(filteredSubtreeComparator()).forEach(child -> {
                    if (child.included()) emitFilteredHierarchy(child);
                    else emitContextDescendant(child);
                });
            }
        }

        private Row flatFilteredRow(MaterializedNode node) {
            Row source = node.row();
            return new Row(
                    source.path(),
                    source.entry(),
                    0,
                    source.root(),
                    source.expanded(),
                    source.activeSortKey(),
                    FilterRole.MATCH
            );
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
                    || !childrenOf(root).isEmpty());
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
                    for (SFMPath child : sorted(childrenOf(root))) {
                        addRow(child, 0, false);
                        if (descend(child)) {
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
            for (SFMPath child : sorted(childrenOf(parent))) {
                if (ancestry.contains(child)) {
                    diagnostics.add("cycle suppressed below " + parent.canonical());
                    continue;
                }
                addRow(child, depth, false);
                if (descend(child)) {
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

        private boolean descend(SFMPath parent) {
            if (session.expanded().contains(parent)) return true;
            var children = childrenOf(parent);
            return children.size() == 1 && SFMExplorerCompaction.canJoin(parent, children.get(0),
                    session.settings().compaction(), session.roots(), relations, childrenByParent, entries);
        }

        private void collectDescendants(
                SFMPath parent,
                Set<SFMPath> answer,
                Set<SFMPath> ancestry
        ) {
            if (!ancestry.add(parent)) return;
            for (SFMPath child : childrenOf(parent)) {
                if (answer.add(child)) collectDescendants(child, answer, ancestry);
            }
            ancestry.remove(parent);
        }

        private void addRow(SFMPath path, int depth, boolean root) {
            rows.add(row(path, depth, root));
        }

        private Row row(SFMPath path, int depth, boolean root) {
            SFMExplorerEntry entry = entries.getOrDefault(path, fallback(path));
            SFMExplorerEntry.SortKey active = entry.sortKey(session.settings().sort().contributionId());
            active.unavailableReason().ifPresent(reason -> diagnostics.add(
                    path.canonical() + ": " + reason
            ));
            diagnostics.addAll(entry.diagnostics());
            return new Row(
                    path,
                    entry,
                    depth,
                    root,
                    session.expanded().contains(path),
                    active,
                    FilterRole.NONE
            );
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
            SFMExplorerEntry known = entries.get(path);
            return known != null ? known : fallback(path);
        }

        private static SFMExplorerEntry fallback(SFMPath path) {
            String label = path.segments().isEmpty()
                    ? path.authority()
                    : path.segments().get(path.segments().size() - 1);
            if (label.isEmpty()) label = path.canonical();
            return SFMExplorerEntry.simple(path, label, true, Optional.empty());
        }

        private static final class MaterializedNode {
            private final Row row;
            private final ArrayList<MaterializedNode> children = new ArrayList<>();
            private float directScore = Float.POSITIVE_INFINITY;
            private float bestDescendantScore = Float.POSITIVE_INFINITY;
            private boolean matches;

            private MaterializedNode(Row row) {
                this.row = row;
            }

            private Row row() {
                return row;
            }

            private ArrayList<MaterializedNode> children() {
                return children;
            }

            private float directScore() {
                return directScore;
            }

            private void directScore(float directScore) {
                this.directScore = directScore;
            }

            private float bestDescendantScore() {
                return bestDescendantScore;
            }

            private void bestDescendantScore(float bestDescendantScore) {
                this.bestDescendantScore = bestDescendantScore;
            }

            private boolean matches() {
                return matches;
            }

            private void matches(boolean matches) {
                this.matches = matches;
            }

            private boolean included() {
                return Float.isFinite(bestDescendantScore);
            }
        }
    }
}
