package ca.teamdman.sfml.ast;

import com.github.bsideup.jabel.Desugar;

import ca.teamdman.sfm.common.program.ProgramContext;

@Desugar
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
