package ca.teamdman.sfm.client.screen.item_picker;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}

import java.util.Locale;
import java.util.List;
import java.util.Objects;

/** Registry-backed item identity plus text that remains useful without its icon. */
public record SFMItemPickerEntry(
{% case minecraft_version %}
{% when "26.1.2" %}
        Identifier itemId,
{% else %}
        ResourceLocation itemId,
{% endcase %}
        String accessibleName,
{% case minecraft_version %}
{% when "26.1.2" %}
        List<Identifier> tags
{% else %}
        List<ResourceLocation> tags
{% endcase %}
) {
{% case minecraft_version %}
{% when "26.1.2" %}
    public SFMItemPickerEntry(Identifier itemId, String accessibleName) {
{% else %}
    public SFMItemPickerEntry(ResourceLocation itemId, String accessibleName) {
{% endcase %}
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

{% case minecraft_version %}
{% when "26.1.2" %}
    public SFMItemIcon toIcon(Identifier fallbackItem) {
{% else %}
    public SFMItemIcon toIcon(ResourceLocation fallbackItem) {
{% endcase %}
        return new SFMItemIcon(itemId, fallbackItem, accessibleName);
    }

    public List<String> accessibleDetails() {
        return List.of(accessibleName, itemId.toString());
    }
}
