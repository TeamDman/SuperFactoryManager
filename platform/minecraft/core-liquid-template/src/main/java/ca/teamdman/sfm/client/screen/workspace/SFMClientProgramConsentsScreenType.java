package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.program.ClientProgramConsentsPanel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

public final class SFMClientProgramConsentsScreenType implements SFMClientScreenType {
    @Override public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(ResourceLocation id, Opener opener) {
        return LiteralArgumentBuilder.<SFMClientActionSource>literal(id.toString())
{% if features.workspace_panel_reopening %}
                .executes(context -> opener.open(context, new Recipe(id)));
{% else %}
                .executes(context -> opener.open(context, new ClientProgramConsentsPanel()));
{% endif %}
    }
{% if features.workspace_panel_reopening %}
    public record Recipe(ResourceLocation sceneTypeId) implements SFMPanelReopenRecipe {
        @Override public SFMScreenPanel reopen() { return new ClientProgramConsentsPanel(); }
    }
{% endif %}
}
