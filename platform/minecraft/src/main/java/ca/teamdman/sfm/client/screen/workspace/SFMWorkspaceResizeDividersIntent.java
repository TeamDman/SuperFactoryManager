package ca.teamdman.sfm.client.screen.workspace;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Coordinate-free semantic resize request shared by pointer, actions, and automation. */
public record SFMWorkspaceResizeDividersIntent(
        List<SFMWorkspaceDividerId> dividerIds,
        int deltaX,
        int deltaY
) {
    public SFMWorkspaceResizeDividersIntent {
        Objects.requireNonNull(dividerIds, "dividerIds");
        dividerIds = List.copyOf(new LinkedHashSet<>(dividerIds));
        if (dividerIds.isEmpty()) throw new IllegalArgumentException("At least one divider id is required");
    }

    public static List<SFMWorkspaceDividerId> parseSelector(String selector) {
        Objects.requireNonNull(selector, "selector");
        LinkedHashSet<SFMWorkspaceDividerId> answer = new LinkedHashSet<>();
        for (String token : selector.split(",")) {
            String value = token.strip();
            if (!value.isEmpty()) answer.add(SFMWorkspaceDividerId.parse(value));
        }
        if (answer.isEmpty()) throw new IllegalArgumentException("Divider selector must not be empty");
        return List.copyOf(answer);
    }

    public String selector() {
        return String.join(",", dividerIds.stream().map(SFMWorkspaceDividerId::toString).toList());
    }
}
