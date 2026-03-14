package ca.teamdman.antlr.ext_antlr4c3;

import ca.teamdman.antlr.IAstBuilder;
import ca.teamdman.antlr.IAstNode;
import ca.teamdman.antlr.IProgramMetadata;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;

import java.util.List;

public record ProgramMetadata<AST_NODE extends IAstNode<?>, LEXER extends Lexer, PARSER extends Parser, BUILDER extends IAstBuilder<AST_NODE>>(
        String programString,

        LEXER lexer,

        CommonTokenStream tokens,

        PARSER parser,

        BUILDER astBuilder,

        List<TranslatableContents> errors
) implements IProgramMetadata<AST_NODE, LEXER, PARSER, BUILDER> {
}
