package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import java.util.Objects;

/** Immutable measurements shown by the viewport calibration panel. */
public record SFMViewportDiagnostics(
        int windowWidth,
        int windowHeight,
        int framebufferWidth,
        int framebufferHeight,
        int logicalWidth,
        int logicalHeight,
        String requestedGuiScale,
        double effectiveGuiScale,
        String responsiveMode
) {
    public SFMViewportDiagnostics {
        if (windowWidth < 0 || windowHeight < 0 || framebufferWidth < 0 || framebufferHeight < 0
                || logicalWidth < 0 || logicalHeight < 0) {
            throw new IllegalArgumentException("Viewport dimensions must not be negative");
        }
        Objects.requireNonNull(requestedGuiScale);
        Objects.requireNonNull(responsiveMode);
        if (!(effectiveGuiScale > 0.0) || !Double.isFinite(effectiveGuiScale)) {
            throw new IllegalArgumentException("Effective GUI scale must be finite and positive");
        }
    }
}
