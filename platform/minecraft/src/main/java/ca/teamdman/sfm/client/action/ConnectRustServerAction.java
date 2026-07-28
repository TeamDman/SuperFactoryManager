package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.terminal.SFMTerminalServiceFactory;
import ca.teamdman.sfm.client.terminal.SFMVoxTerminalService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

import java.net.InetSocketAddress;

/** Opens a Rust terminal connection using the configured or explicitly supplied endpoint. */
public final class ConnectRustServerAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() { return Component.literal("Connect Rust terminal"); }

    @Override
    public Component description() { return Component.literal("Connect to teamy-terminal at the configured or supplied address"); }

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
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        String raw = optionalAddress(context);
        InetSocketAddress endpoint = raw == null
                ? SFMTerminalServiceFactory.configuredEndpoint().orElseThrow()
                : SFMTerminalServiceFactory.parseEndpoint(raw, "connect-rust-server address");
        return OpenTerminalAction.open(target, new SFMVoxTerminalService(endpoint));
    }

    static String optionalAddress(CommandContext<SFMClientActionSource> context) {
        try {
            return StringArgumentType.getString(context, "address");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
