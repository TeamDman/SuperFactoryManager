package ca.teamdman.sfml.program_builder;

import ca.teamdman.antlr.IProgramMetadata;
import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfml.ast.SfmlAstBuilder;
import ca.teamdman.sfml.ast.SfmlAstNode;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.List;

public record SFMLProgramMetadata(
        String programString,
        SFMLLexer lexer,
        CommonTokenStream tokens,
        SFMLParser parser,
        SfmlAstBuilder astBuilder,
        List<TranslatableContents> errors
) implements IProgramMetadata<SfmlAstNode, SFMLLexer, SFMLParser, SfmlAstBuilder> {
    public SFMLProgramMetadata {
    }

    public SFMLProgramMetadata(IProgramMetadata<SfmlAstNode, SFMLLexer, SFMLParser, SfmlAstBuilder> other) {
        this(
                other.programString(),
                other.lexer(),
                other.tokens(),
                other.parser(),
                other.astBuilder(),
                other.errors()
        );
    }
}
