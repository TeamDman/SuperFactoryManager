package ca.teamdman.sfml.program_builder;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfm.common.util.SFMTranslationUtils;
import ca.teamdman.sfml.ast.ASTBuilder;
import ca.teamdman.sfml.ast.Program;
{% if features.sfml_execution_side %}
import ca.teamdman.sfml.ast.ProgramExecutionSide;
{% endif %}
import ca.teamdman.sfml.ast.ResourceIdentifier;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.IdentifierException;
{% else %}
import net.minecraft.ResourceLocationException;
{% endcase %}
import net.minecraft.network.chat.contents.TranslatableContents;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
{% if features.sfml_execution_side %}
import java.util.HashMap;
{% endif %}
import java.util.List;
{% if features.sfml_execution_side %}
import java.util.Map;
{% endif %}
import java.util.Objects;
import java.util.WeakHashMap;

/// Helper for building programs and acquiring a {@link ProgramBuildResult}
public class ProgramBuilder {
    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_ERROR_MALFORMED_RESOURCE_TYPE = new LocalizationEntry(
            "program.sfm.error.malformed_resource_type",
            "Program has a malformed resource type \"%s\".\nReminder: Resource types must be literals, not wildcards."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_ERROR_UNKNOWN_RESOURCE_TYPE = new LocalizationEntry(
            "program.sfm.error.unknown_resource_type",
            "Program references an unknown resource type \"%s\""
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_ERROR_DISALLOWED_RESOURCE_TYPE = new LocalizationEntry(
            "program.sfm.error.disallowed_resource_type",
            "Program references a disallowed resource type \"%s\""
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_ERROR_COMPILE_FAILED = new LocalizationEntry(
            "program.sfm.error.compile_failed",
            "Failed to compile."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_ERROR_LITERAL = new LocalizationEntry(
            "program.sfm.error.literal",
            "%s"
    );

    /// Reduce duplication of effort compiling the same program over and over again
{% if features.sfml_execution_side %}
    private static final WeakHashMap<String, Map<ProgramExecutionSide, CachedProgramBuildResult>> cache = new WeakHashMap<>();
{% else %}
    private static final WeakHashMap<String, CachedProgramBuildResult> cache = new WeakHashMap<>();
{% endif %}

    private record CachedProgramBuildResult(
            int serverConfigRevision,
            ProgramBuildResult result
    ) {
    }

    /// The Super Factory Manager Language source code
    private final String programString;

    /// Indicates that the resulting program may be mutated in naughty ways that we don't want interfering with our cache.
    private boolean useCache = true;

{% if features.sfml_execution_side %}
    /** Null means a portable syntax-only build; an actual manager supplies its logical host. */
    private @Nullable ProgramExecutionSide executionSide;

{% endif %}
    public ProgramBuilder(@Nullable String programString) {

        if (programString == null) {
            programString = "";
        }
        this.programString = programString;
    }

    /// Checks if the program object is stored in the cache.
    /// If so, mutating the program object is a disallowed behaviour.
    public static boolean isMutationAllowed(Program program) {

{% if features.sfml_execution_side %}
        return cache.values().stream()
                .flatMap(bySide -> bySide.values().stream())
                .noneMatch(cached -> cached.result().program() == program);
{% else %}
        return cache.values().stream().noneMatch(cached -> cached.result().program() == program);
{% endif %}
    }

    public static void clearCache() {

        cache.clear();
    }

    /// MUST be set to {@code false} if the resulting {@link Program} will be mutated.
    public ProgramBuilder useCache(boolean useCache) {

        this.useCache = useCache;
        return this;
    }

{% if features.sfml_execution_side %}
    public ProgramBuilder forExecutionSide(ProgramExecutionSide side) {
        this.executionSide = Objects.requireNonNull(side, "side");
        return this;
    }

{% endif %}
    public ProgramBuildResult build(
    ) {

        int serverConfigRevision = SFMConfig.SERVER_CONFIG.getRevision();
        if (useCache) {
{% if features.sfml_execution_side %}
            Map<ProgramExecutionSide, CachedProgramBuildResult> bySide = cache.get(programString);
            @Nullable CachedProgramBuildResult cached = bySide == null ? null : bySide.get(executionSide);
{% else %}
            @Nullable CachedProgramBuildResult cached = cache.get(programString);
{% endif %}
            if (cached != null) {
                if (cached.serverConfigRevision() != serverConfigRevision) {
                    SFM.LOGGER.debug(
                            "Program cache hit for server config revision {}, but current revision is {}. Will rebuild program.",
                            cached.serverConfigRevision(),
                            serverConfigRevision
                    );
                } else if (cached.result().metadata().errors().isEmpty()) {
                    return cached.result();
                } else {
                    SFM.LOGGER.warn(
                            "Program cache hit, but the program build result contained errors. Will rebuild program."
                    );
                }
            }
        }
        SFMLLexer lexer = new SFMLLexer(CharStreams.fromString(programString));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SFMLParser parser = new SFMLParser(tokens);
        ASTBuilder builder = new ASTBuilder();

        // set up error capturing
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        List<TranslatableContents> errors = new ArrayList<>();
        List<String> buildErrors = new ArrayList<>();
        ListErrorListener listener = new ListErrorListener(buildErrors);
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);

        // initial parse
        SFMLParser.ProgramContext context = parser.program();
        buildErrors.stream().map(PROGRAM_ERROR_LITERAL::get).forEach(errors::add);


        // build program from AST only when there are no errors from previous phases
        @Nullable Program program = null;
        if (errors.isEmpty()) {
            try {
                program = builder.visitProgram(context);
{% if features.sfml_execution_side %}
                if (executionSide != null) {
                    program.assertCompatibleWith(executionSide);
                }
{% endif %}
                // Make sure all referenced resources are valid during compilation instead of waiting for the program to tick
                checkResourceTypes(program, errors);
{% case minecraft_version %}
{% when "26.1.2" %}
            } catch (IdentifierException | IllegalArgumentException | AssertionError e) {
{% else %}
            } catch (ResourceLocationException | IllegalArgumentException | AssertionError e) {
{% endcase %}
                errors.add(PROGRAM_ERROR_LITERAL.get(e.getMessage()));
            } catch (Throwable t) {
                errors.add(PROGRAM_ERROR_COMPILE_FAILED.get());
                SFM.LOGGER.warn(
                        "Encountered unhandled error while compiling program\n```\n{}\n```",
                        programString,
                        t
                );
                var message = t.getMessage();
                if (message != null) {
                    errors.add(SFMTranslationUtils.getTranslatableContents(
                            t.getClass().getSimpleName() + ": " + message
                    ));
                } else {
                    errors.add(SFMTranslationUtils.getTranslatableContents(t.getClass().getSimpleName()));
                }
            }
        }

        ProgramMetadata metadata = new ProgramMetadata(
                programString,
                lexer,
                tokens,
                parser,
                builder,
                errors
        );
        ProgramBuildResult programBuildResult = new ProgramBuildResult(program, metadata);

        // We don't cache results with errors because the server config can change, and it affects the outcome.
        if (useCache && errors.isEmpty()) {
{% if features.sfml_execution_side %}
            cache.computeIfAbsent(programString, ignored -> new HashMap<>())
                    .put(executionSide, new CachedProgramBuildResult(serverConfigRevision, programBuildResult));
{% else %}
            cache.put(programString, new CachedProgramBuildResult(serverConfigRevision, programBuildResult));
{% endif %}
        }

        return programBuildResult;
    }


    private static void checkResourceTypes(
            Program program,
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
                    errors.add(PROGRAM_ERROR_UNKNOWN_RESOURCE_TYPE.get(
                            referencedResource));
                } else {
{% case minecraft_version %}
{% when "26.1.2" %}
                    Identifier resourceTypeId = Objects.requireNonNull(SFMResourceTypes
{% else %}
                    ResourceLocation resourceTypeId = Objects.requireNonNull(SFMResourceTypes
{% endcase %}
                                                                                     .registry()
                                                                                     .getId(resourceType));
                    if (disallowedResourceTypes.contains(resourceTypeId.toString())) {
                        errors.add(PROGRAM_ERROR_DISALLOWED_RESOURCE_TYPE.get(
                                referencedResource));
                    }
                }
{% case minecraft_version %}
{% when "26.1.2" %}
            } catch (IdentifierException e) {
{% else %}
            } catch (ResourceLocationException e) {
{% endcase %}
                errors.add(PROGRAM_ERROR_MALFORMED_RESOURCE_TYPE.get(
                        referencedResource));
            }
        }
    }

}
