package ca.teamdman.antlr;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.jetbrains.annotations.Nullable;

public record ProgramBuildResult<ASTNODE extends IAstNode<?>, PROGRAM, LEXER extends Lexer, PARSER extends Parser, BUILDER extends IAstBuilder<ASTNODE>, METADATA extends IProgramMetadata<ASTNODE, LEXER, PARSER, BUILDER>>(
        @Nullable PROGRAM maybeProgram,

        METADATA metadata
) implements IProgramBuildResult<PROGRAM, IProgramMetadata<ASTNODE, LEXER, PARSER, BUILDER>, ProgramBuildResult<ASTNODE, PROGRAM, LEXER, PARSER, BUILDER, METADATA>> {
}
