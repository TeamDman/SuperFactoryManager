package ca.teamdman.sfm.client.keybinding;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SFMKeyBindingProfile {
    private final Map<String, SFMKeyBinding> bindings = new LinkedHashMap<>();
    private long revision;

    public synchronized void put(SFMKeyBinding binding) {
        bindings.put(binding.bindingId(), binding);
        revision++;
    }

    public synchronized boolean remove(String bindingId) {
        if (bindings.remove(bindingId) == null) return false;
        revision++;
        return true;
    }

    public synchronized boolean setEnabled(String bindingId, boolean enabled) {
        SFMKeyBinding current = bindings.get(bindingId);
        if (current == null || current.enabled() == enabled) return false;
        bindings.put(bindingId, current.withEnabled(enabled));
        revision++;
        return true;
    }

    public synchronized List<SFMKeyBinding> bindingsForAction(String actionId) {
        return bindings.values().stream()
                .filter(binding -> binding.actionId().equals(actionId))
                .toList();
    }

    public synchronized List<SFMKeyBinding> conflictsWith(SFMKeyBinding candidate) {
        return bindings.values().stream()
                .filter(SFMKeyBinding::enabled)
                .filter(binding -> !binding.bindingId().equals(candidate.bindingId()))
                .filter(binding -> binding.sequence().equals(candidate.sequence()))
                .toList();
    }

    public synchronized SFMKeyBindingSnapshot snapshot() {
        return new SFMKeyBindingSnapshot(revision, List.copyOf(bindings.values()));
    }
}
