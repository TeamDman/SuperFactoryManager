package ca.teamdman.sfm.client.keybinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SFMKeyBindingEngine {
    public enum ResetReason { FOCUS_LOST, BINDINGS_REPLACED, MANUAL }

    private final long sequenceTimeoutTicks;
    private SFMKeyBindingSnapshot snapshot = new SFMKeyBindingSnapshot(0, List.of());
    private final Map<String, List<MatchState>> matches = new HashMap<>();
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
    }

    public List<SFMActionInvocationIntent> accept(SFMKeyInputEvent event) {
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
    }

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

    private record MatchState(int nextStroke, long firstEvent, long expiresAtTick) {
    }
}
