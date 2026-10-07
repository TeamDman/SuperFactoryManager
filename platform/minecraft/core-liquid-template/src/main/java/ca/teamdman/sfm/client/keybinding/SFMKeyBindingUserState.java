package ca.teamdman.sfm.client.keybinding;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persisted user layer over immutable built-in binding definitions. */
public record SFMKeyBindingUserState(
        List<SFMKeyBinding> bindings,
        Map<String, SFMKeyBindingOverride> defaultOverrides,
        Set<String> tombstones,
        Map<String, String> defaultFingerprints
) {
    public static final SFMKeyBindingUserState EMPTY =
            new SFMKeyBindingUserState(List.of(), Map.of(), Set.of(), Map.of());

    public SFMKeyBindingUserState(
            List<SFMKeyBinding> bindings,
            Set<String> tombstones
    ) {
        this(bindings, Map.of(), tombstones, Map.of());
    }

    public SFMKeyBindingUserState {
        bindings = List.copyOf(bindings);
        defaultOverrides = Map.copyOf(defaultOverrides);
        tombstones = Set.copyOf(tombstones);
        defaultFingerprints = Map.copyOf(defaultFingerprints);
    }
}
