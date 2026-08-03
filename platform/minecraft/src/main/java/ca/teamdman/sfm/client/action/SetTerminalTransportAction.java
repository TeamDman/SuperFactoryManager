package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalTransportChangeResult;
import ca.teamdman.sfm.client.terminal.SFMTerminalTransportId;
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
            for (SFMTerminalTransportId id : SFMTerminalTransportId.values()) builder.suggest(id.wireId());
            return builder.buildFuture();
        }).executes(this::invoke));
    }

    @Override
    public int execute(SFMScreenMultiplexer workspace, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        if (!(workspace.focusedPanelInstance() instanceof SFMTerminalPanel terminal)) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Focused panel is not a Rust terminal")).create();
        }
        String transportId = StringArgumentType.getString(context, "transport-id");
        SFMTerminalTransportChangeResult result = terminal.requestTransport(transportId);
        if (!result.accepted()) {
            throw new SimpleCommandExceptionType(Component.literal(result.message())).create();
        }
        context.getSource().sendFeedback(Component.literal(result.message()));
        return PanelActionSupport.closePaletteAfter(1);
    }
}
