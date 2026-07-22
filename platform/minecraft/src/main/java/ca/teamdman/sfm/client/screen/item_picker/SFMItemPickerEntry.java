package ca.teamdman.sfm.client.screen.item_picker;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.List;
import java.util.Objects;

/** Registry-backed item identity plus text that remains useful without its icon. */
public record SFMItemPickerEntry(
        ResourceLocation itemId,
        String accessibleName,
        List<ResourceLocation> tags
) {
    public SFMItemPickerEntry(ResourceLocation itemId, String accessibleName) {
        this(itemId, accessibleName, List.of());
    }

    public SFMItemPickerEntry {
        Objects.requireNonNull(itemId, "itemId");
        accessibleName = Objects.requireNonNull(accessibleName, "accessibleName").strip();
        if (accessibleName.isEmpty()) throw new IllegalArgumentException("Accessible item name must not be blank");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
    }

    public boolean matches(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return needle.isEmpty()
                || itemId.toString().toLowerCase(Locale.ROOT).contains(needle)
                || accessibleName.toLowerCase(Locale.ROOT).contains(needle);
    }

    public SFMItemIcon toIcon(ResourceLocation fallbackItem) {
        return new SFMItemIcon(itemId, fallbackItem, accessibleName);
    }

    public List<String> accessibleDetails() {
        return List.of(accessibleName, itemId.toString());
    }
}
