package ca.teamdman.sfm.client.keybinding;

import java.util.EnumSet;
import java.util.Set;

public record SFMKeyStroke(int keyCode, Set<SFMKeyModifier> modifiers) {
    public SFMKeyStroke {
        modifiers = modifiers.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(modifiers));
    }

    public static SFMKeyStroke of(int keyCode, SFMKeyModifier... modifiers) {
        EnumSet<SFMKeyModifier> set = EnumSet.noneOf(SFMKeyModifier.class);
        for (SFMKeyModifier modifier : modifiers) set.add(modifier);
        return new SFMKeyStroke(keyCode, set);
    }
}
