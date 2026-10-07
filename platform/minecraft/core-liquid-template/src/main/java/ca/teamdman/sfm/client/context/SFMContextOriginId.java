package ca.teamdman.sfm.client.context;

import java.util.Objects;

/**
 * Stable identity for one independently addressable context projection.
 *
 * <p>The identity deliberately excludes projected content. The contributor is
 * a stable kind/namespace, the container is a globally stable host identity
 * such as {@code workspace-panel-7}, and the local id identifies one projection
 * within that host. Two panels showing the same path therefore remain distinct
 * origins while cursor, selection, and document generations change.</p>
 */
public record SFMContextOriginId(
        String contributorId,
        String containerId,
        String localId
) implements Comparable<SFMContextOriginId> {
    public SFMContextOriginId {
        contributorId = requireIdPart(contributorId, "contributorId");
        containerId = requireIdPart(containerId, "containerId");
        localId = requireIdPart(localId, "localId");
    }

    public String canonical() {
        return contributorId + "/" + containerId + "/" + localId;
    }

    public boolean sharesContainerWith(SFMContextOriginId other) {
        Objects.requireNonNull(other, "other");
        return containerId.equals(other.containerId);
    }

    @Override
    public int compareTo(SFMContextOriginId other) {
        int contributor = contributorId.compareTo(other.contributorId);
        if (contributor != 0) return contributor;
        int container = containerId.compareTo(other.containerId);
        if (container != 0) return container;
        return localId.compareTo(other.localId);
    }

    @Override
    public String toString() {
        return canonical();
    }

    private static String requireIdPart(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        if (value.indexOf('/') >= 0 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " must be one printable canonical segment");
        }
        return value;
    }
}
