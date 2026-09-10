package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildPage;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.search.SFMTextMatchOptions;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/**
 * Optional resolver capability for a complete, immutable typed matching domain.
 *
 * <p>The returned data contains presentation entries and immediate-child
 * relations only. It must not eagerly load document bodies merely to make an
 * Explorer query complete.</p>
 */
public interface SFMExplorerFilterDomainResolver extends SFMExplorerResolver {
    int MAXIMUM_FILTER_DOMAIN_ENTRIES = 100_000;
    int DEFAULT_MAXIMUM_FILTER_MATCHES = 4_096;

    record FilterDomainRequest(
            SFMPath root,
            String query,
            int maximumMatches,
            long expectedResolverGeneration,
            SFMExplorerCancellationToken cancellation,
            SFMTextMatchOptions options
    ) {
        public FilterDomainRequest {
            Objects.requireNonNull(root, "root");
            query = Objects.requireNonNull(query, "query");
            if (query.isEmpty()) throw new IllegalArgumentException("Filter-domain query must not be blank");
            if (maximumMatches <= 0 || maximumMatches > MAXIMUM_FILTER_DOMAIN_ENTRIES) {
                throw new IllegalArgumentException("Invalid maximum filter match count");
            }
            if (expectedResolverGeneration < 0) {
                throw new IllegalArgumentException("Resolver generation must not be negative");
            }
            Objects.requireNonNull(cancellation, "cancellation");
            Objects.requireNonNull(options, "options");
        }

        /** Existing integrations requested fuzzy domains before typed modes existed. */
        public FilterDomainRequest(SFMPath root, String query, int maximumMatches,
                                   long expectedResolverGeneration, SFMExplorerCancellationToken cancellation) {
            this(root, query.strip(), maximumMatches, expectedResolverGeneration, cancellation, SFMTextMatchOptions.legacyFuzzy());
        }
    }

    record FilterDomain(
            SFMPath root,
            String query,
            List<SFMExplorerEntry> entries,
            List<SFMChildPage> childPages,
            Set<SFMPath> matchedPaths,
            int totalMatchCount,
            boolean complete,
            long resolverGeneration,
            List<String> diagnostics,
            SFMTextMatchOptions options,
            java.util.Map<SFMPath, SFMExplorerEntryMatch> matchEvidence
    ) {
        public FilterDomain {
            Objects.requireNonNull(root, "root");
            query = Objects.requireNonNull(query, "query");
            if (query.isEmpty()) throw new IllegalArgumentException("Filter-domain query must not be blank");
            entries = List.copyOf(entries);
            childPages = List.copyOf(childPages);
            matchedPaths = java.util.Collections.unmodifiableSet(new TreeSet<>(matchedPaths));
            if (totalMatchCount < matchedPaths.size()) {
                throw new IllegalArgumentException("Total filter matches cannot be below published matches");
            }
            if (complete && totalMatchCount != matchedPaths.size()) {
                throw new IllegalArgumentException("A complete filter domain publishes every match");
            }
            if (entries.size() > MAXIMUM_FILTER_DOMAIN_ENTRIES) {
                throw new IllegalArgumentException(
                        "Explorer filter domain exceeds " + MAXIMUM_FILTER_DOMAIN_ENTRIES + " entries"
                );
            }
            if (resolverGeneration < 0) {
                throw new IllegalArgumentException("Resolver generation must not be negative");
            }
            diagnostics = List.copyOf(diagnostics);
            Objects.requireNonNull(options, "options");
            matchEvidence = java.util.Map.copyOf(matchEvidence);
            var entriesByPath = entries.stream().collect(java.util.stream.Collectors.toMap(
                    SFMExplorerEntry::path, java.util.function.Function.identity(), (left, right) -> left));
            for (var supplied : matchEvidence.entrySet()) {
                var entry = entriesByPath.get(supplied.getKey());
                if (entry == null || supplied.getValue().matches() != matchedPaths.contains(supplied.getKey()))
                    throw new IllegalArgumentException("Prepared match evidence disagrees with the published domain");
                for (var fragment : supplied.getValue().labelFragments()) {
                    if (fragment.end() > entry.label().length())
                        throw new IllegalArgumentException("Prepared label match is outside the entry label");
                    for (int offset : new int[]{fragment.start(), fragment.end()}) {
                        if (offset > 0 && offset < entry.label().length()
                                && Character.isHighSurrogate(entry.label().charAt(offset - 1))
                                && Character.isLowSurrogate(entry.label().charAt(offset)))
                            throw new IllegalArgumentException("Prepared label match splits a Unicode code point");
                    }
                }
            }
        }

        public FilterDomain(SFMPath root, String query, List<SFMExplorerEntry> entries,
                            List<SFMChildPage> childPages, Set<SFMPath> matchedPaths, int totalMatchCount,
                            boolean complete, long resolverGeneration, List<String> diagnostics, SFMTextMatchOptions options) {
            this(root, query, entries, childPages, matchedPaths, totalMatchCount, complete,
                    resolverGeneration, diagnostics, options, java.util.Map.of());
        }

        public FilterDomain(SFMPath root, String query, List<SFMExplorerEntry> entries,
                            List<SFMChildPage> childPages, Set<SFMPath> matchedPaths, int totalMatchCount,
                            boolean complete, long resolverGeneration, List<String> diagnostics) {
            this(root, query.strip(), entries, childPages, matchedPaths, totalMatchCount, complete,
                    resolverGeneration, diagnostics, SFMTextMatchOptions.legacyFuzzy());
        }
    }

    CompletableFuture<FilterDomain> resolveFilterDomain(FilterDomainRequest request);
}
