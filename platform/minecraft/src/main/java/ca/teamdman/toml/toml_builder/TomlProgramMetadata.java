package ca.teamdman.toml.toml_builder;

import ca.teamdman.antlr.IProgramMetadata;
import ca.teamdman.langs.TomlLexer;
import ca.teamdman.langs.TomlParser;
import ca.teamdman.toml.ast.TomlAstNode;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.List;

public record TomlProgramMetadata(
        String programString,

        TomlLexer lexer,

        CommonTokenStream tokens,

        TomlParser parser,

        TomlAstBuilder astBuilder,

        List<TranslatableContents> errors
) implements IProgramMetadata<TomlAstNode, TomlLexer, TomlParser, TomlAstBuilder> {
}
