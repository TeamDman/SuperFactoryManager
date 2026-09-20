package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class SFMTestScreenType implements SFMClientScreenType {
    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        return LiteralArgumentBuilder.<SFMClientActionSource>literal(screenTypeId.toString())
                .then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                                "display_text",
                                StringArgumentType.greedyString()
                        )
                        .executes(context -> {
                            String displayText = StringArgumentType.getString(context, "display_text");
                            return opener.open(context, new Recipe(screenTypeId, displayText));
                        }));
    }

    public record Recipe(ResourceLocation sceneTypeId, String displayText) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId);
            Objects.requireNonNull(displayText);
        }

        @Override
        public SFMScreenPanel reopen() {
            return new SFMTestScreenPanel(displayText);
        }
    }
}
