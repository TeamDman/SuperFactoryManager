package ca.teamdman.sfm.client.presentation;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Presentation-only reference to a Minecraft item icon.
 *
 * <p>The requested item may come from a later user theme. The vanilla fallback
 * and accessible label are therefore part of the immutable specification so a
 * missing registry entry can never produce an empty or unnamed icon.</p>
 */
public record SFMItemIcon(
        ResourceLocation requestedItem,
        ResourceLocation fallbackItem,
        String accessibleLabel
) {
    public static final ResourceLocation PAPER = new ResourceLocation("minecraft", "paper");

    public SFMItemIcon {
        Objects.requireNonNull(requestedItem, "requestedItem");
        Objects.requireNonNull(fallbackItem, "fallbackItem");
        accessibleLabel = Objects.requireNonNull(accessibleLabel, "accessibleLabel").strip();
        if (accessibleLabel.isEmpty()) throw new IllegalArgumentException("Accessible label must not be blank");
    }

    public static SFMItemIcon vanilla(String itemPath, String accessibleLabel) {
        return new SFMItemIcon(new ResourceLocation("minecraft", itemPath), PAPER, accessibleLabel);
    }
}
