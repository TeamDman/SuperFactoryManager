package ca.teamdman.sfm.client.explorer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One bounded immediate-child resolver page. */
public record SFMChildPage(
        SFMPath parent,
        List<SFMChildEdge> edges,
        Optional<String> continuation,
        Completeness completeness,
        long resolverGeneration,
        List<String> diagnostics
) {
    public enum Completeness {
        COMPLETE,
        PARTIAL
    }

    public SFMChildPage {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(continuation, "continuation");
        Objects.requireNonNull(completeness, "completeness");
        Objects.requireNonNull(diagnostics, "diagnostics");
        edges = List.copyOf(edges);
        diagnostics = List.copyOf(diagnostics);
        if (resolverGeneration < 0) throw new IllegalArgumentException("Resolver generation must not be negative");
        if (edges.stream().anyMatch(edge -> !edge.parent().equals(parent))) {
            throw new IllegalArgumentException("Every edge in a page must use the page parent");
        }
        if (continuation.isPresent() != (completeness == Completeness.PARTIAL)) {
            throw new IllegalArgumentException("Only partial pages carry continuation tokens");
        }
        continuation.ifPresent(value -> {
            SFMCanonicalText.requireValidUnicode(value, "relation.invalid-continuation");
            if (value.isEmpty()) throw new IllegalArgumentException("Continuation token must not be empty");
        });
    }
}
