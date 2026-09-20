package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.SFMEpisodeContext;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualController;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualRuntime;
import ca.teamdman.sfm.client.screen.history.workspace.SFMWorkspaceCounterfactualDocumentPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Selector-explicit actions for the bounded whole-workspace counterfactual fixture. */
public final class SFMWorkspaceCounterfactualAction implements SFMClientAction<SFMClientActionContext> {
    public enum Kind {
        SELECTION_SET("episode/workspace-counterfactual/selection/set", "Select fixture document"),
        FORK_BEFORE_SELECTION("episode/workspace-counterfactual/fork/before-selection", "Fork before selection"),
        CHECKOUT_RECORDED_A("episode/workspace-counterfactual/checkout/recorded-a", "Checkout recorded A result"),
        REPLAY_FROZEN_A("episode/workspace-counterfactual/replay/frozen-a", "Replay frozen A witness"),
        REEVALUATE_SELECTED(
                "episode/workspace-counterfactual/replay/reevaluate-selected",
                "Re-evaluate intent against selected document"
        );

        private final String path;
        private final String title;

        Kind(String path, String title) {
            this.path = path;
            this.title = title;
        }

        public String path() { return path; }
        public String title() { return title; }
    }

    private final Kind kind;
    private final SFMWorkspaceCounterfactualRuntime runtime;

    public SFMWorkspaceCounterfactualAction(Kind kind) {
        this(kind, SFMWorkspaceCounterfactualRuntime.get());
    }

    SFMWorkspaceCounterfactualAction(Kind kind, SFMWorkspaceCounterfactualRuntime runtime) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override public Component title() { return Component.literal(kind.title()); }
    @Override public Component description() {
        return Component.literal("Apply " + kind.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " to an explicit whole-workspace episode selector");
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
                .<SFMClientActionSource, String>argument("episode_selector", SFMCanonicalTokenArgument.token())
                .suggests((context, builder) -> {
                    builder.suggest("focused");
                    builder.suggest("all");
                    runtime.episodeIds().forEach(id -> builder.suggest(SFMEntitySelector.exact(
                            SFMEntitySelector.Domain.EPISODE,
                            id
                    ).canonical()));
                    return builder.buildFuture();
                });
        if (kind == Kind.SELECTION_SET) {
            selector.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("logical_path", SFMCanonicalTokenArgument.token())
                    .suggests((context, builder) -> {
                        builder.suggest(SFMWorkspaceCounterfactualController.A_PATH);
                        builder.suggest(SFMWorkspaceCounterfactualController.B_PATH);
                        return builder.buildFuture();
                    })
                    .executes(this::select));
        } else {
            selector.executes(this::operate);
        }
        node.then(selector);
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.literal(
                "Provide an explicit whole-workspace episode selector")).create();
    }

    private int select(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        String logicalPath = SFMCanonicalTokenArgument.get(context, "logical_path");
        return present(context, runtime.select(
                selector(context),
                focusedEpisode(context.getSource().context()),
                logicalPath
        ));
    }

    private int operate(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        SFMWorkspaceCounterfactualRuntime.OperationKind operation = switch (kind) {
            case FORK_BEFORE_SELECTION -> SFMWorkspaceCounterfactualRuntime.OperationKind.FORK_BEFORE_SELECTION;
            case CHECKOUT_RECORDED_A -> SFMWorkspaceCounterfactualRuntime.OperationKind.CHECKOUT_RECORDED_A;
            case REPLAY_FROZEN_A -> SFMWorkspaceCounterfactualRuntime.OperationKind.REPLAY_FROZEN_A;
            case REEVALUATE_SELECTED -> SFMWorkspaceCounterfactualRuntime.OperationKind.REEVALUATE_INTENT_ON_B;
            case SELECTION_SET -> throw new IllegalStateException("selection uses its dedicated command branch");
        };
        int affected = present(context, runtime.execute(
                selector(context),
                focusedEpisode(context.getSource().context()),
                operation
        ));
        if (kind == Kind.FORK_BEFORE_SELECTION && affected > 0) closeInvokingDocument(context.getSource().context());
        return affected;
    }

    private static int present(
            CommandContext<SFMClientActionSource> context,
            List<SFMWorkspaceCounterfactualRuntime.TargetResult> results
    ) throws CommandSyntaxException {
        for (var target : results) {
            context.getSource().sendFeedback(Component.literal(
                    target.episodeId() + ": " + target.result().message()));
        }
        if (results.isEmpty()) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "No whole-workspace counterfactual episode matched")).create();
        }
        if (results.stream().allMatch(target ->
                target.result().status() == SFMHistoryGraphRuntime.OperationStatus.REJECTED)) {
            throw new SimpleCommandExceptionType(Component.literal(results.get(0).result().message())).create();
        }
        return results.size();
    }

    private static SFMEntitySelector selector(CommandContext<SFMClientActionSource> context) {
        return SFMEntitySelector.parseCanonical(
                SFMEntitySelector.Domain.EPISODE,
                SFMCanonicalTokenArgument.get(context, "episode_selector")
        );
    }

    private static Optional<String> focusedEpisode(SFMClientActionContext context) {
        if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                || context.originatingPanelId() == null) return Optional.empty();
        return workspace.panel(context.originatingPanelId())
                .filter(SFMEpisodeContext.class::isInstance)
                .map(SFMEpisodeContext.class::cast)
                .flatMap(SFMEpisodeContext::episodeId);
    }

    private static void closeInvokingDocument(SFMClientActionContext context) {
        if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                || context.originatingPanelId() == null) return;
        if (workspace.panelInstance(context.originatingPanelId()) instanceof SFMWorkspaceCounterfactualDocumentPanel) {
            workspace.closePanel(context.originatingPanelId());
        }
    }
}
