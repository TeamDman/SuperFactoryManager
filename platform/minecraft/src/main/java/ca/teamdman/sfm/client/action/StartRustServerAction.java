package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.terminal.SFMTerminalServiceFactory;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

import java.net.InetSocketAddress;

/** Starts the configured Rust CLI server without opening or replacing a panel. */
public final class StartRustServerAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() { return Component.literal("Start Rust terminal server"); }

    @Override
    public Component description() { return Component.literal("Start teamy-terminal serve without opening or replacing a panel"); }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.executes(this::invoke);
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                "address", StringArgumentType.word()).executes(this::invoke));
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        String raw = ConnectRustServerAction.optionalAddress(context);
        try {
            InetSocketAddress endpoint = SFMTerminalServiceFactory.startRustServer(raw);
            context.getSource().sendFeedback(Component.literal(
                    "Rust terminal server started and is reachable at " + endpoint));
            return 1;
        } catch (Exception error) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Could not start Rust terminal server: " + error.getMessage())).create();
        }
    }
}
