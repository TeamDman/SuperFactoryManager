package ca.teamdman.sfm.client.keybinding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMKeyBindingEngineTests {
    private static final SFMKeyStroke CTRL_K = SFMKeyStroke.of(75, SFMKeyModifier.CONTROL);
    private static final SFMKeyStroke CTRL_E = SFMKeyStroke.of(69, SFMKeyModifier.CONTROL);

    @Test
    void matchesSingleStrokeAndRetainsProvenance() {
        SFMKeyBindingEngine engine = engine(binding("one", SFMKeySequence.of(CTRL_K)));

        List<SFMActionInvocationIntent> intents = engine.accept(press(17, 3, 75, SFMKeyModifier.CONTROL));

        assertEquals(1, intents.size());
        assertEquals("sfm:help", intents.get(0).actionId());
        assertEquals(17, intents.get(0).firstSourceEvent());
        assertEquals(17, intents.get(0).lastSourceEvent());
        assertEquals(1, intents.get(0).bindingRevision());
    }

    @Test
    void matchesSequenceAcrossReleaseEvents() {
        SFMKeyBindingEngine engine = engine(binding("sequence", SFMKeySequence.of(CTRL_K, CTRL_E)));

        assertTrue(engine.accept(press(1, 0, 75, SFMKeyModifier.CONTROL)).isEmpty());
        assertTrue(engine.accept(release(2, 0, 75, SFMKeyModifier.CONTROL)).isEmpty());
        List<SFMActionInvocationIntent> intents = engine.accept(press(3, 4, 69, SFMKeyModifier.CONTROL));

        assertEquals(1, intents.size());
        assertEquals(1, intents.get(0).firstSourceEvent());
        assertEquals(3, intents.get(0).lastSourceEvent());
    }

    @Test
    void mismatchRestartsWhenStrokeIsAlsoSequencePrefix() {
        SFMKeyBindingEngine engine = engine(binding(
                "repeat-prefix",
                SFMKeySequence.of(CTRL_K, CTRL_K, CTRL_E)
        ));

        engine.accept(press(1, 0, 75, SFMKeyModifier.CONTROL));
        engine.accept(press(2, 1, 75, SFMKeyModifier.CONTROL));
        engine.accept(press(3, 2, 75, SFMKeyModifier.CONTROL));
        List<SFMActionInvocationIntent> intents = engine.accept(press(4, 3, 69, SFMKeyModifier.CONTROL));

        assertEquals(1, intents.size());
        assertEquals(2, intents.get(0).firstSourceEvent());
    }

    @Test
    void timeoutAndFocusLossCancelPartialSequences() {
        SFMKeyBinding binding = binding("sequence", SFMKeySequence.of(CTRL_K, CTRL_E));
        SFMKeyBindingEngine engine = engine(binding);
        engine.accept(press(1, 0, 75, SFMKeyModifier.CONTROL));
        assertTrue(engine.accept(press(2, 21, 69, SFMKeyModifier.CONTROL)).isEmpty());

        engine.accept(press(3, 30, 75, SFMKeyModifier.CONTROL));
        engine.reset(SFMKeyBindingEngine.ResetReason.FOCUS_LOST);
        assertTrue(engine.accept(press(4, 31, 69, SFMKeyModifier.CONTROL)).isEmpty());
    }

    @Test
    void replacingAndDisablingBindingsTakesEffectAtRuntime() {
        SFMKeyBindingProfile profile = new SFMKeyBindingProfile();
        profile.put(binding("one", SFMKeySequence.of(CTRL_K)));
        SFMKeyBindingEngine engine = new SFMKeyBindingEngine(20);
        engine.replaceBindings(profile.snapshot());
        assertEquals(1, engine.accept(press(1, 0, 75, SFMKeyModifier.CONTROL)).size());

        profile.setEnabled("one", false);
        engine.replaceBindings(profile.snapshot());
        assertTrue(engine.accept(press(2, 1, 75, SFMKeyModifier.CONTROL)).isEmpty());

        profile.remove("one");
        engine.replaceBindings(profile.snapshot());
        assertTrue(engine.accept(press(3, 2, 75, SFMKeyModifier.CONTROL)).isEmpty());
    }

    @Test
    void profileReportsEnabledExactSequenceConflicts() {
        SFMKeyBindingProfile profile = new SFMKeyBindingProfile();
        SFMKeyBinding first = binding("first", SFMKeySequence.of(CTRL_K));
        SFMKeyBinding second = new SFMKeyBinding(
                "second",
                "sfm:echo",
                "sfm action invoke sfm:echo value",
                SFMKeySequence.of(CTRL_K),
                true
        );
        profile.put(first);
        profile.put(second);

        assertEquals(List.of(second), profile.conflictsWith(first));
        profile.setEnabled("second", false);
        assertTrue(profile.conflictsWith(first).isEmpty());
    }

    @Test
    void replayIntoFreshEngineProducesIdenticalIntentTrace() {
        SFMKeyBinding binding = binding("sequence", SFMKeySequence.of(CTRL_K, CTRL_E));
        List<SFMKeyInputEvent> events = List.of(
                press(1, 0, 75, SFMKeyModifier.CONTROL),
                release(2, 1, 75, SFMKeyModifier.CONTROL),
                press(3, 4, 69, SFMKeyModifier.CONTROL)
        );

        assertEquals(replay(engine(binding), events), replay(engine(binding), events));
    }

    @Test
    void rejectsBackwardTimeWithoutWeakeningReplayOrdering() {
        SFMKeyBindingEngine engine = engine(binding("one", SFMKeySequence.of(CTRL_K)));
        engine.advanceTime(12);

        assertEquals(12, engine.currentTick());
        assertThrows(IllegalArgumentException.class, () -> engine.advanceTime(11));
    }

    private static SFMKeyBindingEngine engine(SFMKeyBinding binding) {
        SFMKeyBindingProfile profile = new SFMKeyBindingProfile();
        profile.put(binding);
        SFMKeyBindingEngine engine = new SFMKeyBindingEngine(20);
        engine.replaceBindings(profile.snapshot());
        return engine;
    }

    private static List<SFMActionInvocationIntent> replay(
            SFMKeyBindingEngine engine,
            List<SFMKeyInputEvent> events
    ) {
        List<SFMActionInvocationIntent> result = new ArrayList<>();
        for (SFMKeyInputEvent event : events) result.addAll(engine.accept(event));
        return result;
    }

    private static SFMKeyBinding binding(String id, SFMKeySequence sequence) {
        return new SFMKeyBinding(
                id,
                "sfm:help",
                "sfm action invoke sfm:help",
                sequence,
                true
        );
    }

    private static SFMKeyInputEvent press(
            long sequence,
            long tick,
            int key,
            SFMKeyModifier... modifiers
    ) {
        return new SFMKeyInputEvent(
                sequence,
                tick,
                key,
                SFMKeyInputEvent.Type.PRESS,
                Set.of(modifiers)
        );
    }

    private static SFMKeyInputEvent release(
            long sequence,
            long tick,
            int key,
            SFMKeyModifier... modifiers
    ) {
        return new SFMKeyInputEvent(
                sequence,
                tick,
                key,
                SFMKeyInputEvent.Type.RELEASE,
                Set.of(modifiers)
        );
    }
}
