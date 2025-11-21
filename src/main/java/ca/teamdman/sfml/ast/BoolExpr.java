package ca.teamdman.sfml.ast;

import java.util.function.Predicate;

import ca.teamdman.sfm.common.program.ProgramContext;

public interface BoolExpr extends Predicate<ProgramContext>, ASTNode, ToStringPretty {}
