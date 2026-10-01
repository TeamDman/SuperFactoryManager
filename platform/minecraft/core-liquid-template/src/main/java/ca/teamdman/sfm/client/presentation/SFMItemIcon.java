package ca.teamdman.sfm.client.presentation;

{% case minecraft_version %}
{% when "1.21", "1.21.0", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;

{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}

import java.util.Objects;

/**
 * Presentation-only reference to a Minecraft item icon.
 *
 * <p>The requested item may come from a later user theme. The vanilla fallback
 * and accessible label are therefore part of the immutable specification so a
 * missing registry entry can never produce an empty or unnamed icon.</p>
 */
public record SFMItemIcon(
{% case minecraft_version %}
{% when "26.1.2" %}
        Identifier requestedItem,
        Identifier fallbackItem,
{% else %}
        ResourceLocation requestedItem,
        ResourceLocation fallbackItem,
{% endcase %}
        String accessibleLabel
) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public static final ResourceLocation PAPER = new ResourceLocation("minecraft", "paper");
    public static final ResourceLocation BARREL = new ResourceLocation("minecraft", "barrel");
{% when "1.21", "1.21.0", "1.21.1" %}
    public static final ResourceLocation PAPER = SFMResourceLocation.fromNamespaceAndPath("minecraft", "paper");
{% when "26.1.2" %}
    public static final Identifier PAPER = SFMResourceLocation.fromNamespaceAndPath("minecraft", "paper");
{% else %}
    public static final ResourceLocation PAPER = new ResourceLocation("minecraft", "paper");
{% endcase %}

    public SFMItemIcon {
        Objects.requireNonNull(requestedItem, "requestedItem");
        Objects.requireNonNull(fallbackItem, "fallbackItem");
        accessibleLabel = Objects.requireNonNull(accessibleLabel, "accessibleLabel").strip();
        if (accessibleLabel.isEmpty()) throw new IllegalArgumentException("Accessible label must not be blank");
    }

    public static SFMItemIcon vanilla(String itemPath, String accessibleLabel) {
{% case minecraft_version %}
{% when "1.21", "1.21.0", "1.21.1", "26.1.2" %}
        return new SFMItemIcon(SFMResourceLocation.fromNamespaceAndPath("minecraft", itemPath), PAPER, accessibleLabel);
{% else %}
        return new SFMItemIcon(new ResourceLocation("minecraft", itemPath), PAPER, accessibleLabel);
{% endcase %}
    }
}
