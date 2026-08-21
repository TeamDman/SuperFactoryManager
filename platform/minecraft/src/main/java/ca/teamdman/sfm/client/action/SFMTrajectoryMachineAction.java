package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMEpisodeContext;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Selector-explicit registered actions for one or more trajectory machines. */
public final class SFMTrajectoryMachineAction implements SFMClientAction<SFMClientActionContext> {
    public enum Kind {
        PLAN("episode/trajectory/plan", "Plan trajectory"),
        STEP("episode/trajectory/step", "Step trajectory"),
        RUN("episode/trajectory/run", "Run trajectory"),
        PAUSE("episode/trajectory/pause", "Pause trajectory"),
        REPLAN("episode/trajectory/replan", "Replan trajectory"),
        SELECT_ROUTE("episode/trajectory/route/select", "Select trajectory route"),
        INSPECT_COST("episode/trajectory/cost/inspect", "Inspect trajectory cost");

        private final String path;
        private final String title;

        Kind(String path, String title) {
            this.path = path;
            this.title = title;
        }

        public String path() {
            return path;
        }

        public String actionId() {
            return "sfm:" + path;
        }

        public String title() {
            return title;
        }
    }

    private final Kind kind;
    private final SFMHistoryGraphRuntime runtime;

    public SFMTrajectoryMachineAction(Kind kind) {
        this(kind, SFMHistoryGraphRuntime.get());
    }

    SFMTrajectoryMachineAction(Kind kind, SFMHistoryGraphRuntime runtime) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public Component title() {
        return Component.literal(kind.title());
    }

    @Override
    public Component description() {
        return Component.literal("Apply " + kind.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " to an explicit trajectory-machine selector");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        RequiredArgumentBuilder<SFMClientActionSource, String> selector = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument(
                        "machine_selector",
                        SFMCanonicalTokenArgument.token()
                )
                .suggests((context, builder) -> {
                    builder.suggest("focused");
                    builder.suggest("all");
                    runtime.machineIds().forEach(id -> builder.suggest(SFMEntitySelector.exact(
                            SFMEntitySelector.Domain.EPISODE,
                            id
                    ).canonical()));
                    return builder.buildFuture();
                });

        switch (kind) {
            case RUN -> {
                selector.executes(context -> invokeOperation(context, new SFMHistoryGraphRuntime.Run(32)));
                selector.then(RequiredArgumentBuilder
                        .<SFMClientActionSource, Integer>argument(
                                "max_steps",
                                IntegerArgumentType.integer(1, 1_024)
                        )
                        .executes(context -> invokeOperation(
                                context,
                                new SFMHistoryGraphRuntime.Run(IntegerArgumentType.getInteger(
                                        context, "max_steps"))
                        )));
            }
            case SELECT_ROUTE -> {
                selector.executes(this::openRouteCompletion);
                RequiredArgumentBuilder<SFMClientActionSource, String> plan = RequiredArgumentBuilder
                        .<SFMClientActionSource, String>argument(
                                "plan_revision_id",
                                SFMCanonicalTokenArgument.token()
                        )
                        .suggests((context, builder) -> {
                            matchingSnapshots(context).stream()
                                    .flatMap(snapshot -> snapshot.planBook().plans().stream())
                                    .map(SFMTrajectoryContract.TrajectoryPlanRevision::id)
                                    .distinct()
                                    .sorted()
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        });
                plan.then(RequiredArgumentBuilder
                        .<SFMClientActionSource, String>argument(
                                "route_id",
                                SFMCanonicalTokenArgument.token()
                        )
                        .suggests((context, builder) -> {
                            String planId = SFMCanonicalTokenArgument.get(context, "plan_revision_id");
                            matchingSnapshots(context).stream()
                                    .flatMap(snapshot -> snapshot.planBook().plans().stream())
                                    .filter(candidate -> candidate.id().equals(planId))
                                    .flatMap(candidate -> candidate.routes().stream())
                                    .map(SFMTrajectoryContract.TrajectoryRoute::id)
                                    .distinct()
                                    .sorted()
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> invokeOperation(
                                context,
                                new SFMHistoryGraphRuntime.SelectRoute(
                                        SFMCanonicalTokenArgument.get(context, "plan_revision_id"),
                                        SFMCanonicalTokenArgument.get(context, "route_id")
                                )
                        )));
                selector.then(plan);
            }
            case PLAN -> selector.executes(context -> invokeOperation(context, new SFMHistoryGraphRuntime.Plan()));
            case STEP -> selector.executes(context -> invokeOperation(context, new SFMHistoryGraphRuntime.Step()));
            case PAUSE -> selector.executes(context -> invokeOperation(context, new SFMHistoryGraphRuntime.Pause()));
            case REPLAN -> selector.executes(context -> invokeOperation(context, new SFMHistoryGraphRuntime.Replan()));
            case INSPECT_COST -> selector.executes(
                    context -> invokeOperation(context, new SFMHistoryGraphRuntime.InspectCost()));
        }
        node.then(selector);
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.literal(
                "Provide an explicit trajectory-machine selector")).create();
    }

    private int invokeOperation(
            CommandContext<SFMClientActionSource> context,
            SFMHistoryGraphRuntime.Operation operation
    ) throws CommandSyntaxException {
        SFMEntitySelector selector = parseSelector(context);
        SFMHistoryGraphRuntime.BatchResult result = runtime.execute(
                selector,
                focusedMachineId(context.getSource().context()),
                operation
        );
        for (SFMHistoryGraphRuntime.TargetResult target : result.targets()) {
            context.getSource().sendFeedback(Component.literal(
                    target.machineId() + ": " + target.result().message()));
        }
        if (result.targets().isEmpty()) {
            throw new SimpleCommandExceptionType(Component.literal(
                    result.diagnostics().isEmpty()
                            ? "No trajectory machine matched"
                            : result.diagnostics().get(0))).create();
        }
        if (result.targets().stream().allMatch(target ->
                target.result().status() == SFMHistoryGraphRuntime.OperationStatus.REJECTED)) {
            throw new SimpleCommandExceptionType(Component.literal(
                    result.targets().get(0).result().message())).create();
        }
        return result.targets().size();
    }

    private int openRouteCompletion(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        String selector = SFMCanonicalTokenArgument.get(context, "machine_selector");
        ResourceLocation actionId = new ResourceLocation("sfm", kind.path());
        List<SFMActionChoice> choices = routeChoices(actionId, matchingSnapshots(context));
        if (choices.isEmpty()) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "No selectable trajectory routes matched " + selector)).create();
        }
        SFMCommandPaletteScreen.openChoices(
                context.getSource().context(),
                Component.literal("Select trajectory route"),
                choices
        );
        return 1;
    }

    static List<SFMActionChoice> routeChoices(
            ResourceLocation actionId,
            List<SFMHistoryGraphRuntime.MachineSnapshot> snapshots
    ) {
        ArrayList<SFMActionChoice> choices = new ArrayList<>();
        snapshots.stream()
                .sorted(java.util.Comparator.comparing(SFMHistoryGraphRuntime.MachineSnapshot::machineId))
                .forEach(snapshot -> {
                    String exactSelector = SFMEntitySelector.exact(
                            SFMEntitySelector.Domain.EPISODE,
                            snapshot.machineId()
                    ).canonical();
                    snapshot.planBook().plans().stream()
                            .sorted(java.util.Comparator.comparing(
                                    SFMTrajectoryContract.TrajectoryPlanRevision::id))
                            .forEach(plan -> plan.routes().stream()
                                    .sorted(java.util.Comparator.comparing(
                                            SFMTrajectoryContract.TrajectoryRoute::id))
                                    .forEach(route -> choices.add(SFMActionChoice.invoke(
                                            actionId,
                                            exactSelector + " " + plan.id() + " " + route.id()
                                    ))));
                });
        return List.copyOf(choices);
    }

    private List<SFMHistoryGraphRuntime.MachineSnapshot> matchingSnapshots(
            CommandContext<SFMClientActionSource> context
    ) {
        try {
            return runtime.resolveSnapshots(
                    parseSelector(context),
                    focusedMachineId(context.getSource().context())
            );
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static SFMEntitySelector parseSelector(CommandContext<SFMClientActionSource> context) {
        return SFMEntitySelector.parseCanonical(
                SFMEntitySelector.Domain.EPISODE,
                SFMCanonicalTokenArgument.get(context, "machine_selector")
        );
    }

    private static Optional<String> focusedMachineId(SFMClientActionContext context) {
        if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)) return Optional.empty();
        if (context.originatingPanelId() == null) return Optional.empty();
        return workspace.panel(context.originatingPanelId())
                .filter(SFMEpisodeContext.class::isInstance)
                .map(SFMEpisodeContext.class::cast)
                .flatMap(SFMEpisodeContext::episodeId);
    }
}
