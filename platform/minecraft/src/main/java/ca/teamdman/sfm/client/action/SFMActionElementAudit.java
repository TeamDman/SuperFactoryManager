package ca.teamdman.sfm.client.action;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic validation and inventory formatting for visible SFM controls. */
public final class SFMActionElementAudit {
    private SFMActionElementAudit() {
    }

    public static List<String> errors(Iterable<? extends SFMActionElement> elements) {
        List<String> errors = new ArrayList<>();
        Set<ResourceLocation> ids = new HashSet<>();
        for (SFMActionElement element : elements) {
            if (element == null) {
                errors.add("null element");
                continue;
            }
            ResourceLocation id = element.elementId();
            if (id == null) errors.add("missing element id");
            else if (!ids.add(id)) errors.add("duplicate element id: " + id);
            if (element.keyboardUsageSituationId() == null) {
                errors.add(id + ": missing keyboard usage situation");
            }
            Component narration = element.narration();
            if (narration == null || narration.getString().isBlank()) {
                errors.add(id + ": missing narration");
            }
            String draft = element.actionDraft().orElse("").trim();
            if (draft.isBlank()) errors.add(id + ": missing semantic action");
            if (draft.contains("screen/mouse/click") || draft.matches(".*\\b\\d+\\s+\\d+\\s*$")) {
                errors.add(id + ": coordinate action is not a public semantic action");
            }
            if (!element.isKeyboardReachable()) errors.add(id + ": missing keyboard reachability");
        }
        return List.copyOf(errors);
    }

    /** Stable line-oriented artifact suitable for test output and review. */
    public static List<String> inventory(Iterable<? extends SFMActionElement> elements) {
        List<SFMActionElement> sorted = new ArrayList<>();
        for (SFMActionElement element : elements) sorted.add(element);
        sorted.sort(Comparator.comparing(element -> element.elementId().toString()));
        return sorted.stream()
                .map(element -> String.join("|",
                        element.elementId().toString(),
                        element.keyboardUsageSituationId().toString(),
                        element.actionDraft().orElse(""),
                        Boolean.toString(element.isKeyboardReachable()),
                        element.narration().getString()))
                .toList();
    }

    public static void requireValid(SFMActionElement element) {
        List<String> errors = errors(List.of(element));
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
    }
}
