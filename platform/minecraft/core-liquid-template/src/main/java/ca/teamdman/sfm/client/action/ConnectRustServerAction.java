package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.terminal.SFMTerminalServiceFactory;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import net.minecraft.network.chat.Component;

import java.net.InetSocketAddress;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import java.time.Duration;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
/** Verifies that the configured or explicitly supplied Rust terminal endpoint is reachable. */
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
/** Opens a Rust terminal connection using the configured or explicitly supplied endpoint. */
{% endcase %}
public final class ConnectRustServerAction implements SFMClientAction<SFMClientActionContext> {
    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public Component title() { return Component.literal("Connect Rust terminal server"); }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public Component title() { return Component.literal("Connect Rust terminal"); }
{% endcase %}

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public Component description() { return Component.literal("Connect the Rust terminal scene to a running teamy-terminal endpoint"); }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public Component description() { return Component.literal("Connect to teamy-terminal at the configured or supplied address"); }
{% endcase %}

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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
{% endcase %}
        String raw = optionalAddress(context);
        InetSocketAddress endpoint = raw == null
                ? SFMTerminalServiceFactory.configuredEndpoint().orElseThrow()
                : SFMTerminalServiceFactory.parseEndpoint(raw, "connect-rust-server address");
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
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
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.terminal_legacy_open_actions and features.workspace_panels %}
        return OpenTerminalAction.open(target, SFMTerminalServiceFactory.createRust(endpoint));
{% else %}
        context.getSource().sendFeedback(Component.literal("Opening Rust terminals is unavailable"));
        return 0;
{% endif %}
{% endcase %}
    }

    static String optionalAddress(CommandContext<SFMClientActionSource> context) {
        try {
            return StringArgumentType.getString(context, "address");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
