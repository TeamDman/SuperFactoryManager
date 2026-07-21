package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.resources.ResourceLocation;

/** A registered typed factory which contributes its arguments to Brigadier. */
public interface SFMClientScreenType {
    LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    );

    @FunctionalInterface
    interface Opener {
        int open(
                CommandContext<SFMClientActionSource> context,
                SFMScreenPanel panel
        ) throws CommandSyntaxException;
    }
}
