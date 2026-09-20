package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.value.SFMValuePattern;

import java.util.Objects;

public record ProgramPatternDeclaration(String alias, SFMValuePattern pattern) implements ASTNode {
    public ProgramPatternDeclaration {
        Objects.requireNonNull(alias);
        Objects.requireNonNull(pattern);
    }
}
