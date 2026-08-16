package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceDividerResizeResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceResizeDividersIntent;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

/** Explicit divider-selector action equivalent to a pointer resize transaction. */
public final class ResizeDividersAction implements SFMClientAction<SFMScreenMultiplexer> {
    @Override
    public Component title() {
        return Component.literal("Resize workspace dividers");
    }

    @Override
    public Component description() {
        return Component.literal("Adjust explicit divider identities by logical x/y deltas");
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return PanelActionSupport::resolve;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                .<SFMClientActionSource, Integer>argument("delta_x", IntegerArgumentType.integer())
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                        .<SFMClientActionSource, Integer>argument("delta_y", IntegerArgumentType.integer())
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                .<SFMClientActionSource, String>argument(
                                        "divider_selector", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    SFMClientActionAvailability<SFMScreenMultiplexer> availability =
                                            PanelActionSupport.resolve(context.getSource().context());
                                    if (availability.isAvailable()) {
                                        availability.target().dividerDescriptions().forEach(divider ->
                                                builder.suggest(divider.id().toString()));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(this::invoke))));
    }

    @Override
    public int execute(
            SFMScreenMultiplexer workspace,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        String selector = StringArgumentType.getString(context, "divider_selector");
        SFMWorkspaceResizeDividersIntent intent;
        try {
            intent = new SFMWorkspaceResizeDividersIntent(
                    SFMWorkspaceResizeDividersIntent.parseSelector(selector),
                    IntegerArgumentType.getInteger(context, "delta_x"),
                    IntegerArgumentType.getInteger(context, "delta_y"));
        } catch (IllegalArgumentException exception) {
            throw new SimpleCommandExceptionType(Component.literal(exception.getMessage())).create();
        }
        SFMWorkspaceDividerResizeResult result = workspace.resizeDividers(intent);
        context.getSource().sendFeedback(Component.literal(
                "Divider resize " + result.status().name().toLowerCase()
                        + ": " + intent.selector()));
        int code = result.status() == SFMWorkspaceDividerResizeResult.Status.APPLIED
                || result.status() == SFMWorkspaceDividerResizeResult.Status.CLAMPED
                ? 1
                : 0;
        return PanelActionSupport.closePaletteAfter(code);
    }
}
