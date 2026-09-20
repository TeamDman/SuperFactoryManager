package ca.teamdman.sfm.client.keybinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class SFMKeyBindingEngine {
    public enum ResetReason { FOCUS_LOST, CONTEXT_CHANGED, BINDINGS_REPLACED, MANUAL }

    private final long sequenceTimeoutTicks;
    private SFMKeyBindingSnapshot snapshot = new SFMKeyBindingSnapshot(0, List.of());
    private final Map<String, List<MatchState>> matches = new HashMap<>();
    private SFMKeyboardUsageContextSnapshot.MatchIdentity activeContext;
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
        activeContext = null;
    }

    public List<SFMActionInvocationIntent> accept(SFMKeyInputEvent event) {
        return accept(
                event,
                SFMKeyboardUsageContextSnapshot.global(null, () -> true),
                ignored -> true).intents();
    }

    public SFMKeyBindingMatchResult accept(
            SFMKeyInputEvent event,
            SFMKeyboardUsageContextSnapshot context
    ) {
        return accept(event, context, ignored -> true);
    }

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

    private static void addState(List<MatchState> states, MatchState state) {
        if (!states.contains(state)) states.add(state);
    }

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

    private record MatchState(int nextStroke, long firstEvent, long expiresAtTick) {
    }

    private record EligibleBinding(SFMKeyBinding binding, int specificity) {
    }

    private record CompletedMatch(
            SFMKeyBinding binding,
            int specificity,
            long firstEvent,
            long lastEvent
    ) {
    }
}
