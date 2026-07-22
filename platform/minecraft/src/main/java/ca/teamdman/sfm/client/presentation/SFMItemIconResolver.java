package ca.teamdman.sfm.client.presentation;

import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Resolves presentation specs only after Minecraft's item registry is available. */
public final class SFMItemIconResolver {
    private SFMItemIconResolver() {
    }

    public static SFMResolvedItemIcon resolve(SFMItemIcon icon) {
        Item requested = SFMWellKnownRegistries.ITEMS.get(icon.requestedItem());
        boolean fallback = !isAvailable(requested, icon.requestedItem());
        Item resolved = fallback ? SFMWellKnownRegistries.ITEMS.get(icon.fallbackItem()) : requested;
        if (fallback && !isAvailable(resolved, icon.fallbackItem())) resolved = Items.PAPER;
        return new SFMResolvedItemIcon(new ItemStack(resolved), icon.accessibleLabel(), fallback);
    }

    private static boolean isAvailable(Item item, ResourceLocation requestedId) {
        if (item == null || item == Items.AIR) return false;
        ResourceLocation actualId = SFMWellKnownRegistries.ITEMS.getId(item);
        return requestedId.equals(actualId);
    }
}
