package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMCanonicalTokenArgument;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMRouteComparisonRuntime;
import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession;
import ca.teamdman.sfm.client.registry.SFMClientScreenTypes;
import ca.teamdman.sfm.client.screen.history.SFMRouteComparisonPanel;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Typed scene for a persisted side-by-side comparison of two retained routes. */
public final class SFMRouteComparisonScreenType implements SFMClientScreenType {
    public static final ResourceLocation ID = new ResourceLocation(SFM.MOD_ID, "episode/route-comparison");

    private final SFMHistoryGraphRuntime historyRuntime;
    private final SFMRouteComparisonRuntime comparisonRuntime;

    public SFMRouteComparisonScreenType() {
        this(SFMHistoryGraphRuntime.get(), SFMRouteComparisonRuntime.get());
    }

    SFMRouteComparisonScreenType(
            SFMHistoryGraphRuntime historyRuntime,
            SFMRouteComparisonRuntime comparisonRuntime
    ) {
        this.historyRuntime = Objects.requireNonNull(historyRuntime, "historyRuntime");
        this.comparisonRuntime = Objects.requireNonNull(comparisonRuntime, "comparisonRuntime");
    }

    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder
                .<SFMClientActionSource>literal(screenTypeId.toString())
                .executes(context -> openLatest(
                        context,
                        opener,
                        screenTypeId,
                        SFMHistoryGraphScreenType.defaultEpisodeSelector(context)
                ));

        RequiredArgumentBuilder<SFMClientActionSource, String> episode = token("episode_selector")
                .suggests((context, builder) -> {
                    builder.suggest("focused");
                    historyRuntime.machineIds().forEach(id -> builder.suggest(
                            SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, id).canonical()));
                    return builder.buildFuture();
                })
                .executes(context -> openLatest(
                        context,
                        opener,
                        screenTypeId,
                        tokenValue(context, "episode_selector")
                ));

        RequiredArgumentBuilder<SFMClientActionSource, String> leftPlan = token("left_plan_revision")
                .suggests((context, builder) -> {
                    matchingSnapshots(context).stream()
                            .flatMap(snapshot -> snapshot.planBook().plans().stream())
                            .map(value -> value.id())
                            .distinct()
                            .sorted()
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
        RequiredArgumentBuilder<SFMClientActionSource, String> leftRoute = token("left_route")
                .suggests((context, builder) -> {
                    routesForPlan(context, tokenValue(context, "left_plan_revision"))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
        RequiredArgumentBuilder<SFMClientActionSource, String> rightPlan = token("right_plan_revision")
                .suggests((context, builder) -> {
                    matchingSnapshots(context).stream()
                            .flatMap(snapshot -> snapshot.planBook().plans().stream())
                            .map(value -> value.id())
                            .distinct()
                            .sorted()
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
        RequiredArgumentBuilder<SFMClientActionSource, String> rightRoute = token("right_route")
                .suggests((context, builder) -> {
                    routesForPlan(context, tokenValue(context, "right_plan_revision"))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> openExplicit(context, opener, screenTypeId, Optional.empty()));
        RequiredArgumentBuilder<SFMClientActionSource, String> sessionId = token("session_id")
                .executes(context -> openExplicit(
                        context,
                        opener,
                        screenTypeId,
                        Optional.of(tokenValue(context, "session_id"))
                ));

        rightRoute.then(sessionId);
        rightPlan.then(rightRoute);
        leftRoute.then(rightPlan);
        leftPlan.then(leftRoute);
        episode.then(leftPlan);
        node.then(episode);
        return node;
    }

    public static void register(IEventBus bus) {
        Registration.REGISTERER.register(bus);
    }

    public static SFMRegistryObject<SFMClientScreenType, SFMRouteComparisonScreenType> registration() {
        return Registration.SCREEN_TYPE;
    }

    private int openLatest(
            CommandContext<SFMClientActionSource> context,
            Opener opener,
            ResourceLocation screenTypeId,
            String selectorText
    ) throws CommandSyntaxException {
        SFMHistoryGraphRuntime.MachineSnapshot snapshot = requireOneSnapshot(context, selectorText);
        try {
            SFMRouteComparisonSession session = comparisonRuntime.openLatest(snapshot);
            return opener.open(context, new Recipe(screenTypeId, session.id()));
        } catch (RuntimeException failure) {
            throw commandFailure(failure);
        }
    }

    private int openExplicit(
            CommandContext<SFMClientActionSource> context,
            Opener opener,
            ResourceLocation screenTypeId,
            Optional<String> requestedSessionId
    ) throws CommandSyntaxException {
        SFMHistoryGraphRuntime.MachineSnapshot snapshot = requireOneSnapshot(
                context,
                tokenValue(context, "episode_selector")
        );
        try {
            SFMRouteComparisonSession session = comparisonRuntime.open(
                    snapshot,
                    new SFMRouteComparisonSession.RouteAddress(
                            snapshot.machineId(),
                            tokenValue(context, "left_plan_revision"),
                            tokenValue(context, "left_route")
                    ),
                    new SFMRouteComparisonSession.RouteAddress(
                            snapshot.machineId(),
                            tokenValue(context, "right_plan_revision"),
                            tokenValue(context, "right_route")
                    ),
                    requestedSessionId
            );
            return opener.open(context, new Recipe(screenTypeId, session.id()));
        } catch (RuntimeException failure) {
            throw commandFailure(failure);
        }
    }

    private SFMHistoryGraphRuntime.MachineSnapshot requireOneSnapshot(
            CommandContext<SFMClientActionSource> context,
            String selectorText
    ) throws CommandSyntaxException {
        final SFMEntitySelector selector;
        try {
            selector = SFMEntitySelector.parseCanonical(SFMEntitySelector.Domain.EPISODE, selectorText);
        } catch (RuntimeException failure) {
            throw commandFailure(failure);
        }
        List<SFMHistoryGraphRuntime.MachineSnapshot> matches = historyRuntime.resolveSnapshots(
                selector,
                focusedMachineId(context)
        );
        if (matches.size() != 1) {
            throw new SimpleCommandExceptionType(Component.literal(matches.isEmpty()
                    ? "No trajectory machine matched " + selector.canonical()
                    : "Route comparison requires exactly one trajectory machine; matched " + matches.size()
            )).create();
        }
        return matches.get(0);
    }

    private List<SFMHistoryGraphRuntime.MachineSnapshot> matchingSnapshots(
            CommandContext<SFMClientActionSource> context
    ) {
        try {
            SFMEntitySelector selector = SFMEntitySelector.parseCanonical(
                    SFMEntitySelector.Domain.EPISODE,
                    tokenValue(context, "episode_selector")
            );
            return historyRuntime.resolveSnapshots(selector, focusedMachineId(context));
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<String> routesForPlan(CommandContext<SFMClientActionSource> context, String planId) {
        return matchingSnapshots(context).stream()
                .flatMap(snapshot -> snapshot.planBook().plans().stream())
                .filter(plan -> plan.id().equals(planId))
                .flatMap(plan -> plan.routes().stream())
                .map(route -> route.id())
                .distinct()
                .sorted()
                .toList();
    }

    private static Optional<String> focusedMachineId(CommandContext<SFMClientActionSource> context) {
        var actionContext = context.getSource().context();
        if (!(actionContext.originatingHost() instanceof SFMScreenMultiplexer workspace)
                || actionContext.originatingPanelId() == null) return Optional.empty();
        return workspace.panel(actionContext.originatingPanelId())
                .filter(ca.teamdman.sfm.client.history.SFMEpisodeContext.class::isInstance)
                .map(ca.teamdman.sfm.client.history.SFMEpisodeContext.class::cast)
                .flatMap(ca.teamdman.sfm.client.history.SFMEpisodeContext::episodeId);
    }

    private static RequiredArgumentBuilder<SFMClientActionSource, String> token(String name) {
        return RequiredArgumentBuilder.argument(name, SFMCanonicalTokenArgument.token());
    }

    private static String tokenValue(CommandContext<SFMClientActionSource> context, String name) {
        return SFMCanonicalTokenArgument.get(context, name);
    }

    private static CommandSyntaxException commandFailure(RuntimeException failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }

    public record Recipe(ResourceLocation sceneTypeId, String sessionId) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId, "sceneTypeId");
            Objects.requireNonNull(sessionId, "sessionId");
            if (sessionId.isBlank()) throw new IllegalArgumentException("sessionId must not be blank");
        }

        @Override
        public SFMRouteComparisonPanel reopen() {
            return new SFMRouteComparisonPanel(sessionId);
        }
    }

    /** Keeps registry bootstrap lazy so the typed grammar remains unit-testable in isolation. */
    private static final class Registration {
        private static final SFMDeferredRegister<SFMClientScreenType> REGISTERER =
                SFMClientScreenTypes.createContributor(SFM.MOD_ID);
        private static final SFMRegistryObject<SFMClientScreenType, SFMRouteComparisonScreenType> SCREEN_TYPE =
                REGISTERER.register(ID.getPath(), SFMRouteComparisonScreenType::new);
    }
}
