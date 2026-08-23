package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

/** One-shot action against the exact panel object captured by a numbered affordance. */
public final class SFMPanelEntryAction implements SFMClientAction<SFMScreenMultiplexer> {
    public enum Operation {
        FOCUS(null),
        CLOSE(null),
        MOVE_LEFT(SFMWorkspaceSide.LEFT),
        MOVE_RIGHT(SFMWorkspaceSide.RIGHT),
        MOVE_ABOVE(SFMWorkspaceSide.ABOVE),
        MOVE_BELOW(SFMWorkspaceSide.BELOW);

        private final SFMWorkspaceSide side;

        Operation(SFMWorkspaceSide side) {
            this.side = side;
        }
    }

    private static final SimpleCommandExceptionType STALE = new SimpleCommandExceptionType(
            Component.literal("That captured panel entry is stale or no longer available"));
    private static final SimpleCommandExceptionType UNAVAILABLE = new SimpleCommandExceptionType(
            Component.literal("The captured panel-entry operation is unavailable"));
    private final Operation operation;

    public SFMPanelEntryAction(Operation operation) {
        this.operation = operation;
    }

    @Override public Component title() {
        return Component.literal(switch (operation) {
            case FOCUS -> "Focus panel entry";
            case CLOSE -> "Close panel entry";
            case MOVE_LEFT -> "Move panel entry left";
            case MOVE_RIGHT -> "Move panel entry right";
            case MOVE_ABOVE -> "Move panel entry above";
            case MOVE_BELOW -> "Move panel entry below";
        });
    }

    @Override public Component description() {
        return Component.literal("Act only on the exact panel entry captured by its numbered affordance");
    }

    @Override public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return PanelActionSupport::resolve;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, Long>argument(
                        "panel-entry-session", LongArgumentType.longArg(1))
                .suggests((context, builder) -> {
                    Object host = context.getSource().context().originatingHost();
                    if (host instanceof SFMScreenMultiplexer workspace) {
                        SFMPanelEntryInteractionSessionService.activeIds(workspace)
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
        long id = LongArgumentType.getLong(context, "panel-entry-session");
        SFMPanelEntryInteractionSessionService.Session session =
                SFMPanelEntryInteractionSessionService.consume(id, workspace).orElseThrow(STALE::create);
        boolean applied = switch (operation) {
            case FOCUS -> workspace.focusPanel(session.entryId());
            case CLOSE -> workspace.closePanel(session.entryId()) == SFMWorkspacePanelIntentResult.APPLIED;
            case MOVE_LEFT, MOVE_RIGHT, MOVE_ABOVE, MOVE_BELOW ->
                    workspace.movePanel(session.entryId(), operation.side) == SFMWorkspacePanelIntentResult.APPLIED;
        };
        if (!applied) throw UNAVAILABLE.create();
        context.getSource().sendFeedback(Component.literal(
                title().getString() + ": " + session.stableId()));
        return PanelActionSupport.closePaletteAfter(1);
    }
}
