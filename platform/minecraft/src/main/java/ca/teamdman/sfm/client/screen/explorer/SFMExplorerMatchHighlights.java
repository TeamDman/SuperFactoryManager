package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntryMatch;
import ca.teamdman.sfm.client.search.SFMTextMatcher;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMColourRole;

import java.util.*;

/** One disjoint pass classifies each displayed code point; overlap is never double-painted. */
final class SFMExplorerMatchHighlights {
    enum Membership { NONE, FIND, FILTER, BOTH }
    record Run(int start, int end, Membership membership) { }

    static List<Run> runs(String label, List<SFMTextMatcher.Fragment> find,
                          List<SFMTextMatcher.Fragment> filter) {
        var findBits = mask(label, find);
        var filterBits = mask(label, filter);
        var answer = new ArrayList<Run>();
        int start = 0;
        Membership previous = null;
        for (int i = 0; i < label.length(); i += Character.charCount(label.codePointAt(i))) {
            Membership membership = findBits.get(i) ? filterBits.get(i) ? Membership.BOTH : Membership.FIND
                    : filterBits.get(i) ? Membership.FILTER : Membership.NONE;
            if (previous != null && membership != previous) { answer.add(new Run(start, i, previous)); start = i; }
            previous = membership;
        }
        if (previous != null) answer.add(new Run(start, label.length(), previous));
        return List.copyOf(answer);
    }

    private static BitSet mask(String label, List<SFMTextMatcher.Fragment> fragments) {
        var bits = new BitSet(label.length());
        for (var fragment : fragments) {
            int start = Math.min(label.length(), fragment.start());
            int end = Math.min(label.length(), fragment.end());
            if (start < end) bits.set(start, end);
        }
        return bits;
    }

    /** Translate only exact field/label occurrences, including a decorated context prefix. */
    static List<SFMTextMatcher.Fragment> project(String label, SFMExplorerEntryMatch evidence) {
        if (evidence == null) return List.of();
        var fragments = new ArrayList<SFMTextMatcher.Fragment>();
        for (var field : evidence.fields()) {
            if (field.value().isEmpty()) continue;
            for (int at = label.indexOf(field.value()); at >= 0; at = label.indexOf(field.value(), at + field.value().length())) {
                for (var fragment : field.match().fragments()) {
                    if (fragments.size() >= SFMTextMatcher.MAX_FRAGMENTS) return List.copyOf(fragments);
                    if (fragment.end() <= field.value().length())
                        fragments.add(new SFMTextMatcher.Fragment(at + fragment.start(), at + fragment.end()));
                }
            }
        }
        return List.copyOf(fragments);
    }

    /** Scope fragments to their segment so repeated names do not cross-highlight aliases. */
    static List<SFMTextMatcher.Fragment> project(String label, ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row row,
            java.util.function.Function<ca.teamdman.sfm.client.explorer.SFMPath, SFMExplorerEntryMatch> evidence) {
        if (row.segments().size() == 1) return project(label, evidence.apply(row.path()));
        String compact = row.compactLabel();
        int origin = label.indexOf(compact);
        var result = new ArrayList<SFMTextMatcher.Fragment>();
        if (origin >= 0) {
            int offset = origin;
            for (var segment : row.segments()) {
                for (var fragment : project(segment.label(), evidence.apply(segment.path())))
                    if (result.size() < SFMTextMatcher.MAX_FRAGMENTS)
                        result.add(new SFMTextMatcher.Fragment(offset + fragment.start(), offset + fragment.end()));
                offset += segment.label().length() + 1;
            }
        } else {
            // Absolute/relative address mode: only exact field occurrences, never guessed offsets.
            for (var segment : row.segments()) {
                for (var fragment : project(label, evidence.apply(segment.path())))
                    if (result.size() < SFMTextMatcher.MAX_FRAGMENTS) result.add(fragment);
            }
        }
        return List.copyOf(result);
    }

    static int background(Membership membership, SFMClientTheme theme, int under) {
        int overlay = theme.colour(switch (membership) {
            case FIND -> SFMColourRole.SEARCH_FIND;
            case FILTER -> SFMColourRole.SEARCH_FILTER;
            case BOTH -> SFMColourRole.SEARCH_INTERSECTION;
            case NONE -> throw new IllegalArgumentException("No background for an unmatched glyph");
        });
        int alpha = (overlay >>> 24) & 255;
        int result = 0xFF000000;
        for (int shift : new int[] {0, 8, 16})
            result |= ((((overlay >>> shift) & 255) * alpha + ((under >>> shift) & 255) * (255 - alpha) + 127) / 255) << shift;
        return result;
    }

    static int foreground(int background) {
        return ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMSizeDisplayGeometry.accessibleTextColour(background);
    }
}
