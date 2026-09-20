package ca.teamdman.sfm.client.history.chamber;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDecimalNumberingChamberTests {
    private final SFMDecimalNumberingChamber chamber = new SFMDecimalNumberingChamber();

    @Test
    void twoItemSemanticRouteIsExactCheaperAndSupervisionReady() {
        String source = "- apples\n- bananas\n";
        SFMChamberDocumentState initial = SFMChamberDocumentState.root(source);
        SFMDecimalNumberingChamber.Target target = chamber.requireTarget(initial);

        List<SFMDecimalNumberingChamber.Transition> initialCandidates =
                chamber.transitions(initial, target);
        assertEquals(3, initialCandidates.size(), "one semantic selection plus two literal edits");
        SFMDecimalNumberingChamber.Transition select = findSelect(initialCandidates);
        assertEquals(source, select.result().text());
        assertEquals(2, select.result().selection().orElseThrow().regions().size());
        assertEquals(initial.revisionId(), select.result().parentRevisionId().orElseThrow());

        SFMDecimalNumberingChamber.Transition replace = findDecimalReplace(
                chamber.transitions(select.result(), target)
        );
        assertEquals("1. apples\n2. bananas\n", replace.result().text());
        assertTrue(target.matches(replace.result()));
        assertTrue(replace.result().selection().isEmpty());
        assertEquals(2, select.action().cost() + replace.action().cost());

        long semanticRouteCost = select.action().cost() + replace.action().cost();
        assertTrue(initialCandidates.stream()
                .filter(value -> value.action() instanceof SFMDecimalNumberingChamber.LiteralReplaceAction)
                .allMatch(value -> value.action().cost() > semanticRouteCost));

        SFMDecimalNumberingChamber.SupervisionDefinition definition = chamber.supervision(initial, target);
        assertEquals(SFMTrajectoryContract.SupervisionStatus.PLANNED, definition.contract().status());
        assertEquals(SFMTrajectoryContract.ApprovalRequirement.HUMAN,
                definition.contract().approvalRequirement());
        assertTrue(definition.contract().approval().isEmpty());
        assertFalse(definition.contract().forbiddenEffectClasses()
                .contains(SFMHistoryGraphContract.EffectClass.PURE));
        assertTrue(definition.contract().forbiddenEffectClasses()
                .contains(SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE));

        SFMDecimalNumberingChamber.SupervisionEvidence evidence = chamber.evaluateSupervision(
                definition,
                initial,
                replace.result(),
                List.of(select, replace),
                measuredEvidence(initial, select.result(), replace.result())
        );
        assertEquals(SFMTrajectoryContract.SupervisionStatus.SUPERVISION_READY, evidence.status());
        assertTrue(evidence.exactTarget());
        assertTrue(evidence.startRetained());
        assertTrue(evidence.ambientCheckoutUnchanged());
        assertTrue(evidence.retainedParentsImmutable());
        assertTrue(evidence.canonicalStart());
        assertTrue(evidence.canonicalTargetDerivation());
        assertTrue(evidence.pureEffectsOnly());
        assertTrue(evidence.generatedActionsExact());
        assertTrue(evidence.transitionChainExact());
        assertTrue(evidence.orderedWitnessesExact());
        assertEquals(SFMTrajectoryContract.SupervisionStatus.SUPERVISION_READY,
                definition.resolvedContract(evidence).status());
        assertTrue(definition.resolvedContract(evidence).approval().isEmpty(),
                "automation readiness must not manufacture human approval");
        assertTrue(definition.contract().goalPredicates().stream()
                .allMatch(predicate -> chamber.evaluatePredicate(replace.result(), predicate).satisfied()));
        assertTrue(definition.contract().hardInvariants().stream()
                .allMatch(predicate -> chamber.evaluatePredicate(
                        replace.result(),
                        predicate,
                        Optional.of(measuredEvidence(initial, select.result(), replace.result()))
                ).satisfied()));
        assertFalse(chamber.evaluatePredicate(
                initial,
                definition.contract().goalPredicates().stream()
                        .filter(predicate -> predicate.kind().equals("document-text-exact"))
                        .findFirst()
                        .orElseThrow()
        ).satisfied());

        SFMTrajectoryContract.TrajectoryStep step = replace.toTrajectoryStep(
                "step-replace",
                semanticRouteCost,
                0
        );
        assertEquals(select.result().revisionId(), step.expectedParentStateId());
        assertEquals(replace.result().revisionId(), step.predictedStateId());
        assertEquals(SFMHistoryGraphContract.EffectClass.PURE, step.effectClass());
        assertEquals(SFMHistoryGraphContract.EvaluationPolicy.FROZEN_WITNESS_REEXECUTION,
                step.evaluationPolicy());

        assertEquals(source, initial.text(), "the immutable root must remain unchanged");
        assertTrue(initial.selection().isEmpty());

        SFMDecimalNumberingChamber.SupervisionEvidence incompleteRetention = chamber.evaluateSupervision(
                definition,
                initial,
                replace.result(),
                List.of(select, replace),
                measuredEvidence(initial)
        );
        assertEquals(SFMTrajectoryContract.SupervisionStatus.BLOCKED, incompleteRetention.status());
        assertFalse(incompleteRetention.retainedParentsImmutable(),
                "route retention evidence must cover every exact parent/result state");
    }

    @Test
    void insertingThirdItemCreatesSiblingAndRetainsBothPriorHistories() {
        String source = "- apples\n- bananas\n";
        SFMChamberDocumentState initial = SFMChamberDocumentState.root(source);
        SFMDecimalNumberingChamber.Target twoItemTarget = chamber.requireTarget(initial);
        SFMDecimalNumberingChamber.Transition oldSelect = findSelect(chamber.transitions(initial, twoItemTarget));
        SFMDecimalNumberingChamber.Transition oldReplace = findDecimalReplace(
                chamber.transitions(oldSelect.result(), twoItemTarget)
        );

        int insertionOffset = "- apples\n".codePointCount(0, "- apples\n".length());
        SFMDecimalNumberingChamber.Transition insertion = chamber.literalInsert(
                oldSelect.result(),
                insertionOffset,
                "- apricots\n"
        );
        SFMChamberDocumentState threeItemState = insertion.result();

        assertEquals(initial.revisionId(), oldSelect.result().parentRevisionId().orElseThrow());
        assertEquals(oldSelect.result().revisionId(), threeItemState.parentRevisionId().orElseThrow());
        assertEquals(oldSelect.result().revisionId(), oldReplace.result().parentRevisionId().orElseThrow());
        assertNotEquals(oldReplace.result().revisionId(), threeItemState.revisionId(),
                "numbering and insertion are retained sibling revisions");
        assertEquals(source, initial.text());
        assertEquals("1. apples\n2. bananas\n", oldReplace.result().text());
        assertTrue(twoItemTarget.matches(oldReplace.result()));

        assertEquals("- apples\n- apricots\n- bananas\n", threeItemState.text());
        SFMDecimalNumberingChamber.Target threeItemTarget = chamber.requireTarget(threeItemState);
        SFMDecimalNumberingChamber.Transition newSelect = findSelect(
                chamber.transitions(threeItemState, threeItemTarget)
        );
        assertEquals(3, newSelect.result().selection().orElseThrow().regions().size());
        SFMDecimalNumberingChamber.Transition newReplace = findDecimalReplace(
                chamber.transitions(newSelect.result(), threeItemTarget)
        );
        assertEquals("1. apples\n2. apricots\n3. bananas\n", newReplace.result().text());
        assertTrue(threeItemTarget.matches(newReplace.result()));

        assertEquals("1. apples\n2. bananas\n", oldReplace.result().text(),
                "the old numbered branch must remain immutable after sibling work");
        assertTrue(twoItemTarget.matches(oldReplace.result()),
                "the old target remains meaningful on its retained branch");
        assertEquals("- apples\n- apricots\n- bananas\n", threeItemState.text(),
                "numbering creates a child and does not mutate the inserted parent");
    }

    @Test
    void unicodeTextUsesCodePointOffsetsAndStableSourceOrder() {
        String source = "- 😀 apples\r\n\t- café\n- 香蕉\n";
        SFMChamberDocumentState initial = SFMChamberDocumentState.root(source);
        SFMDecimalNumberingChamber.Target target = chamber.requireTarget(initial);
        SFMDecimalNumberingChamber.Transition select = findSelect(chamber.transitions(initial, target));
        List<SFMChamberDocumentState.SourceRegion> regions =
                select.result().selection().orElseThrow().regions();

        int secondMarkerCharOffset = source.indexOf("- café");
        int secondMarkerCodePointOffset = source.codePointCount(0, secondMarkerCharOffset);
        assertEquals(secondMarkerCodePointOffset, regions.get(1).startCodePointOffset(),
                "the astral emoji consumes one code point, not two UTF-16 units");
        assertEquals(2, regions.get(1).lineOneBased());
        assertEquals(2, regions.get(1).columnCodePointOneBased(),
                "a tab is one source code point even if its rendered width differs");
        assertEquals(List.of("-", "-", "-"), regions.stream()
                .map(region -> region.slice(source))
                .toList());
        assertTrue(regions.get(0).startCodePointOffset() < regions.get(1).startCodePointOffset());
        assertTrue(regions.get(1).startCodePointOffset() < regions.get(2).startCodePointOffset());

        SFMDecimalNumberingChamber.Transition replace = findDecimalReplace(
                chamber.transitions(select.result(), target)
        );
        assertEquals("1. 😀 apples\r\n\t2. café\n3. 香蕉\n", replace.result().text());
        assertTrue(target.matches(replace.result()));
    }

    @Test
    void malformedEmptyStaleAndInvalidWitnessInputsFailClosed() {
        SFMChamberDocumentState empty = SFMChamberDocumentState.root("");
        SFMDecimalNumberingChamber.TargetDerivation emptyTarget = chamber.deriveTarget(empty);
        assertFalse(emptyTarget.analysis().valid());
        assertTrue(emptyTarget.target().isEmpty());
        assertEquals("chamber.document.empty", emptyTarget.analysis().diagnostics().get(0).code());
        assertThrows(IllegalArgumentException.class, () -> chamber.requireTarget(empty));

        SFMChamberDocumentState malformed = SFMChamberDocumentState.root("-\n- valid\n");
        SFMDecimalNumberingChamber.TargetDerivation malformedTarget = chamber.deriveTarget(malformed);
        assertFalse(malformedTarget.analysis().valid());
        assertTrue(malformedTarget.target().isEmpty());
        assertEquals("chamber.line.marker-spacing",
                malformedTarget.analysis().diagnostics().get(0).code());

        SFMChamberDocumentState valid = SFMChamberDocumentState.root("- valid\n");
        SFMDecimalNumberingChamber.Target validTarget = chamber.requireTarget(valid);
        assertTrue(chamber.transitions(empty, validTarget).isEmpty());
        assertTrue(chamber.transitions(malformed, validTarget).isEmpty());

        String validHash = valid.textHash();
        assertThrows(IllegalArgumentException.class, () -> new SFMChamberDocumentState.SelectionWitness(
                validHash,
                SFMDecimalNumberingChamber.HYPHEN_EVALUATOR_ID,
                SFMDecimalNumberingChamber.HYPHEN_EVALUATOR_REVISION,
                SFMChamberDocumentState.OrderingPolicy.SOURCE_ORDER,
                List.of()
        ));

        String twoItems = "- one\n- two\n";
        SFMChamberDocumentState twoItemState = SFMChamberDocumentState.root(twoItems);
        List<SFMChamberDocumentState.SourceRegion> ordered = findSelect(chamber.transitions(
                twoItemState,
                chamber.requireTarget(twoItemState)
        )).result().selection().orElseThrow().regions();
        assertThrows(IllegalArgumentException.class, () -> new SFMChamberDocumentState.SelectionWitness(
                twoItemState.textHash(),
                SFMDecimalNumberingChamber.HYPHEN_EVALUATOR_ID,
                SFMDecimalNumberingChamber.HYPHEN_EVALUATOR_REVISION,
                SFMChamberDocumentState.OrderingPolicy.SOURCE_ORDER,
                List.of(ordered.get(1), ordered.get(0))
        ));

        SFMDecimalNumberingChamber.Transition select = findSelect(chamber.transitions(valid, validTarget));
        SFMChamberDocumentState sibling = chamber.literalInsert(valid, valid.codePointLength(), "- sibling\n").result();
        assertThrows(SFMDecimalNumberingChamber.StaleParentException.class,
                () -> select.action().apply(sibling));
        assertThrows(IllegalArgumentException.class,
                () -> chamber.literalInsert(valid, valid.codePointLength() + 1, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> chamber.literalInsert(valid, 0, ""));
        assertThrows(IllegalArgumentException.class,
                () -> SFMChamberDocumentState.root("\uD800"));
        assertThrows(IllegalArgumentException.class,
                () -> SFMChamberDocumentState.root("\uDC00"));
    }

    @Test
    void finiteCandidateAndLiteralEditOrderingIsDeterministic() {
        SFMChamberDocumentState initial = SFMChamberDocumentState.root("- apples\n- bananas\n");
        SFMDecimalNumberingChamber.Target target = chamber.requireTarget(initial);
        List<SFMDecimalNumberingChamber.Transition> first = chamber.transitions(initial, target);
        List<SFMDecimalNumberingChamber.Transition> second = chamber.transitions(initial, target);

        assertEquals(first, second);
        assertTrue(chamber.actionGeneratorIdentity().finite());
        assertEquals(SFMDecimalNumberingChamber.ACTION_GENERATOR_REVISION,
                chamber.actionGeneratorIdentity().revision());
        assertEquals(SFMDecimalNumberingChamber.COST_POLICY_REVISION,
                chamber.costPolicyIdentity().revision());
        SFMDecimalNumberingChamber alternateCosts = new SFMDecimalNumberingChamber(
                new SFMDecimalNumberingChamber.CostPolicy(2, 2, 11, 3, 3)
        );
        assertNotEquals(chamber.costPolicyIdentity(), alternateCosts.costPolicyIdentity(),
                "cost evidence must identify the actual configured graph costs");

        List<String> orderingKeys = first.stream()
                .map(value -> value.toPlannerTransition().orderingKey())
                .toList();
        List<String> sortedKeys = new ArrayList<>(orderingKeys);
        sortedKeys.sort(String::compareTo);
        assertEquals(sortedKeys, orderingKeys);
        assertEquals(orderingKeys.size(), new HashSet<>(orderingKeys).size());

        SFMDecimalNumberingChamber.Transition firstLiteral = findLiteral(first, 1);
        SFMDecimalNumberingChamber.Transition secondLiteral = findLiteral(
                chamber.transitions(firstLiteral.result(), target),
                2
        );
        assertEquals("1. apples\n2. bananas\n", secondLiteral.result().text());
        assertTrue(target.matches(secondLiteral.result()));
        assertTrue(firstLiteral.action().cost() > 2);
        assertTrue(secondLiteral.action().cost() > 2);
        assertTrue(firstLiteral.action().cost() + secondLiteral.action().cost()
                > chamber.costPolicy().semanticSelectionCost()
                + chamber.costPolicy().semanticDecimalReplacementCost());

        SFMDecimalNumberingChamber.Transition twoItemSelect = findSelect(first);
        SFMDecimalNumberingChamber.Transition inserted = chamber.literalInsert(
                twoItemSelect.result(),
                "- apples\n".codePointCount(0, "- apples\n".length()),
                "- apricots\n"
        );
        SFMDecimalNumberingChamber.Transition threeItemSelect = findSelect(chamber.transitions(
                inserted.result(),
                chamber.requireTarget(inserted.result())
        ));
        assertEquals(twoItemSelect.action().intent(), threeItemSelect.action().intent(),
                "semantic intent identity must be stable across branch-specific evaluations");
        assertNotEquals(
                ((SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction) twoItemSelect.action())
                        .witness().witnessHash(),
                ((SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction) threeItemSelect.action())
                        .witness().witnessHash()
        );
    }

    private static SFMDecimalNumberingChamber.Transition findSelect(
            List<SFMDecimalNumberingChamber.Transition> transitions
    ) {
        return transitions.stream()
                .filter(value -> value.action()
                        instanceof SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction)
                .findFirst()
                .orElseThrow();
    }

    private static SFMDecimalNumberingChamber.Transition findDecimalReplace(
            List<SFMDecimalNumberingChamber.Transition> transitions
    ) {
        return transitions.stream()
                .filter(value -> value.action()
                        instanceof SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction)
                .findFirst()
                .orElseThrow();
    }

    private static SFMDecimalNumberingChamber.Transition findLiteral(
            List<SFMDecimalNumberingChamber.Transition> transitions,
            int ordinalOneBased
    ) {
        return transitions.stream()
                .filter(value -> value.action()
                        instanceof SFMDecimalNumberingChamber.LiteralReplaceAction literal
                        && literal.itemOrdinalOneBased() == ordinalOneBased)
                .findFirst()
                .orElseThrow();
    }

    private static SFMDecimalNumberingChamber.ExternalInvariantEvidence measuredEvidence(
            SFMChamberDocumentState... states
    ) {
        Map<String, String> retained = java.util.Arrays.stream(states).collect(
                java.util.stream.Collectors.toMap(
                        SFMChamberDocumentState::revisionId,
                        SFMChamberDocumentState::stateHash
                )
        );
        return new SFMDecimalNumberingChamber.ExternalInvariantEvidence(
                "sha256:ambient-before-and-after",
                "sha256:ambient-before-and-after",
                retained,
                retained
        );
    }
}
