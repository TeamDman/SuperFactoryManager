package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

public interface NumExpr extends ASTNode {
    long eval(ProgramContext context);

    String toString();
}
