package ca.teamdman.sfm.gametest.puppet;

import java.util.List;

public enum SFMGamePuppetViewportProfile {
    /** Backwards-compatible singleton at the launcher-supplied startup viewport. */
    CURRENT,
    /** Stable responsive evidence profile. Explicit scales are expanded after framebuffer measurement. */
    COMMON_RESPONSIVE,
    /** Review-only acceptance additionally permits the explicit square viewport witness. */
    REVIEW_READINESS,
    /** One deterministic 1280x720 run using Minecraft's automatic GUI scale. */
    FIXED_1280X720_AUTO,
    /**
     * One large physical viewport whose declared run expands from Auto to
     * every numeric GUI scale supported by that viewport.
     */
    GUI_SCALE_MATRIX,
    /**
     * Terminal presentation acceptance keeps its declared matrix bounded while
     * also authorizing the exact 3840x2130 physical-resolution witness.
     */
    TERMINAL_PRESENTATION;

    public SFMGamePuppetViewportVariant preferred() {
        if (this == CURRENT) return null;
        if (this == GUI_SCALE_MATRIX) return new SFMGamePuppetViewportVariant(3840, 2130, 0);
        return new SFMGamePuppetViewportVariant(1280, 720, 0);
    }

    public List<int[]> requestedSizes() {
        if (this == CURRENT) return List.of();
        if (this == FIXED_1280X720_AUTO) return List.of(new int[]{1280, 720});
        if (this == TERMINAL_PRESENTATION) return List.of(new int[]{1280, 720});
        if (this == GUI_SCALE_MATRIX) return List.of(new int[]{3840, 2130});
        return List.of(new int[]{640, 480}, new int[]{854, 480}, new int[]{1280, 720}, new int[]{1920, 1080});
    }

    public List<int[]> acceptedExactSizes() {
        if (this == REVIEW_READINESS) {
            return List.of(new int[]{640, 480}, new int[]{854, 480}, new int[]{1280, 720},
                    new int[]{1920, 1080}, new int[]{2000, 2000});
        }
        return this == TERMINAL_PRESENTATION
                ? List.of(new int[]{1280, 720}, new int[]{3840, 2130})
                : requestedSizes();
    }
}
