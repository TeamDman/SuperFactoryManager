package ca.teamdman.sfm.client.theme;

import java.util.Arrays;
import java.util.Optional;

/** Stable semantic colour ids consumed by client rendering. */
public enum SFMColourRole {
    SCREEN_OVERLAY("screen.overlay", 0x66000000),
    PANEL_BACKGROUND("panel.background", 0xF0202020),
    PANEL_BORDER("panel.border", 0xFF707070),
    PANEL_SELECTION("panel.selection", 0xFF404040),
    TEXT_PRIMARY("text.primary", 0xFFFFFFFF),
    TEXT_MUTED("text.muted", 0xFFB0B0B0),
    TEXT_ERROR("text.error", 0xFFFF5555),
    TEXT_ACCENT("text.accent", 0xFF55FFFF),
    TIMELINE_BACKGROUND("timeline.background", 0xEE11151A),
    TIMELINE_TRACK("timeline.track", 0xFF4A5159),
    TIMELINE_KEYFRAME("timeline.keyframe", 0xFF55FFFF),
    TIMELINE_TIME("timeline.time", 0xFFFFAA33),
    TIMELINE_MARKER("timeline.marker", 0xFFB8C0C8);

    private final String id;
    private final int defaultArgb;

    SFMColourRole(String id, int defaultArgb) {
        this.id = id;
        this.defaultArgb = defaultArgb;
    }

    public String id() { return id; }
    public int defaultArgb() { return defaultArgb; }

    public static Optional<SFMColourRole> byId(String id) {
        return Arrays.stream(values()).filter(role -> role.id.equals(id)).findFirst();
    }
}
