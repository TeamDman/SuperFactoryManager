package ca.teamdman.sfm.client.presentation;

import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/** Resolves presentation specs only after Minecraft's item registry is available. */
public final class SFMItemIconResolver {
    private SFMItemIconResolver() {
    }

    public static SFMResolvedItemIcon resolve(SFMItemIcon icon) {
{% case minecraft_version %}
{% when "26.1.2" %}
        Identifier selectedId = selectAvailableId(icon, SFMItemIconResolver::isAvailable);
{% else %}
        ResourceLocation selectedId = selectAvailableId(icon, SFMItemIconResolver::isAvailable);
{% endcase %}
        boolean fallback = !selectedId.equals(icon.requestedItem());
{% if features.item_icon_context_safety %}
        return resolveSelected(selectedId, icon.accessibleLabel(), fallback);
{% else %}
{% case minecraft_version %}
{% when "26.1.2" %}
        Item resolved = SFMWellKnownRegistries.ITEMS.get(selectedId).map(reference -> reference.value()).orElse(null);
{% else %}
        Item resolved = SFMWellKnownRegistries.ITEMS.get(selectedId);
{% endcase %}
        if (resolved == null || resolved == Items.AIR) resolved = Items.PAPER;
        return new SFMResolvedItemIcon(new ItemStack(resolved), icon.accessibleLabel(), fallback);
{% endif %}
    }

{% if features.item_icon_context_safety %}
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

{% endif %}
{% case minecraft_version %}
{% when "26.1.2" %}
    public static Identifier selectAvailableId(SFMItemIcon icon, Predicate<Identifier> available) {
{% else %}
    public static ResourceLocation selectAvailableId(SFMItemIcon icon, Predicate<ResourceLocation> available) {
{% endcase %}
        if (available.test(icon.requestedItem())) return icon.requestedItem();
        if (available.test(icon.fallbackItem())) return icon.fallbackItem();
        return SFMItemIcon.PAPER;
    }

{% case minecraft_version %}
{% when "26.1.2" %}
    private static boolean isAvailable(Identifier requestedId) {
        Item item = SFMWellKnownRegistries.ITEMS.get(requestedId).map(reference -> reference.value()).orElse(null);
{% else %}
    private static boolean isAvailable(ResourceLocation requestedId) {
        Item item = SFMWellKnownRegistries.ITEMS.get(requestedId);
{% endcase %}
        if (item == null || item == Items.AIR) return false;
{% case minecraft_version %}
{% when "26.1.2" %}
        Identifier actualId = SFMWellKnownRegistries.ITEMS.getId(item);
{% else %}
        ResourceLocation actualId = SFMWellKnownRegistries.ITEMS.getId(item);
{% endcase %}
        return requestedId.equals(actualId);
    }
}
