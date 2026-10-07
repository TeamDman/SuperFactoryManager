package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.search.SFMTextMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Field-aware evidence so a path/metadata match does not invent a name highlight. */
public record SFMExplorerEntryMatch(boolean matches, float score, List<Field> fields,
                                    List<SFMTextMatcher.Fragment> labelFragments,
                                    boolean complete, List<String> diagnostics) {
    public enum Role { LABEL, PATH, SUBJECT_NAME, SEARCH_TERM }
    public record Field(Role role, int termIndex, String value, SFMTextMatcher.Match match) { }
    public SFMExplorerEntryMatch {
        fields = List.copyOf(fields);
        labelFragments = List.copyOf(labelFragments);
        diagnostics = List.copyOf(diagnostics);
    }
    public static SFMExplorerEntryMatch evaluate(SFMExplorerEntry entry, SFMTextMatcher matcher) {
        return evaluate(entry, matcher, new ca.teamdman.sfm.client.search.SFMMatchBudget(SFMTextMatcher.MAX_COMPARISONS));
    }
    public static SFMExplorerEntryMatch evaluate(SFMExplorerEntry entry, SFMTextMatcher matcher,
            ca.teamdman.sfm.client.search.SFMMatchBudget budget) {
        Objects.requireNonNull(entry);
        Objects.requireNonNull(matcher);
        var fields = new ArrayList<Field>();
        var fragments = new ArrayList<SFMTextMatcher.Fragment>();
        var diagnostics = new ArrayList<String>();
        float score = Float.POSITIVE_INFINITY;
        String subject = entry.sortKey(SFMExplorerEntry.SUBJECT_NAME).value().orElse("");
        int maximumFields = 128;
        if (entry.searchTerms().size() > maximumFields) diagnostics.add("Search field limit exceeded; result is incomplete");
        for (int index = 0; index < Math.min(maximumFields, entry.searchTerms().size()); index++) {
            String term = entry.searchTerms().get(index);
            try {
                var match = matcher.match(term, budget);
                if (!match.matches()) continue;
                Role role = term.equals(entry.label()) ? Role.LABEL : term.equals(entry.path().canonical()) ? Role.PATH
                        : term.equals(subject) ? Role.SUBJECT_NAME : Role.SEARCH_TERM;
                fields.add(new Field(role, index, term, match));
                score = Math.min(score, match.score());
                // A provider may search a semantic basename while decorating the displayed label.
                // Translate only a complete verbatim occurrence of that field, never a guessed suffix.
                for (int start = term.isEmpty() ? -1 : entry.label().indexOf(term); start >= 0;
                     start = entry.label().indexOf(term, start + term.length())) {
                    for (var fragment : match.fragments()) {
                        if (fragments.size() == SFMTextMatcher.MAX_FRAGMENTS) {
                            throw new SFMTextMatcher.LimitExceeded("Label fragment limit exceeded");
                        }
                        fragments.add(new SFMTextMatcher.Fragment(start + fragment.start(), start + fragment.end()));
                    }
                }
            } catch (SFMTextMatcher.LimitExceeded failure) {
                diagnostics.add("Search term " + index + ": " + failure.getMessage());
                if (budget.exhausted()) break;
            }
        }
        return new SFMExplorerEntryMatch(!fields.isEmpty(), score, fields,
                fragments.stream().distinct().sorted(java.util.Comparator.comparingInt(SFMTextMatcher.Fragment::start)
                        .thenComparingInt(SFMTextMatcher.Fragment::end)).toList(), diagnostics.isEmpty(), diagnostics);
    }
}
