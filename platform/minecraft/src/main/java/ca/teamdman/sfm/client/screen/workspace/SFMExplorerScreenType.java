package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.action.SFMCanonicalTokenArgument;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.lazy.SFMItemRegistryExplorerResolver;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Generic selection-backed explorer scene with an optional path-expression location. */
public final class SFMExplorerScreenType implements SFMClientScreenType {
    private static final SFMPathExpression DEFAULT_LOCATION = new SFMPathExpression.Literal(
            SFMItemRegistryExplorerResolver.ROOT
    );

    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder
                .<SFMClientActionSource>literal(screenTypeId.toString())
                .executes(context -> opener.open(
                        context,
                        new Recipe(screenTypeId, DEFAULT_LOCATION)
                ));
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "path_expression",
                        SFMCanonicalTokenArgument.token()
                )
                .suggests((context, builder) -> {
                    builder.suggest(SFMItemRegistryExplorerResolver.ROOT.canonical());
                    return builder.buildFuture();
                })
                .executes(context -> opener.open(
                        context,
                        new Recipe(
                                screenTypeId,
                                SFMPathExpression.parse(SFMCanonicalTokenArgument.get(context, "path_expression"))
                        )
                )));
        return node;
    }

    public record Recipe(
            ResourceLocation sceneTypeId,
            SFMPathExpression initialLocation
    ) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId, "sceneTypeId");
            Objects.requireNonNull(initialLocation, "initialLocation");
        }

        @Override
        public SFMScreenPanel reopen() {
            return SFMExplorerRuntime.get().openScene(initialLocation);
        }
    }
}
