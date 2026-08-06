package ca.teamdman.sfm.client.keybinding;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable, deterministic view of contributed keyboard usage situations. */
public final class SFMKeyboardUsageSituationCatalog {
    private final Map<ResourceLocation, SFMKeyboardUsageSituation> situations;

    public SFMKeyboardUsageSituationCatalog(Map<ResourceLocation, SFMKeyboardUsageSituation> situations) {
        this.situations = Map.copyOf(new LinkedHashMap<>(situations));
        for (var entry : this.situations.entrySet()) {
            for (ResourceLocation parent : entry.getValue().parents()) {
                if (!this.situations.containsKey(parent)) {
                    throw new IllegalArgumentException(
                            "Unknown keyboard usage situation parent " + parent + " for " + entry.getKey());
                }
            }
        }
        Set<ResourceLocation> validated = new LinkedHashSet<>();
        for (ResourceLocation id : this.situations.keySet()) {
            validateAcyclic(id, new LinkedHashSet<>(), validated);
        }
    }

    public Optional<SFMKeyboardUsageSituation> get(ResourceLocation id) {
        return Optional.ofNullable(situations.get(id));
    }

    public List<ResourceLocation> ids() {
        return situations.keySet().stream().sorted().toList();
    }

    /** Deepest first, preserving contributed parent order and rejecting cycles. */
    public List<ResourceLocation> ancestry(ResourceLocation deepest) {
        return resolve(deepest).situations();
    }

    /** Breadth-by-depth resolution keeps all direct parents equally specific. */
    public ActiveAncestry resolve(ResourceLocation deepest) {
        Map<ResourceLocation, Integer> depths = new LinkedHashMap<>();
        List<ResourceLocation> frontier = List.of(deepest);
        int depth = 0;
        while (!frontier.isEmpty()) {
            LinkedHashSet<ResourceLocation> next = new LinkedHashSet<>();
            for (ResourceLocation id : frontier) {
                if (depths.putIfAbsent(id, depth) != null) continue;
                SFMKeyboardUsageSituation situation = situations.get(id);
                if (situation != null) next.addAll(situation.parents());
            }
            frontier = List.copyOf(next);
            depth++;
        }
        return new ActiveAncestry(List.copyOf(depths.keySet()), Map.copyOf(depths));
    }

    public boolean canOverlap(ResourceLocation first, ResourceLocation second) {
        if (first.equals(second)) return true;
        for (ResourceLocation deepest : situations.keySet()) {
            List<ResourceLocation> active = resolve(deepest).situations();
            if (active.contains(first) && active.contains(second)) return true;
        }
        return false;
    }

    private void validateAcyclic(
            ResourceLocation id,
            Set<ResourceLocation> visiting,
            Set<ResourceLocation> validated
    ) {
        if (validated.contains(id)) return;
        if (visiting.contains(id)) {
            throw new IllegalStateException("Keyboard usage situation ancestry cycle at " + id);
        }
        visiting.add(id);
        SFMKeyboardUsageSituation situation = situations.get(id);
        if (situation != null) {
            for (ResourceLocation parent : situation.parents()) {
                validateAcyclic(parent, visiting, validated);
            }
        }
        visiting.remove(id);
        validated.add(id);
    }

    public record ActiveAncestry(
            List<ResourceLocation> situations,
            Map<ResourceLocation, Integer> depths
    ) {
        public ActiveAncestry {
            situations = List.copyOf(situations);
            depths = Map.copyOf(depths);
        }
    }
}
