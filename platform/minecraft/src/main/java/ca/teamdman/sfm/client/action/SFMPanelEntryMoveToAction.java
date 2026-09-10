package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

/** One-shot action for moving one exact panel entry into another exact pane. */
public final class SFMPanelEntryMoveToAction implements SFMClientAction<SFMScreenMultiplexer> {
    private static final SimpleCommandExceptionType STALE = new SimpleCommandExceptionType(
            Component.literal("One of the captured panel entries is stale or no longer available"));
    private static final SimpleCommandExceptionType UNAVAILABLE = new SimpleCommandExceptionType(
            Component.literal("The captured panel-entry destination is unavailable"));

    @Override public Component title() {
        return Component.literal("Move panel entry into pane");
    }

    @Override public Component description() {
        return Component.literal("Move one exact captured panel entry into another exact captured pane");
    }

    @Override public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return PanelActionSupport::resolve;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(sessionArgument("source-panel-entry-session")
                .then(sessionArgument("destination-panel-entry-session")
                        .executes(this::invoke)));
    }

    private static RequiredArgumentBuilder<SFMClientActionSource, Long> sessionArgument(String name) {
        return RequiredArgumentBuilder.<SFMClientActionSource, Long>argument(name, LongArgumentType.longArg(1))
                .suggests((context, builder) -> {
                    Object host = context.getSource().context().originatingHost();
                    if (host instanceof SFMScreenMultiplexer workspace) {
                        SFMPanelEntryInteractionSessionService.activeIds(workspace)
                                .forEach(id -> builder.suggest(Long.toString(id)));
                    }
                    return builder.buildFuture();
                });
    }

    @Override
    public int execute(
            SFMScreenMultiplexer workspace,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        long sourceId = LongArgumentType.getLong(context, "source-panel-entry-session");
        long destinationId = LongArgumentType.getLong(context, "destination-panel-entry-session");
        SFMPanelEntryInteractionSessionService.Session source =
                SFMPanelEntryInteractionSessionService.consume(sourceId, workspace).orElseThrow(STALE::create);
        SFMPanelEntryInteractionSessionService.Session destination =
                SFMPanelEntryInteractionSessionService.consume(destinationId, workspace).orElseThrow(STALE::create);
        if (source.entryId().equals(destination.entryId())
                || workspace.movePanelToStack(source.entryId(), destination.entryId())
                != SFMWorkspacePanelIntentResult.APPLIED) {
            throw UNAVAILABLE.create();
        }
        context.getSource().sendFeedback(Component.literal(
                "Moved " + source.stableId() + " into the pane containing " + destination.stableId()));
        return PanelActionSupport.closePaletteAfter(1);
    }
}
