package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMRouteComparisonRuntime;
import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession;
import ca.teamdman.sfm.client.screen.history.SFMRouteComparisonPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Palette adapter for persisted route-comparison review state and explicit route selection. */
public final class SFMRouteComparisonAction implements SFMClientAction<SFMClientActionContext> {
    public enum Kind {
        MODE_SET("episode/route-comparison/mode/set", "Set route comparison mode"),
        SEEK("episode/route-comparison/seek", "Seek route comparison"),
        DISPOSITION_SET("episode/route-comparison/disposition/set", "Set route disposition"),
        TRAJECTORY_SELECT("episode/route-comparison/trajectory/select", "Select compared trajectory route");

        private final String path;
        private final String title;

        Kind(String path, String title) {
            this.path = path;
            this.title = title;
        }

        public String path() {
            return path;
        }

        public String title() {
            return title;
        }
    }

    private final Kind kind;
    private final SFMRouteComparisonRuntime comparisonRuntime;
    private final SFMHistoryGraphRuntime historyRuntime;

    public SFMRouteComparisonAction(Kind kind) {
        this(kind, SFMRouteComparisonRuntime.get(), SFMHistoryGraphRuntime.get());
    }

    SFMRouteComparisonAction(
            Kind kind,
            SFMRouteComparisonRuntime comparisonRuntime,
            SFMHistoryGraphRuntime historyRuntime
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.comparisonRuntime = Objects.requireNonNull(comparisonRuntime, "comparisonRuntime");
        this.historyRuntime = Objects.requireNonNull(historyRuntime, "historyRuntime");
    }

    @Override
    public Component title() {
        return Component.literal(kind.title());
    }

    @Override
    public Component description() {
        return Component.literal(switch (kind) {
            case MODE_SET -> "Switch a persisted route comparison between lockstep and independent cursors";
            case SEEK -> "Seek one or both persisted candidate-route cursors";
            case DISPOSITION_SET -> "Record review-only preferred, rejected, or undecided metadata";
            case TRAJECTORY_SELECT -> "Explicitly select one compared route on its trajectory machine";
        });
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
                        "comparison_selector",
                        SFMCanonicalTokenArgument.token()
                )
                .suggests((context, builder) -> {
                    builder.suggest("focused");
                    comparisonRuntime.loadedSessionIds().forEach(id -> {
                        builder.suggest(id);
                        builder.suggest("id(" + id + ")");
                    });
                    return builder.buildFuture();
                });

        switch (kind) {
            case MODE_SET -> selector.then(enumArgument(
                    "mode",
                    SFMRouteComparisonSession.Mode.values()
            ).executes(this::setMode));
            case SEEK -> {
                RequiredArgumentBuilder<SFMClientActionSource, String> side = enumArgument(
                        "side",
                        SFMRouteComparisonSession.Side.values()
                );
                side.then(RequiredArgumentBuilder
                        .<SFMClientActionSource, Integer>argument(
                                "position",
                                IntegerArgumentType.integer(0)
                        )
                        .executes(this::seek));
                selector.then(side);
            }
            case DISPOSITION_SET -> {
                RequiredArgumentBuilder<SFMClientActionSource, String> side = sideArgument("side");
                side.then(enumArgument(
                        "disposition",
                        SFMRouteComparisonSession.Disposition.values()
                ).executes(this::setDisposition));
                selector.then(side);
            }
            case TRAJECTORY_SELECT -> selector.then(
                    sideArgument("side").executes(this::selectTrajectoryRoute));
        }
        node.then(selector);
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.literal(
                "Provide a comparison selector and the required operation arguments"
        )).create();
    }

    private int setMode(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        try {
            SFMRouteComparisonSession updated = comparisonRuntime.setMode(
                    resolveSessionId(context),
                    enumValue(context, "mode", SFMRouteComparisonSession.Mode.class)
            );
            context.getSource().sendFeedback(Component.literal(
                    updated.id() + ": mode " + lower(updated.mode()) + " · revision " + updated.revision()));
            return 1;
        } catch (RuntimeException failure) {
            throw commandFailure(failure);
        }
    }

    private int seek(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        try {
            SFMRouteComparisonSession updated = comparisonRuntime.seek(
                    resolveSessionId(context),
                    enumValue(context, "side", SFMRouteComparisonSession.Side.class),
                    IntegerArgumentType.getInteger(context, "position")
            );
            context.getSource().sendFeedback(Component.literal(
                    updated.id() + ": left " + updated.leftCursor()
                            + " · right " + updated.rightCursor()
                            + " · " + lower(updated.mode())));
            return 1;
        } catch (RuntimeException failure) {
            throw commandFailure(failure);
        }
    }

    private int setDisposition(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        try {
            SFMRouteComparisonSession updated = comparisonRuntime.setDisposition(
                    resolveSessionId(context),
                    singleSide(context),
                    enumValue(context, "disposition", SFMRouteComparisonSession.Disposition.class)
            );
            context.getSource().sendFeedback(Component.literal(
                    updated.id() + ": left " + lower(updated.leftDisposition())
                            + " · right " + lower(updated.rightDisposition())));
            return 1;
        } catch (RuntimeException failure) {
            throw commandFailure(failure);
        }
    }

    private int selectTrajectoryRoute(
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        try {
            String sessionId = resolveSessionId(context);
            SFMRouteComparisonSession session = comparisonRuntime.require(sessionId);
            SFMRouteComparisonSession.Side side = singleSide(context);
            SFMRouteComparisonSession.RouteAddress address = session.address(side);
            SFMHistoryGraphRuntime.BatchResult result = historyRuntime.execute(
                    SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, address.machineId()),
                    Optional.of(address.machineId()),
                    new SFMHistoryGraphRuntime.SelectRoute(
                            address.planRevisionId(),
                            address.routeId()
                    )
            );
            if (result.targets().isEmpty()) {
                throw new IllegalArgumentException(result.diagnostics().isEmpty()
                        ? "Compared trajectory machine is unavailable"
                        : result.diagnostics().get(0));
            }
            for (SFMHistoryGraphRuntime.TargetResult target : result.targets()) {
                context.getSource().sendFeedback(Component.literal(
                        target.machineId() + ": " + target.result().message()));
            }
            if (result.targets().stream().allMatch(target ->
                    target.result().status() == SFMHistoryGraphRuntime.OperationStatus.REJECTED)) {
                throw new IllegalArgumentException(result.targets().get(0).result().message());
            }
            return result.targets().size();
        } catch (RuntimeException failure) {
            throw commandFailure(failure);
        }
    }

    private String resolveSessionId(CommandContext<SFMClientActionSource> context) {
        return comparisonRuntime.resolveSelector(
                SFMCanonicalTokenArgument.get(context, "comparison_selector"),
                focusedSessionId(context.getSource().context())
        );
    }

    static Optional<String> focusedSessionId(SFMClientActionContext context) {
        if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                || context.originatingPanelId() == null) return Optional.empty();
        return workspace.panel(context.originatingPanelId())
                .filter(SFMRouteComparisonPanel.class::isInstance)
                .map(SFMRouteComparisonPanel.class::cast)
                .map(SFMRouteComparisonPanel::sessionId);
    }

    private static RequiredArgumentBuilder<SFMClientActionSource, String> sideArgument(String name) {
        return RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        name,
                        SFMCanonicalTokenArgument.token()
                )
                .suggests((context, builder) -> {
                    builder.suggest("left");
                    builder.suggest("right");
                    return builder.buildFuture();
                });
    }

    private static <E extends Enum<E>> RequiredArgumentBuilder<SFMClientActionSource, String> enumArgument(
            String name,
            E[] values
    ) {
        return RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        name,
                        SFMCanonicalTokenArgument.token()
                )
                .suggests((context, builder) -> {
                    for (E value : values) builder.suggest(lower(value));
                    return builder.buildFuture();
                });
    }

    private static <E extends Enum<E>> E enumValue(
            CommandContext<SFMClientActionSource> context,
            String name,
            Class<E> type
    ) {
        String value = SFMCanonicalTokenArgument.get(context, name).toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Invalid " + name + " " + value.toLowerCase(Locale.ROOT));
        }
    }

    private static SFMRouteComparisonSession.Side singleSide(
            CommandContext<SFMClientActionSource> context
    ) {
        SFMRouteComparisonSession.Side side = enumValue(
                context,
                "side",
                SFMRouteComparisonSession.Side.class
        );
        if (side == SFMRouteComparisonSession.Side.BOTH) {
            throw new IllegalArgumentException("This operation requires left or right");
        }
        return side;
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static CommandSyntaxException commandFailure(RuntimeException failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
}
