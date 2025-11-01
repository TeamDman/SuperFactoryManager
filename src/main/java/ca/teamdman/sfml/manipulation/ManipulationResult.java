package ca.teamdman.sfml.manipulation;

public record ManipulationResult(
        String content,
        int cursorPosition,
        int selectionCursorPosition
) {
}
