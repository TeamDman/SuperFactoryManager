package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMCanonicalTokenArgument;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.screen.history.SFMCandidateHistoryPanel;
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMTimelinePanel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Typed scene for independently scrubbing an immutable candidate route. */
public final class SFMCandidateHistoryScreenType implements SFMClientScreenType {
    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder
                .<SFMClientActionSource>literal(screenTypeId.toString())
                .executes(context -> opener.open(context, recipe(
                        screenTypeId,
                        SFMHistoryGraphScreenType.defaultEpisodeSelector(context),
                        Optional.empty(),
                        Optional.empty()
                )));
        RequiredArgumentBuilder<SFMClientActionSource, String> episode = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument("episode_selector", SFMCanonicalTokenArgument.token())
                .suggests((context, builder) -> {
                    builder.suggest("focused");
                    SFMHistoryGraphRuntime.get().machineIds().forEach(id -> builder.suggest(
                            SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, id).canonical()));
                    return builder.buildFuture();
                })
                .executes(context -> opener.open(context, recipe(
                        screenTypeId,
                        SFMCanonicalTokenArgument.get(context, "episode_selector"),
                        Optional.empty(),
                        Optional.empty()
                )));
        RequiredArgumentBuilder<SFMClientActionSource, String> plan = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument("plan_revision", SFMCanonicalTokenArgument.token())
                .suggests((context, builder) -> {
                    SFMHistoryGraphRuntime.get().snapshotEvent().machines().stream()
                            .flatMap(machine -> machine.planBook().plans().stream())
                            .map(value -> value.id())
                            .distinct()
                            .sorted()
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> opener.open(context, recipe(
                        screenTypeId,
                        SFMCanonicalTokenArgument.get(context, "episode_selector"),
                        Optional.of(SFMCanonicalTokenArgument.get(context, "plan_revision")),
                        Optional.empty()
                )));
        RequiredArgumentBuilder<SFMClientActionSource, String> route = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument("route", SFMCanonicalTokenArgument.token())
                .suggests((context, builder) -> {
                    SFMHistoryGraphRuntime.get().snapshotEvent().machines().stream()
                            .flatMap(machine -> machine.planBook().plans().stream())
                            .flatMap(value -> value.routes().stream())
                            .map(value -> value.id())
                            .distinct()
                            .sorted()
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> opener.open(context, recipe(
                        screenTypeId,
                        SFMCanonicalTokenArgument.get(context, "episode_selector"),
                        Optional.of(SFMCanonicalTokenArgument.get(context, "plan_revision")),
                        Optional.of(SFMCanonicalTokenArgument.get(context, "route"))
                )));
        plan.then(route);
        episode.then(plan);
        node.then(episode);
        return node;
    }

    private static Recipe recipe(
            ResourceLocation screenTypeId,
            String episodeSelector,
            Optional<String> planRevisionId,
            Optional<String> routeId
    ) {
        return new Recipe(screenTypeId, episodeSelector, planRevisionId, routeId);
    }

    public record Recipe(
            ResourceLocation sceneTypeId,
            String episodeSelector,
            Optional<String> planRevisionId,
            Optional<String> routeId
    ) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId, "sceneTypeId");
            SFMEntitySelector parsed = SFMEntitySelector.parseCanonical(
                    SFMEntitySelector.Domain.EPISODE,
                    Objects.requireNonNull(episodeSelector, "episodeSelector")
            );
            episodeSelector = parsed.canonical();
            planRevisionId = requireOptional(planRevisionId, "planRevisionId");
            routeId = requireOptional(routeId, "routeId");
            if (routeId.isPresent() && planRevisionId.isEmpty()) {
                throw new IllegalArgumentException("An explicit candidate route requires a plan revision");
            }
        }

        @Override
        public SFMTimelinePanel reopen() {
            return new SFMTimelinePanel(new SFMCandidateHistoryPanel(
                    SFMEntitySelector.parseCanonical(SFMEntitySelector.Domain.EPISODE, episodeSelector),
                    planRevisionId,
                    routeId
            ), 12);
        }

        private static Optional<String> requireOptional(Optional<String> value, String label) {
            Objects.requireNonNull(value, label);
            return value.map(item -> {
                if (item.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
                return item;
            });
        }
    }
}
