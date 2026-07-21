package ca.teamdman.sfm.client.screen.workspace;

/** Stable identity for one panel instance, independent of its current tree position. */
public record SFMWorkspacePanelId(long value) {
    public SFMWorkspacePanelId {
        if (value < 0) throw new IllegalArgumentException("Panel id must be non-negative");
    }
}
