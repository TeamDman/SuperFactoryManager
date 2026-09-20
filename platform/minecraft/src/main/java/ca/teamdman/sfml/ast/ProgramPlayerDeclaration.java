package ca.teamdman.sfml.ast;

import java.util.Objects;

public record ProgramPlayerDeclaration(String alias, String playerName) implements ASTNode {
    public ProgramPlayerDeclaration {
        Objects.requireNonNull(alias);
        Objects.requireNonNull(playerName);
    }
}
