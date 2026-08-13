package ca.teamdman.sfm.client.explorer;

import java.util.Objects;

/** One parent/child relation row; hierarchy is not nested object ownership. */
public record SFMChildEdge(SFMPath parent, SFMPath child) implements Comparable<SFMChildEdge> {
    public SFMChildEdge {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(child, "child");
        if (parent.equals(child)) throw new IllegalArgumentException("A path cannot be its own child");
    }

    @Override
    public int compareTo(SFMChildEdge other) {
        int parentComparison = parent.compareTo(other.parent);
        return parentComparison != 0 ? parentComparison : child.compareTo(other.child);
    }
}
