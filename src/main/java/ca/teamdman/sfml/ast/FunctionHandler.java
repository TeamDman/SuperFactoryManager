package ca.teamdman.sfml.ast;

@FunctionalInterface
public interface FunctionHandler {
    NumExpr build(FunctionArgs args);
}
