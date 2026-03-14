package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

public interface Tickable extends SfmlAstNode {
    void tick(ProgramContext programContext);
}
