package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.screen.history.chamber.SFMDecimalNumberingChamberPanel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Registered fresh-session scene for the supervised temporal-numbering chamber. */
public final class SFMDecimalNumberingChamberScreenType implements SFMClientScreenType {
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
            Objects.requireNonNull(sceneTypeId, "sceneTypeId");
        }

        @Override
        public SFMDecimalNumberingChamberPanel reopen() {
            return SFMDecimalNumberingChamberPanel.createForCurrentCheckout();
        }
    }
}
