package ca.teamdman.sfm.client.history.chamber;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.SFMBoundedTrajectoryPlanner;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure domain model for the supervised temporal decimal-numbering chamber.
 *
 * <p>The action catalog is finite for every state. It deliberately contains a
 * cheap two-action semantic route (select matching hyphens, then replace the
 * frozen witness with a decimal sequence) and more expensive literal marker
 * edits. No action performs I/O or mutates a supplied state.</p>
 */
public final class SFMDecimalNumberingChamber {
    public static final String DOMAIN_ID = "sfm:chamber/temporal-decimal-numbering";
    public static final String ACTION_GENERATOR_ID = DOMAIN_ID + "/actions";
    public static final String ACTION_GENERATOR_REVISION = "v1";
    public static final String COST_POLICY_ID = DOMAIN_ID + "/semantic-first-cost";
    public static final String COST_POLICY_REVISION = "v1";
    public static final String SELECT_ALL_HYPHENS_ACTION_ID =
            "sfm:text/selection/select/all_matching_hyphen_markers";
    public static final String REPLACE_DECIMAL_SEQUENCE_ACTION_ID =
            "sfm:text/selection/replace/decimal_sequence";
    public static final String LITERAL_REPLACE_ACTION_ID = "sfm:text/literal/replace";
    public static final String LITERAL_INSERT_ACTION_ID = "sfm:text/literal/insert";
    public static final String HYPHEN_EVALUATOR_ID = DOMAIN_ID + "/hyphen-line-markers";
    public static final String HYPHEN_EVALUATOR_REVISION = "v1";

    private final SFMChamberDocumentState.IdentityScope scope;
    private final CostPolicy costPolicy;

    public SFMDecimalNumberingChamber() {
        this(SFMChamberDocumentState.IdentityScope.testScope(), CostPolicy.semanticFirst());
    }

    public SFMDecimalNumberingChamber(CostPolicy costPolicy) {
        this(SFMChamberDocumentState.IdentityScope.testScope(), costPolicy);
    }

    public SFMDecimalNumberingChamber(SFMChamberDocumentState.IdentityScope scope) {
        this(scope, CostPolicy.semanticFirst());
    }

    public SFMDecimalNumberingChamber(
            SFMChamberDocumentState.IdentityScope scope,
            CostPolicy costPolicy
    ) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.costPolicy = Objects.requireNonNull(costPolicy, "costPolicy");
    }

    public SFMChamberDocumentState.IdentityScope scope() {
        return scope;
    }

    public CostPolicy costPolicy() {
        return costPolicy;
    }

    public SFMTrajectoryContract.ActionGeneratorIdentity actionGeneratorIdentity() {
        return new SFMTrajectoryContract.ActionGeneratorIdentity(
                ACTION_GENERATOR_ID,
                ACTION_GENERATOR_REVISION,
                true
        );
    }

    public SFMTrajectoryContract.CostPolicyIdentity costPolicyIdentity() {
        return new SFMTrajectoryContract.CostPolicyIdentity(
                COST_POLICY_ID,
                costPolicy.revisionIdentity()
        );
    }

    /** Analyze strict chamber list syntax without changing the supplied state. */
    public DocumentAnalysis analyze(SFMChamberDocumentState state) {
        requireScoped(state);
        return analyzeText(state.text());
    }

    /**
     * Derive the exact numbered target for a well-formed all-hyphen list.
     * Malformed, empty, or already-numbered inputs produce diagnostics rather
     * than an optimistic target.
     */
    public TargetDerivation deriveTarget(SFMChamberDocumentState state) {
        requireScoped(state);
        DocumentAnalysis analysis = analyze(state);
        if (!analysis.valid()) {
            return new TargetDerivation(analysis, Optional.empty());
        }
        if (analysis.items().stream().anyMatch(item -> item.markerKind() != MarkerKind.HYPHEN)) {
            List<Diagnostic> diagnostics = new ArrayList<>(analysis.diagnostics());
            diagnostics.add(new Diagnostic(
                    1,
                    "chamber.target.requires-all-hyphen-markers",
                    "An initial numbering target requires every list marker to be '-'."
            ));
            return new TargetDerivation(
                    new DocumentAnalysis(analysis.items(), diagnostics),
                    Optional.empty()
            );
        }
        List<TargetReplacement> replacements = new ArrayList<>();
        for (ItemLine item : analysis.items()) {
            replacements.add(new TargetReplacement(
                    item.ordinalOneBased(),
                    item.markerRegion(),
                    item.ordinalOneBased() + "."
            ));
        }
        String expectedText = replaceRegions(
                state.text(),
                replacements.stream()
                        .map(value -> new ReplacementEdit(value.sourceRegion(), value.replacement()))
                        .toList()
        );
        Target target = new Target(
                scope,
                scope.qualify("target", hashToken(state.revisionId(), state.stateHash(), expectedText)),
                state.revisionId(),
                state.stateHash(),
                state.textHash(),
                expectedText,
                SFMChamberDocumentState.sha256(expectedText),
                replacements
        );
        return new TargetDerivation(analysis, Optional.of(target));
    }

    public Target requireTarget(SFMChamberDocumentState state) {
        TargetDerivation derivation = deriveTarget(state);
        return derivation.target().orElseThrow(() -> new IllegalArgumentException(
                "Cannot derive decimal-numbering target: " + derivation.diagnosticsSummary()
        ));
    }

    /**
     * Generate finite, deterministic candidate transitions for a target.
     * Candidate ordering is the canonical action-intent key followed by the
     * resulting revision id, matching the tie-break vocabulary used by the
     * trajectory contract.
     */
    public List<Transition> transitions(
            SFMChamberDocumentState state,
            Target target
    ) {
        requireScoped(state);
        Objects.requireNonNull(target, "target");
        if (!target.scope().equals(scope)) {
            throw new IllegalArgumentException("Target belongs to a different chamber scope");
        }
        if (target.matches(state)) {
            return List.of();
        }
        DocumentAnalysis analysis = analyze(state);
        if (!analysis.valid() || !canonicalNumberedText(state.text(), analysis).equals(target.expectedText())) {
            return List.of();
        }

        List<ChamberAction> actions = new ArrayList<>();
        Optional<SFMChamberDocumentState.SelectionWitness> hyphenWitness = hyphenWitness(state, analysis);
        if (state.selection().isEmpty()) {
            hyphenWitness.ifPresent(witness -> actions.add(new SelectAllHyphenMarkersAction(
                    scope,
                    state.revisionId(),
                    state.stateHash(),
                    witness,
                    costPolicy.semanticSelectionCost()
            )));
        } else if (hyphenWitness.isPresent() && state.selection().equals(hyphenWitness)) {
            actions.add(new ReplaceOrderedWitnessAction(
                    scope,
                    state.revisionId(),
                    state.stateHash(),
                    hyphenWitness.orElseThrow(),
                    1,
                    1,
                    ".",
                    costPolicy.semanticDecimalReplacementCost()
            ));
        }

        for (ItemLine item : analysis.items()) {
            String expectedMarker = item.ordinalOneBased() + ".";
            if (!item.markerRegion().expectedText().equals(expectedMarker)) {
                actions.add(new LiteralReplaceAction(
                        scope,
                        state.revisionId(),
                        state.stateHash(),
                        item.markerRegion(),
                        expectedMarker,
                        item.ordinalOneBased(),
                        costPolicy.literalReplacementCost(item.markerRegion().expectedText(), expectedMarker)
                ));
            }
        }

        return actions.stream()
                .map(action -> transition(state, action))
                .sorted(Comparator.comparing(value -> value.toPlannerTransition().orderingKey()))
                .toList();
    }

    /** Create an explicit expensive literal insertion, used to form a sibling history branch. */
    public Transition literalInsert(
            SFMChamberDocumentState state,
            int codePointOffset,
            String insertedText
    ) {
        requireScoped(state);
        insertedText = SFMChamberDocumentState.requireWellFormedUtf16(insertedText, "insertedText");
        LiteralInsertAction action = new LiteralInsertAction(
                scope,
                state.revisionId(),
                state.stateHash(),
                codePointOffset,
                insertedText,
                costPolicy.literalInsertionCost(insertedText)
        );
        return transition(state, action);
    }

    public SupervisionDefinition supervision(
            SFMChamberDocumentState start,
            Target target
    ) {
        return supervision(
                start,
                target,
                new SFMTrajectoryContract.SearchBudget(512, 2_048, 5_000)
        );
    }

    /** Canonical contract with an explicitly bounded, evidence-bearing search budget. */
    public SupervisionDefinition supervision(
            SFMChamberDocumentState start,
            Target target,
            SFMTrajectoryContract.SearchBudget searchBudget
    ) {
        requireScoped(start);
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(searchBudget, "searchBudget");
        Target canonicalTarget = requireTarget(start);
        if (!canonicalTarget.equals(target)) {
            throw new IllegalArgumentException("Target is not the canonical derivation from the supervision start");
        }
        EnumSet<SFMHistoryGraphContract.EffectClass> forbidden =
                EnumSet.allOf(SFMHistoryGraphContract.EffectClass.class);
        forbidden.remove(SFMHistoryGraphContract.EffectClass.PURE);
        String revision = scope.qualify("supervision", hashToken(
                start.revisionId(),
                start.stateHash(),
                target.id(),
                target.derivationHash(),
                actionGeneratorIdentity().revision(),
                costPolicyIdentity().revision(),
                Long.toString(searchBudget.maxExpanded()),
                Long.toString(searchBudget.maxGenerated()),
                Long.toString(searchBudget.maxElapsedMillis())
        ));
        SFMTrajectoryContract.SupervisionContract contract = new SFMTrajectoryContract.SupervisionContract(
                DOMAIN_ID + "/supervision",
                revision,
                DOMAIN_ID,
                start.revisionId(),
                List.of(
                        new SFMTrajectoryContract.Predicate(
                                "goal-document-text-exact",
                                "document-text-exact",
                                target.expectedText()
                        ),
                        new SFMTrajectoryContract.Predicate(
                                "goal-document-text-sha256",
                                "document-text-sha256",
                                target.expectedTextHash()
                        ),
                        new SFMTrajectoryContract.Predicate(
                                "goal-selection-empty",
                                "selection-empty",
                                "true"
                        )
                ),
                List.of(
                        new SFMTrajectoryContract.Predicate(
                                "invariant-ambient-checkout-unchanged",
                                "ambient-checkout-mutated",
                                "false"
                        ),
                        new SFMTrajectoryContract.Predicate(
                                "invariant-parent-state-retained",
                                "parent-state-immutable",
                                "true"
                        ),
                        new SFMTrajectoryContract.Predicate(
                                "invariant-source-order",
                                "selection-ordering-policy",
                                SFMChamberDocumentState.OrderingPolicy.SOURCE_ORDER.name()
                        )
                ),
                forbidden,
                List.of(
                        new SFMTrajectoryContract.EvidenceRequirement(
                                "evidence-action-chain",
                                "deterministic-transition-chain",
                                "Record every expected parent, action intent, and resulting revision."
                        ),
                        new SFMTrajectoryContract.EvidenceRequirement(
                                "evidence-exact-target",
                                "document-text-and-hash",
                                "Record exact final text and its SHA-256 identity."
                        ),
                        new SFMTrajectoryContract.EvidenceRequirement(
                                "evidence-immutable-start",
                                "retained-start-revision",
                                "Retain the immutable start revision beside the result."
                        ),
                        new SFMTrajectoryContract.EvidenceRequirement(
                                "evidence-ordered-witness",
                                "source-ordered-code-point-regions",
                                "Record the exact ordered hyphen witness used by semantic replacement."
                        )
                ),
                searchBudget,
                Math.max(4L, target.replacements().size() * 2L + 2L),
                SFMTrajectoryContract.ApprovalRequirement.HUMAN,
                SFMTrajectoryContract.SupervisionStatus.PLANNED,
                Optional.empty()
        );
        return new SupervisionDefinition(
                scope,
                start.revisionId(),
                start.stateHash(),
                start.textHash(),
                target.derivationHash(),
                target,
                contract
        );
    }

    public SupervisionEvidence evaluateSupervision(
            SupervisionDefinition definition,
            SFMChamberDocumentState start,
            SFMChamberDocumentState result,
            List<Transition> route,
            ExternalInvariantEvidence externalEvidence
    ) {
        Objects.requireNonNull(definition, "definition");
        requireScoped(start);
        requireScoped(result);
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(externalEvidence, "externalEvidence");
        route = List.copyOf(route);

        boolean canonicalStart = definition.scope().equals(scope)
                && definition.expectedStartRevisionId().equals(start.revisionId())
                && definition.expectedStartStateHash().equals(start.stateHash())
                && definition.expectedStartTextHash().equals(start.textHash());
        boolean canonicalTarget = false;
        if (canonicalStart) {
            try {
                Target canonical = requireTarget(start);
                canonicalTarget = definition.expectedTargetDerivationHash().equals(canonical.derivationHash())
                        && definition.target().equals(canonical)
                        && definition.equals(supervision(
                                start,
                                canonical,
                                definition.contract().searchBudget()
                        ));
            } catch (RuntimeException ignored) {
                canonicalTarget = false;
            }
        }
        boolean startRetained = externalEvidence.retains(start);
        boolean ambientCheckoutUnchanged = externalEvidence.ambientCheckoutUnchanged();
        List<SFMChamberDocumentState> exactRouteStates = new ArrayList<>();
        exactRouteStates.add(start);
        for (Transition transition : route) {
            exactRouteStates.add(transition.parent());
            exactRouteStates.add(transition.result());
        }
        boolean retainedParentsImmutable = externalEvidence.retainsAll(exactRouteStates);
        boolean exactTarget = definition.target().matches(result);
        boolean pureEffectsOnly = route.stream().allMatch(
                value -> value.action().effectClass() == SFMHistoryGraphContract.EffectClass.PURE
        );
        boolean generatedActionsExact = route.stream().allMatch(transition ->
                transitions(transition.parent(), definition.target()).contains(transition));
        boolean chainValid = routeIsExact(start, result, route);
        boolean orderedWitnessesValid = witnessesAreExact(route);
        boolean ready = canonicalStart
                && canonicalTarget
                && startRetained
                && ambientCheckoutUnchanged
                && retainedParentsImmutable
                && exactTarget
                && pureEffectsOnly
                && generatedActionsExact
                && chainValid
                && orderedWitnessesValid;

        List<String> evidence = List.of(
                "action-chain=" + (chainValid ? "exact" : "invalid"),
                "ambient-checkout-before=" + externalEvidence.ambientCheckoutBeforeHash(),
                "ambient-checkout-after=" + externalEvidence.ambientCheckoutAfterHash(),
                "ambient-checkout-unchanged=" + ambientCheckoutUnchanged,
                "actual-target-hash=" + result.textHash(),
                "canonical-start=" + canonicalStart,
                "canonical-target-derivation=" + canonicalTarget,
                "effect-classes=" + (pureEffectsOnly ? "PURE" : "forbidden"),
                "generated-actions=" + (generatedActionsExact ? "canonical" : "foreign-or-tampered"),
                "expected-target-hash=" + definition.target().expectedTextHash(),
                "ordered-witnesses=" + (orderedWitnessesValid ? "valid" : "invalid"),
                "retained-parents-immutable=" + retainedParentsImmutable,
                "retained-start-state=" + start.revisionId(),
                "target-match=" + exactTarget
        );
        return new SupervisionEvidence(
                ready
                        ? SFMTrajectoryContract.SupervisionStatus.SUPERVISION_READY
                        : SFMTrajectoryContract.SupervisionStatus.BLOCKED,
                exactTarget,
                startRetained,
                ambientCheckoutUnchanged,
                retainedParentsImmutable,
                canonicalStart,
                canonicalTarget,
                pureEffectsOnly,
                generatedActionsExact,
                chainValid,
                orderedWitnessesValid,
                result.textHash(),
                evidence
        );
    }

    /** Evaluate the chamber's versioned predicate vocabulary without depending on a planner implementation. */
    public PredicateEvaluation evaluatePredicate(
            SFMChamberDocumentState state,
            SFMTrajectoryContract.Predicate predicate
    ) {
        return evaluatePredicate(state, predicate, Optional.empty());
    }

    public PredicateEvaluation evaluatePredicate(
            SFMChamberDocumentState state,
            SFMTrajectoryContract.Predicate predicate,
            Optional<ExternalInvariantEvidence> externalEvidence
    ) {
        requireScoped(state);
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(externalEvidence, "externalEvidence");
        String actual = switch (predicate.kind()) {
            case "document-text-exact" -> state.text();
            case "document-text-sha256" -> state.textHash();
            case "selection-empty" -> Boolean.toString(state.selection().isEmpty());
            case "ambient-checkout-mutated" -> externalEvidence
                    .map(value -> Boolean.toString(!value.ambientCheckoutUnchanged()))
                    .orElse(null);
            case "parent-state-immutable" -> externalEvidence
                    .map(value -> Boolean.toString(value.retainedParentsImmutable()))
                    .orElse(null);
            case "selection-ordering-policy" -> state.selection()
                    .map(value -> value.orderingPolicy().name())
                    .orElse(SFMChamberDocumentState.OrderingPolicy.SOURCE_ORDER.name());
            default -> null;
        };
        if (actual == null) {
            return new PredicateEvaluation(
                    false,
                    "unsupported chamber predicate kind=" + predicate.kind()
            );
        }
        boolean satisfied = actual.equals(predicate.expectedValue());
        return new PredicateEvaluation(
                satisfied,
                "predicate=" + predicate.id()
                        + " expected=" + predicate.expectedValue()
                        + " actual=" + actual
        );
    }

    private Transition transition(SFMChamberDocumentState parent, ChamberAction action) {
        requireScoped(parent);
        if (!action.scope().equals(scope)) {
            throw new IllegalArgumentException("Action belongs to a different chamber scope");
        }
        SFMChamberDocumentState result = action.apply(parent);
        return new Transition(parent, action, result);
    }

    private void requireScoped(SFMChamberDocumentState state) {
        Objects.requireNonNull(state, "state");
        if (!scope.equals(state.scope())) {
            throw new IllegalArgumentException("Document state belongs to a different chamber scope");
        }
    }

    private static boolean routeIsExact(
            SFMChamberDocumentState start,
            SFMChamberDocumentState expectedResult,
            List<Transition> route
    ) {
        SFMChamberDocumentState cursor = start;
        try {
            for (Transition transition : route) {
                if (!transition.parent().equals(cursor)) {
                    return false;
                }
                SFMChamberDocumentState replayed = transition.action().apply(transition.parent());
                if (!replayed.equals(transition.result())) {
                    return false;
                }
                cursor = transition.result();
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return cursor.equals(expectedResult);
    }

    private static boolean witnessesAreExact(List<Transition> route) {
        try {
            for (Transition transition : route) {
                if (transition.action() instanceof SelectAllHyphenMarkersAction select) {
                    select.witness().validateAgainst(transition.parent().text());
                    if (select.witness().orderingPolicy()
                            != SFMChamberDocumentState.OrderingPolicy.SOURCE_ORDER) {
                        return false;
                    }
                }
                if (transition.action() instanceof ReplaceOrderedWitnessAction replace) {
                    replace.witness().validateAgainst(transition.parent().text());
                    if (replace.witness().orderingPolicy()
                            != SFMChamberDocumentState.OrderingPolicy.SOURCE_ORDER) {
                        return false;
                    }
                }
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Optional<SFMChamberDocumentState.SelectionWitness> hyphenWitness(
            SFMChamberDocumentState state,
            DocumentAnalysis analysis
    ) {
        List<SFMChamberDocumentState.SourceRegion> regions = analysis.items().stream()
                .filter(item -> item.markerKind() == MarkerKind.HYPHEN)
                .map(ItemLine::markerRegion)
                .toList();
        if (regions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SFMChamberDocumentState.SelectionWitness(
                state.textHash(),
                HYPHEN_EVALUATOR_ID,
                HYPHEN_EVALUATOR_REVISION,
                SFMChamberDocumentState.OrderingPolicy.SOURCE_ORDER,
                regions
        ));
    }

    private static String canonicalNumberedText(String text, DocumentAnalysis analysis) {
        List<ReplacementEdit> edits = analysis.items().stream()
                .map(item -> new ReplacementEdit(
                        item.markerRegion(),
                        item.ordinalOneBased() + "."
                ))
                .toList();
        return replaceRegions(text, edits);
    }

    private static DocumentAnalysis analyzeText(String text) {
        List<ItemLine> items = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (text.isEmpty()) {
            diagnostics.add(new Diagnostic(
                    1,
                    "chamber.document.empty",
                    "The numbering chamber requires at least one list item."
            ));
            return new DocumentAnalysis(items, diagnostics);
        }

        int lineStart = 0;
        int lineOneBased = 1;
        while (lineStart < text.length()) {
            int lineEnd = lineStart;
            while (lineEnd < text.length()) {
                char value = text.charAt(lineEnd);
                if (value == '\r' || value == '\n') {
                    break;
                }
                lineEnd++;
            }
            parseLine(text, lineStart, lineEnd, lineOneBased, items, diagnostics);
            if (lineEnd == text.length()) {
                break;
            }
            if (text.charAt(lineEnd) == '\r'
                    && lineEnd + 1 < text.length()
                    && text.charAt(lineEnd + 1) == '\n') {
                lineStart = lineEnd + 2;
            } else {
                lineStart = lineEnd + 1;
            }
            lineOneBased++;
        }
        return new DocumentAnalysis(items, diagnostics);
    }

    private static void parseLine(
            String document,
            int lineStart,
            int lineEnd,
            int lineOneBased,
            List<ItemLine> items,
            List<Diagnostic> diagnostics
    ) {
        if (lineStart == lineEnd) {
            diagnostics.add(new Diagnostic(
                    lineOneBased,
                    "chamber.line.empty",
                    "Blank lines are not list items."
            ));
            return;
        }
        int markerStart = lineStart;
        while (markerStart < lineEnd) {
            char value = document.charAt(markerStart);
            if (value != ' ' && value != '\t') {
                break;
            }
            markerStart++;
        }
        if (markerStart == lineEnd) {
            diagnostics.add(new Diagnostic(
                    lineOneBased,
                    "chamber.line.whitespace-only",
                    "Whitespace-only lines are not list items."
            ));
            return;
        }

        MarkerKind markerKind;
        int markerEnd;
        if (document.charAt(markerStart) == '-') {
            markerKind = MarkerKind.HYPHEN;
            markerEnd = markerStart + 1;
        } else {
            markerEnd = markerStart;
            while (markerEnd < lineEnd) {
                char value = document.charAt(markerEnd);
                if (value < '0' || value > '9') {
                    break;
                }
                markerEnd++;
            }
            if (markerEnd == markerStart || markerEnd >= lineEnd || document.charAt(markerEnd) != '.') {
                diagnostics.add(new Diagnostic(
                        lineOneBased,
                        "chamber.line.invalid-marker",
                        "Expected '-' or an ASCII decimal marker followed by one space."
                ));
                return;
            }
            markerEnd++;
            markerKind = MarkerKind.DECIMAL;
        }

        if (markerEnd >= lineEnd || document.charAt(markerEnd) != ' ') {
            diagnostics.add(new Diagnostic(
                    lineOneBased,
                    "chamber.line.marker-spacing",
                    "A list marker must be followed by one space and a non-empty payload."
            ));
            return;
        }
        int payloadStart = markerEnd + 1;
        String payload = document.substring(payloadStart, lineEnd);
        if (payload.codePoints().noneMatch(value -> !Character.isWhitespace(value))) {
            diagnostics.add(new Diagnostic(
                    lineOneBased,
                    "chamber.line.empty-payload",
                    "A list item payload must contain a non-whitespace code point."
            ));
            return;
        }

        int startCodePoint = document.codePointCount(0, markerStart);
        int endCodePoint = document.codePointCount(0, markerEnd);
        int columnCodePointOneBased = document.codePointCount(lineStart, markerStart) + 1;
        SFMChamberDocumentState.SourceRegion markerRegion = new SFMChamberDocumentState.SourceRegion(
                startCodePoint,
                endCodePoint,
                lineOneBased,
                columnCodePointOneBased,
                document.substring(markerStart, markerEnd)
        );
        items.add(new ItemLine(
                items.size() + 1,
                lineOneBased,
                markerKind,
                markerRegion,
                payload
        ));
    }

    private static String replaceRegions(String text, List<ReplacementEdit> edits) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(edits, "edits");
        List<ReplacementEdit> ordered = edits.stream()
                .sorted(Comparator.comparingInt(value -> value.region().startCodePointOffset()))
                .toList();
        int previousEnd = -1;
        for (ReplacementEdit edit : ordered) {
            edit.region().validateAgainst(text);
            if (edit.region().startCodePointOffset() < previousEnd) {
                throw new IllegalArgumentException("Replacement regions overlap");
            }
            previousEnd = edit.region().endCodePointOffset();
        }
        StringBuilder answer = new StringBuilder(text);
        for (int i = ordered.size() - 1; i >= 0; i--) {
            ReplacementEdit edit = ordered.get(i);
            int start = SFMChamberDocumentState.charIndexAtCodePoint(
                    text,
                    edit.region().startCodePointOffset()
            );
            int end = SFMChamberDocumentState.charIndexAtCodePoint(
                    text,
                    edit.region().endCodePointOffset()
            );
            answer.replace(start, end, edit.replacement());
        }
        return answer.toString();
    }

    private static SFMHistoryGraphContract.ActionIntent intent(
            SFMChamberDocumentState.IdentityScope scope,
            String actionId,
            List<String> arguments
    ) {
        Objects.requireNonNull(scope, "scope");
        List<String> hashParts = new ArrayList<>();
        hashParts.add("sfm.chamber-action-intent/1");
        hashParts.add(scope.episodeId());
        hashParts.add(scope.documentId());
        hashParts.add(actionId);
        hashParts.addAll(arguments);
        String hash = SFMChamberDocumentState.fingerprint(hashParts.toArray(String[]::new));
        return new SFMHistoryGraphContract.ActionIntent(
                scope.qualify("intent", hash.substring("sha256:".length())),
                actionId,
                arguments,
                hash
        );
    }

    private static String hashToken(String... values) {
        return SFMChamberDocumentState.fingerprint(values).substring("sha256:".length());
    }

    private static String requireText(String value, String label) {
        value = SFMChamberDocumentState.requireWellFormedUtf16(value, label);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        return value;
    }

    private static void requirePositive(long value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private static void requireExpectedParent(
            SFMChamberDocumentState.IdentityScope scope,
            String expectedRevisionId,
            String expectedStateHash,
            SFMChamberDocumentState actual
    ) {
        Objects.requireNonNull(actual, "actual parent state");
        if (!scope.equals(actual.scope())
                || !expectedRevisionId.equals(actual.revisionId())
                || !expectedStateHash.equals(actual.stateHash())) {
            throw new StaleParentException(
                    "Action expected " + expectedRevisionId + " / " + expectedStateHash
                            + " but received " + actual.revisionId() + " / " + actual.stateHash()
            );
        }
    }

    public enum MarkerKind {
        HYPHEN,
        DECIMAL
    }

    public record Diagnostic(int lineOneBased, String code, String message) {
        public Diagnostic {
            if (lineOneBased < 1) {
                throw new IllegalArgumentException("Diagnostic lines are one-based");
            }
            code = requireText(code, "diagnostic.code");
            message = requireText(message, "diagnostic.message");
        }
    }

    public record ItemLine(
            int ordinalOneBased,
            int lineOneBased,
            MarkerKind markerKind,
            SFMChamberDocumentState.SourceRegion markerRegion,
            String payload
    ) {
        public ItemLine {
            if (ordinalOneBased < 1 || lineOneBased < 1) {
                throw new IllegalArgumentException("Item ordinals and lines are one-based");
            }
            Objects.requireNonNull(markerKind, "markerKind");
            Objects.requireNonNull(markerRegion, "markerRegion");
            payload = requireText(payload, "payload");
        }
    }

    public record DocumentAnalysis(List<ItemLine> items, List<Diagnostic> diagnostics) {
        public DocumentAnalysis {
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(diagnostics, "diagnostics");
            items = List.copyOf(items);
            diagnostics = diagnostics.stream()
                    .sorted(Comparator.comparingInt(Diagnostic::lineOneBased).thenComparing(Diagnostic::code))
                    .toList();
        }

        public boolean valid() {
            return !items.isEmpty() && diagnostics.isEmpty();
        }
    }

    public record TargetReplacement(
            int ordinalOneBased,
            SFMChamberDocumentState.SourceRegion sourceRegion,
            String replacement
    ) {
        public TargetReplacement {
            if (ordinalOneBased < 1) {
                throw new IllegalArgumentException("Target ordinals are one-based");
            }
            Objects.requireNonNull(sourceRegion, "sourceRegion");
            replacement = requireText(replacement, "replacement");
        }
    }

    public record Target(
            SFMChamberDocumentState.IdentityScope scope,
            String id,
            String sourceRevisionId,
            String sourceStateHash,
            String sourceTextHash,
            String expectedText,
            String expectedTextHash,
            List<TargetReplacement> replacements
    ) {
        public Target {
            Objects.requireNonNull(scope, "target.scope");
            id = requireText(id, "target.id");
            sourceRevisionId = requireText(sourceRevisionId, "target.sourceRevisionId");
            sourceStateHash = requireText(sourceStateHash, "target.sourceStateHash");
            sourceTextHash = requireText(sourceTextHash, "target.sourceTextHash");
            expectedText = requireText(expectedText, "target.expectedText");
            expectedTextHash = requireText(expectedTextHash, "target.expectedTextHash");
            if (!expectedTextHash.equals(SFMChamberDocumentState.sha256(expectedText))) {
                throw new IllegalArgumentException("Target expected-text hash does not match its text");
            }
            Objects.requireNonNull(replacements, "replacements");
            replacements = List.copyOf(replacements);
            if (replacements.isEmpty()) {
                throw new IllegalArgumentException("A numbering target must contain replacements");
            }
            int previousOrdinal = 0;
            int previousEnd = -1;
            for (TargetReplacement replacement : replacements) {
                Objects.requireNonNull(replacement, "target replacement");
                if (replacement.ordinalOneBased() != previousOrdinal + 1
                        || replacement.sourceRegion().startCodePointOffset() < previousEnd) {
                    throw new IllegalArgumentException(
                            "Target replacements must be contiguous ordinals in source order"
                    );
                }
                previousOrdinal = replacement.ordinalOneBased();
                previousEnd = replacement.sourceRegion().endCodePointOffset();
            }
        }

        public boolean matches(SFMChamberDocumentState state) {
            Objects.requireNonNull(state, "state");
            return scope.equals(state.scope())
                    && state.selection().isEmpty()
                    && expectedText.equals(state.text())
                    && expectedTextHash.equals(state.textHash());
        }

        public String derivationHash() {
            ArrayList<String> parts = new ArrayList<>();
            parts.add("sfm.chamber-target-derivation/1");
            parts.add(scope.episodeId());
            parts.add(scope.documentId());
            parts.add(sourceRevisionId);
            parts.add(sourceStateHash);
            parts.add(sourceTextHash);
            parts.add(expectedText);
            parts.add(expectedTextHash);
            for (TargetReplacement replacement : replacements) {
                parts.add(Integer.toString(replacement.ordinalOneBased()));
                parts.add(replacement.sourceRegion().canonicalForm());
                parts.add(replacement.replacement());
            }
            return SFMChamberDocumentState.fingerprint(parts.toArray(String[]::new));
        }
    }

    public record TargetDerivation(DocumentAnalysis analysis, Optional<Target> target) {
        public TargetDerivation {
            Objects.requireNonNull(analysis, "analysis");
            Objects.requireNonNull(target, "target");
            if (target.isPresent() && !analysis.valid()) {
                throw new IllegalArgumentException("An invalid source analysis cannot produce a target");
            }
        }

        public String diagnosticsSummary() {
            if (analysis.diagnostics().isEmpty()) {
                return "target unavailable";
            }
            return analysis.diagnostics().stream()
                    .map(value -> value.code() + " at line " + value.lineOneBased())
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("target unavailable");
        }
    }

    /** Explicit non-negative costs for the finite chamber action library. */
    public record CostPolicy(
            long semanticSelectionCost,
            long semanticDecimalReplacementCost,
            long literalBaseCost,
            long literalPerRemovedCodePointCost,
            long literalPerInsertedCodePointCost
    ) {
        public CostPolicy {
            requirePositive(semanticSelectionCost, "semanticSelectionCost");
            requirePositive(semanticDecimalReplacementCost, "semanticDecimalReplacementCost");
            requirePositive(literalBaseCost, "literalBaseCost");
            if (literalPerRemovedCodePointCost < 0 || literalPerInsertedCodePointCost < 0) {
                throw new IllegalArgumentException("Literal per-code-point costs must not be negative");
            }
            if (literalBaseCost <= Math.addExact(semanticSelectionCost, semanticDecimalReplacementCost)) {
                throw new IllegalArgumentException(
                        "Every literal edit must be more expensive than the complete semantic route"
                );
            }
        }

        public static CostPolicy semanticFirst() {
            return new CostPolicy(1, 1, 10, 2, 2);
        }

        /** Identity of the exact configured graph costs, not merely the policy family. */
        public String revisionIdentity() {
            if (equals(semanticFirst())) return COST_POLICY_REVISION;
            return COST_POLICY_REVISION + "/" + SFMChamberDocumentState.fingerprint(
                    "sfm.chamber-cost-policy/1",
                    Long.toString(semanticSelectionCost),
                    Long.toString(semanticDecimalReplacementCost),
                    Long.toString(literalBaseCost),
                    Long.toString(literalPerRemovedCodePointCost),
                    Long.toString(literalPerInsertedCodePointCost)
            ).substring("sha256:".length());
        }

        public long literalReplacementCost(String removed, String inserted) {
            Objects.requireNonNull(removed, "removed");
            Objects.requireNonNull(inserted, "inserted");
            long removedCost = Math.multiplyExact(
                    removed.codePointCount(0, removed.length()),
                    literalPerRemovedCodePointCost
            );
            long insertedCost = Math.multiplyExact(
                    inserted.codePointCount(0, inserted.length()),
                    literalPerInsertedCodePointCost
            );
            return Math.addExact(literalBaseCost, Math.addExact(removedCost, insertedCost));
        }

        public long literalInsertionCost(String inserted) {
            Objects.requireNonNull(inserted, "inserted");
            if (inserted.isEmpty()) {
                throw new IllegalArgumentException("Literal insertion text must not be empty");
            }
            return Math.addExact(
                    literalBaseCost,
                    Math.multiplyExact(
                            inserted.codePointCount(0, inserted.length()),
                            literalPerInsertedCodePointCost
                    )
            );
        }
    }

    /** Planner-facing pure action seam. */
    public sealed interface ChamberAction permits
            SelectAllHyphenMarkersAction,
            ReplaceOrderedWitnessAction,
            LiteralReplaceAction,
            LiteralInsertAction {
        SFMChamberDocumentState.IdentityScope scope();

        String expectedParentRevisionId();

        String expectedParentStateHash();

        long cost();

        String actionId();

        SFMHistoryGraphContract.ActionIntent intent();

        SFMHistoryGraphContract.EvaluationPolicy evaluationPolicy();

        SFMChamberDocumentState apply(SFMChamberDocumentState parent);

        default SFMHistoryGraphContract.EffectClass effectClass() {
            return SFMHistoryGraphContract.EffectClass.PURE;
        }

        default String orderingKey() {
            return intent().orderingKey();
        }
    }

    public record SelectAllHyphenMarkersAction(
            SFMChamberDocumentState.IdentityScope scope,
            String expectedParentRevisionId,
            String expectedParentStateHash,
            SFMChamberDocumentState.SelectionWitness witness,
            long cost
    ) implements ChamberAction {
        public SelectAllHyphenMarkersAction {
            Objects.requireNonNull(scope, "scope");
            expectedParentRevisionId = requireText(expectedParentRevisionId, "expectedParentRevisionId");
            expectedParentStateHash = requireText(expectedParentStateHash, "expectedParentStateHash");
            Objects.requireNonNull(witness, "witness");
            requirePositive(cost, "cost");
        }

        @Override
        public String actionId() {
            return SELECT_ALL_HYPHENS_ACTION_ID;
        }

        @Override
        public SFMHistoryGraphContract.ActionIntent intent() {
            return SFMDecimalNumberingChamber.intent(scope, actionId(), List.of(
                    "marker=-",
                    "ordering=" + witness.orderingPolicy().name(),
                    "evaluator=" + witness.evaluatorId(),
                    "evaluator-revision=" + witness.evaluatorRevision()
            ));
        }

        @Override
        public SFMHistoryGraphContract.EvaluationPolicy evaluationPolicy() {
            return SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION;
        }

        @Override
        public SFMChamberDocumentState apply(SFMChamberDocumentState parent) {
            requireExpectedParent(scope, expectedParentRevisionId, expectedParentStateHash, parent);
            DocumentAnalysis analysis = analyzeText(parent.text());
            SFMChamberDocumentState.SelectionWitness current = hyphenWitness(parent, analysis)
                    .orElseThrow(() -> new IllegalArgumentException("No matching hyphen markers remain"));
            if (!current.equals(witness)) {
                throw new IllegalArgumentException("Frozen witness is not the complete current hyphen selection");
            }
            return parent.child(orderingKey(), parent.text(), Optional.of(witness));
        }
    }

    public record ReplaceOrderedWitnessAction(
            SFMChamberDocumentState.IdentityScope scope,
            String expectedParentRevisionId,
            String expectedParentStateHash,
            SFMChamberDocumentState.SelectionWitness witness,
            int start,
            int step,
            String suffix,
            long cost
    ) implements ChamberAction {
        public ReplaceOrderedWitnessAction {
            Objects.requireNonNull(scope, "scope");
            expectedParentRevisionId = requireText(expectedParentRevisionId, "expectedParentRevisionId");
            expectedParentStateHash = requireText(expectedParentStateHash, "expectedParentStateHash");
            Objects.requireNonNull(witness, "witness");
            if (start < 0 || step <= 0) {
                throw new IllegalArgumentException("Decimal sequence start must be non-negative and step positive");
            }
            suffix = requireText(suffix, "suffix");
            requirePositive(cost, "cost");
        }

        @Override
        public String actionId() {
            return REPLACE_DECIMAL_SEQUENCE_ACTION_ID;
        }

        @Override
        public SFMHistoryGraphContract.ActionIntent intent() {
            return SFMDecimalNumberingChamber.intent(scope, actionId(), List.of(
                    "ordering=" + witness.orderingPolicy().name(),
                    "start=" + start,
                    "step=" + step,
                    "suffix=" + suffix
            ));
        }

        @Override
        public SFMHistoryGraphContract.EvaluationPolicy evaluationPolicy() {
            return SFMHistoryGraphContract.EvaluationPolicy.FROZEN_WITNESS_REEXECUTION;
        }

        @Override
        public SFMChamberDocumentState apply(SFMChamberDocumentState parent) {
            requireExpectedParent(scope, expectedParentRevisionId, expectedParentStateHash, parent);
            if (parent.selection().isEmpty() || !parent.selection().orElseThrow().equals(witness)) {
                throw new IllegalArgumentException("Decimal replacement requires its exact selected witness");
            }
            witness.validateAgainst(parent.text());
            List<ReplacementEdit> edits = new ArrayList<>();
            for (int i = 0; i < witness.regions().size(); i++) {
                SFMChamberDocumentState.SourceRegion region = witness.regions().get(i);
                if (!region.expectedText().equals("-")) {
                    throw new IllegalArgumentException("Decimal replacement witness must contain only hyphens");
                }
                int value = Math.addExact(start, Math.multiplyExact(i, step));
                edits.add(new ReplacementEdit(region, value + suffix));
            }
            String result = replaceRegions(parent.text(), edits);
            return parent.child(orderingKey(), result, Optional.empty());
        }
    }

    public record LiteralReplaceAction(
            SFMChamberDocumentState.IdentityScope scope,
            String expectedParentRevisionId,
            String expectedParentStateHash,
            SFMChamberDocumentState.SourceRegion region,
            String replacement,
            int itemOrdinalOneBased,
            long cost
    ) implements ChamberAction {
        public LiteralReplaceAction {
            Objects.requireNonNull(scope, "scope");
            expectedParentRevisionId = requireText(expectedParentRevisionId, "expectedParentRevisionId");
            expectedParentStateHash = requireText(expectedParentStateHash, "expectedParentStateHash");
            Objects.requireNonNull(region, "region");
            replacement = requireText(replacement, "replacement");
            if (itemOrdinalOneBased < 1) {
                throw new IllegalArgumentException("Literal item ordinal is one-based");
            }
            requirePositive(cost, "cost");
        }

        @Override
        public String actionId() {
            return LITERAL_REPLACE_ACTION_ID;
        }

        @Override
        public SFMHistoryGraphContract.ActionIntent intent() {
            return SFMDecimalNumberingChamber.intent(scope, actionId(), List.of(
                    "start-code-point=" + region.startCodePointOffset(),
                    "replacement=" + replacement,
                    "item-ordinal=" + itemOrdinalOneBased
            ));
        }

        @Override
        public SFMHistoryGraphContract.EvaluationPolicy evaluationPolicy() {
            return SFMHistoryGraphContract.EvaluationPolicy.FROZEN_WITNESS_REEXECUTION;
        }

        @Override
        public SFMChamberDocumentState apply(SFMChamberDocumentState parent) {
            requireExpectedParent(scope, expectedParentRevisionId, expectedParentStateHash, parent);
            String result = replaceRegions(parent.text(), List.of(new ReplacementEdit(region, replacement)));
            return parent.child(orderingKey(), result, Optional.empty());
        }
    }

    public record LiteralInsertAction(
            SFMChamberDocumentState.IdentityScope scope,
            String expectedParentRevisionId,
            String expectedParentStateHash,
            int codePointOffset,
            String insertedText,
            long cost
    ) implements ChamberAction {
        public LiteralInsertAction {
            Objects.requireNonNull(scope, "scope");
            expectedParentRevisionId = requireText(expectedParentRevisionId, "expectedParentRevisionId");
            expectedParentStateHash = requireText(expectedParentStateHash, "expectedParentStateHash");
            if (codePointOffset < 0) {
                throw new IllegalArgumentException("Insertion code-point offset must not be negative");
            }
            insertedText = requireText(insertedText, "insertedText");
            requirePositive(cost, "cost");
        }

        @Override
        public String actionId() {
            return LITERAL_INSERT_ACTION_ID;
        }

        @Override
        public SFMHistoryGraphContract.ActionIntent intent() {
            return SFMDecimalNumberingChamber.intent(scope, actionId(), List.of(
                    "code-point-offset=" + codePointOffset,
                    "inserted-text=" + insertedText
            ));
        }

        @Override
        public SFMHistoryGraphContract.EvaluationPolicy evaluationPolicy() {
            return SFMHistoryGraphContract.EvaluationPolicy.FROZEN_WITNESS_REEXECUTION;
        }

        @Override
        public SFMChamberDocumentState apply(SFMChamberDocumentState parent) {
            requireExpectedParent(scope, expectedParentRevisionId, expectedParentStateHash, parent);
            int charIndex = SFMChamberDocumentState.charIndexAtCodePoint(parent.text(), codePointOffset);
            String result = parent.text().substring(0, charIndex)
                    + insertedText
                    + parent.text().substring(charIndex);
            return parent.child(orderingKey(), result, Optional.empty());
        }
    }

    public record Transition(
            SFMChamberDocumentState parent,
            ChamberAction action,
            SFMChamberDocumentState result
    ) {
        public Transition {
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(result, "result");
            if (!action.expectedParentRevisionId().equals(parent.revisionId())
                    || !action.expectedParentStateHash().equals(parent.stateHash())) {
                throw new IllegalArgumentException("Transition action does not target its parent");
            }
            if (result.parentRevisionId().isEmpty()
                    || !result.parentRevisionId().orElseThrow().equals(parent.revisionId())) {
                throw new IllegalArgumentException("Transition result does not descend from its parent");
            }
            if (!parent.scope().equals(action.scope()) || !parent.scope().equals(result.scope())) {
                throw new IllegalArgumentException("Transition scope is inconsistent");
            }
        }

        public SFMBoundedTrajectoryPlanner.Transition<SFMChamberDocumentState> toPlannerTransition() {
            String transitionToken = hashToken(
                    parent.revisionId(),
                    action.intent().id(),
                    result.revisionId(),
                    Long.toString(action.cost())
            );
            return new SFMBoundedTrajectoryPlanner.Transition<>(
                    parent.scope().qualify("transition", transitionToken),
                    action.intent(),
                    action.evaluationPolicy(),
                    outcomeId(),
                    new SFMBoundedTrajectoryPlanner.State<>(result.revisionId(), result),
                    action.effectClass(),
                    action.cost()
            );
        }

        public String outcomeId() {
            return parent.scope().qualify("outcome", hashToken(
                    parent.revisionId(),
                    action.intent().id(),
                    result.revisionId()
            ));
        }

        public SFMTrajectoryContract.TrajectoryStep toTrajectoryStep(
                String stepId,
                long accumulatedCost,
                long estimatedRemainingCost
        ) {
            if (accumulatedCost < action.cost()) {
                throw new IllegalArgumentException("Accumulated cost cannot be less than this action cost");
            }
            return new SFMTrajectoryContract.TrajectoryStep(
                    stepId,
                    parent.revisionId(),
                    action.intent(),
                    action.evaluationPolicy(),
                    outcomeId(),
                    result.revisionId(),
                    action.effectClass(),
                    action.cost(),
                    accumulatedCost,
                    estimatedRemainingCost
            );
        }
    }

    public record SupervisionDefinition(
            SFMChamberDocumentState.IdentityScope scope,
            String expectedStartRevisionId,
            String expectedStartStateHash,
            String expectedStartTextHash,
            String expectedTargetDerivationHash,
            Target target,
            SFMTrajectoryContract.SupervisionContract contract
    ) {
        public SupervisionDefinition {
            Objects.requireNonNull(scope, "supervisionDefinition.scope");
            expectedStartRevisionId = requireText(expectedStartRevisionId, "expectedStartRevisionId");
            expectedStartStateHash = requireText(expectedStartStateHash, "expectedStartStateHash");
            expectedStartTextHash = requireText(expectedStartTextHash, "expectedStartTextHash");
            expectedTargetDerivationHash = requireText(
                    expectedTargetDerivationHash,
                    "expectedTargetDerivationHash"
            );
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(contract, "contract");
            if (!scope.equals(target.scope())) {
                throw new IllegalArgumentException("Supervision target belongs to a different scope");
            }
            if (!expectedTargetDerivationHash.equals(target.derivationHash())) {
                throw new IllegalArgumentException("Supervision target derivation hash is inconsistent");
            }
        }

        public SFMTrajectoryContract.SupervisionContract resolvedContract(SupervisionEvidence evidence) {
            Objects.requireNonNull(evidence, "evidence");
            return new SFMTrajectoryContract.SupervisionContract(
                    contract.id(),
                    contract.revision(),
                    contract.startDomainId(),
                    contract.startStateRevisionId(),
                    contract.goalPredicates(),
                    contract.hardInvariants(),
                    contract.forbiddenEffectClasses(),
                    contract.requiredEvidence(),
                    contract.searchBudget(),
                    contract.maxExecutionSteps(),
                    contract.approvalRequirement(),
                    evidence.status(),
                    Optional.empty()
            );
        }
    }

    public record SupervisionEvidence(
            SFMTrajectoryContract.SupervisionStatus status,
            boolean exactTarget,
            boolean startRetained,
            boolean ambientCheckoutUnchanged,
            boolean retainedParentsImmutable,
            boolean canonicalStart,
            boolean canonicalTargetDerivation,
            boolean pureEffectsOnly,
            boolean generatedActionsExact,
            boolean transitionChainExact,
            boolean orderedWitnessesExact,
            String actualTextHash,
            List<String> evidence
    ) {
        public SupervisionEvidence {
            Objects.requireNonNull(status, "status");
            actualTextHash = requireText(actualTextHash, "actualTextHash");
            Objects.requireNonNull(evidence, "evidence");
            evidence = List.copyOf(evidence);
            if (status == SFMTrajectoryContract.SupervisionStatus.SUPERVISION_READY
                    && !(exactTarget && startRetained && ambientCheckoutUnchanged
                    && retainedParentsImmutable && canonicalStart && canonicalTargetDerivation && pureEffectsOnly
                    && generatedActionsExact
                    && transitionChainExact && orderedWitnessesExact)) {
                throw new IllegalArgumentException("Supervision-ready evidence must satisfy every hard gate");
            }
            if (status == SFMTrajectoryContract.SupervisionStatus.APPROVED) {
                throw new IllegalArgumentException("Chamber automation cannot manufacture human approval");
            }
        }
    }

    /** Measured evidence supplied by the bounded overlay authority, never inferred by the chamber. */
    public record ExternalInvariantEvidence(
            String ambientCheckoutBeforeHash,
            String ambientCheckoutAfterHash,
            Map<String, String> retainedStatesBefore,
            Map<String, String> retainedStatesAfter
    ) {
        public ExternalInvariantEvidence {
            ambientCheckoutBeforeHash = requireText(
                    ambientCheckoutBeforeHash,
                    "ambientCheckoutBeforeHash"
            );
            ambientCheckoutAfterHash = requireText(
                    ambientCheckoutAfterHash,
                    "ambientCheckoutAfterHash"
            );
            Objects.requireNonNull(retainedStatesBefore, "retainedStatesBefore");
            Objects.requireNonNull(retainedStatesAfter, "retainedStatesAfter");
            retainedStatesBefore = Map.copyOf(retainedStatesBefore);
            retainedStatesAfter = Map.copyOf(retainedStatesAfter);
            retainedStatesBefore.forEach((id, hash) -> {
                requireText(id, "retained state id");
                requireText(hash, "retained state hash");
            });
            retainedStatesAfter.forEach((id, hash) -> {
                requireText(id, "retained state id");
                requireText(hash, "retained state hash");
            });
        }

        public boolean ambientCheckoutUnchanged() {
            return ambientCheckoutBeforeHash.equals(ambientCheckoutAfterHash);
        }

        public boolean retainedParentsImmutable() {
            return retainedStatesBefore.entrySet().stream()
                    .allMatch(entry -> entry.getValue().equals(retainedStatesAfter.get(entry.getKey())));
        }

        public boolean retains(SFMChamberDocumentState state) {
            Objects.requireNonNull(state, "state");
            return state.stateHash().equals(retainedStatesBefore.get(state.revisionId()))
                    && state.stateHash().equals(retainedStatesAfter.get(state.revisionId()));
        }

        /** Every state in an exact route must have a measured first/current identity. */
        public boolean retainsAll(List<SFMChamberDocumentState> states) {
            Objects.requireNonNull(states, "states");
            return retainedParentsImmutable() && states.stream().allMatch(this::retains);
        }
    }

    public record PredicateEvaluation(boolean satisfied, String evidence) {
        public PredicateEvaluation {
            evidence = requireText(evidence, "predicateEvaluation.evidence");
        }
    }

    public static final class StaleParentException extends IllegalStateException {
        public StaleParentException(String message) {
            super(message);
        }
    }

    private record ReplacementEdit(
            SFMChamberDocumentState.SourceRegion region,
            String replacement
    ) {
        private ReplacementEdit {
            Objects.requireNonNull(region, "region");
            replacement = requireText(replacement, "replacement");
        }
    }
}
