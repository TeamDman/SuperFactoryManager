package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.List;

public record BoolTrue() implements BoolExpr {
    @Override
    public boolean test(ProgramContext programContext) {
        return true;
    }

    @Override
    public String toString() {
        return "TRUE";
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of();
    }

}
