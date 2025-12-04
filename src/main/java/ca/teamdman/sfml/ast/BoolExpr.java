package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.function.Predicate;

public interface BoolExpr extends Predicate<ProgramContext>, ASTNode, ToStringPretty {
}
