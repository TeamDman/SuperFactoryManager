package ca.teamdman.sfm.client.keybinding;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

public final class SFMKeyBindingProfile {
    public enum Origin { BUILT_IN, OVERRIDDEN_DEFAULT, USER, EPHEMERAL }
    private final Map<String, SFMKeyBinding> builtIns = new LinkedHashMap<>();
    private final Map<String, SFMKeyBinding> userBindings = new LinkedHashMap<>();
    private final Map<String, SFMKeyBindingOverride> defaultOverrides = new LinkedHashMap<>();
    private final Map<String, SFMKeyBinding> ephemeralBindings = new LinkedHashMap<>();
    private final Set<String> tombstones = new LinkedHashSet<>();
    private final SFMKeyboardUsageSituationCatalog situations;
    private final Map<String, String> defaultFingerprints;
    private long revision;

    public SFMKeyBindingProfile() {
        this(List.of(), SFMKeyBindingUserState.EMPTY,
                new SFMKeyboardUsageSituationCatalog(Map.of()));
    }

    public SFMKeyBindingProfile(
            List<SFMKeyBinding> builtIns,
            SFMKeyBindingUserState userState,
            SFMKeyboardUsageSituationCatalog situations
    ) {
        for (SFMKeyBinding binding : builtIns) {
            if (this.builtIns.put(binding.bindingId(), binding) != null) {
                throw new IllegalArgumentException("Duplicate built-in binding id " + binding.bindingId());
            }
        }
        for (SFMKeyBinding binding : userState.bindings()) {
            userBindings.put(binding.bindingId(), binding);
        }
        defaultOverrides.putAll(userState.defaultOverrides());
        tombstones.addAll(userState.tombstones());
        this.situations = situations;
        defaultFingerprints = SFMKeyBindingDefaults.fingerprints(builtIns);
    }

    public synchronized void put(SFMKeyBinding binding) {
        if (builtIns.containsKey(binding.bindingId())
                && !userBindings.containsKey(binding.bindingId())) {
            defaultOverrides.put(binding.bindingId(), SFMKeyBindingOverride.from(binding));
        } else {
            userBindings.put(binding.bindingId(), binding);
        }
        tombstones.remove(binding.bindingId());
        revision++;
    }

    public synchronized void putEphemeral(SFMKeyBinding binding) {
        ephemeralBindings.put(binding.bindingId(), binding);
        revision++;
    }

    public synchronized boolean removeEphemeral(String bindingId) {
        if (ephemeralBindings.remove(bindingId) == null) return false;
        revision++;
        return true;
    }

    public synchronized boolean remove(String bindingId) {
        boolean changed;
        if (builtIns.containsKey(bindingId)) {
            changed = userBindings.remove(bindingId) != null
                    | defaultOverrides.remove(bindingId) != null
                    | tombstones.add(bindingId);
        } else {
            changed = userBindings.remove(bindingId) != null;
        }
        if (!changed) return false;
        revision++;
        return true;
    }

    public synchronized boolean restoreBuiltIn(String bindingId) {
        if (!builtIns.containsKey(bindingId)) return false;
        boolean changed = userBindings.remove(bindingId) != null
                | defaultOverrides.remove(bindingId) != null
                | tombstones.remove(bindingId);
        if (!changed) return false;
        revision++;
        return true;
    }

    public synchronized boolean setEnabled(String bindingId, boolean enabled) {
        SFMKeyBinding current = effectiveBindings().get(bindingId);
        if (current == null || current.enabled() == enabled) return false;
        if (ephemeralBindings.containsKey(bindingId)) {
            ephemeralBindings.put(bindingId, current.withEnabled(enabled));
            revision++;
            return true;
        }
        if (builtIns.containsKey(bindingId) && !userBindings.containsKey(bindingId)) {
            defaultOverrides.put(bindingId, SFMKeyBindingOverride.from(current.withEnabled(enabled)));
        } else {
            userBindings.put(bindingId, current.withEnabled(enabled));
        }
        tombstones.remove(bindingId);
        revision++;
        return true;
    }

    public synchronized List<SFMKeyBinding> bindingsForAction(String actionId) {
        return effectiveBindings().values().stream()
                .filter(binding -> binding.actionId().equals(actionId))
                .toList();
    }

    public synchronized List<SFMKeyBinding> tombstonedBuiltInsForAction(String actionId) {
        return builtIns.values().stream()
                .filter(binding -> binding.actionId().equals(actionId))
                .filter(binding -> tombstones.contains(binding.bindingId()))
                .toList();
    }

    public synchronized Origin origin(String bindingId) {
        if (ephemeralBindings.containsKey(bindingId)) return Origin.EPHEMERAL;
        if (userBindings.containsKey(bindingId)) return Origin.USER;
        if (builtIns.containsKey(bindingId)) {
            return defaultOverrides.containsKey(bindingId)
                    ? Origin.OVERRIDDEN_DEFAULT
                    : Origin.BUILT_IN;
        }
        return Origin.USER;
    }

    public synchronized List<SFMKeyBinding> conflictsWith(SFMKeyBinding candidate) {
        return effectiveBindings().values().stream()
                .filter(SFMKeyBinding::enabled)
                .filter(binding -> !binding.bindingId().equals(candidate.bindingId()))
                .filter(binding -> binding.sequence().equals(candidate.sequence()))
                .filter(binding -> situations.canOverlap(
                        binding.situationId(), candidate.situationId()))
                .toList();
    }

    public synchronized SFMKeyBindingSnapshot snapshot() {
        return new SFMKeyBindingSnapshot(revision, List.copyOf(effectiveBindings().values()));
    }

    public synchronized SFMKeyBindingUserState userState() {
        return new SFMKeyBindingUserState(
                List.copyOf(userBindings.values()),
                Map.copyOf(defaultOverrides),
                Set.copyOf(tombstones),
                defaultFingerprints);
    }

    public synchronized boolean isBuiltIn(String bindingId) {
        return builtIns.containsKey(bindingId);
    }

    public synchronized boolean isTombstoned(String bindingId) {
        return tombstones.contains(bindingId);
    }

    private Map<String, SFMKeyBinding> effectiveBindings() {
        Map<String, SFMKeyBinding> effective = new LinkedHashMap<>();
        for (SFMKeyBinding binding : builtIns.values()) {
            if (tombstones.contains(binding.bindingId())) continue;
            SFMKeyBindingOverride override = defaultOverrides.get(binding.bindingId());
            effective.put(binding.bindingId(), override == null ? binding : override.applyTo(binding));
        }
        for (SFMKeyBinding binding : userBindings.values()) {
            if (!tombstones.contains(binding.bindingId())) effective.put(binding.bindingId(), binding);
        }
        effective.putAll(ephemeralBindings);
        return effective;
    }
}
