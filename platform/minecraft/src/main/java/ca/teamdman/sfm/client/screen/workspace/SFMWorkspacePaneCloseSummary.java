package ca.teamdman.sfm.client.screen.workspace;

/** Exact preflight counts for one captured pane close. */
public record SFMWorkspacePaneCloseSummary(
        int total,
        int dirty,
        int readOnly,
        int recoverable,
        int nonRecoverable
) {
    public SFMWorkspacePaneCloseSummary {
        if (total < 1) throw new IllegalArgumentException("A pane close must contain at least one entry");
        if (dirty < 0 || dirty > total) throw new IllegalArgumentException("Invalid dirty count");
        if (readOnly < 0 || readOnly > total) throw new IllegalArgumentException("Invalid read-only count");
        if (recoverable < 0 || nonRecoverable < 0 || recoverable + nonRecoverable != total) {
            throw new IllegalArgumentException("Recoverability counts must partition the pane");
        }
    }

    public boolean requiresConfirmation() {
        return total > 1 || dirty > 0 || nonRecoverable > 0;
    }

    public String exactCounts() {
        return "total=" + total
                + ", dirty=" + dirty
                + ", read-only=" + readOnly
                + ", recoverable=" + recoverable
                + ", non-recoverable=" + nonRecoverable;
    }
}
