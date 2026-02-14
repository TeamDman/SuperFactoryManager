package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;
import com.github.bsideup.jabel.Desugar;

@Desugar public record BoolFalse(
        ) implements BoolExpr {
    @Override
    public boolean test(ProgramContext programContext) {
        return false;
    }

    @Override
    public String toString() {
        return "FALSE";
    }
}
