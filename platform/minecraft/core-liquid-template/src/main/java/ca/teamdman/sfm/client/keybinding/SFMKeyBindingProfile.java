package ca.teamdman.sfm.client.keybinding;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
import java.util.Set;
import java.util.LinkedHashSet;
{% endcase %}

public final class SFMKeyBindingProfile {
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
    private final Map<String, SFMKeyBinding> bindings = new LinkedHashMap<>();
{% when "1.19.2", "1.19.4" %}
    public enum Origin { BUILT_IN, OVERRIDDEN_DEFAULT, USER, EPHEMERAL }
    private final Map<String, SFMKeyBinding> builtIns = new LinkedHashMap<>();
    private final Map<String, SFMKeyBinding> userBindings = new LinkedHashMap<>();
    private final Map<String, SFMKeyBindingOverride> defaultOverrides = new LinkedHashMap<>();
    private final Map<String, SFMKeyBinding> ephemeralBindings = new LinkedHashMap<>();
    private final Set<String> tombstones = new LinkedHashSet<>();
    private final SFMKeyboardUsageSituationCatalog situations;
    private final Map<String, String> defaultFingerprints;
{% endcase %}
    private long revision;

{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    public SFMKeyBindingProfile() {
        this(List.of(), SFMKeyBindingUserState.EMPTY,
                new SFMKeyboardUsageSituationCatalog(Map.of()));
    }

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
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

{% endcase %}
    public synchronized void put(SFMKeyBinding binding) {
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
        bindings.put(binding.bindingId(), binding);
{% when "1.19.2", "1.19.4" %}
        if (builtIns.containsKey(binding.bindingId())
                && !userBindings.containsKey(binding.bindingId())) {
            defaultOverrides.put(binding.bindingId(), SFMKeyBindingOverride.from(binding));
        } else {
            userBindings.put(binding.bindingId(), binding);
        }
        tombstones.remove(binding.bindingId());
{% endcase %}
        revision++;
    }

{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    public synchronized void putEphemeral(SFMKeyBinding binding) {
        ephemeralBindings.put(binding.bindingId(), binding);
        revision++;
    }

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    public synchronized boolean removeEphemeral(String bindingId) {
        if (ephemeralBindings.remove(bindingId) == null) return false;
        revision++;
        return true;
    }

{% endcase %}
    public synchronized boolean remove(String bindingId) {
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
        if (bindings.remove(bindingId) == null) return false;
{% when "1.19.2", "1.19.4" %}
        boolean changed;
        if (builtIns.containsKey(bindingId)) {
            changed = userBindings.remove(bindingId) != null
                    | defaultOverrides.remove(bindingId) != null
                    | tombstones.add(bindingId);
        } else {
            changed = userBindings.remove(bindingId) != null;
        }
        if (!changed) return false;
{% endcase %}
        revision++;
        return true;
    }

{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    public synchronized boolean restoreBuiltIn(String bindingId) {
        if (!builtIns.containsKey(bindingId)) return false;
        boolean changed = userBindings.remove(bindingId) != null
                | defaultOverrides.remove(bindingId) != null
                | tombstones.remove(bindingId);
        if (!changed) return false;
        revision++;
        return true;
    }

{% endcase %}
    public synchronized boolean setEnabled(String bindingId, boolean enabled) {
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
        SFMKeyBinding current = bindings.get(bindingId);
{% when "1.19.2", "1.19.4" %}
        SFMKeyBinding current = effectiveBindings().get(bindingId);
{% endcase %}
        if (current == null || current.enabled() == enabled) return false;
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
        bindings.put(bindingId, current.withEnabled(enabled));
{% when "1.19.2", "1.19.4" %}
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
{% endcase %}
        revision++;
        return true;
    }

    public synchronized List<SFMKeyBinding> bindingsForAction(String actionId) {
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
        return bindings.values().stream()
{% when "1.19.2", "1.19.4" %}
        return effectiveBindings().values().stream()
{% endcase %}
                .filter(binding -> binding.actionId().equals(actionId))
                .toList();
    }

{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    /**
     * Returns bindings for one exact command rather than every parameterized
     * invocation in the same action family.
     */
{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    public synchronized List<SFMKeyBinding> bindingsForCommand(
            String actionId,
            String commandDraft
    ) {
        String expected = normalizeCommandDraft(commandDraft);
        return effectiveBindings().values().stream()
                .filter(binding -> binding.actionId().equals(actionId))
                .filter(binding -> normalizeCommandDraft(binding.commandDraft()).equals(expected))
                .toList();
    }

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    public synchronized List<SFMKeyBinding> tombstonedBuiltInsForAction(String actionId) {
        return builtIns.values().stream()
                .filter(binding -> binding.actionId().equals(actionId))
                .filter(binding -> tombstones.contains(binding.bindingId()))
                .toList();
    }

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
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

{% endcase %}
    public synchronized List<SFMKeyBinding> conflictsWith(SFMKeyBinding candidate) {
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
        return bindings.values().stream()
{% when "1.19.2", "1.19.4" %}
        return effectiveBindings().values().stream()
{% endcase %}
                .filter(SFMKeyBinding::enabled)
                .filter(binding -> !binding.bindingId().equals(candidate.bindingId()))
                .filter(binding -> binding.sequence().equals(candidate.sequence()))
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
                .filter(binding -> situations.canOverlap(
                        binding.situationId(), candidate.situationId()))
{% endcase %}
                .toList();
    }

    public synchronized SFMKeyBindingSnapshot snapshot() {
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
        return new SFMKeyBindingSnapshot(revision, List.copyOf(bindings.values()));
{% when "1.19.2", "1.19.4" %}
        return new SFMKeyBindingSnapshot(revision, List.copyOf(effectiveBindings().values()));
{% endcase %}
    }
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    public synchronized SFMKeyBindingUserState userState() {
        return new SFMKeyBindingUserState(
                List.copyOf(userBindings.values()),
                Map.copyOf(defaultOverrides),
                Set.copyOf(tombstones),
                defaultFingerprints);
    }

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    public synchronized boolean isBuiltIn(String bindingId) {
        return builtIns.containsKey(bindingId);
    }

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    public synchronized boolean isTombstoned(String bindingId) {
        return tombstones.contains(bindingId);
    }

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
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

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    private static String normalizeCommandDraft(String commandDraft) {
        String normalized = commandDraft.strip();
        return normalized.startsWith("/") ? normalized.substring(1).stripLeading() : normalized;
    }
{% endcase %}
}
