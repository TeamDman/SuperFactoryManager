package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalPresentationChangeResult;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

/** Changes only the exact terminal panel currently focused in the workspace. */
public final class SetTerminalTransportAction implements SFMClientAction<SFMScreenMultiplexer> {
    @Override
    public Component title() {
        return Component.literal("Set terminal transport");
    }

    @Override
    public Component description() {
        return Component.literal("Select the Rust raster transport for the focused terminal panel");
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return PanelActionSupport::resolve;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                "transport-id", StringArgumentType.word()
        ).suggests((context, builder) -> {
            SFMClientActionAvailability<SFMScreenMultiplexer> availability =
                    PanelActionSupport.resolve(context.getSource().context());
            if (availability.isAvailable()
                    && PanelActionSupport.capturedPanel(
                            availability.target(), context.getSource().context())
                    .orElse(null) instanceof SFMTerminalPanel terminal) {
                terminal.transportOptions().forEach(option -> {
                    if (option.supported()) {
                        builder.suggest(option.id().wireId());
                    } else {
                        builder.suggest(
                                option.id().wireId(),
                                Component.literal(option.unavailableReason()));
                    }
                });
            }
            return builder.buildFuture();
        }).executes(this::invoke));
    }

    @Override
    public int execute(SFMScreenMultiplexer workspace, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        if (!(PanelActionSupport.capturedPanel(workspace, context.getSource().context())
                .orElse(null) instanceof SFMTerminalPanel terminal)) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Focused panel is not a Rust terminal")).create();
        }
        String transportId = StringArgumentType.getString(context, "transport-id");
        SFMTerminalPresentationChangeResult result = terminal.requestTransport(transportId);
        if (!result.accepted()) {
            throw new SimpleCommandExceptionType(Component.literal(result.message())).create();
        }
        context.getSource().sendFeedback(Component.literal(result.message()));
        return PanelActionSupport.closePaletteAfter(1);
    }
}
