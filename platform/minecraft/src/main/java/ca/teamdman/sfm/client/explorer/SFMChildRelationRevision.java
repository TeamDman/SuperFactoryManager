package ca.teamdman.sfm.client.explorer;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Immutable published parent/child relation revision. */
public record SFMChildRelationRevision(long id, Set<SFMChildEdge> edges) {
    public SFMChildRelationRevision {
        if (id < 0) throw new IllegalArgumentException("Revision id must not be negative");
        edges = Collections.unmodifiableSet(new TreeSet<>(edges));
    }

    public Set<SFMPath> childrenOf(SFMPath parent) {
        TreeSet<SFMPath> answer = new TreeSet<>();
        for (SFMChildEdge edge : edges) {
            if (edge.parent().equals(parent)) answer.add(edge.child());
        }
        return Collections.unmodifiableSet(answer);
    }
}
