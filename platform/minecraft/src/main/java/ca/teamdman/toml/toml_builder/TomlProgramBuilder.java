package ca.teamdman.toml.toml_builder;

import ca.teamdman.antlr.ListErrorListener;
import ca.teamdman.langs.TomlLexer;
import ca.teamdman.langs.TomlParser;
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.SFMTranslationUtils;
import ca.teamdman.toml.ast.TomlAstNode;
import net.minecraft.ResourceLocationException;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TomlProgramBuilder {
    private final String programString;

    public TomlProgramBuilder(@Nullable String programString) {
        if (programString == null) {
            programString = "";
        }
        this.programString = programString;
    }
    public TomlProgramBuildResult build() {

        TomlLexer lexer = new TomlLexer(CharStreams.fromString(programString));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TomlParser parser = new TomlParser(tokens);
        TomlAstBuilder builder = new TomlAstBuilder();

        // set up error capturing
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        List<TranslatableContents> errors = new ArrayList<>();
        List<String> buildErrors = new ArrayList<>();
        ListErrorListener listener = new ListErrorListener(buildErrors);
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);

        // initial parse
        TomlParser.DocumentContext context = parser.document();
        buildErrors.stream().map(LocalizationKeys.PROGRAM_BUILD_ERROR_LITERAL::get).forEach(errors::add);


        // build program from AST only when there are no errors from previous phases
        @Nullable TomlAstNode program = null;
        if (errors.isEmpty()) {
            try {
                program = builder.visitDocument(context);
                // Make sure all referenced resources are valid during compilation instead of waiting for the program to tick
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
                            e.getClass().getSimpleName() + ": " + message
                    ));
                } else {
                    errors.add(SFMTranslationUtils.getTranslatableContents(e.getClass().getCanonicalName()));
                }
            }
        }

        TomlProgramMetadata metadata = new TomlProgramMetadata(
                programString,
                lexer,
                tokens,
                parser,
                builder,
                errors
        );

        return new TomlProgramBuildResult(program, metadata);
    }
}
