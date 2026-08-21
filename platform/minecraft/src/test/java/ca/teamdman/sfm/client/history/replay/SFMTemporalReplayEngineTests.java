package ca.teamdman.sfm.client.history.replay;

import ca.teamdman.sfm.client.history.chamber.SFMChamberDocumentState;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingChamber;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTemporalReplayEngineTests {
    private static final String TWO_ITEMS = "- apples\n- bananas\n";
    private static final String TWO_ITEMS_NUMBERED = "1. apples\n2. bananas\n";
    private static final String THREE_ITEMS = "- apples\n- apricots\n- bananas\n";
    private static final String THREE_ITEMS_NUMBERED = "1. apples\n2. apricots\n3. bananas\n";

    private final SFMDecimalNumberingChamber chamber = new SFMDecimalNumberingChamber();
    private final SFMTemporalReplayEngine engine = new SFMTemporalReplayEngine(chamber);

    @Test
    void exactReplayReusesFrozenActionsAndReproducesTwoItemResult() {
        SemanticRoute source = semanticRoute(TWO_ITEMS);
        SFMChamberDocumentState explicitParent = SFMChamberDocumentState.root(
                chamber.scope(),
                TWO_ITEMS
        );

        SFMTemporalReplayEngine.ReplayResult result = engine.exactReplay(
                source.transitions(),
                explicitParent,
                1
        );

        assertTrue(result.succeeded());
        assertEquals(SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY, result.report().mode());
        assertEquals(SFMTemporalReplayArchive.ReplayStatus.SUCCEEDED, result.report().status());
        assertEquals(TWO_ITEMS_NUMBERED, result.resultingState().orElseThrow().text());
        assertEquals(source.numbered(), result.resultingState().orElseThrow());
        assertEquals(2, result.stagedTransitions().size());
        for (int index = 0; index < source.transitions().size(); index++) {
            assertEquals(
                    source.transitions().get(index).action(),
                    result.stagedTransitions().get(index).transition().action(),
                    "exact replay must reuse every frozen witness and action parameter"
            );
        }
        assertEquals(
                source.transitions().stream()
                        .map(SFMTemporalReplayEngine::chamberTransitionId)
                        .toList(),
                result.report().sourceTransitionIds()
        );
        assertTrue(result.report().actionLineage().stream().allMatch(lineage ->
                lineage.sourceWitnessId().equals(lineage.resultingWitnessId())
        ));
        assertThrows(UnsupportedOperationException.class, () -> result.stagedTransitions().add(
                result.stagedTransitions().get(0)
        ));
        assertEquals(TWO_ITEMS, source.initial().text());
        assertEquals(TWO_ITEMS_NUMBERED, source.numbered().text());
    }

    @Test
    void exactReplayRejectsChangedParentWithoutPublishingAPrefix() {
        SemanticRoute source = semanticRoute(TWO_ITEMS);
        SFMChamberDocumentState changedParent = chamber.literalInsert(
                source.initial(),
                "- apples\n".codePointCount(0, "- apples\n".length()),
                "- apricots\n"
        ).result();

        SFMTemporalReplayEngine.ReplayResult result = engine.exactReplay(
                source.transitions(),
                changedParent,
                2
        );

        assertFalse(result.succeeded());
        assertEquals(
                SFMTemporalReplayArchive.ReplayStatus.PRECONDITION_MISMATCH,
                result.report().status()
        );
        assertTrue(result.stagedTransitions().isEmpty(), "no exact-replay prefix may escape");
        assertTrue(result.resultingState().isEmpty());
        assertTrue(result.report().resultingTransitionIds().isEmpty());
        assertEquals(2, result.report().actionLineage().size());
        assertTrue(result.report().actionLineage().stream().allMatch(lineage ->
                lineage.resultingTransitionId().isEmpty()
                        && lineage.status()
                        == SFMTemporalReplayArchive.ReplayStatus.PRECONDITION_MISMATCH
        ));
        assertEquals(THREE_ITEMS, changedParent.text());
        assertEquals(TWO_ITEMS_NUMBERED, source.numbered().text());
    }

    @Test
    void semanticRebaseReevaluatesThreeRegionsAndRetainsWitnessLineage() {
        SemanticRoute source = semanticRoute(TWO_ITEMS);
        SFMChamberDocumentState changedParent = chamber.literalInsert(
                source.initial(),
                "- apples\n".codePointCount(0, "- apples\n".length()),
                "- apricots\n"
        ).result();
        String originalInitialHash = source.initial().stateHash();
        String originalNumberedHash = source.numbered().stateHash();
        String changedParentHash = changedParent.stateHash();

        SFMTemporalReplayEngine.ReplayResult result = engine.semanticRebase(
                source.transitions(),
                changedParent,
                3
        );

        assertTrue(result.succeeded());
        assertEquals(SFMTemporalReplayArchive.ReplayMode.SEMANTIC_REBASE, result.report().mode());
        assertEquals(THREE_ITEMS_NUMBERED, result.resultingState().orElseThrow().text());
        assertEquals(2, result.stagedTransitions().size());
        SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction rebasedSelect =
                (SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction)
                        result.stagedTransitions().get(0).transition().action();
        SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction rebasedReplace =
                (SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction)
                        result.stagedTransitions().get(1).transition().action();
        assertEquals(3, rebasedSelect.witness().regions().size());
        assertEquals(rebasedSelect.witness(), rebasedReplace.witness());
        assertEquals(source.replaceAction().start(), rebasedReplace.start());
        assertEquals(source.replaceAction().step(), rebasedReplace.step());
        assertEquals(source.replaceAction().suffix(), rebasedReplace.suffix());

        for (int index = 0; index < source.transitions().size(); index++) {
            SFMDecimalNumberingChamber.Transition oldTransition = source.transitions().get(index);
            SFMDecimalNumberingChamber.Transition newTransition =
                    result.stagedTransitions().get(index).transition();
            SFMChamberDocumentState.SelectionWitness oldWitness = index == 0
                    ? source.selectAction().witness()
                    : source.replaceAction().witness();
            SFMChamberDocumentState.SelectionWitness newWitness = index == 0
                    ? rebasedSelect.witness()
                    : rebasedReplace.witness();
            String oldWitnessId = SFMTemporalReplayEngine.chamberWitnessId(
                    oldTransition.parent(),
                    oldWitness
            );
            String newWitnessId = SFMTemporalReplayEngine.chamberWitnessId(
                    newTransition.parent(),
                    newWitness
            );
            assertNotEquals(oldWitnessId, newWitnessId);
            assertEquals(oldWitnessId,
                    result.report().actionLineage().get(index).sourceWitnessId().orElseThrow());
            assertEquals(newWitnessId,
                    result.report().actionLineage().get(index).resultingWitnessId().orElseThrow());
        }

        assertEquals(originalInitialHash, source.initial().stateHash());
        assertEquals(TWO_ITEMS, source.initial().text());
        assertEquals(originalNumberedHash, source.numbered().stateHash());
        assertEquals(TWO_ITEMS_NUMBERED, source.numbered().text());
        assertEquals(changedParentHash, changedParent.stateHash());
        assertEquals(THREE_ITEMS, changedParent.text());
    }

    @Test
    void ineligibleAndConflictingRoutesReturnTypedEmptyResults() {
        SemanticRoute source = semanticRoute(TWO_ITEMS);
        SFMDecimalNumberingChamber.Transition literal = chamber.literalInsert(
                source.initial(),
                source.initial().codePointLength(),
                "- cherries\n"
        );
        SFMTemporalReplayEngine.ReplayResult ineligible = engine.semanticRebase(
                List.of(literal),
                source.initial(),
                4
        );
        assertEquals(SFMTemporalReplayArchive.ReplayStatus.INELIGIBLE_ACTION,
                ineligible.report().status());
        assertTrue(ineligible.stagedTransitions().isEmpty());
        assertTrue(ineligible.resultingState().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> engine.exactReplay(
                List.of(literal, literal, literal),
                source.initial(),
                5
        ));

        SemanticRoute unrelated = semanticRoute("- oranges\n- pears\n");
        List<SFMDecimalNumberingChamber.Transition> brokenChain = List.of(
                source.selectTransition(),
                unrelated.replaceTransition()
        );
        SFMTemporalReplayEngine.ReplayResult conflict = engine.exactReplay(
                brokenChain,
                source.initial(),
                6
        );
        assertEquals(SFMTemporalReplayArchive.ReplayStatus.CONFLICT, conflict.report().status());
        assertTrue(conflict.stagedTransitions().isEmpty());
        assertTrue(conflict.resultingState().isEmpty());
        assertTrue(conflict.report().actionLineage().stream().allMatch(lineage ->
                lineage.resultingTransitionId().isEmpty()
        ));
    }

    @Test
    void replayIsDeterministicAcrossFreshInvocations() {
        SemanticRoute source = semanticRoute(TWO_ITEMS);
        SFMChamberDocumentState changedParent = chamber.literalInsert(
                source.initial(),
                "- apples\n".codePointCount(0, "- apples\n".length()),
                "- apricots\n"
        ).result();

        assertEquals(
                engine.exactReplay(source.transitions(), source.initial(), 7),
                engine.exactReplay(source.transitions(), source.initial(), 7)
        );
        assertEquals(
                engine.semanticRebase(source.transitions(), changedParent, 8),
                engine.semanticRebase(source.transitions(), changedParent, 8)
        );
    }

    private SemanticRoute semanticRoute(String text) {
        SFMChamberDocumentState initial = SFMChamberDocumentState.root(chamber.scope(), text);
        SFMDecimalNumberingChamber.Target target = chamber.requireTarget(initial);
        SFMDecimalNumberingChamber.Transition select = chamber.transitions(initial, target)
                .stream()
                .filter(transition -> transition.action()
                        instanceof SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction)
                .findFirst()
                .orElseThrow();
        SFMDecimalNumberingChamber.Transition replace = chamber.transitions(select.result(), target)
                .stream()
                .filter(transition -> transition.action()
                        instanceof SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction)
                .findFirst()
                .orElseThrow();
        return new SemanticRoute(
                initial,
                select,
                (SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction) select.action(),
                replace,
                (SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction) replace.action(),
                replace.result()
        );
    }

    private record SemanticRoute(
            SFMChamberDocumentState initial,
            SFMDecimalNumberingChamber.Transition selectTransition,
            SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction selectAction,
            SFMDecimalNumberingChamber.Transition replaceTransition,
            SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction replaceAction,
            SFMChamberDocumentState numbered
    ) {
        List<SFMDecimalNumberingChamber.Transition> transitions() {
            return List.of(selectTransition, replaceTransition);
        }
    }
}
