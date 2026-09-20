package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.program.ClientProgramConsentsPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Human-only contextual panel actions; intentionally has no machine descriptor or handler. */
public final class SFMClientProgramConsentControlAction implements SFMClientAction<SFMClientActionContext> {
    @Override public Component title() { return Component.literal("Control client consent review"); }
    @Override public Component description() { return Component.literal("Use an explicit control in the originating consent panel"); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() { return SFMClientActionAvailability::available; }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("control", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (var control : ClientProgramConsentsPanel.Control.values()) builder.suggest(control.name().toLowerCase(Locale.ROOT));
                    return builder.buildFuture();
                }).executes(this::invoke));
    }
    @Override public int execute(SFMClientActionContext origin, CommandContext<SFMClientActionSource> context) {
        if (!origin.originatingHostIsCurrent().getAsBoolean()
                || !(origin.originatingHost() instanceof SFMScreenMultiplexer workspace)
                || origin.originatingPanelId() == null) {
            context.getSource().sendFeedback(Component.literal("Open the client consent panel first"));
            return 0;
        }
        var panel = workspace.panel(origin.originatingPanelId()).filter(ClientProgramConsentsPanel.class::isInstance)
                .map(ClientProgramConsentsPanel.class::cast);
        if (panel.isEmpty()) return 0;
        try {
            var control = ClientProgramConsentsPanel.Control.valueOf(StringArgumentType.getString(context, "control").toUpperCase(Locale.ROOT));
            return panel.orElseThrow().activate(control) ? 1 : 0;
        } catch (IllegalArgumentException invalid) {
            context.getSource().sendFeedback(Component.literal("Unknown consent panel control"));
            return 0;
        }
    }
}
