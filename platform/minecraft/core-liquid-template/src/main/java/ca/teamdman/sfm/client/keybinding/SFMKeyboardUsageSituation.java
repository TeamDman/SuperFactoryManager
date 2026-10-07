package ca.teamdman.sfm.client.keybinding;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Contributor-defined keyboard scope with stable ancestry metadata. */
public record SFMKeyboardUsageSituation(
        Component title,
        Component description,
        List<ResourceLocation> parents
) {
    public SFMKeyboardUsageSituation {
        Objects.requireNonNull(title);
        Objects.requireNonNull(description);
        parents = List.copyOf(parents);
    }
}
