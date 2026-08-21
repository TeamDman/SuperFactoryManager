package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualRuntime;
import ca.teamdman.sfm.client.screen.history.workspace.SFMWorkspaceCounterfactualExplorerPanel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Fresh ordinary Explorer/Text Editor V3 scene for the bounded X5 journey. */
public final class SFMWorkspaceCounterfactualScreenType implements SFMClientScreenType {
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
        public SFMWorkspaceCounterfactualExplorerPanel reopen() {
            return SFMWorkspaceCounterfactualRuntime.get().openScene();
        }
    }
}
