package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.terminal.TouchDisplayTerminalMountsPanel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

public final class SFMTerminalMountsScreenType implements SFMClientScreenType {
    @Override public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(ResourceLocation id, Opener opener) {
        return LiteralArgumentBuilder.<SFMClientActionSource>literal(id.toString()).executes(context -> opener.open(context, new Recipe(id)));
    }
    public record Recipe(ResourceLocation sceneTypeId) implements SFMPanelReopenRecipe {
        @Override public SFMScreenPanel reopen() { return new TouchDisplayTerminalMountsPanel(); }
    }
}
