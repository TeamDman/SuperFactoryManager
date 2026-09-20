package ca.teamdman.sfm.client.keybinding;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record SFMKeyBinding(
        String bindingId,
        String actionId,
        String commandDraft,
        ResourceLocation situationId,
        SFMKeySequence sequence,
        boolean enabled
) {
    public SFMKeyBinding {
        Objects.requireNonNull(bindingId);
        Objects.requireNonNull(actionId);
        Objects.requireNonNull(commandDraft);
        Objects.requireNonNull(situationId);
        Objects.requireNonNull(sequence);
    }

    public SFMKeyBinding withEnabled(boolean value) {
        return new SFMKeyBinding(bindingId, actionId, commandDraft, situationId, sequence, value);
    }
}
