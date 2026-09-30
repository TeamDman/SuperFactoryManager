package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

public interface Statement extends ASTNode {
    void tick(ProgramContext context);
}
