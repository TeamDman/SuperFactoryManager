package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalPropertiesPanel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Opens diagnostics bound to the exact terminal focused when invoked. */
public final class SFMTerminalPropertiesScreenType implements SFMClientScreenType {
    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        return LiteralArgumentBuilder.<SFMClientActionSource>literal(screenTypeId.toString())
                .executes(context -> {
                    SFMClientActionContext actionContext = context.getSource().context();
                    if (!actionContext.originatingHostIsCurrent().getAsBoolean()
                            || !(actionContext.originatingHost() instanceof SFMScreenMultiplexer workspace)
                            || !(workspace.focusedPanelInstance() instanceof SFMTerminalPanel terminal)
                            || !terminal.isRustBacked()) {
                        throw new SimpleCommandExceptionType(Component.literal(
                                "Terminal properties require a focused Rust terminal panel")).create();
                    }
                    SFMWorkspacePanelId owner = workspace.focusedPanelId();
                    return opener.open(context, new SFMTerminalPropertiesPanel(owner));
                });
    }
}
