package ca.teamdman.sfm.client.terminal;

/** Transport-neutral inclusive terminal-cell selection owned by the Rust session. */
public record SFMTerminalSelection(int anchorX, int anchorY, int focusX, int focusY) {
}
