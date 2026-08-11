package ca.teamdman.sfml.intellisense;

import ca.teamdman.sfm.client.text_editor.SFMTextEditorIntellisenseLevel;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.program_builder.ProgramBuildResult;

public record IntellisenseContext(
        ProgramBuildResult programBuildResult,
        int cursorPosition,
        int selectionCursorPosition,
        LabelPositionHolder labelPositionHolder,
        SFMTextEditorIntellisenseLevel intellisenseLevel
) {
    public MutableProgramString createMutableProgramString() {
        return new MutableProgramString(
                programBuildResult.metadata().programString(),
                cursorPosition,
                selectionCursorPosition
        );
    }
}
