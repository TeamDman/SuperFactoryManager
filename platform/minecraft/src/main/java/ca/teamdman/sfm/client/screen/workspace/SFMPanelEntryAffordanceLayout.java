package ca.teamdman.sfm.client.screen.workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure GUI-coordinate geometry shared by numbered-entry rendering and pointer hit testing. */
public final class SFMPanelEntryAffordanceLayout {
    public static final int BOX_SIZE = 11;
    public static final int GAP = 2;
    public static final int EDGE_MARGIN = 3;

    private SFMPanelEntryAffordanceLayout() {
    }

    public static List<HitRegion> layout(
            SFMScreenPanelBounds paneBounds,
            SFMWorkspaceStackId paneId,
            List<SFMWorkspaceLayout.PanelEntry> entries,
            SFMWorkspacePanelId focusedEntry
    ) {
        Objects.requireNonNull(paneBounds, "paneBounds");
        Objects.requireNonNull(paneId, "paneId");
        entries = List.copyOf(entries);
        if (entries.size() <= 1) return List.of();
        int totalWidth = entries.size() * BOX_SIZE + (entries.size() - 1) * GAP;
        int startX = Math.max(
                paneBounds.x() + 2,
                paneBounds.x() + paneBounds.width() - totalWidth - EDGE_MARGIN
        );
        int y = Math.max(
                paneBounds.y() + 2,
                paneBounds.y() + paneBounds.height() - BOX_SIZE - EDGE_MARGIN
        );
        ArrayList<HitRegion> answer = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            SFMWorkspaceLayout.PanelEntry entry = entries.get(index);
            int x = startX + index * (BOX_SIZE + GAP);
            answer.add(new HitRegion(
                    paneId,
                    entry.id(),
                    entry.panel(),
                    index,
                    entries.size(),
                    new SFMScreenPanelBounds(x, y, BOX_SIZE, BOX_SIZE),
                    entry.id().equals(focusedEntry)
            ));
        }
        return List.copyOf(answer);
    }

    public static Optional<HitRegion> hitTest(List<HitRegion> regions, double mouseX, double mouseY) {
        Objects.requireNonNull(regions, "regions");
        for (int index = regions.size() - 1; index >= 0; index--) {
            HitRegion candidate = regions.get(index);
            if (candidate.bounds().contains(mouseX, mouseY)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public record HitRegion(
            SFMWorkspaceStackId paneId,
            SFMWorkspacePanelId entryId,
            SFMScreenPanel capturedPanel,
            int zeroBasedIndex,
            int entryCount,
            SFMScreenPanelBounds bounds,
            boolean focused
    ) {
        public HitRegion {
            Objects.requireNonNull(paneId, "paneId");
            Objects.requireNonNull(entryId, "entryId");
            Objects.requireNonNull(capturedPanel, "capturedPanel");
            Objects.requireNonNull(bounds, "bounds");
            if (entryCount < 2 || zeroBasedIndex < 0 || zeroBasedIndex >= entryCount) {
                throw new IllegalArgumentException("Invalid panel-entry affordance ordinal");
            }
        }

        public int oneBasedIndex() {
            return zeroBasedIndex + 1;
        }

        public String stableId() {
            return "panel-entry-" + entryId.value();
        }
    }
}
