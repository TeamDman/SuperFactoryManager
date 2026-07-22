package ca.teamdman.sfm.client.screen.item_picker;

import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Takes a stable, searchable snapshot of the live item registry. */
public final class SFMItemPickerRegistryEntries {
    private static final Map<String, Integer> FEATURED_ORDER = Map.of(
            "sfm:disk", 0,
            "minecraft:chest", 1,
            "minecraft:compass", 2,
            "minecraft:book", 3,
            "minecraft:diamond", 4,
            "minecraft:paper", 5
    );

    private SFMItemPickerRegistryEntries() {
    }

    public static List<SFMItemPickerEntry> load() {
        List<SFMItemPickerEntry> answer = new ArrayList<>();
        for (ResourceLocation id : SFMWellKnownRegistries.ITEMS.keys()) {
            Item item = SFMWellKnownRegistries.ITEMS.get(id);
            if (item == null || item == Items.AIR) continue;
            answer.add(new SFMItemPickerEntry(id, new ItemStack(item).getHoverName().getString()));
        }
        answer.sort(Comparator
                .comparingInt((SFMItemPickerEntry entry) -> FEATURED_ORDER.getOrDefault(entry.itemId().toString(), 100))
                .thenComparing(entry -> entry.itemId().toString()));
        return List.copyOf(answer);
    }
}
