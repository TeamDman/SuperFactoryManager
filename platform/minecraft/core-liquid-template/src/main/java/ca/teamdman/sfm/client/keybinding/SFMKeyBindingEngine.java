package ca.teamdman.sfm.client.keybinding;

import java.util.ArrayList;
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
import java.util.Comparator;
{% endcase %}
import java.util.HashMap;
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
import java.util.HashSet;
{% endcase %}
import java.util.List;
import java.util.Map;
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
{% endcase %}

public final class SFMKeyBindingEngine {
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
    public enum ResetReason { FOCUS_LOST, BINDINGS_REPLACED, MANUAL }
{% when "1.19.2", "1.19.4" %}
    public enum ResetReason { FOCUS_LOST, CONTEXT_CHANGED, BINDINGS_REPLACED, MANUAL }
{% endcase %}

    private final long sequenceTimeoutTicks;
    private SFMKeyBindingSnapshot snapshot = new SFMKeyBindingSnapshot(0, List.of());
    private final Map<String, List<MatchState>> matches = new HashMap<>();
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    private SFMKeyboardUsageContextSnapshot.MatchIdentity activeContext;
{% endcase %}
    private long currentTick;

    public SFMKeyBindingEngine(long sequenceTimeoutTicks) {
        if (sequenceTimeoutTicks < 1) throw new IllegalArgumentException("Sequence timeout must be positive");
        this.sequenceTimeoutTicks = sequenceTimeoutTicks;
    }

    public void replaceBindings(SFMKeyBindingSnapshot replacement) {
        snapshot = replacement;
        reset(ResetReason.BINDINGS_REPLACED);
    }

    public void advanceTime(long tick) {
        if (tick < currentTick) throw new IllegalArgumentException("Time cannot move backwards");
        currentTick = tick;
        matches.values().forEach(states -> states.removeIf(state -> state.expiresAtTick < tick));
        matches.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public long currentTick() {
        return currentTick;
    }

    public void reset(ResetReason ignored) {
        matches.clear();
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
        activeContext = null;
{% endcase %}
    }

    public List<SFMActionInvocationIntent> accept(SFMKeyInputEvent event) {
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
        advanceTime(event.tick());
        if (event.type() != SFMKeyInputEvent.Type.PRESS) return List.of();
        SFMKeyStroke stroke = new SFMKeyStroke(event.keyCode(), event.modifiers());
        List<SFMActionInvocationIntent> emitted = new ArrayList<>();
        for (SFMKeyBinding binding : snapshot.bindings()) {
            if (!binding.enabled()) continue;
            List<SFMKeyStroke> expected = binding.sequence().strokes();
            List<MatchState> nextStates = new ArrayList<>();
            for (MatchState state : matches.getOrDefault(binding.bindingId(), List.of())) {
                if (!expected.get(state.nextStroke).equals(stroke)) continue;
                int nextStroke = state.nextStroke + 1;
                if (nextStroke == expected.size()) {
                    emit(emitted, binding, state.firstEvent, event.sequenceNumber());
                } else {
                    nextStates.add(new MatchState(
                            nextStroke,
                            state.firstEvent,
                            event.tick() + sequenceTimeoutTicks
                    ));
                }
            }
            if (expected.get(0).equals(stroke)) {
                if (expected.size() == 1) {
                    emit(emitted, binding, event.sequenceNumber(), event.sequenceNumber());
                } else {
                    nextStates.add(new MatchState(
                            1,
                            event.sequenceNumber(),
                            event.tick() + sequenceTimeoutTicks
                    ));
                }
            }
            if (nextStates.isEmpty()) matches.remove(binding.bindingId());
            else matches.put(binding.bindingId(), nextStates);
        }
        return List.copyOf(emitted);
{% when "1.19.2", "1.19.4" %}
        return accept(
                event,
                SFMKeyboardUsageContextSnapshot.global(null, () -> true),
                ignored -> true).intents();
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
    private void emit(
            List<SFMActionInvocationIntent> emitted,
            SFMKeyBinding binding,
            long firstEvent,
            long lastEvent
    ) {
        emitted.add(new SFMActionInvocationIntent(
                binding.actionId(),
                binding.commandDraft(),
                binding.bindingId(),
                snapshot.revision(),
                firstEvent,
                lastEvent
        ));
    }

{% when "1.19.2", "1.19.4" %}
{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    public SFMKeyBindingMatchResult accept(
            SFMKeyInputEvent event,
            SFMKeyboardUsageContextSnapshot context
    ) {
        return accept(event, context, ignored -> true);
    }

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    public SFMKeyBindingMatchResult accept(
            SFMKeyInputEvent event,
            SFMKeyboardUsageContextSnapshot context,
            Predicate<SFMKeyBinding> available
    ) {
        Objects.requireNonNull(event);
        Objects.requireNonNull(context);
        Objects.requireNonNull(available);
        advanceTime(event.tick());
        if (!context.isCurrent()) {
            reset(ResetReason.CONTEXT_CHANGED);
            return SFMKeyBindingMatchResult.UNMATCHED;
        }
        SFMKeyboardUsageContextSnapshot.MatchIdentity identity = context.matchIdentity();
        if (!identity.equals(activeContext)) {
            matches.clear();
            activeContext = identity;
        }
        if (event.type() != SFMKeyInputEvent.Type.PRESS) return SFMKeyBindingMatchResult.UNMATCHED;
        SFMKeyStroke stroke = new SFMKeyStroke(event.keyCode(), event.modifiers());
        List<EligibleBinding> eligible = new ArrayList<>();
        for (SFMKeyBinding binding : snapshot.bindings()) {
            int specificity = context.specificity(binding.situationId());
            if (binding.enabled() && specificity >= 0 && available.test(binding)) {
                eligible.add(new EligibleBinding(binding, specificity));
            }
        }

        List<CompletedMatch> completed = new ArrayList<>();
        Map<String, List<MatchState>> continuedMatches = new HashMap<>();
        Set<String> previouslyActiveIds = new HashSet<>(matches.keySet());
        boolean advancedExistingMatch = false;
        int bestViablePartialSpecificity = Integer.MAX_VALUE;
        for (EligibleBinding candidate : eligible) {
            SFMKeyBinding binding = candidate.binding;
            List<SFMKeyStroke> expected = binding.sequence().strokes();
            List<MatchState> nextStates = new ArrayList<>();
            for (MatchState state : matches.getOrDefault(binding.bindingId(), List.of())) {
                if (!expected.get(state.nextStroke).equals(stroke)) continue;
                advancedExistingMatch = true;
                int nextStroke = state.nextStroke + 1;
                if (nextStroke == expected.size()) {
                    completed.add(new CompletedMatch(
                            binding,
                            candidate.specificity,
                            state.firstEvent,
                            event.sequenceNumber()));
                } else {
                    addState(nextStates, new MatchState(
                            nextStroke,
                            state.firstEvent,
                            event.tick() + sequenceTimeoutTicks));
                }
            }
            if (!nextStates.isEmpty()) {
                continuedMatches.put(binding.bindingId(), nextStates);
                bestViablePartialSpecificity = Math.min(
                        bestViablePartialSpecificity,
                        candidate.specificity);
            }
        }

        matches.clear();
        if (advancedExistingMatch) {
            matches.putAll(continuedMatches);
            // Preserve overlapping prefixes for relationships that were
            // already active (for example K,K,E). A successful continuation
            // must not also start/fire an unrelated one-stroke relationship.
            for (EligibleBinding candidate : eligible) {
                SFMKeyBinding binding = candidate.binding;
                List<SFMKeyStroke> expected = binding.sequence().strokes();
                if (!previouslyActiveIds.contains(binding.bindingId())
                        || expected.size() == 1
                        || !expected.get(0).equals(stroke)) continue;
                List<MatchState> states = matches.computeIfAbsent(
                        binding.bindingId(),
                        ignored -> new ArrayList<>());
                addState(states, new MatchState(
                        1,
                        event.sequenceNumber(),
                        event.tick() + sequenceTimeoutTicks));
                bestViablePartialSpecificity = Math.min(
                        bestViablePartialSpecificity,
                        candidate.specificity);
            }
        } else {
            // Every active partial mismatched. Re-evaluate this stroke exactly
            // once as a fresh prefix so ordinary input can fall through or
            // begin another relationship without also double-matching a
            // successfully continued sequence.
            completed.clear();
            bestViablePartialSpecificity = Integer.MAX_VALUE;
            for (EligibleBinding candidate : eligible) {
                SFMKeyBinding binding = candidate.binding;
                List<SFMKeyStroke> expected = binding.sequence().strokes();
                if (!expected.get(0).equals(stroke)) continue;
                List<MatchState> nextStates = new ArrayList<>();
                int specificity = candidate.specificity;
                if (expected.size() == 1) {
                    completed.add(new CompletedMatch(
                            binding,
                            specificity,
                            event.sequenceNumber(),
                            event.sequenceNumber()));
                } else {
                    addState(nextStates, new MatchState(
                            1,
                            event.sequenceNumber(),
                            event.tick() + sequenceTimeoutTicks));
                }
                if (!nextStates.isEmpty()) {
                    matches.put(binding.bindingId(), nextStates);
                    bestViablePartialSpecificity = Math.min(
                            bestViablePartialSpecificity,
                            specificity);
                }
            }
        }

        if (!completed.isEmpty()) {
            int bestSpecificity = completed.stream()
                    .mapToInt(CompletedMatch::specificity)
                    .min().orElseThrow();
            if (bestViablePartialSpecificity < bestSpecificity) {
                return new SFMKeyBindingMatchResult(List.of(), true, List.of());
            }
            List<CompletedMatch> winners = completed.stream()
                    .filter(match -> match.specificity == bestSpecificity)
                    .sorted(Comparator.comparing(match -> match.binding.bindingId()))
                    .toList();
            matches.clear();
            if (winners.size() > 1) {
                SFMKeyBinding first = winners.get(0).binding;
                return new SFMKeyBindingMatchResult(
                        List.of(),
                        true,
                        List.of(new SFMKeyBindingConflict(
                                first.situationId(),
                                first.sequence(),
                                winners.stream().map(match -> match.binding.bindingId()).toList())));
            }
            CompletedMatch winner = winners.get(0);
            return new SFMKeyBindingMatchResult(
                    List.of(intent(
                            winner.binding,
                            winner.firstEvent,
                            winner.lastEvent)),
                    true,
                    List.of());
        }
        return new SFMKeyBindingMatchResult(List.of(), !matches.isEmpty(), List.of());
    }

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    private static void addState(List<MatchState> states, MatchState state) {
        if (!states.contains(state)) states.add(state);
    }

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    private SFMActionInvocationIntent intent(
            SFMKeyBinding binding,
            long firstEvent,
            long lastEvent
    ) {
        return new SFMActionInvocationIntent(
                binding.actionId(),
                binding.commandDraft(),
                binding.bindingId(),
                snapshot.revision(),
                firstEvent,
                lastEvent
        );
    }

{% endcase %}
    private record MatchState(int nextStroke, long firstEvent, long expiresAtTick) {
    }
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    private record EligibleBinding(SFMKeyBinding binding, int specificity) {
    }

{% endcase %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21" %}
{% when "1.19.2", "1.19.4" %}
    private record CompletedMatch(
            SFMKeyBinding binding,
            int specificity,
            long firstEvent,
            long lastEvent
    ) {
    }
{% endcase %}
}
