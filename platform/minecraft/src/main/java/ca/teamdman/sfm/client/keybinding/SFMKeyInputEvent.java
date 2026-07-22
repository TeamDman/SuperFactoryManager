package ca.teamdman.sfm.client.keybinding;

import java.util.EnumSet;
import java.util.Set;

public record SFMKeyInputEvent(
        long sequenceNumber,
        long tick,
        int keyCode,
        Type type,
        Set<SFMKeyModifier> modifiers
) {
    public enum Type { PRESS, RELEASE, REPEAT }

    public SFMKeyInputEvent {
        modifiers = modifiers.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(modifiers));
    }
}
