package ca.teamdman.sfml.manipulation;

import com.github.bsideup.jabel.Desugar;

@Desugar public record ManipulationResult(
        String content,
        int cursorPosition,
        int selectionCursorPosition
) {}
