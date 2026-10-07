package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.terminal.SFMTerminalServiceFactory;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.workspace_panel_lookup %}
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

import java.net.InetSocketAddress;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
/** Starts the configured Rust CLI server without opening or replacing a panel. */
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
/** Starts the configured Rust CLI server and opens its terminal endpoint. */
{% endcase %}
public final class StartRustServerAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() { return Component.literal("Start Rust terminal server"); }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public Component description() { return Component.literal("Start teamy-terminal serve without opening or replacing a panel"); }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public Component description() { return Component.literal("Start teamy-terminal serve without opening a console window, then connect"); }
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
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        String raw = ConnectRustServerAction.optionalAddress(context);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.workspace_panel_lookup %}
        SFMClientActionContext actionContext = context.getSource().context();
        if (raw == null
                && actionContext.originatingHost() instanceof SFMScreenMultiplexer workspace
                && actionContext.originatingPanelId() != null) {
            var panel = workspace.panel(actionContext.originatingPanelId());
            if (panel.isPresent() && panel.get() instanceof SFMTerminalPanel terminal
                    && terminal.isRustBacked()) {
                SFMTerminalPanel.RustLifecycleRequest result = terminal.requestStartOrRetryRustServer();
                context.getSource().sendFeedback(Component.literal(switch (result) {
                    case STARTING_SERVER -> "Starting Rust terminal server";
                    case RETRYING_CONNECTION -> "Retrying Rust terminal connection";
                    case ALREADY_CONNECTED -> "Rust terminal is already connected";
                    case PRESENTATION_PENDING -> "Rust terminal presentation is still preparing";
                    case REQUEST_IN_PROGRESS -> "Rust terminal lifecycle request is already in progress";
                    case UNAVAILABLE -> "Rust terminal support is unavailable";
                }));
                return 1;
            }
        }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
        try {
            InetSocketAddress endpoint = SFMTerminalServiceFactory.startRustServer(raw);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            context.getSource().sendFeedback(Component.literal(
                    "Rust terminal server started and is reachable at " + endpoint));
            return 1;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.terminal_legacy_open_actions and features.workspace_panels %}
            return OpenTerminalAction.open(target, SFMTerminalServiceFactory.createRust(endpoint));
{% else %}
            context.getSource().sendFeedback(Component.literal(
                    "Rust terminal server started and is reachable at " + endpoint));
            return 1;
{% endif %}
{% endcase %}
        } catch (Exception error) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Could not start Rust terminal server: " + error.getMessage())).create();
        }
    }
}
