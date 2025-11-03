package ca.teamdman.sfml.program_builder;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfm.common.util.SFMTranslationUtils;
import ca.teamdman.sfml.ast.ASTBuilder;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.ResourceIdentifier;
import net.minecraft.ResourceLocationException;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.ResourceLocation;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProgramBuilder {
    public static ProgramBuildResult build(
            @Nullable String programString
    ) {
        if (programString == null) {
            programString = "";
        }
        SFMLLexer lexer = new SFMLLexer(CharStreams.fromString(programString));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SFMLParser parser = new SFMLParser(tokens);
        ASTBuilder builder = new ASTBuilder();

        // set up error capturing
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        List<TextComponentTranslation> errors = new ArrayList<>();
        List<String> buildErrors = new ArrayList<>();
        ListErrorListener listener = new ListErrorListener(buildErrors);
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);

        // initial parse
        SFMLParser.ProgramContext context = parser.program();
        buildErrors.stream().map(LocalizationKeys.PROGRAM_ERROR_LITERAL::get).forEach(errors::add);


        // build program from AST only when there are no errors from previous phases
        @Nullable Program program = null;
        if (errors.isEmpty()) {
            try {
                program = builder.visitProgram(context);
                // Make sure all referenced resources are valid during compilation instead of waiting for the program to tick
                checkResourceTypes(program, errors);
            } catch (ResourceLocationException | IllegalArgumentException | AssertionError e) {
                errors.add(LocalizationKeys.PROGRAM_ERROR_LITERAL.get(e.getMessage()));
            } catch (Throwable t) {
                errors.add(LocalizationKeys.PROGRAM_ERROR_COMPILE_FAILED.get());
                SFM.LOGGER.warn(
                        "Encountered unhandled error while compiling program\n```\n{}\n```",
                        programString,
                        t
                );
                var message = t.getMessage();
                if (message != null) {
                    errors.add(SFMTranslationUtils.getTextComponentTranslation(
                            t.getClass().getSimpleName() + ": " + message
                    ));
                } else {
                    errors.add(SFMTranslationUtils.getTextComponentTranslation(t.getClass().getSimpleName()));
                }
            }
        }

        // Assert just in case, this should never happen
        //noinspection ConstantValue
        if (program == null && errors.isEmpty()) {
            errors.add(LocalizationKeys.PROGRAM_ERROR_COMPILE_FAILED.get());
            SFM.LOGGER.error(
                    "Program was somehow null after a successful compile. I have no idea how this could happen, but it definitely shouldn't.\n```\n{}\n```",
                    programString
            );
        }

        ProgramMetadata metadata = new ProgramMetadata(
                programString,
                lexer,
                tokens,
                parser,
                builder,
                errors
        );
        return new ProgramBuildResult(program, metadata);
    }


    private static void checkResourceTypes(
            Program program,
            List<TextComponentTranslation> errors
    ) {
        if (!SFMEnvironmentUtils.isGameLoaded()) {
            return;
        }
        List<String> disallowedResourceTypes = java.util.Arrays.asList(SFMConfig.server.disallowedResourceTypesForTransfer);
        for (ResourceIdentifier<?, ?, ?> referencedResource : program.referencedResources()) {
            try {
                ResourceType<?, ?, ?> resourceType = referencedResource.getResourceType();
                if (resourceType == null) {
                    errors.add(LocalizationKeys.PROGRAM_ERROR_UNKNOWN_RESOURCE_TYPE.get(
                            referencedResource));
                } else {
                    ResourceLocation resourceTypeId = Objects.requireNonNull(SFMResourceTypes
                                                                                     .registry()
                                                                                     .getId(resourceType));
                    if (disallowedResourceTypes.contains(resourceTypeId.toString())) {
                        errors.add(LocalizationKeys.PROGRAM_ERROR_DISALLOWED_RESOURCE_TYPE.get(
                                referencedResource));
                    }
                }
            } catch (ResourceLocationException e) {
                errors.add(LocalizationKeys.PROGRAM_ERROR_MALFORMED_RESOURCE_TYPE.get(
                        referencedResource));
            }
        }
    }
}
