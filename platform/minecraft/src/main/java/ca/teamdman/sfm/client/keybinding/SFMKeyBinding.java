package ca.teamdman.sfm.client.keybinding;

import java.util.Objects;

public record SFMKeyBinding(
        String bindingId,
        String actionId,
        String commandDraft,
        SFMKeySequence sequence,
        boolean enabled
) {
    public SFMKeyBinding {
        Objects.requireNonNull(bindingId);
        Objects.requireNonNull(actionId);
        Objects.requireNonNull(commandDraft);
        Objects.requireNonNull(sequence);
    }

    public SFMKeyBinding withEnabled(boolean value) {
        return new SFMKeyBinding(bindingId, actionId, commandDraft, sequence, value);
    }
}
