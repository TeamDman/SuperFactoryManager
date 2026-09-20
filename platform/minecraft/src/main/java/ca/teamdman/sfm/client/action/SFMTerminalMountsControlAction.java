package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.terminal.TouchDisplayTerminalMountsPanel;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Human-only contextual controls; intentionally no programmatic descriptor/handler. */
public final class SFMTerminalMountsControlAction implements SFMClientAction<SFMClientActionContext> {
    @Override public Component title() { return Component.literal("Control Touch Display terminal mounts"); }
    @Override public Component description() { return Component.literal("Select a local session and explicitly enable its display/input"); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() { return SFMClientActionAvailability::available; }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("control", StringArgumentType.word())
                .suggests((context, builder) -> { for (var control : TouchDisplayTerminalMountsPanel.Control.values()) builder.suggest(control.name().toLowerCase(Locale.ROOT)); return builder.buildFuture(); })
                .executes(this::invoke));
    }
    @Override public int execute(SFMClientActionContext origin, CommandContext<SFMClientActionSource> context) {
        if (!origin.originatingHostIsCurrent().getAsBoolean() || !(origin.originatingHost() instanceof SFMScreenMultiplexer workspace)
                || origin.originatingPanelId() == null) return 0;
        var panel = workspace.panel(origin.originatingPanelId()).filter(TouchDisplayTerminalMountsPanel.class::isInstance).map(TouchDisplayTerminalMountsPanel.class::cast);
        if (panel.isEmpty()) return 0;
        try { return panel.orElseThrow().activate(TouchDisplayTerminalMountsPanel.Control.valueOf(StringArgumentType.getString(context, "control").toUpperCase(Locale.ROOT))) ? 1 : 0; }
        catch (IllegalArgumentException invalid) { context.getSource().sendFeedback(Component.literal("Unknown terminal mount control")); return 0; }
    }
}
