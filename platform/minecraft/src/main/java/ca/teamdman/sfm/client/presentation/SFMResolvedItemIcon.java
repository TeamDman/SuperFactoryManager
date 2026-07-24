package ca.teamdman.sfm.client.presentation;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** A registry-resolved icon snapshot safe to reuse for one render operation. */
public record SFMResolvedItemIcon(
        ItemStack stack,
        String accessibleLabel,
        boolean usedFallback
) {
    public SFMResolvedItemIcon {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(accessibleLabel, "accessibleLabel");
    }
}
