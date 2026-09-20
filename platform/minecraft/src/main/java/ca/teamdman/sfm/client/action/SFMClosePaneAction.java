package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePaneCloseCapture;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePaneCloseSummary;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Hierarchical pane close with an identity-sensitive preflight and constrained confirmation. */
public final class SFMClosePaneAction implements SFMClientAction<SFMScreenMultiplexer> {
    public enum Phase { PREFLIGHT, CONFIRM }

    @FunctionalInterface
    interface ChoicePresenter {
        void open(
                SFMClientActionContext context,
                Component title,
                List<SFMActionChoice> choices,
                Runnable closeListener
        );
    }

    private static final ResourceLocation CANCEL = new ResourceLocation("sfm", "palette/close");
    private static final SimpleCommandExceptionType UNAVAILABLE = new SimpleCommandExceptionType(
            Component.literal("The originating pane is no longer available"));
    private static final SimpleCommandExceptionType STALE = new SimpleCommandExceptionType(
            Component.literal("That pane-close confirmation is stale; no panel was closed"));

    private final Phase phase;
    private final ChoicePresenter choicePresenter;

    public SFMClosePaneAction(Phase phase) {
        this(phase, (context, title, choices, closeListener) ->
                SFMCommandPaletteScreen.openChoices(context, title, choices, closeListener));
    }

    SFMClosePaneAction(Phase phase, ChoicePresenter choicePresenter) {
        this.phase = phase;
        this.choicePresenter = choicePresenter;
    }

    @Override public Component title() {
        return Component.literal(phase == Phase.PREFLIGHT ? "Close pane" : "Confirm close pane");
    }

    @Override public Component description() {
        return Component.literal(phase == Phase.PREFLIGHT
                ? "Close every panel entry in one pane after an exact data-loss preflight"
                : "Close only the exact pane membership shown by the confirmation");
    }

    @Override public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return PanelActionSupport::resolve;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        if (phase == Phase.PREFLIGHT) {
            node.executes(this::invoke);
            return;
        }
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, Long>argument(
                        "pane-close-session", LongArgumentType.longArg(1))
                .suggests((context, builder) -> {
                    Object host = context.getSource().context().originatingHost();
                    if (host instanceof SFMScreenMultiplexer workspace) {
                        SFMWorkspacePaneCloseSessionService.activeIds(workspace)
                                .forEach(id -> builder.suggest(Long.toString(id)));
                    }
                    return builder.buildFuture();
                })
                .executes(this::invoke));
    }

    @Override
    public int execute(
            SFMScreenMultiplexer workspace,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        return phase == Phase.PREFLIGHT
                ? preflight(workspace, context)
                : confirm(workspace, context);
    }

    private int preflight(
            SFMScreenMultiplexer workspace,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        SFMWorkspacePanelId anchor = context.getSource().context().originatingPanelId();
        if (anchor == null) anchor = workspace.focusedPanelId();
        SFMWorkspacePaneCloseCapture capture = workspace.capturePaneClose(anchor)
                .orElseThrow(UNAVAILABLE::create);
        SFMWorkspacePaneCloseSummary summary = capture.summary();
        if (!summary.requiresConfirmation()) {
            if (workspace.closePane(capture) != SFMWorkspacePanelIntentResult.APPLIED) throw STALE.create();
            context.getSource().sendFeedback(Component.literal("Closed pane: " + summary.exactCounts()));
            return PanelActionSupport.closePaletteAfter(1);
        }

        SFMWorkspacePaneCloseSessionService.Session session =
                SFMWorkspacePaneCloseSessionService.create(workspace, capture);
        List<SFMActionChoice> choices = confirmationChoices(session, summary);
        try {
            choicePresenter.open(
                    context.getSource().context(),
                    Component.literal("Close pane? " + summary.exactCounts()),
                    choices,
                    () -> SFMWorkspacePaneCloseSessionService.invalidate(session)
            );
        } catch (RuntimeException failure) {
            SFMWorkspacePaneCloseSessionService.invalidate(session);
            throw failure;
        }
        context.getSource().sendFeedback(Component.literal(
                "Pane close requires confirmation: " + summary.exactCounts()));
        return 1;
    }

    private int confirm(
            SFMScreenMultiplexer workspace,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        long id = LongArgumentType.getLong(context, "pane-close-session");
        SFMWorkspacePaneCloseSessionService.Session session =
                SFMWorkspacePaneCloseSessionService.consume(id, workspace).orElseThrow(STALE::create);
        SFMWorkspacePaneCloseSummary summary = session.capture().summary();
        if (workspace.closePane(session.capture()) != SFMWorkspacePanelIntentResult.APPLIED) throw STALE.create();
        context.getSource().sendFeedback(Component.literal("Closed pane: " + summary.exactCounts()));
        return PanelActionSupport.closePaletteChainAfter(1, workspace);
    }

    static List<SFMActionChoice> confirmationChoices(
            SFMWorkspacePaneCloseSessionService.Session session,
            SFMWorkspacePaneCloseSummary summary
    ) {
        return List.of(
                SFMActionChoice.invoke(
                        SFMWorkspaceLifecycleActionIds.PANE_CLOSE_CONFIRM,
                        session.commandArgument(),
                        "Close pane — " + summary.exactCounts()
                ),
                SFMActionChoice.invoke(CANCEL, "", "Cancel — keep all pane entries")
        );
    }
}
