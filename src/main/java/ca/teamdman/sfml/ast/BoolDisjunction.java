package ca.teamdman.sfml.ast;

import com.github.bsideup.jabel.Desugar;

import ca.teamdman.sfm.common.program.ProgramContext;

@Desugar
public record BoolDisjunction(
                              BoolExpr left, BoolExpr right)
        implements BoolExpr {

    @Override
    public boolean test(ProgramContext programContext) {
        return left.test(programContext) || right.test(programContext);
    }

    @Override
    public String toString() {
        return left + " OR " + right;
    }
}
