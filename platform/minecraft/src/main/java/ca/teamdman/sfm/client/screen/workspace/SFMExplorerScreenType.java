package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.lazy.SFMItemRegistryExplorerResolver;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Generic selection-backed explorer scene with an optional concrete root. */
public final class SFMExplorerScreenType implements SFMClientScreenType {
    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder
                .<SFMClientActionSource>literal(screenTypeId.toString())
                .executes(context -> opener.open(
                        context,
                        new Recipe(screenTypeId, SFMItemRegistryExplorerResolver.ROOT)
                ));
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "initial_path",
                        StringArgumentType.word()
                )
                .suggests((context, builder) -> {
                    builder.suggest(SFMItemRegistryExplorerResolver.ROOT.canonical());
                    return builder.buildFuture();
                })
                .executes(context -> opener.open(
                        context,
                        new Recipe(
                                screenTypeId,
                                concretePath(StringArgumentType.getString(context, "initial_path"))
                        )
                )));
        return node;
    }

    public record Recipe(ResourceLocation sceneTypeId, SFMPath initialRoot) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId, "sceneTypeId");
            Objects.requireNonNull(initialRoot, "initialRoot");
        }

        @Override
        public SFMScreenPanel reopen() {
            return SFMExplorerRuntime.get().openScene(initialRoot);
        }
    }

    private static SFMPath concretePath(String text) {
        SFMPathExpression expression = SFMPathExpression.parse(text);
        if (!(expression instanceof SFMPathExpression.Literal literal)) {
            throw new IllegalArgumentException("An explorer scene requires one concrete root path");
        }
        return literal.path();
    }
}
