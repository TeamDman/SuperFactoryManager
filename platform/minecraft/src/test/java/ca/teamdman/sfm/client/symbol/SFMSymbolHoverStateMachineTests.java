package ca.teamdman.sfm.client.symbol;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMSymbolHoverStateMachineTests {
    private static final SFMSymbolHoverIdentity.Modifiers CTRL =
            new SFMSymbolHoverIdentity.Modifiers(true, false, false, false);
    private static final SFMSymbolHoverIdentity.Modifiers CTRL_ALT =
            new SFMSymbolHoverIdentity.Modifiers(true, true, false, false);

    @Test
    void modifierPressWithoutMovementSubmitsOnceAndOwnsExactUnderlineAfterResolution() {
        LookupHarness lookup = new LookupHarness();
        var machine = machine(lookup);
        var target = target("name", 0, 4, 0, 4, 0, 1);

        machine.observe(Optional.of(target));
        assertEquals(SFMSymbolHoverStateMachine.Phase.IDLE, machine.snapshot().phase());
        assertEquals(0, lookup.submissions.size());

        machine.modifiersChanged(CTRL);
        assertEquals(SFMSymbolHoverStateMachine.Phase.LOOKING_UP, machine.snapshot().phase());
        assertEquals(1, lookup.submissions.size());
        lookup.complete(0, SFMSymbolHoverLookup.Resolution.ACTIONABLE);

        assertEquals(SFMSymbolHoverStateMachine.Phase.ACTIONABLE, machine.snapshot().phase());
        assertEquals(Optional.of(target.range()), machine.snapshot().underlineRange());
        assertTrue(machine.snapshot().ownsLinkCursor());
    }

    @Test
    void identicalEventsNeverDuplicateSubmission() {
        LookupHarness lookup = new LookupHarness();
        var machine = machine(lookup);
        var target = target("name", 0, 4, 0, 4, 0, 1);

        machine.observe(Optional.of(target));
        machine.modifiersChanged(CTRL);
        machine.modifiersChanged(CTRL);
        machine.observe(Optional.of(target));
        machine.observe(Optional.of(target));

        assertEquals(1, lookup.submissions.size());
    }

    @Test
    void movingToAnotherSymbolCancelsSlowLookupAndRejectsItsStaleResult() {
        LookupHarness lookup = new LookupHarness();
        var machine = machine(lookup);
        machine.modifiersChanged(CTRL);
        machine.observe(Optional.of(target("first second", 0, 5, 0, 5, 0, 1)));
        machine.observe(Optional.of(target("first second", 6, 12, 6, 12, 1, 1)));

        assertEquals(2, lookup.submissions.size());
        assertEquals(1, lookup.cancellations.get());
        lookup.complete(0, SFMSymbolHoverLookup.Resolution.ACTIONABLE);
        assertEquals(SFMSymbolHoverStateMachine.Phase.LOOKING_UP, machine.snapshot().phase());
        assertEquals(0, machine.cachedIdentityCount());

        lookup.complete(1, SFMSymbolHoverLookup.Resolution.UNRESOLVED);
        assertEquals(SFMSymbolHoverStateMachine.Phase.UNRESOLVED, machine.snapshot().phase());
        assertFalse(machine.snapshot().ownsLinkCursor());
    }

    @Test
    void pointerMovementIsIdentitySensitiveAndExitCancelsTheReplacementLookup() {
        LookupHarness lookup = new LookupHarness();
        var machine = machine(lookup);
        machine.modifiersChanged(CTRL);
        machine.observe(Optional.of(target("name", 0, 4, 0, 4, 0, 1)));
        machine.observe(Optional.of(target("name", 0, 4, 0, 4, 1, 1)));

        assertEquals(2, lookup.submissions.size());
        assertEquals(1, lookup.cancellations.get());
        machine.pointerExited();
        assertEquals(2, lookup.cancellations.get());
        assertEquals(SFMSymbolHoverStateMachine.Phase.IDLE, machine.snapshot().phase());
    }

    @Test
    void unresolvedAmbiguousAndUnavailableAreTerminalNonLinks() {
        for (SFMSymbolHoverLookup.Resolution resolution : List.of(
                SFMSymbolHoverLookup.Resolution.UNRESOLVED,
                SFMSymbolHoverLookup.Resolution.AMBIGUOUS,
                SFMSymbolHoverLookup.Resolution.UNAVAILABLE
        )) {
            LookupHarness lookup = new LookupHarness();
            var machine = machine(lookup);
            machine.modifiersChanged(CTRL);
            machine.observe(Optional.of(target("name", 0, 4, 0, 4, 0, 1)));
            lookup.complete(0, resolution);
            assertEquals(resolution.name(), machine.snapshot().phase().name());
            assertTrue(machine.snapshot().underlineRange().isEmpty());
            assertFalse(machine.snapshot().ownsLinkCursor());
            assertEquals(1, machine.cachedIdentityCount());
        }
    }

    @Test
    void terminalResultIsReusedAfterModifierReleaseAndRepress() {
        LookupHarness lookup = new LookupHarness();
        var machine = machine(lookup);
        machine.observe(Optional.of(target("name", 0, 4, 0, 4, 0, 1)));
        machine.modifiersChanged(CTRL);
        lookup.complete(0, SFMSymbolHoverLookup.Resolution.ACTIONABLE);

        machine.modifiersChanged(SFMSymbolHoverIdentity.Modifiers.NONE);
        assertEquals(SFMSymbolHoverStateMachine.Phase.IDLE, machine.snapshot().phase());
        assertFalse(machine.snapshot().ownsLinkCursor());
        machine.modifiersChanged(CTRL);

        assertEquals(1, lookup.submissions.size());
        assertEquals(SFMSymbolHoverStateMachine.Phase.ACTIONABLE, machine.snapshot().phase());
        assertTrue(machine.snapshot().ownsLinkCursor());
    }

    @Test
    void terminalCacheIsBoundedAcrossDocumentAndPointerIdentities() {
        LookupHarness lookup = new LookupHarness();
        var machine = machine(lookup);
        machine.modifiersChanged(CTRL);

        for (int i = 0; i < 300; i++) {
            machine.observe(Optional.of(target("name" + i, 0, 4, 0, 4, i, i + 1L)));
            lookup.complete(i, SFMSymbolHoverLookup.Resolution.UNRESOLVED);
        }

        assertEquals(256, machine.cachedIdentityCount());
    }

    @Test
    void modifierReleaseCancelsSlowLookupAndRestoresCursor() {
        LookupHarness lookup = new LookupHarness();
        var machine = machine(lookup);
        machine.observe(Optional.of(target("name", 0, 4, 0, 4, 0, 1)));
        machine.modifiersChanged(CTRL);

        machine.modifiersChanged(SFMSymbolHoverIdentity.Modifiers.NONE);
        assertEquals(1, lookup.cancellations.get());
        assertEquals(SFMSymbolHoverStateMachine.Phase.IDLE, machine.snapshot().phase());
        assertFalse(machine.snapshot().ownsLinkCursor());
        lookup.complete(0, SFMSymbolHoverLookup.Resolution.ACTIONABLE);
        assertEquals(SFMSymbolHoverStateMachine.Phase.IDLE, machine.snapshot().phase());
    }

    @Test
    void documentMutationInvalidatesIdentityAndStaleCompletion() {
        LookupHarness lookup = new LookupHarness();
        var machine = machine(lookup);
        machine.modifiersChanged(CTRL);
        machine.observe(Optional.of(target("name", 0, 4, 0, 4, 0, 1)));

        machine.documentChanged();
        assertEquals(1, lookup.cancellations.get());
        assertEquals(SFMSymbolHoverStateMachine.Phase.IDLE, machine.snapshot().phase());
        lookup.complete(0, SFMSymbolHoverLookup.Resolution.ACTIONABLE);
        assertEquals(0, machine.cachedIdentityCount());

        machine.observe(Optional.of(target("name!", 0, 4, 0, 4, 0, 2)));
        assertEquals(2, lookup.submissions.size());
    }

    @Test
    void focusChangeAndScreenCloseCancelAndClearEverythingVisible() {
        LookupHarness lookup = new LookupHarness();
        var machine = machine(lookup);
        machine.modifiersChanged(CTRL);
        machine.observe(Optional.of(target("name", 0, 4, 0, 4, 0, 1)));
        machine.focusChanged();
        assertEquals(1, lookup.cancellations.get());
        assertEquals(SFMSymbolHoverStateMachine.Phase.IDLE, machine.snapshot().phase());
        assertEquals(SFMSymbolHoverStateMachine.CancellationCause.FOCUS_CHANGED,
                machine.lastCancellationCause());

        machine.observe(Optional.of(target("name", 0, 4, 0, 4, 0, 1)));
        assertEquals(2, lookup.submissions.size());
        machine.screenClosed();
        assertEquals(2, lookup.cancellations.get());
        assertEquals(SFMSymbolHoverStateMachine.Phase.IDLE, machine.snapshot().phase());
    }

    @Test
    void altPreservesFallbackRatherThanClaimingDefinitionNavigation() {
        LookupHarness lookup = new LookupHarness();
        var machine = machine(lookup);
        machine.observe(Optional.of(target("name", 0, 4, 0, 4, 0, 1)));
        machine.modifiersChanged(CTRL_ALT);

        assertEquals(0, lookup.submissions.size());
        machine.primaryPressed(10, 10);
        assertEquals(
                SFMSymbolHoverStateMachine.GestureKind.FALLBACK_CLICK,
                machine.primaryReleased(10, 10).kind()
        );
    }

    @Test
    void currentActionableHoverActivatesOnlyForClickNotDrag() {
        LookupHarness lookup = new LookupHarness();
        var machine = machine(lookup);
        machine.observe(Optional.of(target("name", 0, 4, 0, 4, 0, 1)));
        machine.modifiersChanged(CTRL);
        lookup.complete(0, SFMSymbolHoverLookup.Resolution.ACTIONABLE);

        machine.primaryPressed(10, 10);
        var click = machine.primaryReleased(12, 12);
        assertEquals(SFMSymbolHoverStateMachine.GestureKind.ACTIVATE_DEFINITION, click.kind());
        assertTrue(click.identity().isPresent());

        machine.primaryPressed(10, 10);
        var drag = machine.primaryReleased(20, 20);
        assertEquals(SFMSymbolHoverStateMachine.GestureKind.FALLBACK_DRAG, drag.kind());
        assertTrue(drag.identity().isEmpty());
    }

    @Test
    void hoverChangeBetweenPressAndReleaseFallsBack() {
        LookupHarness lookup = new LookupHarness();
        var machine = machine(lookup);
        machine.modifiersChanged(CTRL);
        machine.observe(Optional.of(target("first second", 0, 5, 0, 5, 0, 1)));
        lookup.complete(0, SFMSymbolHoverLookup.Resolution.ACTIONABLE);
        machine.primaryPressed(10, 10);
        machine.observe(Optional.of(target("first second", 6, 12, 6, 12, 1, 1)));

        assertEquals(
                SFMSymbolHoverStateMachine.GestureKind.FALLBACK_CLICK,
                machine.primaryReleased(10, 10).kind()
        );
    }

    @Test
    void utfAwareRangeReportsAllCoordinateSystemsAndRejectsSplitSurrogate() {
        String text = "a\uD83D\uDE00name";
        var range = SFMSymbolHoverIdentity.TextGlyphRange.fromUtf16(text, 1, 7, 3, 7);
        assertEquals(1, range.utf16Start());
        assertEquals(7, range.utf16End());
        assertEquals(1, range.scalarStart());
        assertEquals(6, range.scalarEnd());
        assertEquals(1, range.utf8Start());
        assertEquals(9, range.utf8End());
        assertEquals(3, range.glyphStart());
        assertEquals(7, range.glyphEnd());

        assertThrows(
                IllegalArgumentException.class,
                () -> SFMSymbolHoverIdentity.TextGlyphRange.fromUtf16(text, 2, 7, 3, 7)
        );

        String longPrefix = "λ😀".repeat(4_096) + "target";
        int start = longPrefix.length() - "target".length();
        var tail = SFMSymbolHoverIdentity.TextGlyphRange.fromUtf16(
                longPrefix, start, longPrefix.length(), 0, 6
        );
        assertEquals(8_192, tail.scalarStart());
        assertEquals(6 * 4_096L, tail.utf8Start());
        assertEquals(tail.utf8Start() + 6, tail.utf8End());
    }

    private static SFMSymbolHoverStateMachine machine(LookupHarness lookup) {
        return new SFMSymbolHoverStateMachine(lookup, SFMSymbolHoverStateMachine.DragThreshold.euclidean(4));
    }

    private static SFMSymbolHoverStateMachine.Target target(
            String text,
            int utf16Start,
            int utf16End,
            int glyphStart,
            int glyphEnd,
            int pointerGlyph,
            long documentGeneration
    ) {
        return new SFMSymbolHoverStateMachine.Target(
                new SFMSymbolHoverIdentity.EditorOrigin("screen-1", "workspace-1", "stack-1", "panel-1", "editor-1", 3),
                new SFMSymbolHoverIdentity.DocumentVersion(
                        "file:///workspace/A.java",
                        "content-hash:" + text,
                        documentGeneration
                ),
                SFMSymbolHoverIdentity.TextGlyphRange.fromUtf16(
                        text,
                        utf16Start,
                        utf16End,
                        glyphStart,
                        glyphEnd
                ),
                new SFMSymbolHoverIdentity.PointerState(pointerGlyph, false)
        );
    }

    private static final class LookupHarness implements SFMSymbolHoverLookup {
        private final List<CompletableFuture<Resolution>> submissions = new ArrayList<>();
        private final AtomicInteger cancellations = new AtomicInteger();

        @Override
        public Query submit(SFMSymbolHoverIdentity identity) {
            CompletableFuture<Resolution> result = new CompletableFuture<>();
            submissions.add(result);
            return new Query(result, cancellations::incrementAndGet);
        }

        void complete(int index, Resolution resolution) {
            submissions.get(index).complete(resolution);
        }
    }
}
