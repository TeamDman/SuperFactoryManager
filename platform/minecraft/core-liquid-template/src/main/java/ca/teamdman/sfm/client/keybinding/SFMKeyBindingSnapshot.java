package ca.teamdman.sfm.client.keybinding;

import java.util.Comparator;
import java.util.List;

public record SFMKeyBindingSnapshot(long revision, List<SFMKeyBinding> bindings) {
    public SFMKeyBindingSnapshot {
        bindings = bindings.stream()
                .sorted(Comparator.comparing(SFMKeyBinding::bindingId))
                .toList();
    }
}
