package ca.teamdman.sfm.client.keybinding;

import java.util.Comparator;
import java.util.List;

/** Deterministic single-line presentation selection; matching remains simultaneous for every binding. */
public final class SFMKeyBindingCycle {
    public static final long CYCLE_TICKS = 20L;

    private SFMKeyBindingCycle() {
    }

    public static List<SFMKeyBinding> enabledStable(List<SFMKeyBinding> bindings) {
        return bindings.stream()
                .filter(SFMKeyBinding::enabled)
                .sorted(Comparator.comparing(SFMKeyBinding::bindingId))
                .toList();
    }

    public static String displayedSequence(List<SFMKeyBinding> bindings, long tick) {
        SFMKeyBinding displayed = displayedBinding(bindings, tick);
        return displayed == null ? "" : SFMKeyBindingDisplay.format(displayed.sequence());
    }

    public static SFMKeyBinding displayedBinding(List<SFMKeyBinding> bindings, long tick) {
        List<SFMKeyBinding> enabled = enabledStable(bindings);
        if (enabled.isEmpty()) return null;
        int index = enabled.size() == 1 ? 0 : (int) ((tick / CYCLE_TICKS) % enabled.size());
        return enabled.get(index);
    }
}
