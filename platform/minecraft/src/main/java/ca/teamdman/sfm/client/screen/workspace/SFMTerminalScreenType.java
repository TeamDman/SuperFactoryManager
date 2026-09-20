package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalServiceFactory;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

import java.net.InetSocketAddress;
import java.util.Objects;

/** The Rust-authoritative terminal scene used by {@code sfm:panel/open}. */
public final class SFMTerminalScreenType implements SFMClientScreenType {
    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        return LiteralArgumentBuilder.<SFMClientActionSource>literal(screenTypeId.toString())
                .executes(context -> opener.open(context, new Recipe(
                        screenTypeId,
                        SFMTerminalServiceFactory.configuredEndpoint().orElseThrow()
                )));
    }

    public record Recipe(
            ResourceLocation sceneTypeId,
            InetSocketAddress endpoint
    ) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId);
            Objects.requireNonNull(endpoint);
        }

        @Override
        public SFMTerminalPanel reopen() {
            return new SFMTerminalPanel(SFMTerminalServiceFactory.createRustOrUnavailable(endpoint));
        }
    }
}
