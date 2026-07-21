package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

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
                        .executes(context -> opener.open(
                                context,
                                new SFMTestScreenPanel(StringArgumentType.getString(context, "display_text"))
                        )));
    }
}
