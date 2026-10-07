package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.ProgramRelation;

public interface ProgramValueExpression extends ASTNode {
    ProgramRelation evaluate(ProgramContext context);
}
