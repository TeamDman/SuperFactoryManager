package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMChildPage;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Immutable, deterministic diagnostic projection of one visible Explorer row. */
public record SFMExplorerRowInspection(
        SFMExplorerId explorerId,
        SFMPath rowAddress,
        String rowLabel,
        SFMExplorerProjection.RowKind rowKind,
        SFMExplorerProjection.FilterRole filterRole,
        int rowDepth,
        boolean root,
        boolean expandable,
        boolean expanded,
        boolean selected,
        boolean explorerFocused,
        String keyboardFocus,
        SFMExplorerProjection.Settings settings,
        long relationRevision,
        long relationStatusGeneration,
        int publishedChildCount,
        Optional<PageEvidence> page,
        List<LoadingEvidence> loading,
        List<String> entryDiagnostics,
        Optional<SelectionEvidence> selectionEvidence,
        List<SFMPath> compactSegments
) {
    public static final String SCHEMA = "sfm.explorer-row-details/1";

    public record SelectionEvidence(String id, long revision, Optional<SFMPath> primary,
                                    Optional<SFMPath> anchor, java.util.Set<SFMPath> members) {
        public SelectionEvidence { members = java.util.Set.copyOf(members); }
    }

    public SFMExplorerRowInspection(SFMExplorerId explorerId, SFMPath rowAddress, String rowLabel,
            SFMExplorerProjection.RowKind rowKind, SFMExplorerProjection.FilterRole filterRole, int rowDepth,
            boolean root, boolean expandable, boolean expanded, boolean selected, boolean explorerFocused,
            String keyboardFocus, SFMExplorerProjection.Settings settings, long relationRevision,
            long relationStatusGeneration, int publishedChildCount, Optional<PageEvidence> page,
            List<LoadingEvidence> loading, List<String> entryDiagnostics) {
        this(explorerId, rowAddress, rowLabel, rowKind, filterRole, rowDepth, root, expandable, expanded,
                selected, explorerFocused, keyboardFocus, settings, relationRevision, relationStatusGeneration,
                publishedChildCount, page, loading, entryDiagnostics, Optional.empty(), List.of(rowAddress));
    }

    public record PageEvidence(
            long resolverGeneration,
            Optional<String> continuation,
            SFMChildPage.Completeness completeness,
            SFMChildRelationRepository.PageState.Materialization materialization,
            long lastRequestId,
            List<String> diagnostics
    ) {
        public PageEvidence {
            if (resolverGeneration < 0) throw new IllegalArgumentException("Resolver generation must not be negative");
            Objects.requireNonNull(continuation, "continuation");
            Objects.requireNonNull(completeness, "completeness");
            Objects.requireNonNull(materialization, "materialization");
            // Complete query projections publish page evidence with request 0;
            // this is not an in-flight child-page request and must remain inspectable.
            if (lastRequestId < 0) throw new IllegalArgumentException("Last request id must not be negative");
            diagnostics = List.copyOf(diagnostics);
        }

        static PageEvidence capture(SFMChildRelationRepository.PageState page) {
            return new PageEvidence(
                    page.resolverGeneration(),
                    page.continuation(),
                    page.completeness(),
                    page.materialization(),
                    page.lastRequestId(),
                    page.diagnostics()
            );
        }
    }

    public record LoadingEvidence(
            long requestId,
            SFMChildRelationRepository.RequestMode mode,
            SFMPath parent,
            String resolverScheme,
            long resolverGeneration,
            Optional<String> continuation,
            int pageSize,
            boolean targetsRow
    ) {
        public LoadingEvidence {
            if (requestId <= 0) throw new IllegalArgumentException("Request id must be positive");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(resolverScheme, "resolverScheme");
            if (resolverGeneration < 0) throw new IllegalArgumentException("Resolver generation must not be negative");
            Objects.requireNonNull(continuation, "continuation");
            if (pageSize <= 0) throw new IllegalArgumentException("Page size must be positive");
        }

        static LoadingEvidence capture(
                SFMLazyExplorerLoader.RequestEvidence request,
                SFMPath rowAddress
        ) {
            return new LoadingEvidence(
                    request.relationRequestId(),
                    request.mode(),
                    request.parent(),
                    request.resolverScheme(),
                    request.resolverGeneration(),
                    request.continuation(),
                    request.pageSize(),
                    request.parent().equals(rowAddress)
            );
        }
    }

    public SFMExplorerRowInspection {
        Objects.requireNonNull(explorerId, "explorerId");
        Objects.requireNonNull(rowAddress, "rowAddress");
        rowLabel = Objects.requireNonNull(rowLabel, "rowLabel");
        Objects.requireNonNull(rowKind, "rowKind");
        Objects.requireNonNull(filterRole, "filterRole");
        if (rowDepth < 0) throw new IllegalArgumentException("Row depth must not be negative");
        keyboardFocus = Objects.requireNonNull(keyboardFocus, "keyboardFocus");
        Objects.requireNonNull(settings, "settings");
        if (relationRevision < 0 || relationStatusGeneration < 0) {
            throw new IllegalArgumentException("Relation generations must not be negative");
        }
        if (publishedChildCount < 0) throw new IllegalArgumentException("Published child count must not be negative");
        Objects.requireNonNull(page, "page");
        loading = List.copyOf(loading);
        entryDiagnostics = List.copyOf(entryDiagnostics);
        Objects.requireNonNull(selectionEvidence);
        compactSegments = List.copyOf(compactSegments);
    }

    public static SFMExplorerRowInspection capture(
            SFMExplorerSession.Snapshot session,
            SFMExplorerProjection.Row row,
            SFMChildRelationRepository.Snapshot relations,
            List<SFMExplorerSession.RequestObservation> activeRequests,
            boolean explorerFocused,
            String keyboardFocus
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(relations, "relations");
        Objects.requireNonNull(activeRequests, "activeRequests");
        SFMPath address = row.path();
        Optional<PageEvidence> page = Optional.ofNullable(relations.pageStates().get(address))
                .map(PageEvidence::capture);
        List<LoadingEvidence> loading = activeRequests.stream()
                .map(SFMExplorerSession.RequestObservation::evidence)
                .map(request -> LoadingEvidence.capture(request, address))
                .sorted(Comparator.comparingLong(LoadingEvidence::requestId))
                .toList();
        return new SFMExplorerRowInspection(
                session.id(),
                address,
                row.compactLabel(),
                row.rowKind(),
                row.filterRole(),
                row.depth(),
                row.root(),
                row.entry().expandable(),
                row.expanded(),
                row.paths().stream().anyMatch(session.selectedPaths()::contains),
                explorerFocused,
                keyboardFocus,
                session.settings(),
                relations.relation().id(),
                relations.statusGeneration(),
                relations.relation().childrenOf(address).size(),
                page,
                loading,
                row.entry().diagnostics(),
                session.rowSelection().map(value -> new SelectionEvidence(value.selectionId().value(), value.id(),
                        session.navigationCursor(), session.rangeAnchor(), value.members())),
                row.paths()
        );
    }

    public String detailsPayload() {
        StringBuilder answer = new StringBuilder();
        line(answer, "schema", SCHEMA);
        line(answer, "explorer-id", quote(explorerId.value()));
        line(answer, "row-address", quote(rowAddress.canonical()));
        line(answer, "row-label", quote(rowLabel));
        line(answer, "row-kind", wire(rowKind));
        line(answer, "filter-role", wire(filterRole));
        line(answer, "row-depth", rowDepth);
        line(answer, "row-root", root);
        line(answer, "row-expandable", expandable);
        line(answer, "row-expanded", expanded);
        line(answer, "row-loading", rowKind == SFMExplorerProjection.RowKind.LOADING);
        line(answer, "row-selected", selected);
        selectionEvidence.ifPresent(value -> {
            line(answer, "selection-id", quote(value.id()));
            line(answer, "selection-revision", value.revision());
            line(answer, "selection-primary", optional(value.primary().map(SFMPath::canonical)));
            line(answer, "selection-anchor", optional(value.anchor().map(SFMPath::canonical)));
            line(answer, "selection-count", value.members().size());
            line(answer, "row-is-primary", value.primary().equals(Optional.of(rowAddress)));
            line(answer, "row-action-target", "explicit row; not an implicit bulk operation");
            var paths = value.members().stream().sorted().limit(256).toList();
            for (int i = 0; i < paths.size(); i++) line(answer, "selection-member[" + i + "]", quote(paths.get(i).canonical()));
            line(answer, "selection-members-truncated", paths.size() < value.members().size());
        });
        line(answer, "explorer-focused", explorerFocused);
        line(answer, "keyboard-focus", quote(keyboardFocus));
        line(answer, "query", quote(settings.filterQuery()));
        line(answer, "filter.match-mode", wire(settings.filterOptions().mode()));
        line(answer, "filter.match-case", settings.filterOptions().matchCase());
        line(answer, "filter.whole-word", settings.filterOptions().wholeWord());
        line(answer, "filter.dot-all", settings.filterOptions().dotAll());
        line(answer, "view", wire(settings.view()));
        line(answer, "sort", wire(settings.sort()));
        line(answer, "group", wire(settings.group()));
        line(answer, "hoist", wire(settings.hoist()));
        line(answer, "path-display", wire(settings.pathDisplay()));
        line(answer, "compact.enabled", settings.compaction().enabled());
        line(answer, "compact.override-count", settings.compaction().overrides().size());
        line(answer, "compact.segment-count", compactSegments.size());
        for (int i = 0; i < compactSegments.size(); i++) {
            var path = compactSegments.get(i);
            line(answer, "compact.segment[" + i + "].path", quote(path.canonical()));
            line(answer, "compact.segment[" + i + "].override", settings.compaction().overrides().containsKey(path)
                    ? settings.compaction().overrides().get(path).toString() : "default");
        }
        line(answer, "relation-revision", relationRevision);
        line(answer, "relation-status-generation", relationStatusGeneration);
        line(answer, "published-child-count", publishedChildCount);
        line(answer, "page-available", page.isPresent());
        page.ifPresent(value -> {
            line(answer, "page-resolver-generation", value.resolverGeneration());
            line(answer, "page-materialization", wire(value.materialization()));
            line(answer, "page-completeness", wire(value.completeness()));
            line(answer, "page-continuation", optional(value.continuation()));
            line(answer, "page-last-request-id", value.lastRequestId());
            line(answer, "page-diagnostic-count", value.diagnostics().size());
            for (int index = 0; index < value.diagnostics().size(); index++) {
                line(answer, "page-diagnostic[" + index + "]", quote(value.diagnostics().get(index)));
            }
        });
        line(answer, "loading-active-request-count", loading.size());
        for (int index = 0; index < loading.size(); index++) {
            LoadingEvidence request = loading.get(index);
            String prefix = "loading-request[" + index + "].";
            line(answer, prefix + "id", request.requestId());
            line(answer, prefix + "mode", wire(request.mode()));
            line(answer, prefix + "parent", quote(request.parent().canonical()));
            line(answer, prefix + "resolver-scheme", quote(request.resolverScheme()));
            line(answer, prefix + "resolver-generation", request.resolverGeneration());
            line(answer, prefix + "continuation", optional(request.continuation()));
            line(answer, prefix + "page-size", request.pageSize());
            line(answer, prefix + "targets-row", request.targetsRow());
        }
        line(answer, "entry-diagnostic-count", entryDiagnostics.size());
        for (int index = 0; index < entryDiagnostics.size(); index++) {
            line(answer, "entry-diagnostic[" + index + "]", quote(entryDiagnostics.get(index)));
        }
        return answer.toString();
    }

    private static void line(StringBuilder target, String key, Object value) {
        if (!target.isEmpty()) target.append('\n');
        target.append(key).append(": ").append(value);
    }

    private static String optional(Optional<String> value) {
        return value.map(SFMExplorerRowInspection::quote).orElse("none");
    }

    private static String wire(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String quote(String value) {
        StringBuilder answer = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> answer.append("\\\"");
                case '\\' -> answer.append("\\\\");
                case '\b' -> answer.append("\\b");
                case '\f' -> answer.append("\\f");
                case '\n' -> answer.append("\\n");
                case '\r' -> answer.append("\\r");
                case '\t' -> answer.append("\\t");
                default -> {
                    if (character < 0x20) answer.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    else answer.append(character);
                }
            }
        }
        return answer.append('"').toString();
    }
}
