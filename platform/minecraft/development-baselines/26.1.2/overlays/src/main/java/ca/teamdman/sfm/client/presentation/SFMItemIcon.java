package ca.teamdman.sfm.client.presentation;

import ca.teamdman.sfm.common.util.SFMResourceLocation;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Presentation-only reference to a Minecraft item icon.
 *
 * <p>The requested item may come from a later user theme. The vanilla fallback
 * and accessible label are therefore part of the immutable specification so a
 * missing registry entry can never produce an empty or unnamed icon.</p>
 */
public record SFMItemIcon(
        Identifier requestedItem,
        Identifier fallbackItem,
        String accessibleLabel
) {
    public static final Identifier PAPER = SFMResourceLocation.fromNamespaceAndPath("minecraft", "paper");

    public SFMItemIcon {
        Objects.requireNonNull(requestedItem, "requestedItem");
        Objects.requireNonNull(fallbackItem, "fallbackItem");
        accessibleLabel = Objects.requireNonNull(accessibleLabel, "accessibleLabel").strip();
        if (accessibleLabel.isEmpty()) throw new IllegalArgumentException("Accessible label must not be blank");
    }

    public static SFMItemIcon vanilla(String itemPath, String accessibleLabel) {
        return new SFMItemIcon(SFMResourceLocation.fromNamespaceAndPath("minecraft", itemPath), PAPER, accessibleLabel);
    }
}
