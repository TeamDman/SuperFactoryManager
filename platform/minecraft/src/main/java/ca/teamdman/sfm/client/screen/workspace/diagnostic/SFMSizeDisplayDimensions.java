package ca.teamdman.sfm.client.screen.workspace.diagnostic;

/** Logical dimensions supplied to a size-display panel. */
public record SFMSizeDisplayDimensions(int width, int height) {
    public SFMSizeDisplayDimensions {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Logical dimensions must not be negative");
        }
    }
}
