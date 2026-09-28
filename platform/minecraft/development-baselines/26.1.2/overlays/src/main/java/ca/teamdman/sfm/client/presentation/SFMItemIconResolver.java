package ca.teamdman.sfm.client.presentation;

import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/** Resolves presentation specs only after Minecraft's item registry is available. */
public final class SFMItemIconResolver {
    private SFMItemIconResolver() {
    }

    public static SFMResolvedItemIcon resolve(SFMItemIcon icon) {
        Identifier selectedId = selectAvailableId(icon, SFMItemIconResolver::isAvailable);
        boolean fallback = !selectedId.equals(icon.requestedItem());
        Item resolved = SFMWellKnownRegistries.ITEMS.get(selectedId).map(reference -> reference.value()).orElse(null);
        if (resolved == null || resolved == Items.AIR) resolved = Items.PAPER;
        return new SFMResolvedItemIcon(new ItemStack(resolved), icon.accessibleLabel(), fallback);
    }

    public static Identifier selectAvailableId(SFMItemIcon icon, Predicate<Identifier> available) {
        if (available.test(icon.requestedItem())) return icon.requestedItem();
        if (available.test(icon.fallbackItem())) return icon.fallbackItem();
        return SFMItemIcon.PAPER;
    }

    private static boolean isAvailable(Identifier requestedId) {
        Item item = SFMWellKnownRegistries.ITEMS.get(requestedId).map(reference -> reference.value()).orElse(null);
        if (item == null || item == Items.AIR) return false;
        Identifier actualId = SFMWellKnownRegistries.ITEMS.getId(item);
        return requestedId.equals(actualId);
    }
}
