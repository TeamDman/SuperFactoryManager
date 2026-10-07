package ca.teamdman.sfm.client.terminal;

/** Transport-neutral disposition of one authoritative terminal input event. */
public enum SFMTerminalInputDisposition {
    FORWARDED,
    SELECTION_CHANGED,
    NO_CHANGE
}
