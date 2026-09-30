package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

public record BoolFalse() implements BoolExpr {
    @Override
    public boolean test(ProgramContext programContext) {
        return false;
    }

    @Override
    public String toString() {
        return "FALSE";
    }
}
