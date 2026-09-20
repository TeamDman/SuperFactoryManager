package ca.teamdman.sfml.ast;

import java.util.Objects;

/** Optional source assertion; it never chooses or changes the actual execution host. */
public record ProgramExecutionSideDeclaration(ProgramExecutionSide side) implements ASTNode {
    public ProgramExecutionSideDeclaration {
        Objects.requireNonNull(side, "side");
    }

    @Override
    public String toString() {
        return side + " BTW";
    }
}
