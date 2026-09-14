package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.value.SFMValuePattern;

import java.util.Objects;

public record PatternFieldDefinition(
        String name,
        SFMValuePattern pattern
) implements ASTNode {
    public PatternFieldDefinition {
        Objects.requireNonNull(name);
        Objects.requireNonNull(pattern);
    }
}
