package ca.teamdman.sfml.program_builder;

import ca.teamdman.antlr.IProgramBuildResult;
import ca.teamdman.sfml.ast.SFMLProgram;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnusedReturnValue")
public record SFMLProgramBuildResult(
        @Nullable SFMLProgram maybeProgram,

        SFMLProgramMetadata metadata
) implements IProgramBuildResult<SFMLProgram, SFMLProgramMetadata, SFMLProgramBuildResult> {
}
