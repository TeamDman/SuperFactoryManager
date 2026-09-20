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
import java.time.Duration;

/** Verifies that the configured or explicitly supplied Rust terminal endpoint is reachable. */
public final class ConnectRustServerAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() { return Component.literal("Connect Rust terminal server"); }

    @Override
    public Component description() { return Component.literal("Connect the Rust terminal scene to a running teamy-terminal endpoint"); }

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
        String raw = optionalAddress(context);
        InetSocketAddress endpoint = raw == null
                ? SFMTerminalServiceFactory.configuredEndpoint().orElseThrow()
                : SFMTerminalServiceFactory.parseEndpoint(raw, "connect-rust-server address");
        try {
            if (!SFMTerminalServiceFactory.awaitEndpoint(endpoint, Duration.ofSeconds(3))) {
                throw new IllegalStateException("endpoint did not accept a connection within 3 seconds");
            }
            context.getSource().sendFeedback(Component.literal(
                    "Rust terminal server is reachable at " + endpoint));
            return 1;
        } catch (Exception error) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Could not connect to Rust terminal server at " + endpoint + ": " + error.getMessage())).create();
        }
    }

    static String optionalAddress(CommandContext<SFMClientActionSource> context) {
        try {
            return StringArgumentType.getString(context, "address");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
