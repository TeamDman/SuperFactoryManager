package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.action.SFMExplorerRefreshAction;
import ca.teamdman.sfm.client.explorer.*;
import ca.teamdman.sfm.client.explorer.lazy.*;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.search.SFMExplorerNavigationText;
import static ca.teamdman.sfm.client.search.SFMExplorerSearchText.value;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Pure menu construction: roots and path metadata, never directory IO. */
public final class SFMExplorerNavigationChoices {
    private SFMExplorerNavigationChoices() {}
    public static List<SFMActionChoice> roots(SFMExplorerSession.Snapshot snapshot) {
        var choices = new ArrayList<SFMActionChoice>();
        choices.add(SFMActionChoice.invoke(SFMExplorerRefreshAction.ID, "", value(SFMExplorerNavigationText.REFRESH)));
        String selector = selector(snapshot);
        for (var root : snapshot.roots()) parent(root).ifPresent(parent -> {
            choices.add(SFMActionChoice.invoke(new ResourceLocation("sfm", "explorer/root/parent/set"),
                    root.canonical() + " --expected-revision " + snapshot.revision(),
                    value(SFMExplorerNavigationText.PARENT_SET, root.canonical())));
            if (!snapshot.roots().contains(parent)) choices.add(SFMActionChoice.invoke(new ResourceLocation("sfm", "explorer/root/add"),
                    selector + " " + parent.canonical(), value(SFMExplorerNavigationText.PARENT_ADD, root.canonical())));
        });
        return List.copyOf(choices);
    }
    public static List<SFMActionChoice> row(SFMExplorerSession.Snapshot snapshot, SFMExplorerEntry entry) {
        var choices = new ArrayList<>(roots(snapshot));
        if (entry.expandable()) choices.add(SFMActionChoice.invoke(new ResourceLocation("sfm", "explorer/node/refresh"),
                selector(snapshot) + " " + entry.path().canonical(), value(SFMExplorerNavigationText.REFRESH_NODE)));
        if (entry.expandable() && entry.sortKey(SFMExplorerEntry.SUBJECT_KIND).value().filter("container"::equals).isPresent()
                && !snapshot.roots().contains(entry.path()))
            choices.add(SFMActionChoice.invoke(new ResourceLocation("sfm", "explorer/root/add"),
                    selector(snapshot) + " " + entry.path().canonical(), value(SFMExplorerNavigationText.ADD_ROOT)));
        return List.copyOf(choices);
    }
    public static Optional<SFMPath> parent(SFMPath root) {
        // Other resolver authorities need their own parent capability, not guessed URI truncation.
        if (root.kind() != SFMPath.Kind.FILE) return Optional.empty();
        return Optional.ofNullable(root.toNativePath().getParent()).map(SFMPath::fromNative);
    }
    private static String selector(SFMExplorerSession.Snapshot snapshot) {
        return SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, snapshot.id().value()).canonical();
    }
}
