package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.screen.workspace.SFMClientScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Typed, reopenable input diagnostics scene. */
public final class SFMInputDiagnosticsScreenType implements SFMClientScreenType {
    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        return LiteralArgumentBuilder.<SFMClientActionSource>literal(screenTypeId.toString())
                .executes(context -> opener.open(context, new Recipe(screenTypeId)));
    }

    public record Recipe(ResourceLocation sceneTypeId) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId);
        }

        @Override
        public SFMInputDiagnosticsPanel reopen() {
            return new SFMInputDiagnosticsPanel();
        }
    }
}
