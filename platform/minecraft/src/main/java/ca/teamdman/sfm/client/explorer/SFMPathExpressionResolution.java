package ca.teamdman.sfm.client.explorer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable evidence from one path-expression evaluation. */
public record SFMPathExpressionResolution(
        String expression,
        long selectionGeneration,
        long relationRevisionId,
        long relationStatusGeneration,
        Completeness completeness,
        Set<SFMPath> paths,
        Map<SFMSelectionId, Long> capturedSelectionHeads,
        List<SFMSelectorResolution.Diagnostic> diagnostics
) {
    public enum Completeness {
        COMPLETE,
        PARTIAL,
        INVALID
    }

    public SFMPathExpressionResolution {
        Objects.requireNonNull(expression, "expression");
        if (selectionGeneration < 0 || relationRevisionId < 0 || relationStatusGeneration < 0) {
            throw new IllegalArgumentException("Captured generations and revisions must not be negative");
        }
        Objects.requireNonNull(completeness, "completeness");
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(capturedSelectionHeads, "capturedSelectionHeads");
        Objects.requireNonNull(diagnostics, "diagnostics");
        paths = Collections.unmodifiableSet(new TreeSet<>(paths));
        TreeMap<SFMSelectionId, Long> heads = new TreeMap<>(
                java.util.Comparator.comparing(SFMSelectionId::value)
        );
        heads.putAll(capturedSelectionHeads);
        capturedSelectionHeads = Collections.unmodifiableMap(heads);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean complete() {
        return completeness == Completeness.COMPLETE;
    }
}
