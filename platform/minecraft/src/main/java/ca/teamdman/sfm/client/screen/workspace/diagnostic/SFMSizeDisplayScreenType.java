package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.screen.workspace.SFMClientScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Typed size-display scene used by panel layout and GUI-scale diagnostics. */
public final class SFMSizeDisplayScreenType implements SFMClientScreenType {
    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        return LiteralArgumentBuilder.<SFMClientActionSource>literal(screenTypeId.toString())
                .executes(context -> opener.open(
                        context,
                        new Recipe(screenTypeId, "size-display", 0xFF17324D)
                ));
    }

    public record Recipe(
            ResourceLocation sceneTypeId,
            String label,
            int backgroundColour
    ) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId);
            Objects.requireNonNull(label);
        }

        @Override
        public SFMSizeDisplayPanel reopen() {
            return new SFMSizeDisplayPanel(label, backgroundColour);
        }
    }
}
