package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMCanonicalTokenArgument;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.SFMEpisodeContext;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.screen.history.SFMHistoryGraphPanel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Typed scene for the live trajectory-machine History Graph. */
public final class SFMHistoryGraphScreenType implements SFMClientScreenType {
    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder
                .<SFMClientActionSource>literal(screenTypeId.toString())
                .executes(context -> opener.open(context, new Recipe(
                        screenTypeId,
                        defaultEpisodeSelector(context)
                )));
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "episode_selector",
                        SFMCanonicalTokenArgument.token()
                )
                .suggests((context, builder) -> {
                    builder.suggest("focused");
                    SFMHistoryGraphRuntime.get().machineIds().forEach(id -> builder.suggest(
                            SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, id).canonical()));
                    return builder.buildFuture();
                })
                .executes(context -> opener.open(context, new Recipe(
                        screenTypeId,
                        SFMCanonicalTokenArgument.get(context, "episode_selector")
                ))));
        return node;
    }

    static String defaultEpisodeSelector(CommandContext<SFMClientActionSource> context) {
        var actionContext = context.getSource().context();
        Optional<String> focusedEpisode = Optional.empty();
        if (actionContext.originatingHost() instanceof SFMScreenMultiplexer workspace
                && actionContext.originatingPanelId() != null) {
            focusedEpisode = workspace.panel(actionContext.originatingPanelId())
                    .filter(SFMEpisodeContext.class::isInstance)
                    .map(SFMEpisodeContext.class::cast)
                    .flatMap(SFMEpisodeContext::episodeId);
        }
        return canonicalDefaultSelector(focusedEpisode);
    }

    static String canonicalDefaultSelector(Optional<String> focusedEpisode) {
        return focusedEpisode.map(id -> SFMEntitySelector.exact(
                        SFMEntitySelector.Domain.EPISODE,
                        id
                ).canonical())
                .orElse("focused");
    }

    public record Recipe(
            ResourceLocation sceneTypeId,
            String episodeSelector
    ) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId, "sceneTypeId");
            Objects.requireNonNull(episodeSelector, "episodeSelector");
            SFMEntitySelector parsed = SFMEntitySelector.parseCanonical(
                    SFMEntitySelector.Domain.EPISODE,
                    episodeSelector
            );
            episodeSelector = parsed.canonical();
        }

        @Override
        public SFMHistoryGraphPanel reopen() {
            return new SFMHistoryGraphPanel(SFMEntitySelector.parseCanonical(
                    SFMEntitySelector.Domain.EPISODE,
                    episodeSelector
            ));
        }
    }
}
