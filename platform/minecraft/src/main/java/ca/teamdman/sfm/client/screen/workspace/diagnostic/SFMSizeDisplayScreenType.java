package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.screen.workspace.SFMClientScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

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
                        new SFMSizeDisplayPanel("size-display", 0xFF17324D)
                ));
    }
}
