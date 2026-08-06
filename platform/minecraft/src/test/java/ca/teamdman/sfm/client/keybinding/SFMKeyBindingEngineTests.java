package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void completedContinuationDoesNotRestartSameStrokeAsSingleBinding() {
        SFMKeyStroke ctrlH = SFMKeyStroke.of(72, SFMKeyModifier.CONTROL);
        SFMKeyBindingEngine engine = engine(
                binding("chord", SFMKeySequence.of(CTRL_K, ctrlH)),
                binding("single", SFMKeySequence.of(ctrlH))
        );

        SFMKeyBindingMatchResult prefix = engine.accept(
                press(1, 0, 75, SFMKeyModifier.CONTROL),
                SFMKeyboardUsageContextSnapshot.testing(SFMKeyboardUsageSituations.GLOBAL));
        SFMKeyBindingMatchResult completion = engine.accept(
                press(2, 1, 72, SFMKeyModifier.CONTROL),
                SFMKeyboardUsageContextSnapshot.testing(SFMKeyboardUsageSituations.GLOBAL));

        assertTrue(prefix.consumed());
        assertTrue(prefix.intents().isEmpty());
        assertEquals(List.of("chord"), completion.intents().stream()
                .map(SFMActionInvocationIntent::bindingId).toList());
        assertTrue(completion.conflicts().isEmpty());
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
                SFMKeyboardUsageSituations.GLOBAL,
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

    @Test
    void deepestCompleteSituationWinsWithoutDoubleFiringAncestor() {
        SFMKeyBinding terminal = binding(
                "terminal", SFMKeyboardUsageSituations.TERMINAL, SFMKeySequence.of(CTRL_K));
        SFMKeyBinding workspace = binding(
                "workspace", SFMKeyboardUsageSituations.WORKSPACE, SFMKeySequence.of(CTRL_K));
        SFMKeyBindingEngine engine = engine(terminal, workspace);

        SFMKeyBindingMatchResult result = engine.accept(
                press(1, 0, 75, SFMKeyModifier.CONTROL), terminalContext());

        assertTrue(result.consumed());
        assertEquals(List.of("terminal"), result.intents().stream()
                .map(SFMActionInvocationIntent::bindingId).toList());
    }

    @Test
    void equalSpecificityConflictConsumesWithoutInvoking() {
        SFMKeyBinding first = binding(
                "first", SFMKeyboardUsageSituations.DEFAULT, SFMKeySequence.of(CTRL_K));
        SFMKeyBinding second = binding(
                "second", SFMKeyboardUsageSituations.DEFAULT, SFMKeySequence.of(CTRL_K));
        SFMKeyBindingEngine engine = engine(first, second);

        SFMKeyBindingMatchResult result = engine.accept(
                press(1, 0, 75, SFMKeyModifier.CONTROL), terminalContext());

        assertTrue(result.consumed());
        assertTrue(result.intents().isEmpty());
        assertEquals(List.of("first", "second"), result.conflicts().get(0).bindingIds());
    }

    @Test
    void equalDepthContributorParentsConflictEvenWhenTheirTraversalOrderDiffers() {
        var joined = new net.minecraft.resources.ResourceLocation("test", "joined");
        var left = new net.minecraft.resources.ResourceLocation("test", "left");
        var right = new net.minecraft.resources.ResourceLocation("test", "right");
        SFMKeyBindingEngine engine = engine(
                binding("left", left, SFMKeySequence.of(CTRL_K)),
                binding("right", right, SFMKeySequence.of(CTRL_K)));
        SFMKeyboardUsageContextSnapshot context = new SFMKeyboardUsageContextSnapshot(
                null,
                () -> true,
                null,
                null,
                0,
                0,
                List.of(joined, left, right),
                Map.of(joined, 0, left, 1, right, 1));

        SFMKeyBindingMatchResult result = engine.accept(
                press(1, 0, 75, SFMKeyModifier.CONTROL), context);

        assertTrue(result.consumed());
        assertTrue(result.intents().isEmpty());
        assertEquals(List.of("left", "right"), result.conflicts().get(0).bindingIds());
    }

    @Test
    void deeperPartialReservesStrokeBeforeBroaderCompleteAndMismatchFallsThrough() {
        SFMKeyBinding terminalChord = binding(
                "terminal-chord",
                SFMKeyboardUsageSituations.TERMINAL,
                SFMKeySequence.of(CTRL_K, CTRL_E));
        SFMKeyBinding globalSingle = binding(
                "global-single",
                SFMKeyboardUsageSituations.GLOBAL,
                SFMKeySequence.of(CTRL_K));
        SFMKeyBindingEngine engine = engine(terminalChord, globalSingle);

        SFMKeyBindingMatchResult reserved = engine.accept(
                press(1, 0, 75, SFMKeyModifier.CONTROL), terminalContext());
        SFMKeyBindingMatchResult mismatch = engine.accept(
                press(2, 1, 88, SFMKeyModifier.CONTROL), terminalContext());

        assertTrue(reserved.consumed());
        assertTrue(reserved.intents().isEmpty());
        assertFalse(mismatch.consumed());
        assertTrue(mismatch.intents().isEmpty());
    }

    @Test
    void focusRevisionChangeResetsPartialSequence() {
        SFMKeyBindingEngine engine = engine(binding(
                "sequence", SFMKeyboardUsageSituations.DEFAULT, SFMKeySequence.of(CTRL_K, CTRL_E)));
        Object host = new Object();
        var firstFocus = context(host, 1);
        var secondFocus = context(host, 2);

        assertTrue(engine.accept(press(1, 0, 75, SFMKeyModifier.CONTROL), firstFocus).consumed());
        assertFalse(engine.accept(press(2, 1, 69, SFMKeyModifier.CONTROL), secondFocus).consumed());
    }

    @Test
    void unavailableAndUnmatchedTerminalBindingsDoNotConsumePtyInput() {
        SFMKeyBinding binding = binding(
                "unavailable", SFMKeyboardUsageSituations.TERMINAL, SFMKeySequence.of(CTRL_K));
        SFMKeyBindingEngine engine = engine(binding);

        SFMKeyBindingMatchResult unavailable = engine.accept(
                press(1, 0, 75, SFMKeyModifier.CONTROL), terminalContext(), ignored -> false);
        SFMKeyBindingMatchResult unmatched = engine.accept(
                press(2, 1, 88), terminalContext());

        assertFalse(unavailable.consumed());
        assertFalse(unmatched.consumed());
    }

    private static SFMKeyBindingEngine engine(SFMKeyBinding binding) {
        return engine(new SFMKeyBinding[]{binding});
    }

    private static SFMKeyBindingEngine engine(SFMKeyBinding... bindings) {
        SFMKeyBindingProfile profile = new SFMKeyBindingProfile();
        for (SFMKeyBinding binding : bindings) profile.put(binding);
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
                SFMKeyboardUsageSituations.GLOBAL,
                sequence,
                true
        );
    }

    private static SFMKeyBinding binding(
            String id,
            net.minecraft.resources.ResourceLocation situation,
            SFMKeySequence sequence
    ) {
        return new SFMKeyBinding(
                id,
                "sfm:help",
                "sfm action invoke sfm:help",
                situation,
                sequence,
                true);
    }

    private static SFMKeyboardUsageContextSnapshot terminalContext() {
        return SFMKeyboardUsageContextSnapshot.testing(
                SFMKeyboardUsageSituations.TERMINAL,
                SFMKeyboardUsageSituations.DEFAULT,
                SFMKeyboardUsageSituations.WORKSPACE,
                SFMKeyboardUsageSituations.GLOBAL);
    }

    private static SFMKeyboardUsageContextSnapshot context(Object host, long focusRevision) {
        return new SFMKeyboardUsageContextSnapshot(
                host,
                () -> true,
                null,
                null,
                focusRevision,
                0,
                List.of(
                        SFMKeyboardUsageSituations.DEFAULT,
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyboardUsageSituations.GLOBAL));
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
