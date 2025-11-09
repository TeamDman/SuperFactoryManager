package ca.teamdman.sfml.ast;

import com.github.bsideup.jabel.Desugar;

@Desugar public record ProgramName(
        StringHolder value
) implements ASTNode {}
