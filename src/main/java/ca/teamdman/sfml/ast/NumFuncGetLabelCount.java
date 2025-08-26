package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

public final class NumFuncGetLabelCount implements NumExpr {
    private final LabelAccess labelAccess;

    public NumFuncGetLabelCount(LabelAccess labelAccess) {
        this.labelAccess = labelAccess;
    }

    @Override
    public long eval(ProgramContext context) {
        return labelAccess.getLabelledPositions(context.getLabelPositionHolder()).size();
    }

    @Override
    public String toString() {
        return "get_label_count(" + labelAccess + ")";
    }
}
