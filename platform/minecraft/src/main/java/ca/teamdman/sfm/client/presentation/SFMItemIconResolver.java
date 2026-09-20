package ca.teamdman.sfm.client.presentation;

import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/** Resolves presentation specs only after Minecraft's item registry is available. */
public final class SFMItemIconResolver {
    private SFMItemIconResolver() {
    }

    public static SFMResolvedItemIcon resolve(SFMItemIcon icon) {
        ResourceLocation selectedId = selectAvailableId(icon, SFMItemIconResolver::isAvailable);
        boolean fallback = !selectedId.equals(icon.requestedItem());
        return resolveSelected(selectedId, icon.accessibleLabel(), fallback);
    }

    /**
     * Resolves only the declared fallback (or paper). This is used when a
     * present item cannot safely render in the current client context.
     */
    public static SFMResolvedItemIcon resolveFallback(SFMItemIcon icon) {
        ResourceLocation selectedId = isAvailable(icon.fallbackItem())
                ? icon.fallbackItem()
                : SFMItemIcon.PAPER;
        return resolveSelected(selectedId, icon.accessibleLabel(), true);
    }

    /** Last-resort vanilla icon for a context-incompatible custom renderer. */
    public static SFMResolvedItemIcon resolvePaper(SFMItemIcon icon) {
        return resolveSelected(SFMItemIcon.PAPER, icon.accessibleLabel(), true);
    }

    private static SFMResolvedItemIcon resolveSelected(
            ResourceLocation selectedId,
            String accessibleLabel,
            boolean fallback
    ) {
        Item resolved = SFMWellKnownRegistries.ITEMS.get(selectedId);
        if (resolved == null || resolved == Items.AIR) resolved = Items.PAPER;
        return new SFMResolvedItemIcon(new ItemStack(resolved), accessibleLabel, fallback);
    }

    public static ResourceLocation selectAvailableId(SFMItemIcon icon, Predicate<ResourceLocation> available) {
        if (available.test(icon.requestedItem())) return icon.requestedItem();
        if (available.test(icon.fallbackItem())) return icon.fallbackItem();
        return SFMItemIcon.PAPER;
    }

    private static boolean isAvailable(ResourceLocation requestedId) {
        Item item = SFMWellKnownRegistries.ITEMS.get(requestedId);
        if (item == null || item == Items.AIR) return false;
        ResourceLocation actualId = SFMWellKnownRegistries.ITEMS.getId(item);
        return requestedId.equals(actualId);
    }
}
