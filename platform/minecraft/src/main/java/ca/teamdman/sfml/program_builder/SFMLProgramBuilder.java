package ca.teamdman.sfml.program_builder;

import ca.teamdman.antlr.ProgramBuildResult;
import ca.teamdman.antlr.ProgramBuilder;
import ca.teamdman.antlr.ext_antlr4c3.ProgramMetadata;
import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfml.ast.ResourceIdentifier;
import ca.teamdman.sfml.ast.SFMLProgram;
import ca.teamdman.sfml.ast.SfmlAstBuilder;
import ca.teamdman.sfml.ast.SfmlAstNode;
import net.minecraft.ResourceLocationException;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/// Helper for turning SFML source code into an {@link SFMLProgram} object wrapped in a {@link SFMLProgramBuildResult}.
/// This class handles caching.
/// The cached value MUST NOT BE MODIFIED!
/// Despite using records in a lot of places, programs still have some spots of interior mutability that should not be mutated.
public class SFMLProgramBuilder {
    /// Reduce duplication of effort compiling the same program over and over again
    private static final WeakHashMap<String, SFMLProgramBuildResult> CACHE = new WeakHashMap<>();

    /// The Super Factory Manager Language source code
    private final String programString;


    public SFMLProgramBuilder(@Nullable String programString) {

        if (programString == null) {
            programString = "";
        }
        this.programString = programString;
    }

    /// When the server config changes, the cache should be emptied.
    /// Timer trigger minimum interval may have changed.
    public static void bustCache() {

        CACHE.clear();
    }

    public SFMLProgramBuildResult build(
    ) {
        // Use cached value if available
        @Nullable SFMLProgramBuildResult cached = CACHE.get(programString);
        if (cached != null) {
            if (cached.metadata().errors().isEmpty()) {
                return cached;
            } else {
                SFM.LOGGER.warn(
                        "Program cache hit, but the program build result contained errors. Will rebuild program."
                );
            }
        }

        // Build the program
        ProgramBuilder<SfmlAstNode, SFMLLexer, SFMLParser, SfmlAstBuilder> programBuilder = new ProgramBuilder<>(
                SFMLLexer::new,
                SFMLParser::new,
                SfmlAstBuilder::new,
                programString
        );
        ProgramBuildResult<SfmlAstNode, SFMLProgram, SFMLLexer, SFMLParser, SfmlAstBuilder, ProgramMetadata<SfmlAstNode, SFMLLexer, SFMLParser, SfmlAstBuilder>> result
                = programBuilder.build(SFMLParser::program, SfmlAstBuilder::visitProgram);

        // Perform SFML-specific checks
        result.caseSuccess(program -> checkResourceTypes(program, result.metadata().errors()));

        // Convert the result to SFML-specific types
        SFMLProgramMetadata metadata = new SFMLProgramMetadata(result.metadata());
        SFMLProgramBuildResult programBuildResult = new SFMLProgramBuildResult(result.maybeProgram(), metadata);

        // We don't cache results with errors because the server config can change, and it affects the outcome.
        if (metadata.errors().isEmpty()) {
            CACHE.put(programString, programBuildResult);
        }

        return programBuildResult;
    }

    public <PROGRAM, CONTEXT> ProgramBuildResult<SfmlAstNode, PROGRAM, SFMLLexer, SFMLParser, SfmlAstBuilder, ProgramMetadata<SfmlAstNode, SFMLLexer, SFMLParser, SfmlAstBuilder>> build(
            Function<SFMLParser, CONTEXT> contextFn,
            BiFunction<SfmlAstBuilder, CONTEXT, PROGRAM> astFn
    ) {
        // Build the program
        ProgramBuilder<SfmlAstNode, SFMLLexer, SFMLParser, SfmlAstBuilder> programBuilder = new ProgramBuilder<>(
                SFMLLexer::new,
                SFMLParser::new,
                SfmlAstBuilder::new,
                programString
        );

        return programBuilder.build(contextFn, astFn);
    }

    public <PROGRAM, CONTEXT1, CONTEXT2> ProgramBuildResult<SfmlAstNode, PROGRAM, SFMLLexer, SFMLParser, SfmlAstBuilder, ProgramMetadata<SfmlAstNode, SFMLLexer, SFMLParser, SfmlAstBuilder>> build(
            Function<SFMLParser, CONTEXT1> contextFn,
            BiFunction<SfmlAstBuilder, CONTEXT2, PROGRAM> astFn,
            Class<? extends CONTEXT2> contextClass
    ) {
        ProgramBuilder<SfmlAstNode, SFMLLexer, SFMLParser, SfmlAstBuilder> programBuilder = new ProgramBuilder<>(
                SFMLLexer::new,
                SFMLParser::new,
                SfmlAstBuilder::new,
                programString
        );

        return programBuilder.build(contextFn, (builder, context) -> {
            CONTEXT2 castedContext = contextClass.cast(context);
            return astFn.apply(builder, castedContext);
        });
    }

    public <PROGRAM, CONTEXT extends ParseTree> ProgramBuildResult<SfmlAstNode, PROGRAM, SFMLLexer, SFMLParser, SfmlAstBuilder, ProgramMetadata<SfmlAstNode, SFMLLexer, SFMLParser, SfmlAstBuilder>> build(
            Function<SFMLParser, CONTEXT> contextFn,
            Class<PROGRAM> outClass
    ) {
        ProgramBuilder<SfmlAstNode, SFMLLexer, SFMLParser, SfmlAstBuilder> programBuilder = new ProgramBuilder<>(
                SFMLLexer::new,
                SFMLParser::new,
                SfmlAstBuilder::new,
                programString
        );

        return programBuilder.build(contextFn, (builder, context) -> {
            SfmlAstNode node = builder.visit(context);
            return outClass.cast(node);
        });
    }

    public <PROGRAM> ProgramBuildResult<SfmlAstNode, PROGRAM, SFMLLexer, SFMLParser, SfmlAstBuilder, ProgramMetadata<SfmlAstNode, SFMLLexer, SFMLParser, SfmlAstBuilder>> build(
            Function<SFMLParser, ParseTree> contextFn
    ) {
        ProgramBuilder<SfmlAstNode, SFMLLexer, SFMLParser, SfmlAstBuilder> programBuilder = new ProgramBuilder<>(
                SFMLLexer::new,
                SFMLParser::new,
                SfmlAstBuilder::new,
                programString
        );

        return programBuilder.build(contextFn, (builder, context) -> {
            SfmlAstNode node = builder.visit(context);
            //noinspection unchecked
            return (PROGRAM) node;
        });
    }

    private static void checkResourceTypes(
            SFMLProgram program,
            List<TranslatableContents> errors
    ) {

        if (!SFMEnvironmentUtils.isGameLoaded()) {
            return;
        }
        List<? extends String> disallowedResourceTypes = SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.disallowedResourceTypesForTransfer);
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
