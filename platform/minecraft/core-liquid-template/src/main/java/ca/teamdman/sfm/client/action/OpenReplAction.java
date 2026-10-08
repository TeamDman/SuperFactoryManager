package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.terminal.SFMTerminalServiceFactory;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Opens the Java-only terminal without probing or requiring Rust. */
public final class OpenReplAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() { return Component.literal("Open Java REPL"); }

    @Override
    public Component description() { return Component.literal("Open the Java-only terminal, independent of Rust/Vox"); }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panel_actions and features.workspace_widget_hosts %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
        return OpenPanelAction.openPanel(
                target,
                new ca.teamdman.sfm.client.terminal.SFMTerminalPanel(SFMTerminalServiceFactory.createRepl()),
                OpenPanelAction.Direction.FOCUSED
        );
{% else %}
        context.getSource().sendFeedback(Component.literal("Opening Java REPL panels is unavailable"));
        return 0;
{% endif %}
{% else %}
        context.getSource().sendFeedback(Component.literal("Opening Java REPL panels is unavailable"));
        return 0;
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.terminal_legacy_open_actions %}
        return OpenTerminalAction.open(
                target, SFMTerminalServiceFactory.createRepl());
{% else %}
        context.getSource().sendFeedback(Component.literal("Opening Java REPL terminals is unavailable"));
        return 0;
{% endif %}
{% endcase %}
    }
}
