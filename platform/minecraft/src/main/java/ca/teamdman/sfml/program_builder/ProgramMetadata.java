package ca.teamdman.sfml.program_builder;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfml.ast.ASTBuilder;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.text.TextComponentTranslation;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.List;

@Desugar public record ProgramMetadata(
        String programString,
        SFMLLexer lexer,
        CommonTokenStream tokens,
        SFMLParser parser,
        ASTBuilder astBuilder,
        List<TextComponentTranslation> errors
) {}
