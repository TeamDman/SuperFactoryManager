package ca.teamdman.sfm.client.screen.workspace;

import java.util.Locale;
import java.util.Objects;

/** Stable structural identity for one boundary between adjacent layout tracks. */
public record SFMWorkspaceDividerId(
        String nodePath,
        SFMWorkspaceAxis axis,
        int boundaryIndex,
        SFMWorkspacePanelId beforeAnchor,
        SFMWorkspacePanelId afterAnchor
) implements Comparable<SFMWorkspaceDividerId> {
    public SFMWorkspaceDividerId {
        Objects.requireNonNull(nodePath, "nodePath");
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(beforeAnchor, "beforeAnchor");
        Objects.requireNonNull(afterAnchor, "afterAnchor");
        if (!nodePath.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Divider node path must be action-token safe: " + nodePath);
        }
        if (boundaryIndex < 0) throw new IllegalArgumentException("Boundary index must be non-negative");
    }

    public static SFMWorkspaceDividerId parse(String value) {
        Objects.requireNonNull(value, "value");
        String[] parts = value.split("\\.", -1);
        if (parts.length != 5) throw new IllegalArgumentException("Invalid divider id: " + value);
        SFMWorkspaceAxis axis = switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "h" -> SFMWorkspaceAxis.HORIZONTAL;
            case "v" -> SFMWorkspaceAxis.VERTICAL;
            default -> throw new IllegalArgumentException("Invalid divider axis: " + parts[0]);
        };
        try {
            return new SFMWorkspaceDividerId(
                    parts[1],
                    axis,
                    Integer.parseInt(parts[2]),
                    new SFMWorkspacePanelId(Long.parseLong(parts[3])),
                    new SFMWorkspacePanelId(Long.parseLong(parts[4]))
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid divider id: " + value, exception);
        }
    }

    @Override
    public String toString() {
        String axisToken = axis == SFMWorkspaceAxis.HORIZONTAL ? "h" : "v";
        return axisToken + "." + nodePath + "." + boundaryIndex + "."
                + beforeAnchor.value() + "." + afterAnchor.value();
    }

    @Override
    public int compareTo(SFMWorkspaceDividerId other) {
        return toString().compareTo(other.toString());
    }
}
