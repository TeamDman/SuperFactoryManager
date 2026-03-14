package ca.teamdman.toml.toml_builder;

import ca.teamdman.antlr.IProgramBuildResult;
import ca.teamdman.toml.ast.TomlAstNode;
import org.jetbrains.annotations.Nullable;

public record TomlProgramBuildResult(
        @Nullable TomlAstNode maybeProgram,

        TomlProgramMetadata metadata
) implements IProgramBuildResult<TomlAstNode, TomlProgramMetadata, TomlProgramBuildResult> {
}