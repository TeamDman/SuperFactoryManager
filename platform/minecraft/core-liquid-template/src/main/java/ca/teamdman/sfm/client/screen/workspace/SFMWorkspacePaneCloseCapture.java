package ca.teamdman.sfm.client.screen.workspace;

import java.util.List;
import java.util.Objects;

/** Identity-sensitive witness used to prevent a delayed close from targeting replacement content. */
public record SFMWorkspacePaneCloseCapture(
        SFMWorkspaceStackId paneId,
        List<EntryWitness> entries,
        SFMWorkspacePaneCloseSummary summary
) {
    public SFMWorkspacePaneCloseCapture {
        Objects.requireNonNull(paneId, "paneId");
        entries = List.copyOf(entries);
        Objects.requireNonNull(summary, "summary");
        if (entries.isEmpty()) throw new IllegalArgumentException("A pane capture must contain an entry");
        if (entries.size() != summary.total()) {
            throw new IllegalArgumentException("Pane capture and summary totals disagree");
        }
    }

    public record EntryWitness(SFMWorkspacePanelId id, SFMScreenPanel panel) {
        public EntryWitness {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(panel, "panel");
        }
    }
}
