package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalPropertiesPanel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

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
                    SFMWorkspacePanelId owner = actionContext.originatingPanelId();
                    if (!actionContext.originatingHostIsCurrent().getAsBoolean()
                            || !(actionContext.originatingHost() instanceof SFMScreenMultiplexer workspace)
                            || owner == null
                            || !(workspace.panelInstance(owner) instanceof SFMTerminalPanel terminal)
                            || !terminal.isRustBacked()) {
                        throw new SimpleCommandExceptionType(Component.literal(
                                "Terminal properties require a focused Rust terminal panel")).create();
                    }
                    return opener.open(context, new Recipe(screenTypeId, owner));
                });
    }

    public record Recipe(
            ResourceLocation sceneTypeId,
            SFMWorkspacePanelId ownerPanelId
    ) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId);
            Objects.requireNonNull(ownerPanelId);
        }

        @Override
        public Optional<Component> unavailableReason(SFMPanelReopenContext context) {
            if (!(context.workspace().panelInstance(ownerPanelId) instanceof SFMTerminalPanel terminal)
                    || !terminal.isRustBacked()) {
                return Optional.of(Component.literal(
                        "The terminal-properties owner is no longer an available Rust terminal"));
            }
            return Optional.empty();
        }

        @Override
        public SFMTerminalPropertiesPanel reopen() {
            return new SFMTerminalPropertiesPanel(ownerPanelId);
        }
    }
}
