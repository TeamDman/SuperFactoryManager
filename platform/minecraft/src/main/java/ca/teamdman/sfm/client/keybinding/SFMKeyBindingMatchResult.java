package ca.teamdman.sfm.client.keybinding;

import java.util.List;

/** Matcher outcome distinguishes invocation from a still-viable reserved chord. */
public record SFMKeyBindingMatchResult(
        List<SFMActionInvocationIntent> intents,
        boolean consumed,
        List<SFMKeyBindingConflict> conflicts
) {
    public static final SFMKeyBindingMatchResult UNMATCHED =
            new SFMKeyBindingMatchResult(List.of(), false, List.of());

    public SFMKeyBindingMatchResult {
        intents = List.copyOf(intents);
        conflicts = List.copyOf(conflicts);
    }
}
