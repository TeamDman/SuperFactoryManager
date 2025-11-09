package ca.teamdman.sfml.ast;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record StringHolder(String value) implements ASTNode {
}
