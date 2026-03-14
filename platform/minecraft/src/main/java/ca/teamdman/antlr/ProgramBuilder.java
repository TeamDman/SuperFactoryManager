package ca.teamdman.antlr;

import ca.teamdman.antlr.ext_antlr4c3.ProgramMetadata;
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.SFMTranslationUtils;
import ca.teamdman.sfml.program_builder.SFMLProgramBuildResult;
import ca.teamdman.sfml.program_builder.SFMLProgramBuilder;
import net.minecraft.ResourceLocationException;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.antlr.v4.runtime.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/// This class is a helper for turning program source code into program AST objects.
/// Because the SFM mod supports multiple grammars, this class is very generic.
/// The SFML-specific {@link SFMLProgramBuilder} returns a {@link SFMLProgramBuildResult} to hide the generics.
/// Other languages should also introduce new types to rewrap the {@link IProgramBuildResult} and {@link IProgramMetadata} to minimize noise.
public class ProgramBuilder<AST_NODE extends IAstNode<?>, LEXER extends Lexer, PARSER extends Parser, BUILDER extends IAstBuilder<AST_NODE>> {

    /// The source code to be fed to the ANTLR engine
    private final String programString;

    private final Function<CharStream, LEXER> lexerConstructor;

    private final Function<CommonTokenStream, PARSER> parserConstructor;

    private final Supplier<BUILDER> astBuilderConstructor;

    public ProgramBuilder(
            Function<CharStream, LEXER> lexerConstructor,
            Function<CommonTokenStream, PARSER> parserConstructor,
            Supplier<BUILDER> astBuilderConstructor,
            @Nullable String programString
    ) {

        this.lexerConstructor = lexerConstructor;
        this.parserConstructor = parserConstructor;
        this.astBuilderConstructor = astBuilderConstructor;

        if (programString == null) {
            programString = "";
        }
        this.programString = programString;
    }

    /// Build a {@link PROGRAM} {@link AST_NODE}, this does not have to be a top-level directive.
    /// See {@link ca.teamdman.sfml.ast.BoolExpr#from(String)}
    public <PROGRAM, CONTEXT> ProgramBuildResult<AST_NODE, PROGRAM, LEXER, PARSER, BUILDER, ProgramMetadata<AST_NODE, LEXER, PARSER, BUILDER>> build(
            Function<PARSER, CONTEXT> contextFn,
            BiFunction<BUILDER, CONTEXT, PROGRAM> astFn
    ) {
        // Create lexer
        CodePointCharStream charStream = CharStreams.fromString(programString);
        LEXER lexer = lexerConstructor.apply(charStream);

        // Create parser
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PARSER parser = parserConstructor.apply(tokens);

        // Create AST builder
        BUILDER astBuilder = astBuilderConstructor.get();

        // Configure error capturing
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        List<String> buildErrors = new ArrayList<>();
        ListErrorListener listener = new ListErrorListener(buildErrors);
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);

        // Prepare error list out-value
        List<TranslatableContents> errors = new ArrayList<>();

        // initial parse
        CONTEXT context = contextFn.apply(parser);
        buildErrors.stream().map(LocalizationKeys.PROGRAM_BUILD_ERROR_LITERAL::get).forEach(errors::add);

        // build program from AST only when there are no errors from previous phases
        @Nullable PROGRAM maybeProgram = null;
        if (errors.isEmpty()) {
            try {
                maybeProgram = astFn.apply(astBuilder, context);
            } catch (ResourceLocationException | IllegalArgumentException | AssertionError e) {
                errors.add(LocalizationKeys.PROGRAM_BUILD_ERROR_LITERAL.get(e.getMessage()));
            } catch (Exception e) {
                errors.add(LocalizationKeys.PROGRAM_BUILD_ERROR_LITERAL.get(e.getMessage()));
                SFM.LOGGER.warn(
                        "Encountered unhandled error while compiling program\n```\n{}\n```",
                        programString,
                        e
                );
                var message = e.getMessage();
                if (message != null) {
                    errors.add(SFMTranslationUtils.getTranslatableContents(
                            e.getClass().getCanonicalName() + ": " + message
                    ));
                } else {
                    errors.add(SFMTranslationUtils.getTranslatableContents(e.getClass().getCanonicalName()));
                }
            }
        }

        ProgramMetadata<AST_NODE, LEXER, PARSER, BUILDER> metadata = new ProgramMetadata<>(
                programString,
                lexer,
                tokens,
                parser,
                astBuilder,
                errors
        );


        return new ProgramBuildResult<>(
                maybeProgram,
                metadata
        );
    }

}
