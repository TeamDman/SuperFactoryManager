package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

public record BoolConjunction(BoolExpr left, BoolExpr right) implements BoolExpr {
    @Override
    public boolean test(ProgramContext programContext) {
        return left.test(programContext) && right.test(programContext);
    }

    @Override
    public String toString() {
        return left + " AND " + right;
    }
}
