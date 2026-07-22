package ca.teamdman.sfm.gametest.puppet;

import java.util.List;

public enum SFMGamePuppetViewportProfile {
    /** Backwards-compatible singleton at the launcher-supplied startup viewport. */
    CURRENT,
    /** Stable responsive evidence profile. Explicit scales are expanded after framebuffer measurement. */
    COMMON_RESPONSIVE;

    public SFMGamePuppetViewportVariant preferred() {
        return this == CURRENT
                ? null
                : new SFMGamePuppetViewportVariant(1280, 720, 0);
    }

    public List<int[]> requestedSizes() {
        return this == CURRENT
                ? List.of()
                : List.of(new int[]{640, 480}, new int[]{854, 480}, new int[]{1280, 720}, new int[]{1920, 1080});
    }
}
