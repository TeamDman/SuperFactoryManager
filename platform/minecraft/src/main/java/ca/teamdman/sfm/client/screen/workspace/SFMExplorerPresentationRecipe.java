package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCompaction;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Decorates any typed Explorer source recipe without replacing its source/authority semantics. */
public record SFMExplorerPresentationRecipe(SFMPanelReopenRecipe source, SFMExplorerCompaction.Options compaction)
        implements SFMPanelReopenRecipe {
    public SFMExplorerPresentationRecipe { Objects.requireNonNull(source); Objects.requireNonNull(compaction); }
    public ResourceLocation sceneTypeId() { return source.sceneTypeId(); }
    public Optional<Component> unavailableReason(SFMPanelReopenContext context) { return source.unavailableReason(context); }
    public SFMScreenPanel reopen() {
        var panel = source.reopen();
        if (panel instanceof SFMExplorerPanel explorer) explorer.restoreCompaction(compaction);
        return panel;
    }
    public static SFMPanelReopenRecipe capture(SFMPanelReopenRecipe source, SFMScreenPanel panel) {
        if (!(panel instanceof SFMExplorerPanel explorer)) return source;
        if (source instanceof SFMExplorerPresentationRecipe wrapped) source = wrapped.source();
        return new SFMExplorerPresentationRecipe(source, explorer.sessionSnapshot().settings().compaction());
    }
}
