package ca.teamdman.sfm.gametest.puppet;

import java.util.List;

public enum SFMGamePuppetViewportProfile {
    /** Backwards-compatible singleton at the launcher-supplied startup viewport. */
    CURRENT,
    /** Stable responsive evidence profile. Explicit scales are expanded after framebuffer measurement. */
    COMMON_RESPONSIVE,
    /**
     * Terminal presentation acceptance keeps its declared matrix bounded while
     * also authorizing the exact 3840x2130 physical-resolution witness.
     */
    TERMINAL_PRESENTATION;

    public SFMGamePuppetViewportVariant preferred() {
        return this == CURRENT
                ? null
                : new SFMGamePuppetViewportVariant(1280, 720, 0);
    }

    public List<int[]> requestedSizes() {
        return this == CURRENT
                ? List.of()
                : this == TERMINAL_PRESENTATION
                        ? List.of(new int[]{1280, 720})
                        : List.of(new int[]{640, 480}, new int[]{854, 480}, new int[]{1280, 720}, new int[]{1920, 1080});
    }

    public List<int[]> acceptedExactSizes() {
        return this == TERMINAL_PRESENTATION
                ? List.of(new int[]{1280, 720}, new int[]{3840, 2130})
                : requestedSizes();
    }
}
