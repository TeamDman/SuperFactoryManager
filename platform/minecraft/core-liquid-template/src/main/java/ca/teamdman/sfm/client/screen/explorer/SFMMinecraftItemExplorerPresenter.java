package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Minecraft-default contributor for item-registry paths and ItemStack icons. */
public final class SFMMinecraftItemExplorerPresenter implements SFMExplorerPresenter {
    public static final String ID = "sfm:minecraft_item";
    public static final int ORDER = 100;

    @Override
    public Optional<SFMExplorerPresentation> present(SFMExplorerProjection.Row row) {
        SFMPath path = row.path();
        if (path.kind() != SFMPath.Kind.REGISTRY
                || path.segments().isEmpty()
                || !path.segments().get(0).equals("item")) {
            return Optional.empty();
        }

        ResourceLocation itemId = row.entry()
                .sortKey(SFMExplorerEntry.SORT_ICON)
                .value()
                .map(ResourceLocation::tryParse)
                .orElse(null);
        if (itemId == null) itemId = itemIdFromRegistryPath(path).orElse(null);
        if (itemId == null) return Optional.empty();

        return Optional.of(new SFMExplorerPresentation(
                row.entry().label(),
                new SFMExplorerPresentation.ItemIcon(new SFMItemIcon(
                        itemId,
                        SFMItemIcon.PAPER,
                        row.entry().label()
                ))
        ));
    }

    private static Optional<ResourceLocation> itemIdFromRegistryPath(SFMPath path) {
        List<String> segments = path.segments();
        if (segments.size() < 2) return Optional.empty();
        String namespace;
        String itemPath;
        if (segments.size() >= 3) {
            namespace = segments.get(1);
            itemPath = String.join("/", segments.subList(2, segments.size()));
        } else {
            namespace = path.authority();
            itemPath = segments.get(1);
        }
        return Optional.ofNullable(ResourceLocation.tryParse(namespace + ":" + itemPath));
    }
}
