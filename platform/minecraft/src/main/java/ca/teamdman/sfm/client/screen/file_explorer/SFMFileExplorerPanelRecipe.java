package ca.teamdman.sfm.client.screen.file_explorer;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Reconstructs an explorer with fresh controller/model state from a typed source address. */
public record SFMFileExplorerPanelRecipe(
        ResourceLocation sceneTypeId,
        SFMFileExplorerSourceRecipe source
) implements SFMPanelReopenRecipe {
    public static final ResourceLocation SCENE_TYPE_ID = new ResourceLocation(SFM.MOD_ID, "file_explorer");

    public SFMFileExplorerPanelRecipe {
        Objects.requireNonNull(sceneTypeId);
        Objects.requireNonNull(source);
    }

    public static Optional<SFMFileExplorerPanelRecipe> from(SFMFileExplorerSource source) {
        return SFMFileExplorerSourceRecipe.from(source)
                .map(sourceRecipe -> new SFMFileExplorerPanelRecipe(SCENE_TYPE_ID, sourceRecipe));
    }

    @Override
    public SFMScreenPanel reopen() {
        return SFMFileExplorerWorkspace.createPanel(source.open(), true);
    }
}
