package ca.teamdman.sfm.client.explorer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Members of every selection matched in one captured repository generation. */
public record SFMSelectionMembersResolution(
        SFMSelectorResolution<SFMSelectionId> selectorResolution,
        Map<SFMSelectionId, Long> capturedHeadRevisions,
        Set<SFMPath> members
) {
    public SFMSelectionMembersResolution {
        Objects.requireNonNull(selectorResolution, "selectorResolution");
        Objects.requireNonNull(capturedHeadRevisions, "capturedHeadRevisions");
        Objects.requireNonNull(members, "members");
        TreeMap<SFMSelectionId, Long> revisions = new TreeMap<>(
                java.util.Comparator.comparing(SFMSelectionId::value)
        );
        revisions.putAll(capturedHeadRevisions);
        capturedHeadRevisions = Collections.unmodifiableMap(revisions);
        members = Collections.unmodifiableSet(new TreeSet<>(members));
    }

    public boolean complete() {
        return selectorResolution.complete();
    }

    public List<SFMSelectorResolution.Diagnostic> diagnostics() {
        return selectorResolution.diagnostics();
    }
}
