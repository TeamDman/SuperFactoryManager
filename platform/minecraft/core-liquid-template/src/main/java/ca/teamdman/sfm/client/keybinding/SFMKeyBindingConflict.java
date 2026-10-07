package ca.teamdman.sfm.client.keybinding;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Equal-specificity complete matches which are intentionally not double-fired. */
public record SFMKeyBindingConflict(
        ResourceLocation situationId,
        SFMKeySequence sequence,
        List<String> bindingIds
) {
    public SFMKeyBindingConflict {
        bindingIds = bindingIds.stream().sorted().toList();
    }
}
