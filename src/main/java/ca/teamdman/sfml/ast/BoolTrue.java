package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;
import com.github.bsideup.jabel.Desugar;

@Desugar public record BoolTrue(
        ) implements BoolExpr {
    @Override
    public boolean test(ProgramContext programContext) {
        return true;
    }

    @Override
    public String toString() {
        return "TRUE";
    }
}
