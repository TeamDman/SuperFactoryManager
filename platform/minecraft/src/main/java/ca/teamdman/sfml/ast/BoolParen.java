package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.List;

public record BoolParen(BoolExpr inner) implements BoolExpr {
    @Override
    public boolean test(ProgramContext programContext) {
        return inner.test(programContext);
    }

    @Override
    public String toString() {
        return "(" + inner + ")";
    }

    @Override
    public List<BoolExpr> getChildNodes() {

        return List.of(inner);
    }

}
