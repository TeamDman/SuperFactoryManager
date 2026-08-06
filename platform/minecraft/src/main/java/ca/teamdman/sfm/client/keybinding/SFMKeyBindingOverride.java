package ca.teamdman.sfm.client.keybinding;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** User-editable fields layered over an immutable built-in definition. */
public record SFMKeyBindingOverride(
        String bindingId,
        ResourceLocation situationId,
        SFMKeySequence sequence,
        boolean enabled
) {
    public SFMKeyBindingOverride {
        Objects.requireNonNull(bindingId);
        Objects.requireNonNull(situationId);
        Objects.requireNonNull(sequence);
    }

    public static SFMKeyBindingOverride from(SFMKeyBinding binding) {
        return new SFMKeyBindingOverride(
                binding.bindingId(),
                binding.situationId(),
                binding.sequence(),
                binding.enabled());
    }

    public SFMKeyBinding applyTo(SFMKeyBinding definition) {
        if (!bindingId.equals(definition.bindingId())) {
            throw new IllegalArgumentException("Binding override id does not match definition");
        }
        return new SFMKeyBinding(
                definition.bindingId(),
                definition.actionId(),
                definition.commandDraft(),
                situationId,
                sequence,
                enabled);
    }
}
