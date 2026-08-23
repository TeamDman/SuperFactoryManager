package ca.teamdman.sfm.client.screen.workspace;

/** Current user-data posture consulted before closing a panel entry. */
public record SFMPanelCloseState(boolean dirty, boolean readOnly) {
    public static SFMPanelCloseState cleanEditable() {
        return new SFMPanelCloseState(false, false);
    }

    public static SFMPanelCloseState cleanReadOnly() {
        return new SFMPanelCloseState(false, true);
    }
}
